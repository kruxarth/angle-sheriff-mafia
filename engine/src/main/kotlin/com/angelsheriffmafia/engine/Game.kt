package com.angelsheriffmafia.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

@Serializable
data class GameState(
    /** Seating order, which is the order the phone travels. */
    val players: List<Player>,
    val rules: Rules,
    val phase: Phase,
    val nights: List<NightRecord> = emptyList(),
    val days: List<DayRecord> = emptyList(),
    val seed: Long,
    val randomDraws: Int = 0,
    val mode: PlayMode = PlayMode.PASS_PHONE,
) {
    fun player(id: PlayerId): Player = players.first { it.id == id }
    val alive: List<Player> get() = players.filter { it.alive }
    val mafia: List<Player> get() = players.filter { it.role == Role.MAFIA }

    val winner: Team?
        get() {
            val mafiaAlive = alive.count { it.role == Role.MAFIA }
            val townAlive = alive.size - mafiaAlive
            return when {
                mafiaAlive == 0 -> Team.TOWN
                mafiaAlive >= townAlive -> Team.MAFIA
                else -> null
            }
        }

    val currentNight: NightRecord? get() = (phase as? Phase.Night)?.let { nights.last() }

    /**
     * Whose turn it is on the phone right now, or null when the phone sits in the middle.
     * Always null with own phones, where nobody waits for a turn.
     */
    val playerOnTurn: PlayerId?
        get() = if (mode == PlayMode.OWN_PHONES) null else when (val p = phase) {
            is Phase.RoleReveal -> players[p.index].id
            is Phase.Night -> nights.last().turnOrder[p.turn]
            is Phase.Voting -> if (rules.voteMode == VoteMode.SECRET) secretVoters()[p.turn] else null
            else -> null
        }

    /** Living players in seating order, who vote one by one in a secret vote. */
    fun secretVoters(): List<PlayerId> = alive.map { it.id }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(text: String): GameState = json.decodeFromString(serializer(), text)
    }
}

data class NewPlayer(val name: String, val pronunciation: String? = null)

sealed interface Action {
    /** The player on turn has seen their role card. */
    data class RoleSeen(val by: PlayerId) : Action

    /** Same shape for every role: Mafia target, Angel protect, Sheriff inquiry, villager guess. */
    data class Pick(val by: PlayerId, val target: PlayerId) : Action

    /** Dawn to discussion, or a vote result to the next night. */
    data object Advance : Action

    data class StartDiscussion(val atMillis: Long) : Action

    /** More time on the discussion clock. */
    data class AddTime(val seconds: Int) : Action
    data object StartVote : Action

    /** Secret vote by the player on turn. A null target skips. */
    data class Vote(val by: PlayerId, val target: PlayerId?) : Action

    /** Open vote: the players who got the most votes. One is voted out, several is a tie, none means nobody. */
    data class OpenVote(val top: List<PlayerId>) : Action

    /** Own phones: night falls even though not everyone has checked their role. */
    data object EndDeal : Action

    /** Own phones: the night's time is up. Whatever has been chosen counts. */
    data object EndNight : Action

    /** Own phones: count the votes cast so far. */
    data object EndVote : Action
}

sealed interface Event {
    data class NightFell(val night: Int) : Event
    data class SheriffLearned(val by: PlayerId, val check: SheriffCheck) : Event
    data class Killed(val player: PlayerId) : Event
    data class Saved(val target: PlayerId) : Event

    /** Own phones: the night ran out before any Mafia member chose, so nobody was attacked. */
    data object QuietNight : Event
    data class Revote(val tied: List<PlayerId>) : Event
    data class VotedOut(val player: PlayerId, val byRandomTieBreak: Boolean) : Event
    data object NobodyVotedOut : Event
    data class GameWon(val winner: Team) : Event
}

data class Step(val state: GameState, val events: List<Event> = emptyList())

class GameSetupException(message: String) : IllegalArgumentException(message)

object Game {

