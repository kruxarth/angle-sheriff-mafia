package com.angelsheriffmafia.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameTest {

    private val names = listOf("Ava", "Ben", "Cal", "Dee", "Eli", "Fay", "Gus", "Hal").map { NewPlayer(it) }

    private fun step(state: GameState, action: Action): GameState = Game.reduce(state, action).getOrThrow().state

    private fun dealt(rules: Rules = Rules(), seed: Long = 7): GameState {
        var s = Game.newGame(names, rules, seed)
        s.players.forEach { s = step(s, Action.RoleSeen(it.id)) }
        return s
    }

    private fun GameState.idOf(role: Role) = players.first { it.role == role && it.alive }.id
    private fun GameState.idsOf(role: Role) = players.filter { it.role == role && it.alive }.map { it.id }

    /** Plays the whole night: Mafia go for [target], Angel guards [guard], everyone else picks the first valid name. */
    private fun playNight(state: GameState, target: PlayerId, guard: PlayerId? = null, guess: PlayerId? = null): GameState {
        var s = state
        while (s.phase is Phase.Night) {
            val by = s.playerOnTurn!!
            val valid = Game.validNightTargets(s, by)
            val pick = when (s.player(by).role) {
                Role.MAFIA -> target
                Role.ANGEL -> guard?.takeIf { it in valid } ?: valid.first()
                Role.VILLAGER -> guess?.takeIf { it in valid } ?: valid.first()
                Role.SHERIFF -> valid.first()
            }
            s = step(s, Action.Pick(by, pick))
        }
        return s
    }

    @Test
    fun dealsSuggestedRoles() {
        val s = Game.newGame(names, Rules(), seed = 1)
        assertEquals(2, s.players.count { it.role == Role.MAFIA })
        assertEquals(1, s.players.count { it.role == Role.ANGEL })
        assertEquals(1, s.players.count { it.role == Role.SHERIFF })
        assertEquals(4, s.players.count { it.role == Role.VILLAGER })
        assertEquals(Phase.RoleReveal(0), s.phase)
    }

    @Test
    fun suggestedMafiaFollowsTable() {
        assertEquals(1, Rules.suggestedMafia(5))
        assertEquals(2, Rules.suggestedMafia(6))
        assertEquals(2, Rules.suggestedMafia(8))
        assertEquals(3, Rules.suggestedMafia(9))
        assertEquals(3, Rules.suggestedMafia(14))
    }

    @Test
    fun rejectsBadSetups() {
        assertFailsWith<GameSetupException> { Game.newGame(names.take(4), Rules()) }
        assertFailsWith<GameSetupException> { Game.newGame(names.take(6), Rules(mafiaCount = 3)) }
        assertFailsWith<GameSetupException> { Game.newGame(names.take(5) + NewPlayer("ava"), Rules()) }
        assertFailsWith<GameSetupException> { Game.newGame(names.take(5) + NewPlayer("  "), Rules()) }
    }

    @Test
    fun roleRevealGoesRoundThenNightFalls() {
        var s = Game.newGame(names, Rules(), seed = 3)
        assertTrue(Game.reduce(s, Action.RoleSeen(5)).isFailure)
        s.players.forEach { s = step(s, Action.RoleSeen(it.id)) }
        assertEquals(Phase.Night(1, 0), s.phase)
        assertEquals(s.players.map { it.id }, s.nights.single().turnOrder)
    }

    @Test
    fun everyLivingPlayerTakesANightTurnAndStartRotates() {
        val s = dealt()
        val first = s.nights.single().turnOrder
        val afterNight = playNight(s, target = s.idOf(Role.VILLAGER))
        val day1 = step(step(afterNight, Action.Advance), Action.StartVote)
        val night2 = step(step(day1, Action.OpenVote(emptyList())), Action.Advance)
        val order2 = night2.nights.last().turnOrder
        assertEquals(night2.alive.size, order2.size)
        assertTrue(order2.first() != first.first(), "Night 2 should start with a different player")
    }

    @Test
    fun lastMafiaOnThePhoneDecides() {
        val s = dealt()
        val (m1, m2) = s.idsOf(Role.MAFIA)
        val victims = s.idsOf(Role.VILLAGER)
        var cur = s
        while (cur.phase is Phase.Night) {
            val by = cur.playerOnTurn!!
            val valid = Game.validNightTargets(cur, by)
            val pick = when (by) {
                m1, m2 -> {
                    val partner = Game.partnerPicks(cur, by)
                    // The second Mafia sees the first one's choice and switches.
                    if (partner.isEmpty()) victims[0] else victims[1]
                }
                else -> if (cur.player(by).role == Role.ANGEL) cur.idOf(Role.SHERIFF) else valid.first()
            }
            cur = step(cur, Action.Pick(by, pick))
        }
        val night = cur.nights.single()
        assertEquals(victims[1], night.mafiaTarget)
        assertEquals(victims[1], night.killed)
        assertTrue(!cur.player(victims[1]).alive)
        assertTrue(cur.player(victims[0]).alive)
        assertEquals(Phase.Dawn(1), cur.phase)
    }

    @Test
    fun angelSavesTheTarget() {
        val s = dealt()
        val victim = s.idOf(Role.VILLAGER)
        val after = playNight(s, target = victim, guard = victim)
        assertNull(after.nights.single().killed)
        assertTrue(after.nights.single().saved)
        assertTrue(after.player(victim).alive)
    }

    @Test
    fun mafiaCannotPickMafiaAndVillagersCannotPickThemselves() {
        val s = dealt()
        val by = s.playerOnTurn!!
        val valid = Game.validNightTargets(s, by)
        assertTrue(by !in valid || s.player(by).role == Role.ANGEL)
        s.idsOf(Role.MAFIA).forEach { m ->
            assertTrue(Game.validNightTargets(s, m).none { s.player(it).role == Role.MAFIA })
        }
    }

    @Test
    fun angelHouseRules() {
        val strict = dealt(Rules(angelCanSelfProtect = false, angelCanRepeatProtect = false))
        val angel = strict.idOf(Role.ANGEL)
        assertTrue(angel !in Game.validNightTargets(strict, angel))
        val guarded = strict.idOf(Role.SHERIFF)
        val target = strict.idsOf(Role.VILLAGER).first()
        val day = playNight(strict, target = target, guard = guarded)
        val night2 = step(step(step(step(day, Action.Advance), Action.StartVote), Action.OpenVote(emptyList())), Action.Advance)
        assertTrue(guarded !in Game.validNightTargets(night2, angel))

        val loose = dealt(Rules(angelCanSelfProtect = true))
        assertTrue(loose.idOf(Role.ANGEL) in Game.validNightTargets(loose, loose.idOf(Role.ANGEL)))
    }

    @Test
    fun sheriffLearnsMafiaOrExactRole() {
        for (info in SheriffInfo.entries) {
            var s = dealt(Rules(sheriffLearns = info))
            val sheriff = s.idOf(Role.SHERIFF)
            val mafia = s.idOf(Role.MAFIA)
            var learned: SheriffCheck? = null
            while (s.phase is Phase.Night) {
                val by = s.playerOnTurn!!
                val target = when (s.player(by).role) {
                    Role.SHERIFF -> mafia
                    Role.MAFIA -> s.idOf(Role.VILLAGER)
                    else -> Game.validNightTargets(s, by).first()
                }
                val result = Game.reduce(s, Action.Pick(by, target)).getOrThrow()
                result.events.filterIsInstance<Event.SheriffLearned>().firstOrNull()?.let { learned = it.check }
                s = result.state
            }
            assertEquals(sheriff, s.nights.single().picksBy(Role.SHERIFF).single().by)
            assertTrue(learned!!.isMafia)
            assertEquals(if (info == SheriffInfo.EXACT_ROLE) Role.MAFIA else null, learned!!.role)
        }
    }

    @Test
    fun openVoteOutAndTownWins() {
        var s = dealt()
        val mafia = s.idsOf(Role.MAFIA)
        // Night 1: Angel saves, then the town votes out both Mafia over two days.
        s = playNight(s, target = s.idOf(Role.VILLAGER), guard = s.idOf(Role.VILLAGER))
        s = step(step(s, Action.Advance), Action.StartVote)
        val r = Game.reduce(s, Action.OpenVote(listOf(mafia[0]))).getOrThrow()
        assertTrue(r.events.contains(Event.VotedOut(mafia[0], false)))
        s = step(r.state, Action.Advance)
        val victim = s.idsOf(Role.VILLAGER).last()
        s = playNight(s, target = victim, guard = s.idOf(Role.SHERIFF).takeIf { it != victim })
        s = step(step(s, Action.Advance), Action.StartVote)
        s = step(s, Action.OpenVote(listOf(mafia[1])))
        assertEquals(Team.TOWN, s.winner)
        s = step(s, Action.Advance)
        assertEquals(Phase.GameOver(Team.TOWN), s.phase)
    }

    @Test
    fun mafiaWinWhenTheyEqualTheTown() {
        val five = listOf("Ava", "Ben", "Cal", "Dee", "Eli").map { NewPlayer(it) }
        var s = Game.newGame(five, Rules(), seed = 11)
        s.players.forEach { s = step(s, Action.RoleSeen(it.id)) }
        // 1 Mafia vs 4. Kill one at night, vote out a townie, kill another: 1 vs 1.
        s = playNight(s, target = s.idOf(Role.VILLAGER), guard = s.idOf(Role.SHERIFF))
        assertEquals(3, s.alive.size - 1)
        s = step(step(s, Action.Advance), Action.StartVote)
        s = step(s, Action.OpenVote(listOf(s.idOf(Role.VILLAGER))))
        assertNull(s.winner)
        s = step(s, Action.Advance)
        val angel = s.idOf(Role.ANGEL)
        s = playNight(s, target = angel, guard = s.idOf(Role.SHERIFF))
        assertEquals(Team.MAFIA, s.winner)
        s = step(s, Action.Advance)
        assertEquals(Phase.GameOver(Team.MAFIA), s.phase)
    }

    @Test
    fun secretVoteWithRevoteThenNobody() {
        var s = dealt(Rules(voteMode = VoteMode.SECRET, tieRule = TieRule.REVOTE))
        s = playNight(s, target = s.idOf(Role.VILLAGER), guard = s.idOf(Role.VILLAGER))
        s = step(step(s, Action.Advance), Action.StartVote)
        val a = s.alive[0].id
        val b = s.alive[1].id
        fun voteRound(state: GameState): Step {
            var cur = state
            var last: Step? = null
            val voters = cur.secretVoters()
            voters.forEachIndexed { i, voter ->
                // Alternate between a and b, never voting for yourself.
                val target = if (i % 2 == 0) (if (voter == a) b else a) else (if (voter == b) a else b)
                last = Game.reduce(cur, Action.Vote(voter, target)).getOrThrow()
                cur = last!!.state
            }
            return last!!
        }
        val first = voteRound(s)
        assertTrue(first.events.single() is Event.Revote)
        val revote = first.state.phase as Phase.Voting
        assertTrue(revote.isRevote)
        assertEquals(listOf(a, b).sorted(), revote.candidates)
        val second = voteRound(first.state)
        assertEquals(Event.NobodyVotedOut, second.events.first())
        assertEquals(listOf(a, b).sorted(), second.state.days.single().tied)
    }

    @Test
    fun secretVoteSkipsWinTies() {
        var s = dealt(Rules(voteMode = VoteMode.SECRET))
        s = playNight(s, target = s.idOf(Role.VILLAGER), guard = s.idOf(Role.VILLAGER))
        s = step(step(s, Action.Advance), Action.StartVote)
        val voters = s.secretVoters()
        val target = voters.last()
        voters.forEachIndexed { i, v -> s = step(s, Action.Vote(v, if (i % 2 == 0 && v != target) target else null)) }
        assertNull(s.days.single().eliminated)
        assertTrue(Game.reduce(dealt(Rules(voteMode = VoteMode.SECRET, allowSkipVote = false)), Action.Advance).isFailure)
    }

    @Test
    fun randomTieBreakIsDeterministic() {
        var s = dealt(Rules(tieRule = TieRule.RANDOM))
        s = playNight(s, target = s.idOf(Role.VILLAGER), guard = s.idOf(Role.VILLAGER))
        s = step(step(s, Action.Advance), Action.StartVote)
        val tied = s.alive.take(2).map { it.id }
        val a = step(s, Action.OpenVote(tied))
        val b = step(s, Action.OpenVote(tied))
        assertEquals(a.days.single().eliminated, b.days.single().eliminated)
        assertTrue(a.days.single().randomTieBreak)
        assertTrue(a.days.single().eliminated in tied)
    }

    @Test
    fun villagerGuessesAreScored() {
        var s = dealt()
        val victim = s.idsOf(Role.VILLAGER)[0]
        val guesser = s.idsOf(Role.VILLAGER)[1]
        s = playNight(s, target = victim, guess = victim)
        assertEquals(1 to 1, Game.guessScore(s, guesser))
    }

    @Test
    fun outOfTurnAndWrongPhaseActionsFail() {
        val s = dealt()
        val notOnTurn = s.players.first { it.id != s.playerOnTurn }.id
        assertTrue(Game.reduce(s, Action.Pick(notOnTurn, s.playerOnTurn!!)).isFailure)
        assertTrue(Game.reduce(s, Action.StartVote).isFailure)
        assertTrue(Game.reduce(s, Action.OpenVote(emptyList())).isFailure)
    }

    @Test
    fun stateSurvivesJsonRoundTrip() {
        var s = dealt(Rules(voteMode = VoteMode.SECRET))
        s = playNight(s, target = s.idOf(Role.VILLAGER))
        s = step(s, Action.Advance)
        s = step(s, Action.StartDiscussion(1234L))
        val back = GameState.fromJson(s.toJson())
        assertEquals(s, back)
    }
}
