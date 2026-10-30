package com.angelsheriffmafia.lan

import com.angelsheriffmafia.engine.Action
import com.angelsheriffmafia.engine.Event
import com.angelsheriffmafia.engine.Game
import com.angelsheriffmafia.engine.GameState
import com.angelsheriffmafia.engine.NewPlayer
import com.angelsheriffmafia.engine.Phase
import com.angelsheriffmafia.engine.PlayMode
import com.angelsheriffmafia.engine.Rules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Something the host phone should say out loud. */
sealed interface Cue {
    /** Roles have been dealt to everyone's phone. */
    data object Dealt : Cue

    data class Game(val event: Event) : Cue

    /** The discussion clock started. */
    data object Discussion : Cue

    /** The discussion clock ran out. */
    data object TimeUp : Cue
}

/** What the host can do from their own phone. Players' moves come in as [PlayerAction]s instead. */
sealed interface HostCommand {
    /** Deal a new game to everyone at the table. Also "play again". */
    data object Deal : HostCommand

    /** Start the night without waiting for everyone to check their card. */
    data object EndDeal : HostCommand

    /** From dawn: start the discussion clock, or go to the end if someone has won. */
    data object StartTalking : HostCommand

    data class AddTime(val seconds: Int) : HostCommand
    data object StartVote : HostCommand

    /** Count the votes now, without waiting for the last few. */
    data object EndVote : HostCommand

    /** From the vote result: night falls, or the game ends. */
    data object Continue : HostCommand

    /** Abandon the current game and go back to the lobby. */
    data object BackToLobby : HostCommand
}

class TableException(message: String) : Exception(message)

/**
 * The host's side of a game played on everyone's own phone. Runs the rules engine, keeps the
 * seats, the clocks and who is connected, and hands each seat its own [TableView].
 *
 * Thread-safe: the web server calls it from many threads and the host's screen from the main one.
 * Listeners are called while the session is locked, so they must be quick and never wait on
 * another thread.
 */
