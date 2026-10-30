package com.angelsheriffmafia.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Everyone on their own phone: deals, nights and votes happen all at once, in any order. */
class OwnPhonesTest {

    private val names = listOf("Ava", "Ben", "Cal", "Dee", "Eli", "Fay", "Gus", "Hal").map { NewPlayer(it) }

    private fun step(state: GameState, action: Action): GameState = Game.reduce(state, action).getOrThrow().state

    private fun newGame(rules: Rules = Rules(), seed: Long = 7) = Game.newGame(names, rules, seed, PlayMode.OWN_PHONES)

    private fun dealt(rules: Rules = Rules()): GameState {
        var s = newGame(rules)
        // Any order works.
        s.players.reversed().forEach { s = step(s, Action.RoleSeen(it.id)) }
        return s
    }

    private fun GameState.idOf(role: Role) = players.first { it.role == role && it.alive }.id
    private fun GameState.idsOf(role: Role) = players.filter { it.role == role && it.alive }.map { it.id }

    /** Everyone but the Mafia picks: the Angel guards [guard], the rest pick the first valid name. */
    private fun townPicks(state: GameState, guard: PlayerId? = null): GameState {
        var s = state
        s.alive.filter { it.role != Role.MAFIA }.forEach { p ->
            val valid = Game.validNightTargets(s, p.id)
            val target = if (p.role == Role.ANGEL) guard?.takeIf { it in valid } ?: valid.first() else valid.first()
            s = step(s, Action.Pick(p.id, target))
        }
        return s
    }

    @Test
    fun everyoneSeesTheirRoleAtOnceThenNightFalls() {
        var s = newGame()
        assertEquals(Phase.Deal(), s.phase)
        assertNull(s.playerOnTurn)
        s = step(s, Action.RoleSeen(5))
        s = step(s, Action.RoleSeen(5))
        assertEquals(Phase.Deal(setOf(5)), s.phase)
        assertEquals(7, Game.waitingFor(s).size)
        s.players.forEach { if (it.id != 2) s = step(s, Action.RoleSeen(it.id)) }
        assertEquals(listOf(2), Game.waitingFor(s))
        s = step(s, Action.RoleSeen(2))
        assertEquals(Phase.Night(1, 0), s.phase)
    }

    @Test
    fun hostCanStartTheNightBeforeEveryoneHasLooked() {
        val s = step(step(newGame(), Action.RoleSeen(0)), Action.EndDeal)
        assertTrue(s.phase is Phase.Night)
    }

    @Test
    fun nightEndsOnceEveryoneHasChosenAndTheMafiaAgree() {
        var s = dealt()
        val (m1, m2) = s.idsOf(Role.MAFIA)
        val victims = s.idsOf(Role.VILLAGER)
        s = step(s, Action.Pick(m1, victims[0]))
        s = townPicks(s, guard = s.idOf(Role.SHERIFF))
        // The second Mafia disagrees: the night waits.
        s = step(s, Action.Pick(m2, victims[1]))
        assertTrue(s.phase is Phase.Night)
        assertFalse(Game.mafiaAgreed(s))
        assertEquals(setOf(m1, m2), Game.waitingFor(s).toSet())
        assertEquals(listOf(victims[1]), Game.partnerPicks(s, m1).map { it.target })
        // The first switches to agree, which locks the kill and ends the night.
        val r = Game.reduce(s, Action.Pick(m1, victims[1])).getOrThrow()
        assertEquals(Phase.Dawn(1), r.state.phase)
        assertTrue(r.events.contains(Event.Killed(victims[1])))
        assertEquals(1, r.state.nights.single().picksBy(Role.MAFIA).count { it.by == m1 })
    }

    @Test
    fun choicesAreFinalExceptForAMafiaStillAgreeing() {
        var s = dealt()
        val (m1, m2) = s.idsOf(Role.MAFIA)
        val sheriff = s.idOf(Role.SHERIFF)
        val villager = s.idOf(Role.VILLAGER)
        s = step(s, Action.Pick(sheriff, villager))
        assertTrue(Game.reduce(s, Action.Pick(sheriff, m1)).isFailure)
        s = step(s, Action.Pick(m1, villager))
        s = step(s, Action.Pick(m2, villager))
        assertTrue(Game.mafiaAgreed(s))
        // Once the gang agrees, the kill is locked.
        assertTrue(Game.reduce(s, Action.Pick(m1, sheriff)).isFailure)
        assertTrue(s.phase is Phase.Night)
    }

    @Test
    fun sheriffLearnsRightAway() {
        val s = dealt()
        val r = Game.reduce(s, Action.Pick(s.idOf(Role.SHERIFF), s.idOf(Role.MAFIA))).getOrThrow()
        assertTrue(r.events.filterIsInstance<Event.SheriffLearned>().single().check.isMafia)
    }

    @Test
    fun whenTimeRunsOutTheMafiaMajorityWins() {
        val nine = names + NewPlayer("Ivy")
        var s = Game.newGame(nine, Rules(), seed = 3, mode = PlayMode.OWN_PHONES)
        s = step(s, Action.EndDeal)
        val mafia = s.idsOf(Role.MAFIA)
        assertEquals(3, mafia.size)
        val (a, b) = s.idsOf(Role.VILLAGER)
        s = step(s, Action.Pick(mafia[0], a))
        s = step(s, Action.Pick(mafia[1], b))
        s = step(s, Action.Pick(mafia[2], b))
        val r = Game.reduce(s, Action.EndNight).getOrThrow()
        assertEquals(b, r.state.nights.single().mafiaTarget)
    }