    fun newGame(
        names: List<NewPlayer>,
        rules: Rules,
        seed: Long = Random.nextLong(),
        mode: PlayMode = PlayMode.PASS_PHONE,
    ): GameState {
        val trimmed = names.map { it.copy(name = it.name.trim()) }
        if (trimmed.size < Rules.MIN_PLAYERS) throw GameSetupException("You need at least ${Rules.MIN_PLAYERS} players.")
        if (trimmed.size > Rules.MAX_PLAYERS) throw GameSetupException("At most ${Rules.MAX_PLAYERS} players can play.")
        if (trimmed.any { it.name.isEmpty() }) throw GameSetupException("Every player needs a name.")
        if (trimmed.map { it.name.lowercase() }.toSet().size != trimmed.size) throw GameSetupException("Two players have the same name.")
        val mafiaCount = rules.mafiaFor(trimmed.size)
        if (mafiaCount < 1) throw GameSetupException("There must be at least one Mafia.")
        // Mafia, Angel, Sheriff and at least one villager, with the town outnumbering the Mafia.
        if (mafiaCount + 3 > trimmed.size || mafiaCount * 2 >= trimmed.size) {
            throw GameSetupException("$mafiaCount Mafia is too many for ${trimmed.size} players.")
        }

        val roles = buildList {
            repeat(mafiaCount) { add(Role.MAFIA) }
            add(Role.ANGEL)
            add(Role.SHERIFF)
            repeat(trimmed.size - mafiaCount - 2) { add(Role.VILLAGER) }
        }.shuffled(Random(seed))

        val players = trimmed.mapIndexed { i, p ->
            Player(id = i, name = p.name, role = roles[i], pronunciation = p.pronunciation?.trim()?.ifEmpty { null })
        }
        val phase = if (mode == PlayMode.OWN_PHONES) Phase.Deal() else Phase.RoleReveal(0)
        return GameState(players = players, rules = rules, phase = phase, seed = seed, randomDraws = 1, mode = mode)
    }

    fun reduce(state: GameState, action: Action): Result<Step> = runCatching { apply(state, action) }

    /** Who [by] may pick at night. The grid shows everyone else disabled. */
    fun validNightTargets(state: GameState, by: PlayerId): List<PlayerId> {
        val me = state.player(by)
        val lastProtected = state.nights
            .dropLast(if (state.phase is Phase.Night) 1 else 0)
            .lastOrNull()?.picks?.firstOrNull { it.by == by && it.role == Role.ANGEL }?.target
        return state.alive.map { it.id }.filter { target ->
            when (me.role) {
                Role.MAFIA -> state.player(target).role != Role.MAFIA
                Role.ANGEL -> (target != by || state.rules.angelCanSelfProtect) &&
                    (state.rules.angelCanRepeatProtect || target != lastProtected)
                Role.SHERIFF, Role.VILLAGER -> target != by
            }
        }
    }

    /** Earlier Mafia picks tonight, which the next Mafia member sees as a hint. */
    fun partnerPicks(state: GameState, by: PlayerId): List<NightPick> {
        if (state.player(by).role != Role.MAFIA) return emptyList()
        return state.currentNight?.picksBy(Role.MAFIA)?.filter { it.by != by }.orEmpty()
    }

    /** True when [by] is the last living Mafia on the phone tonight, so their pick is final. */
    fun hasFinalSay(state: GameState, by: PlayerId): Boolean {
        val night = state.currentNight ?: return false
        val mafiaTurns = night.turnOrder.filter { state.player(it).role == Role.MAFIA }
        return mafiaTurns.lastOrNull() == by
    }

    /**
     * Own phones: tonight's lead Mafia member. If time runs out while the Mafia are split,
     * the lead's choice settles it. The lead moves round with each night's starting player.
     */
    fun leadMafia(state: GameState): PlayerId? {
        val night = state.currentNight ?: return null
        return night.turnOrder.firstOrNull { state.player(it).role == Role.MAFIA }
    }

    /** Own phones: true once every living Mafia member has chosen the same target. Their choice is then locked. */
    fun mafiaAgreed(state: GameState): Boolean {
        val night = state.currentNight ?: return false
        val living = state.alive.filter { it.role == Role.MAFIA }.map { it.id }.toSet()
        val picks = night.picksBy(Role.MAFIA).filter { it.by in living }
        return living.isNotEmpty() && picks.size == living.size && picks.map { it.target }.toSet().size == 1
    }