class HostSession(
    initial: TableState,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    interface Listener {
        /** The table changed. Save it so the game survives the app restarting. */
        fun onChanged(table: TableState) {}

        /** Lines for the narrator, after the change that caused them. */
        fun onCues(cues: List<Cue>, table: TableState) {}
    }

    @Volatile
    var listener: Listener? = null

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val random = SecureRandom()
    private var table: TableState = initial
    private var versionCounter = 0L
    private val streams = HashMap<Int, Int>()
    private val _version = MutableStateFlow(0L)

    /** Goes up on every change, including players connecting and disconnecting. */
    val version: StateFlow<Long> = _version.asStateFlow()

    val state: TableState get() = lock.withLock { table }

    /* ---------- Seats ---------- */

    fun join(request: JoinRequest): JoinResponse = lock.withLock {
        if (table.closed) return JoinResponse(error = "This table has closed.")
        request.token?.let { token -> table.seatByToken(token)?.let { return JoinResponse(token = it.token) } }
        val name = cleanName(request.name)
        if (name.isEmpty()) return JoinResponse(error = "Type your name first.")
        val pronunciation = request.pronunciation?.let(::cleanPronunciation)
        val existing = table.seats.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            if (existing.token.isNotEmpty()) {
                return JoinResponse(
                    error = "Someone here is already called ${existing.name}. Pick another name, " +
                        "or if that's you on a new phone, ask the host to let you back in.",
                )
            }
            // The host freed this seat for a new phone: whoever gives the name gets it back.
            val token = newToken()
            change(table.copy(seats = table.seats.map { if (it.id == existing.id) it.copy(token = token, fromApp = request.app) else it }))
            return JoinResponse(token = token)
        }
        if (table.seats.size >= Rules.MAX_PLAYERS) return JoinResponse(error = "The table is full. At most ${Rules.MAX_PLAYERS} can play.")
        val token = newToken()
        val seat = Seat(id = table.nextSeatId, name = name, pronunciation = pronunciation, token = token, fromApp = request.app)
        change(table.copy(seats = table.seats + seat, nextSeatId = table.nextSeatId + 1))
        JoinResponse(token = token)
    }

    /** The host's own name, and whether the host plays or only runs the table. */
    fun setHost(name: String, pronunciation: String?, plays: Boolean): Result<Unit> = locked {
        val clean = cleanName(name)
        requireLobby()
        val others = table.seats.filter { !it.host }
        if (plays && clean.isEmpty()) throw TableException("Type your name first.")
        if (plays && others.any { it.name.equals(clean, ignoreCase = true) }) {
            throw TableException("Someone at the table is already called $clean.")
        }
        val seats = if (plays) {
            val mine = table.hostSeat?.copy(name = clean, pronunciation = pronunciation?.let(::cleanPronunciation))
                ?: Seat(id = table.nextSeatId, name = clean, pronunciation = pronunciation?.let(::cleanPronunciation), token = newToken(), host = true)
            listOf(mine) + others
        } else {
            others
        }
        if (seats.size > Rules.MAX_PLAYERS) throw TableException("The table is full. At most ${Rules.MAX_PLAYERS} can play.")
        val nextId = if (plays && table.hostSeat == null) table.nextSeatId + 1 else table.nextSeatId
        change(table.copy(name = tableName(clean.takeIf { it.isNotEmpty() }), seats = seats, nextSeatId = nextId))
    }

    fun setRules(rules: Rules): Result<Unit> = locked {
        requireLobby()
        change(table.copy(rules = rules))
    }

    /** Takes someone off the table. Not while they're in a game. */
    fun removeSeat(id: Int): Result<Unit> = locked {
        val seat = table.seat(id) ?: throw TableException("They've already left.")
        if (seat.host) throw TableException("The host can't leave their own table.")
        if (seat.player != null && table.game?.phase !is Phase.GameOver && table.game != null) {
            throw TableException("${seat.name} is in the game. End the game first.")
        }
        change(table.copy(seats = table.seats - seat))
    }

    /**
     * For a player whose phone died or who opened a new browser: signs out their old phone
     * and gives the seat to the next person who joins with that name.
     */
    fun letBackIn(id: Int): Result<Unit> = locked {
        val seat = table.seat(id) ?: throw TableException("They've already left.")
        if (seat.host) throw TableException("That's your own seat.")
        change(table.copy(seats = table.seats.map { if (it.id == id) it.copy(token = "") else it }))
    }

    fun close() = lock.withLock {
        if (!table.closed) change(table.copy(closed = true, nightEndsAt = null))
    }

    /* ---------- Views ---------- */

    /** The view for the phone holding [token], or null if that token has no seat. */
    fun view(token: String): TableView? = lock.withLock {
        val seat = table.seatByToken(token) ?: return null
        buildView(seat)
    }

    /** The host's own view: their seat if they play, otherwise the public view. */
    fun hostView(): TableView = lock.withLock { buildView(table.hostSeat) }

    fun seatIdFor(token: String): Int? = lock.withLock { table.seatByToken(token)?.id }

    private fun buildView(seat: Seat?) = Views.build(table, seat, versionCounter, clock(), ::isConnected)

    private fun isConnected(seat: Seat) = seat.host || (streams[seat.id] ?: 0) > 0

    /* ---------- Connections ---------- */

    fun streamOpened(seatId: Int) = lock.withLock {
        streams[seatId] = (streams[seatId] ?: 0) + 1
        bump()
    }

    fun streamClosed(seatId: Int) = lock.withLock {
        val left = (streams[seatId] ?: 1) - 1
        if (left <= 0) streams.remove(seatId) else streams[seatId] = left
        bump()
    }

    /** Blocks until the version passes [after] or [timeoutMillis] goes by. Returns the current version. */
    fun awaitVersion(after: Long, timeoutMillis: Long): Long = lock.withLock {
        var left = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (versionCounter <= after && left > 0) left = changed.awaitNanos(left)
        versionCounter
    }

    /* ---------- Moves ---------- */

    fun act(token: String, action: PlayerAction): ActResponse {
        val result = locked {
            val seat = table.seatByToken(token) ?: throw TableException("You're no longer at this table.")
            playerAct(seat, action)
        }
        return result.fold({ ActResponse(ok = true) }, { ActResponse(ok = false, error = it.message) })
    }

    /** A move by the host as a player, from the host's own phone. */
    fun hostAct(action: PlayerAction): Result<Unit> = locked {
        val seat = table.hostSeat ?: throw TableException("You're not playing this game.")
        playerAct(seat, action)
    }

    private fun playerAct(seat: Seat, action: PlayerAction) {
        if (action == PlayerAction.Leave) {
            if (seat.host) throw TableException("The host can't leave their own table.")
            val game = table.game
            if (seat.player != null && game != null && game.phase !is Phase.GameOver) {
                throw TableException("You can't leave in the middle of a game.")
            }
            change(table.copy(seats = table.seats - seat))
            return
        }
        val game = table.game ?: throw TableException("The game hasn't started yet.")
        val player = seat.player ?: throw TableException("You're not in this game. You'll be dealt in next time.")
        val engineAction = when (action) {
            PlayerAction.Seen -> Action.RoleSeen(player)
            is PlayerAction.Pick -> Action.Pick(player, action.target)
            is PlayerAction.Vote -> Action.Vote(player, action.target)
            PlayerAction.Leave -> error("handled above")
        }
        // Seeing your card twice is harmless: the second tap just does nothing.
        if (action == PlayerAction.Seen && Game.hasActed(game, player)) return
        reduce(engineAction)
    }

    fun host(command: HostCommand): Result<Unit> = locked {
        if (table.closed) throw TableException("This table has closed.")
        when (command) {
            HostCommand.Deal -> deal()
            HostCommand.EndDeal -> reduce(Action.EndDeal)
            HostCommand.StartTalking -> {
                reduce(Action.Advance)
                if (table.game?.phase is Phase.Discussion) {
                    reduce(Action.StartDiscussion(clock()))
                    cue(Cue.Discussion)
                }
            }
            is HostCommand.AddTime -> reduce(Action.AddTime(command.seconds))
            HostCommand.StartVote -> reduce(Action.StartVote)
            HostCommand.EndVote -> reduce(Action.EndVote)
            HostCommand.Continue -> reduce(Action.Advance)
            HostCommand.BackToLobby -> change(
                table.copy(game = null, nightEndsAt = null, seats = table.seats.map { it.copy(player = null) }),
            )
        }
    }

    private fun deal() {
        val game = table.game
        if (game != null && game.phase !is Phase.GameOver) throw TableException("A game is already running.")
        val seats = table.seats
        if (seats.size < Rules.MIN_PLAYERS) throw TableException("You need at least ${Rules.MIN_PLAYERS} players. ${seats.size} so far.")
        val names = seats.map { NewPlayer(it.name, it.pronunciation) }
        val dealt = try {
            Game.newGame(names, table.rules, seed = random.nextLong(), mode = PlayMode.OWN_PHONES)
        } catch (e: IllegalArgumentException) {
            throw TableException(e.message ?: "Can't deal with these players.")
        }
        change(
            table.copy(
                game = dealt,
                gameNumber = table.gameNumber + 1,
                nightEndsAt = null,
                timeUpCalledFor = null,
                seats = seats.mapIndexed { i, s -> s.copy(player = i) },
            ),
        )
        cue(Cue.Dealt)
    }

    /* ---------- Clocks ---------- */

    /** Call a few times a second. Ends the night when its time is up and calls time on the discussion. */
    fun tick() {
        locked {
            if (table.closed) return@locked
            val game = table.game ?: return@locked
            val now = clock()
            val nightEnds = table.nightEndsAt
            if (game.phase is Phase.Night && nightEnds != null && now >= nightEnds) {
                reduce(Action.EndNight)
            }
            val phase = table.game?.phase
            if (phase is Phase.Discussion) {
                val ends = phase.endsAtMillis(game.rules)
                if (ends != null && now >= ends && table.timeUpCalledFor != ends) {
                    change(table.copy(timeUpCalledFor = ends))
                    cue(Cue.TimeUp)
                    if (game.rules.voteWhenTimeRunsOut) reduce(Action.StartVote)
                }
            }
        }
    }

    /* ---------- Plumbing ---------- */

    private val pendingCues = mutableListOf<Cue>()

    private fun reduce(action: Action) {
        val game = table.game ?: throw TableException("No game is running.")
        val step = Game.reduce(game, action).getOrElse { throw TableException(it.message ?: "That move isn't allowed.") }
        val next = step.state
        val nightEndsAt = when (val phase = next.phase) {
            is Phase.Night -> if ((game.phase as? Phase.Night)?.night == phase.night) table.nightEndsAt else clock() + next.rules.nightSeconds * 1000L
            else -> null
        }
        change(table.copy(game = next, nightEndsAt = nightEndsAt))
        step.events.forEach { cue(Cue.Game(it)) }
    }

    private fun cue(cue: Cue) {
        pendingCues += cue
    }

    private fun requireLobby() {
        val game = table.game
        if (game != null && game.phase !is Phase.GameOver) throw TableException("Not while a game is running.")
    }

    private fun change(next: TableState) {
        table = next
        bump()
        listener?.onChanged(next)
    }

    private fun bump() {
        versionCounter++
        _version.value = versionCounter
        changed.signalAll()
    }

    /** Runs [block] under the lock, turning thrown errors into a failed result and handing out any cues. */
    private fun <T> locked(block: () -> T): Result<T> = lock.withLock {
        val result = runCatching(block).recoverCatching { e ->
            throw if (e is TableException) e else TableException(e.message ?: "Something went wrong.")
        }
        if (pendingCues.isNotEmpty()) {
            val cues = pendingCues.toList()
            pendingCues.clear()
            listener?.onCues(cues, table)
        }
        result
    }

    private fun newToken(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun tableName(host: String?) = if (host.isNullOrBlank()) "Dry Gulch table" else "$host's table"

        fun cleanPronunciation(raw: String): String? = raw.filter { !it.isISOControl() }.trim().take(32).ifEmpty { null }
    }
}