    @Test
    fun aSplitAtTimeUpGoesToTheLeadMafia() {
        var s = dealt()
        val (m1, m2) = s.idsOf(Role.MAFIA)
        val lead = Game.leadMafia(s)!!
        val other = if (lead == m1) m2 else m1
        val (a, b) = s.idsOf(Role.VILLAGER)
        s = step(s, Action.Pick(other, a))
        s = step(s, Action.Pick(lead, b))
        s = step(s, Action.EndNight)
        assertEquals(b, s.nights.single().mafiaTarget)
    }

    @Test
    fun leadMafiaMovesRoundEachNight() {
        var s = dealt(Rules(angelCanRepeatProtect = true))
        val leads = mutableSetOf<PlayerId>()
        repeat(6) {
            if (s.phase !is Phase.Night) return@repeat
            leads += Game.leadMafia(s)!!
            val target = s.idOf(Role.VILLAGER)
            s.idsOf(Role.MAFIA).forEach { s = step(s, Action.Pick(it, target)) }
            s = townPicks(s, guard = target)
            assertEquals(null, s.nights.last().killed)
            s = step(step(step(s, Action.Advance), Action.StartVote), Action.EndVote)
            s = step(s, Action.Advance)
        }
        assertEquals(2, leads.size)
    }

    @Test
    fun aNightWithNoMafiaChoiceIsQuiet() {
        var s = dealt()
        s = townPicks(s)
        val r = Game.reduce(s, Action.EndNight).getOrThrow()
        assertEquals(listOf<Event>(Event.QuietNight), r.events)
        val night = r.state.nights.single()
        assertNull(night.mafiaTarget)
        assertNull(night.killed)
        assertFalse(night.saved)
        assertEquals(8, r.state.alive.size)
    }

    @Test
    fun deadPlayersCannotChooseOrVote() {
        var s = dealt()
        val victim = s.idOf(Role.VILLAGER)
        s.idsOf(Role.MAFIA).forEach { s = step(s, Action.Pick(it, victim)) }
        s = townPicks(s, guard = s.idOf(Role.SHERIFF))
        assertFalse(s.player(victim).alive)
        s = step(step(s, Action.Advance), Action.StartVote)
        assertTrue(Game.reduce(s, Action.Vote(victim, s.idOf(Role.MAFIA))).isFailure)
        assertTrue(victim !in Game.waitingFor(s))
    }

    @Test
    fun everyoneVotesInAnyOrderAndTheVoteClosesItself() {
        var s = dealt()
        val victim = s.idOf(Role.VILLAGER)
        s.idsOf(Role.MAFIA).forEach { s = step(s, Action.Pick(it, victim)) }
        s = townPicks(s, guard = s.idOf(Role.SHERIFF))
        s = step(step(s, Action.Advance), Action.StartVote)
        val target = s.idOf(Role.MAFIA)
        val voters = s.alive.map { it.id }.reversed()
        voters.dropLast(1).forEach { v -> s = step(s, Action.Vote(v, if (v == target) null else target)) }
        assertTrue(Game.reduce(s, Action.Vote(voters.first(), target)).isFailure, "One vote each")
        assertEquals(listOf(voters.last()), Game.waitingFor(s))
        val last = voters.last()
        val r = Game.reduce(s, Action.Vote(last, if (last == target) null else target)).getOrThrow()
        assertEquals(target, r.state.days.single().eliminated)
        assertEquals(voters.size, r.state.days.single().votes.size)
    }

    @Test
    fun hostCanCloseTheVoteEarly() {
        var s = dealt(Rules(tieRule = TieRule.REVOTE))
        s = step(s, Action.EndNight)
        s = step(step(s, Action.Advance), Action.StartVote)
        val (a, b) = s.alive.take(2).map { it.id }
        s = step(s, Action.Vote(a, b))
        s = step(s, Action.Vote(b, a))
        // One each: a tie, so the tied pair goes to a revote with fresh ballots.
        val r = Game.reduce(s, Action.EndVote).getOrThrow()
        assertTrue(r.events.single() is Event.Revote)
        val revote = r.state.phase as Phase.Voting
        assertTrue(revote.isRevote)
        assertTrue(revote.votes.isEmpty())
        assertEquals(listOf(a, b).sorted(), revote.candidates)
    }

    @Test
    fun pointingVoteIsNotUsedWithOwnPhones() {
        var s = dealt()
        s = step(step(step(s, Action.EndNight), Action.Advance), Action.StartVote)
        assertTrue(Game.reduce(s, Action.OpenVote(emptyList())).isFailure)
        assertTrue(Game.reduce(newGame(), Action.EndNight).isFailure)
    }

    @Test
    fun discussionClockCanBeExtended() {
        var s = dealt()
        s = step(step(s, Action.EndNight), Action.Advance)
        s = step(s, Action.StartDiscussion(1_000L))
        s = step(s, Action.AddTime(60))
        val phase = s.phase as Phase.Discussion
        assertEquals(1_000L + (s.rules.discussionSeconds + 60) * 1000L, phase.endsAtMillis(s.rules))
    }

    @Test
    fun stateWithModeSurvivesJsonRoundTrip() {
        val s = step(newGame(), Action.RoleSeen(1))
        assertEquals(s, GameState.fromJson(s.toJson()))
        // Games saved before own-phone play load as pass-the-phone games.
        val old = s.toJson().replace(",\"mode\":\"OWN_PHONES\"", "")
        assertEquals(PlayMode.PASS_PHONE, GameState.fromJson(old).mode)
    }
}