    /** Own phones: true when [by] has made their move for the current round (deal, night or vote). */
    fun hasActed(state: GameState, by: PlayerId): Boolean = when (val phase = state.phase) {
        is Phase.Deal -> by in phase.seen
        is Phase.Night -> state.nights.last().picks.any { it.by == by }
        is Phase.Voting -> by in phase.votes
        else -> false
    }

    /**
     * Own phones: who still has to act before the round can end. During the deal and the vote
     * this is public. During the night it is secret and only the host uses it.
     */
    fun waitingFor(state: GameState): List<PlayerId> = when (state.phase) {
        is Phase.Deal -> state.players.map { it.id }.filter { !hasActed(state, it) }
        is Phase.Night -> state.alive.map { it.id }.filter {
            !hasActed(state, it) || (state.player(it).role == Role.MAFIA && !mafiaAgreed(state))
        }
        is Phase.Voting -> state.alive.map { it.id }.filter { !hasActed(state, it) }
        else -> emptyList()
    }

    /** How many of the Mafia's targets [player] guessed right as a villager, out of nights guessed. */
    fun guessScore(state: GameState, player: PlayerId): Pair<Int, Int> {
        val guessed = state.nights.filter { it.mafiaTarget != null }
            .mapNotNull { n -> n.guesses.firstOrNull { it.by == player }?.let { it.target == n.mafiaTarget } }
        return guessed.count { it } to guessed.size
    }

    private fun apply(state: GameState, action: Action): Step {
        val phase = state.phase
        return when (action) {
            is Action.RoleSeen -> if (state.mode == PlayMode.OWN_PHONES) {
                check(phase is Phase.Deal) { "Roles are not being dealt." }
                require(state.players.any { it.id == action.by }) { "No such player." }
                val seen = phase.seen + action.by
                if (seen.size == state.players.size) startNight(state, 1) else Step(state.copy(phase = Phase.Deal(seen)))
            } else {
                check(phase is Phase.RoleReveal) { "Roles are not being dealt." }
                require(action.by == state.playerOnTurn) { "It is not this player's turn." }
                if (phase.index + 1 < state.players.size) {
                    Step(state.copy(phase = Phase.RoleReveal(phase.index + 1)))
                } else {
                    startNight(state, 1)
                }
            }

            is Action.Pick -> pick(state, action)

            is Action.Advance -> when (phase) {
                is Phase.Dawn -> state.winner?.let { gameOver(state, it) }
                    ?: Step(state.copy(phase = Phase.Discussion(phase.day)))
                is Phase.VoteResult -> state.winner?.let { gameOver(state, it) }
                    ?: startNight(state, phase.day + 1)
                else -> error("Nothing to advance from here.")
            }

            is Action.StartDiscussion -> {
                check(phase is Phase.Discussion) { "Not discussing." }
                Step(state.copy(phase = phase.copy(startedAtMillis = phase.startedAtMillis ?: action.atMillis)))
            }

            is Action.AddTime -> {
                check(phase is Phase.Discussion) { "Not discussing." }
                require(action.seconds > 0) { "Add a positive amount of time." }
                Step(state.copy(phase = phase.copy(extraSeconds = phase.extraSeconds + action.seconds)))
            }

            is Action.StartVote -> {
                check(phase is Phase.Discussion) { "Not discussing." }
                Step(state.copy(phase = Phase.Voting(phase.day, candidates = state.alive.map { it.id })))
            }

            is Action.Vote -> if (state.mode == PlayMode.OWN_PHONES) ownPhoneVote(state, action) else secretVote(state, action)

            is Action.OpenVote -> {
                check(phase is Phase.Voting) { "Not voting." }
                check(state.mode == PlayMode.PASS_PHONE) { "Everyone votes on their own phone." }
                check(state.rules.voteMode == VoteMode.OPEN) { "This game uses a secret vote." }
                require(action.top.distinct().size == action.top.size) { "A player is listed twice." }
                require(action.top.all { it in phase.candidates }) { "Only candidates can be voted out." }
                finishVote(state, phase, action.top, votes = emptyMap())
            }

            is Action.EndDeal -> {
                check(state.mode == PlayMode.OWN_PHONES && phase is Phase.Deal) { "Roles are not being dealt." }
                startNight(state, 1)
            }

            is Action.EndNight -> {
                check(state.mode == PlayMode.OWN_PHONES && phase is Phase.Night) { "It is not night." }
                resolveNight(state, mutableListOf())
            }

            is Action.EndVote -> {
                check(state.mode == PlayMode.OWN_PHONES && phase is Phase.Voting) { "Not voting." }
                tally(state, phase, phase.votes)
            }
        }
    }

