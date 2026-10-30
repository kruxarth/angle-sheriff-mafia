package com.angelsheriffmafia.lan

import com.angelsheriffmafia.engine.Event
import com.angelsheriffmafia.engine.Game
import com.angelsheriffmafia.engine.Phase
import com.angelsheriffmafia.engine.Role
import com.angelsheriffmafia.engine.Rules
import com.angelsheriffmafia.engine.Team
import com.angelsheriffmafia.engine.VoteMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostSessionTest {

    private var now = 1_000_000L
    private val names = listOf("Ava", "Ben", "Cal", "Dee", "Eli", "Fay", "Gus")

    private class Table(val session: HostSession, val tokens: Map<String, String>) {
        fun view(name: String) = if (name == "Hal") session.hostView() else session.view(tokens.getValue(name))!!
        fun act(name: String, action: PlayerAction) = session.act(tokens.getValue(name), action)
    }

    private fun table(rules: Rules = Rules(), hostPlays: Boolean = true): Table {
        val session = HostSession(TableState(name = "Test", rules = rules), clock = { now })
        if (hostPlays) session.setHost("Hal", null, plays = true).getOrThrow()
        val tokens = names.associateWith { session.join(JoinRequest(it)).token!! }
        return Table(session, tokens)
    }

    /** Deals and has everyone look at their card. */
    private fun Table.dealt(): Table {
        session.host(HostCommand.Deal).getOrThrow()
        names.forEach { assertTrue(act(it, PlayerAction.Seen).ok) }
        session.hostAct(PlayerAction.Seen).getOrThrow()
        return this
    }

    private fun Table.game() = session.state.game!!
    private fun Table.nameOf(role: Role) = game().players.first { it.role == role && it.alive }.name
    private fun Table.namesOf(role: Role) = game().players.filter { it.role == role && it.alive }.map { it.name }
    private fun Table.idOf(name: String) = game().players.first { it.name == name }.id

    /** A move by [name], whether they're the host (Hal) or a guest. */
    private fun Table.move(name: String, action: PlayerAction) {
        if (name == "Hal") session.hostAct(action).getOrThrow() else assertTrue(act(name, action).ok, act(name, action).error)
    }

    @Test
    fun joiningNamesAndSeats() {
        val t = table()
        assertEquals(8, t.session.state.seats.size)
        assertEquals("Hal's table", t.session.state.name)
        assertTrue(t.session.state.seats.first().host)
        // Same name, any case: refused.
        assertNotNull(t.session.join(JoinRequest("  ava ")).error)
        // Coming back with your token gives the same seat.
        assertEquals(t.tokens["Ava"], t.session.join(JoinRequest("whatever", token = t.tokens["Ava"])).token)
        assertNotNull(t.session.join(JoinRequest("   ")).error)
        assertEquals("Long Name Gets C", cleanName("  Long   Name Gets Cut Off "))
    }

    @Test
    fun tableIsCappedAtTheMostPlayers() {
        val session = HostSession(TableState(name = "Big"), clock = { now })
        repeat(Rules.MAX_PLAYERS) { assertNotNull(session.join(JoinRequest("P$it")).token) }
        assertNotNull(session.join(JoinRequest("One too many")).error)
    }

    @Test
    fun needsFivePlayersToDeal() {
        val session = HostSession(TableState(name = "Small"), clock = { now })
        listOf("A", "B", "C", "D").forEach { session.join(JoinRequest(it)) }
        assertTrue(session.host(HostCommand.Deal).isFailure)
        session.join(JoinRequest("E"))
        assertTrue(session.host(HostCommand.Deal).isSuccess)
    }

    @Test
    fun everyoneSeesOnlyTheirOwnRoleAndMafiaSeeTheirGang() {
        val t = table()
        t.session.host(HostCommand.Deal).getOrThrow()
        val game = t.game()
        for (name in names) {
            val view = t.view(name)
            val me = view.game!!.me!!
            val player = game.players.first { it.name == name }
            assertEquals(player.role, me.role)
            assertTrue(view.game!!.players.all { it.role == null }, "No roles are public at the start")
            if (player.role == Role.MAFIA) {
                assertEquals(game.mafia.map { it.id }.toSet() - player.id, me.gang.toSet())
            } else {
                assertTrue(me.gang.isEmpty())
            }
        }
    }

    @Test
    fun nightIsSecretAndMafiaAgreeLive() {
        val t = table().dealt()
        val (m1, m2) = t.namesOf(Role.MAFIA)
        val sheriff = t.nameOf(Role.SHERIFF)
        val angel = t.nameOf(Role.ANGEL)
        val villagers = t.namesOf(Role.VILLAGER)
        assertEquals(Stage.NIGHT, t.view(m1).game!!.stage)
        assertEquals(now + 60_000, t.view(m1).game!!.night!!.endsAt)

        t.move(m1, PlayerAction.Pick(t.idOf(villagers[0])))
        // The other Mafia member sees it; nobody else does.
        assertEquals(listOf(Choice(t.idOf(m1), t.idOf(villagers[0]))), t.view(m2).game!!.night!!.gangPicks)
        assertTrue(t.view(villagers[1]).game!!.night!!.gangPicks.isEmpty())

        t.move(sheriff, PlayerAction.Pick(t.idOf(m1)))
        val report = t.view(sheriff).game!!.night!!.sheriff!!
        assertTrue(report.isMafia)
        assertNull(t.view(angel).game!!.night!!.sheriff)
        assertFalse(t.view(sheriff).game!!.night!!.canPick)

        t.move(angel, PlayerAction.Pick(t.idOf(angel)))
        villagers.forEach { t.move(it, PlayerAction.Pick(t.idOf(m2))) }
        // Mafia disagree: still night.
        t.move(m2, PlayerAction.Pick(t.idOf(villagers[1])))
        assertEquals(Stage.NIGHT, t.view(m1).game!!.stage)
        assertFalse(t.view(m1).game!!.night!!.agreed)
        // Agreement ends the night at once.
        t.move(m1, PlayerAction.Pick(t.idOf(villagers[1])))
        val dawn = t.view(angel).game!!
        assertEquals(Stage.DAWN, dawn.stage)
        assertEquals(t.idOf(villagers[1]), dawn.dawn!!.killed)
    }

    @Test
    fun nightEndsWhenTheClockRunsOut() {
        val t = table(Rules(nightSeconds = 45)).dealt()
        val (m1, _) = t.namesOf(Role.MAFIA)
        val sheriff = t.idOf(t.nameOf(Role.SHERIFF))
        t.move(m1, PlayerAction.Pick(sheriff))
        now += 44_000
        t.session.tick()
        assertEquals(Stage.NIGHT, t.view(m1).game!!.stage)
        now += 2_000
        t.session.tick()
        val dawn = t.view(m1).game!!.dawn!!
        assertEquals(sheriff, dawn.killed)
    }

    @Test
    fun dayVoteAndResult() {
        val t = table(Rules(voteMode = VoteMode.OPEN)).dealt()
        now += 61_000
        t.session.tick()
        val dawn = t.view("Ava").game!!.dawn!!
        assertTrue(dawn.quiet)
        t.session.host(HostCommand.StartTalking).getOrThrow()
        val talk = t.view("Ava").game!!.discussion!!
        assertEquals(now, talk.startedAt)
        t.session.host(HostCommand.AddTime(60)).getOrThrow()
        assertEquals(now + 240_000, t.view("Ava").game!!.discussion!!.endsAt)
        t.session.host(HostCommand.StartVote).getOrThrow()

        val mafia = t.nameOf(Role.MAFIA)
        val voters = names + "Hal"
        voters.filter { it != mafia }.forEach { t.move(it, PlayerAction.Vote(t.idOf(mafia))) }
        val vote = t.view(mafia).game!!.vote!!
        assertTrue(vote.canVote)
        assertEquals(listOf(t.idOf(mafia)), vote.waiting)
        t.move(mafia, PlayerAction.Vote(null))
        val result = t.view("Ava").game!!.result!!
        assertEquals(t.idOf(mafia), result.eliminated)
        assertEquals(Role.MAFIA, result.role)
        assertEquals(listOf(Tally(t.idOf(mafia), 7)), result.tally)
        assertEquals(1, result.skips)
        assertEquals(8, result.ballots!!.size)
        // A revealed role becomes public for everyone.
        assertEquals(Role.MAFIA, t.view("Ava").game!!.players.first { it.name == mafia }.role)
    }

    @Test
    fun secretVoteShowsCountsButNotBallots() {
        val t = table(Rules(voteMode = VoteMode.SECRET)).dealt()
        t.session.host(HostCommand.Deal).let { assertTrue(it.isFailure, "Can't deal mid-game") }
        now += 61_000
        t.session.tick()
        t.session.host(HostCommand.StartTalking).getOrThrow()
        t.session.host(HostCommand.StartVote).getOrThrow()
        (names + "Hal").forEach { t.move(it, PlayerAction.Vote(null)) }
        val result = t.view("Ava").game!!.result!!
        assertNull(result.ballots)
        assertEquals(8, result.skips)
    }

    @Test
    fun discussionTimeUpIsCalledOnceAndCanStartTheVote() {
        val collected = mutableListOf<Cue>()
        val cues: List<Cue> = collected
        val t = table(Rules(discussionSeconds = 60, voteWhenTimeRunsOut = true))
        t.session.listener = object : HostSession.Listener {
            override fun onCues(cues: List<Cue>, table: TableState) {
                collected += cues
            }
        }
        t.dealt()
        now += 61_000
        t.session.tick()
        t.session.host(HostCommand.StartTalking).getOrThrow()
        now += 60_000
        t.session.tick()
        t.session.tick()
        assertEquals(1, cues.count { it == Cue.TimeUp })
        assertEquals(Stage.VOTE, t.view("Ava").game!!.stage)
        assertTrue(cues.first() == Cue.Dealt)
        assertTrue(cues.any { it == Cue.Game(Event.QuietNight) })
        assertTrue(cues.any { it == Cue.Discussion })
    }

    @Test
    fun lateArrivalsWatchAndAreDealtInNextGame() {
        val t = table().dealt()
        val late = t.session.join(JoinRequest("Ivy")).token!!
        val view = t.session.view(late)!!
        assertEquals(SeatStatus.WATCHING, view.status)
        assertNull(view.game!!.me)
        assertFalse(t.session.act(late, PlayerAction.Pick(0)).ok)
        t.session.host(HostCommand.BackToLobby).getOrThrow()
        t.session.host(HostCommand.Deal).getOrThrow()
        assertEquals(SeatStatus.PLAYING, t.session.view(late)!!.status)
        assertEquals(9, t.game().players.size)
    }

    @Test
    fun hostCanLetAPlayerBackInOnANewPhone() {
        val t = table().dealt()
        val seat = t.session.state.seats.first { it.name == "Ben" }
        t.session.letBackIn(seat.id).getOrThrow()
        assertNull(t.session.view(t.tokens.getValue("Ben")), "The old phone is signed out")
        val again = t.session.join(JoinRequest("ben")).token!!
        val view = t.session.view(again)!!
        assertEquals(seat.id, view.you!!.id)
        assertEquals(seat.player, view.game!!.me!!.id)
        assertNotNull(t.session.join(JoinRequest("Ben")).error, "Only one phone gets the seat")
    }

    @Test
    fun hostWhoDoesNotPlaySeesOnlyPublicInformation() {
        val t = table(hostPlays = false)
        t.session.join(JoinRequest("Ivy"))
        t.session.host(HostCommand.Deal).getOrThrow()
        val view = t.session.hostView()
        assertNull(view.you)
        assertNull(view.game!!.me)
        assertTrue(view.game!!.players.all { it.role == null })
    }

    @Test
    fun playersCanLeaveBetweenGamesButNotDuringOne() {
        val t = table().dealt()
        assertFalse(t.act("Ava", PlayerAction.Leave).ok)
        assertTrue(t.session.removeSeat(t.session.state.seats.first { it.name == "Ava" }.id).isFailure)
        t.session.host(HostCommand.BackToLobby).getOrThrow()
        assertTrue(t.act("Ava", PlayerAction.Leave).ok)
        assertEquals(7, t.session.state.seats.size)
    }

    @Test
    fun aWholeGameToTheEnd() {
        val t = table().dealt()
        var rounds = 0
        while (t.game().phase !is Phase.GameOver && rounds < 10) {
            rounds++
            val game = t.game()
            // Night: the Mafia agree on the first villager, everyone else picks their first valid name.
            val target = game.alive.first { it.role != Role.MAFIA }.id
            game.alive.forEach { p ->
                val pick = if (p.role == Role.MAFIA) target else Game.validNightTargets(game, p.id).first()
                t.move(p.name, PlayerAction.Pick(pick))
            }
            if (t.game().phase is Phase.GameOver) break
            t.session.host(HostCommand.StartTalking).getOrThrow()
            if (t.game().phase is Phase.GameOver) break
            t.session.host(HostCommand.StartVote).getOrThrow()
            // The town votes out a Mafia member each day.
            val mafia = t.game().alive.first { it.role == Role.MAFIA }
            t.game().alive.forEach { p -> t.move(p.name, PlayerAction.Vote(if (p.id == mafia.id) null else mafia.id)) }
            t.session.host(HostCommand.Continue).getOrThrow()
        }
        val over = t.view("Ava").game!!
        assertEquals(Stage.OVER, over.stage)
        assertEquals(Team.TOWN, over.over!!.winner)
        assertTrue(over.players.all { it.role != null }, "Everything is public at the end")
        assertTrue(over.over!!.story.isNotEmpty())
        // Play again with the same table.
        t.session.host(HostCommand.Deal).getOrThrow()
        assertEquals(2, t.view("Ava").game!!.number)
        assertEquals(Stage.DEAL, t.view("Ava").game!!.stage)
    }

    @Test
    fun tableSurvivesARestart() {
        val t = table().dealt()
        val saved = LanJson.encodeToString(TableState.serializer(), t.session.state)
        val restored = HostSession(LanJson.decodeFromString(TableState.serializer(), saved), clock = { now })
        assertEquals(t.view("Ava").game, restored.view(t.tokens.getValue("Ava"))!!.game)
    }

    @Test
    fun connectionsShowInTheSeatList() {
        val t = table()
        val ava = t.session.state.seats.first { it.name == "Ava" }.id
        assertFalse(t.view("Ben").seats.first { it.id == ava }.connected)
        t.session.streamOpened(ava)
        assertTrue(t.view("Ben").seats.first { it.id == ava }.connected)
        t.session.streamClosed(ava)
        assertFalse(t.view("Ben").seats.first { it.id == ava }.connected)
    }
}
