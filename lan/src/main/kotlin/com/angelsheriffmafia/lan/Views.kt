package com.angelsheriffmafia.lan

import com.angelsheriffmafia.engine.Game
import com.angelsheriffmafia.engine.GameState
import com.angelsheriffmafia.engine.Phase
import com.angelsheriffmafia.engine.Replay
import com.angelsheriffmafia.engine.Role
import com.angelsheriffmafia.engine.VoteMode

/**
 * Cuts the host's full state down to what one seat may see. This is the only place where
 * game state is turned into something that leaves the host, so every secret is decided here.
 */
object Views {

    fun build(table: TableState, seat: Seat?, version: Long, now: Long, connected: (Seat) -> Boolean): TableView {
        val seats = table.seats.map { it.toView(connected(it)) }
        val game = table.game
        val status = when {
            table.closed -> SeatStatus.CLOSED
            game == null -> SeatStatus.LOBBY
            seat?.player != null -> SeatStatus.PLAYING
            else -> SeatStatus.WATCHING
        }
        return TableView(
            version = version,
            now = now,
            table = table.name,
            status = status,
            you = seat?.let { s -> seats.first { it.id == s.id } },
            seats = seats,
            rules = table.rules,
            game = game?.let { gameView(table, it, seat?.player) },
        )
    }

    private fun Seat.toView(connected: Boolean) = SeatView(id = id, name = name, connected = connected, host = host, player = player)

    private fun gameView(table: TableState, game: GameState, me: Int?): GameView {
        val phase = game.phase
        val over = phase is Phase.GameOver
        val players = game.players.map { p ->
            val public = over || (!p.alive && game.rules.revealRoleOnDeath)
            PlayerView(id = p.id, name = p.name, alive = p.alive, role = p.role.takeIf { public }, seat = table.seatOfPlayer(p.id)?.id)
        }
        val mePlayer = me?.let(game::player)
        val meView = mePlayer?.let { p ->
            MeView(
                id = p.id,
                role = p.role,
                alive = p.alive,
                gang = if (p.role == Role.MAFIA) game.mafia.map { it.id } - p.id else emptyList(),
            )
        }
        val stage: Stage
        val day: Int
        var deal: DealView? = null
        var night: NightView? = null
        var dawn: DawnView? = null
        var discussion: DiscussionView? = null
        var vote: VoteView? = null
        var result: ResultView? = null
        var overView: OverView? = null
        when (phase) {
            is Phase.RoleReveal, is Phase.Deal -> {
                stage = Stage.DEAL
                day = 0
                deal = DealView(seen = me != null && Game.hasActed(game, me), waiting = Game.waitingFor(game))
            }
            is Phase.Night -> {
                stage = Stage.NIGHT
                day = phase.night
                night = nightView(table, game, mePlayer?.id)
            }
            is Phase.Dawn -> {
                stage = Stage.DAWN
                day = phase.day
                val record = game.nights.last()
                val killed = record.killed?.let(game::player)
                dawn = DawnView(
                    night = record.night,
                    killed = killed?.id,
                    killedRole = killed?.role?.takeIf { game.rules.revealRoleOnDeath },
                    saved = record.saved,
                    quiet = record.mafiaTarget == null,
                )
            }
            is Phase.Discussion -> {
                stage = Stage.DISCUSSION
                day = phase.day
                discussion = DiscussionView(
                    startedAt = phase.startedAtMillis,
                    endsAt = phase.endsAtMillis(game.rules),
                    seconds = game.rules.discussionSeconds + phase.extraSeconds,
                )
            }
            is Phase.Voting -> {
                stage = Stage.VOTE
                day = phase.day
                val alive = mePlayer?.alive == true
                val voted = me != null && me in phase.votes
                vote = VoteView(
                    candidates = phase.candidates,
                    revote = phase.isRevote,
                    canVote = alive && !voted,
                    voted = voted,
                    myVote = if (voted) phase.votes[me] else null,
                    skipped = voted && phase.votes[me] == null,
                    waiting = Game.waitingFor(game),
                )
            }
            is Phase.VoteResult -> {
                stage = Stage.RESULT
                day = phase.day
                val record = game.days.last()
                val out = record.eliminated?.let(game::player)
                result = ResultView(
                    eliminated = out?.id,
                    role = out?.role?.takeIf { game.rules.revealRoleOnDeath },
                    tied = record.tied,
                    randomTieBreak = record.randomTieBreak,
                    tally = record.votes.values.filterNotNull().groupingBy { it }.eachCount()
                        .map { (target, count) -> Tally(target, count) }
                        .sortedWith(compareByDescending<Tally> { it.count }.thenBy { it.target }),
                    skips = record.votes.values.count { it == null },
                    ballots = if (game.rules.voteMode == VoteMode.OPEN) record.votes.map { (by, target) -> Ballot(by, target) } else null,
                )
            }
            is Phase.GameOver -> {
                stage = Stage.OVER
                day = game.nights.size
                overView = OverView(winner = phase.winner, story = Replay.story(game), guessers = Replay.guessers(game), state = game)
            }
        }
        return GameView(
            number = table.gameNumber,
            stage = stage,
            day = day,
            decided = game.winner != null,
            players = players,
            me = meView,
            deal = deal,
            night = night,
            dawn = dawn,
            discussion = discussion,
            vote = vote,
            result = result,
            over = overView,
        )
    }

    private fun nightView(table: TableState, game: GameState, me: Int?): NightView {
        val endsAt = table.nightEndsAt
        val player = me?.let(game::player)
        if (player == null || !player.alive) return NightView(endsAt = endsAt, canPick = false)
        val record = game.nights.last()
        val myPick = record.picks.firstOrNull { it.by == player.id }?.target
        return if (player.role == Role.MAFIA) {
            val agreed = Game.mafiaAgreed(game)
            NightView(
                endsAt = endsAt,
                canPick = !agreed,
                targets = Game.validNightTargets(game, player.id),
                myPick = myPick,
                gangPicks = Game.partnerPicks(game, player.id).map { Choice(it.by, it.target) },
                agreed = agreed,
                lead = Game.leadMafia(game),
            )
        } else {
            NightView(
                endsAt = endsAt,
                canPick = myPick == null,
                targets = Game.validNightTargets(game, player.id),
                myPick = myPick,
                sheriff = if (player.role == Role.SHERIFF && myPick != null) record.sheriffCheck else null,
            )
        }
    }
}