    private fun startNight(state: GameState, night: Int): Step {
        val living = state.alive.map { it.id }
        // Start with a different player each night, so the same Mafia doesn't always have the final say.
        val start = (night - 1) % living.size
        val order = living.drop(start) + living.take(start)
        val record = NightRecord(night = night, turnOrder = order)
        return Step(
            state.copy(phase = Phase.Night(night, 0), nights = state.nights + record),
            listOf(Event.NightFell(night)),
        )
    }

    private fun pick(state: GameState, action: Action.Pick): Step {
        val phase = state.phase
        check(phase is Phase.Night) { "It is not night." }
        val me = state.player(action.by)
        val ownPhones = state.mode == PlayMode.OWN_PHONES
        if (ownPhones) {
            require(me.alive) { "Only living players choose at night." }
            if (me.role == Role.MAFIA) {
                check(!mafiaAgreed(state)) { "Your gang has already agreed." }
            } else {
                check(!hasActed(state, me.id)) { "You've already chosen tonight." }
            }
        } else {
            require(action.by == state.playerOnTurn) { "It is not this player's turn." }
        }
        require(action.target in validNightTargets(state, action.by)) { "That player can't be picked." }

        val night = state.nights.last()
        val events = mutableListOf<Event>()
        // With own phones a Mafia member can change their pick until the gang agrees.
        val earlier = if (ownPhones) night.picks.filter { it.by != action.by } else night.picks
        var updated = night.copy(picks = earlier + NightPick(action.by, me.role, action.target))
        if (me.role == Role.SHERIFF) {
            val target = state.player(action.target)
            val check = SheriffCheck(
                target = target.id,
                isMafia = target.role == Role.MAFIA,
                role = if (state.rules.sheriffLearns == SheriffInfo.EXACT_ROLE) target.role else null,
            )
            updated = updated.copy(sheriffCheck = check)
            events += Event.SheriffLearned(me.id, check)
        }
        val nights = state.nights.dropLast(1) + updated
        if (ownPhones) {
            val next = state.copy(nights = nights)
            return if (waitingFor(next).isEmpty()) resolveNight(next, events) else Step(next, events)
        }
        if (phase.turn + 1 < night.turnOrder.size) {
            return Step(state.copy(phase = phase.copy(turn = phase.turn + 1), nights = nights), events)
        }
        return resolveNight(state.copy(nights = nights), events)
    }

    /** Who the Mafia go after tonight, or null if no Mafia member chose anyone. */
    private fun mafiaTarget(state: GameState, night: NightRecord): PlayerId? {
        val picks = night.picksBy(Role.MAFIA)
        // On one phone, the last Mafia member to hold it decides.
        if (state.mode == PlayMode.PASS_PHONE) return picks.last().target
        if (picks.isEmpty()) return null
        // Own phones: the gang's agreed or majority choice. On a split, the lead Mafia's pick wins.
        val counts = picks.groupingBy { it.target }.eachCount()
        val most = counts.values.max()
        val top = counts.filterValues { it == most }.keys
        if (top.size == 1) return top.single()
        return night.turnOrder.firstNotNullOf { id -> picks.firstOrNull { it.by == id }?.target?.takeIf { it in top } }
    }

    private fun resolveNight(state: GameState, events: MutableList<Event>): Step {
        val night = state.nights.last()
        val target = mafiaTarget(state, night)
        val protected = night.picksBy(Role.ANGEL).lastOrNull()?.target
        val killed = target?.takeIf { it != protected }
        val resolved = night.copy(mafiaTarget = target, protected = protected, killed = killed)
        val players = state.players.map { if (it.id == killed) it.copy(alive = false) else it }
        events += when {
            killed != null -> Event.Killed(killed)
            target != null -> Event.Saved(target)
            else -> Event.QuietNight
        }
        val next = state.copy(players = players, nights = state.nights.dropLast(1) + resolved, phase = Phase.Dawn(night.night))
        next.winner?.let { events += Event.GameWon(it) }
        return Step(next, events)
    }

    private fun secretVote(state: GameState, action: Action.Vote): Step {
        val phase = state.phase
        check(phase is Phase.Voting) { "Not voting." }
        check(state.rules.voteMode == VoteMode.SECRET) { "This game uses an open vote." }
        require(action.by == state.playerOnTurn) { "It is not this player's turn." }
        checkBallot(state, phase, action)
        val votes = phase.votes + (action.by to action.target)
        if (phase.turn + 1 < state.secretVoters().size) {
            return Step(state.copy(phase = phase.copy(turn = phase.turn + 1, votes = votes)))
        }
        return tally(state, phase, votes)
    }

    /** Own phones: every living player votes once, in any order. */
    private fun ownPhoneVote(state: GameState, action: Action.Vote): Step {
        val phase = state.phase
        check(phase is Phase.Voting) { "Not voting." }
        val me = state.player(action.by)
        require(me.alive) { "Only living players vote." }
        check(action.by !in phase.votes) { "You've already voted." }
        checkBallot(state, phase, action)
        val votes = phase.votes + (action.by to action.target)
        val next = phase.copy(votes = votes)
        return if (state.alive.all { it.id in votes }) tally(state, next, votes) else Step(state.copy(phase = next))
    }

    private fun checkBallot(state: GameState, phase: Phase.Voting, action: Action.Vote) {
        if (action.target == null) {
            require(state.rules.allowSkipVote) { "Skipping is not allowed." }
        } else {
            require(action.target in phase.candidates) { "That player is not up for the vote." }
            require(action.target != action.by) { "You can't vote for yourself." }
        }
    }

    private fun tally(state: GameState, phase: Phase.Voting, votes: Map<PlayerId, PlayerId?>): Step {
        val counts = votes.values.filterNotNull().groupingBy { it }.eachCount()
        val skips = votes.values.count { it == null }
        val most = counts.values.maxOrNull() ?: 0
        // Skipping wins its ties: nobody leaves unless a player clearly has the most votes.
        val top = if (most == 0 || skips >= most) emptyList() else counts.filterValues { it == most }.keys.sorted()
        return finishVote(state, phase, top, votes)
    }

    private fun finishVote(state: GameState, phase: Phase.Voting, top: List<PlayerId>, votes: Map<PlayerId, PlayerId?>): Step {
        if (top.size > 1) {
            when (state.rules.tieRule) {
                TieRule.REVOTE -> if (!phase.isRevote) {
                    val revote = Phase.Voting(phase.day, candidates = top, isRevote = true)
                    return Step(state.copy(phase = revote), listOf(Event.Revote(top)))
                }
                TieRule.RANDOM -> {
                    val chosen = top[Random(state.seed + state.randomDraws).nextInt(top.size)]
                    return eliminate(state.copy(randomDraws = state.randomDraws + 1), phase, chosen, votes, top, random = true)
                }
                TieRule.NOBODY -> Unit
            }
            return eliminate(state, phase, null, votes, top, random = false)
        }
        return eliminate(state, phase, top.singleOrNull(), votes, emptyList(), random = false)
    }

    private fun eliminate(
        state: GameState,
        phase: Phase.Voting,
        out: PlayerId?,
        votes: Map<PlayerId, PlayerId?>,
        tied: List<PlayerId>,
        random: Boolean,
    ): Step {
        val record = DayRecord(day = phase.day, votes = votes, tied = tied, eliminated = out, randomTieBreak = random)
        val players = state.players.map { if (it.id == out) it.copy(alive = false) else it }
        val next = state.copy(players = players, days = state.days + record, phase = Phase.VoteResult(phase.day))
        val events = buildList {
            add(if (out != null) Event.VotedOut(out, random) else Event.NobodyVotedOut)
            next.winner?.let { add(Event.GameWon(it)) }
        }
        return Step(next, events)
    }

    private fun gameOver(state: GameState, winner: Team) =
        Step(state.copy(phase = Phase.GameOver(winner)), listOf(Event.GameWon(winner)))
}
