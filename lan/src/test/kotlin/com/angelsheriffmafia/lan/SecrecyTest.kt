package com.angelsheriffmafia.lan

import com.angelsheriffmafia.engine.Game
import com.angelsheriffmafia.engine.GameState
import com.angelsheriffmafia.engine.Phase
import com.angelsheriffmafia.engine.Role
import com.angelsheriffmafia.engine.Rules
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Plays many random games and checks, after every move, that no phone's view says more than
 * that player may know. The JSON that leaves the host is what's checked.
 */
class SecrecyTest {

    @Test
    fun noViewLeaksASecret() {
        repeat(40) { seed -> playRandomGame(seed, Rules(revealRoleOnDeath = seed % 2 == 0)) }
    }

    private fun playRandomGame(seed: Int, rules: Rules) {
        var now = 0L
        val rnd = Random(seed)
        val session = HostSession(TableState(name = "T", rules = rules), clock = { now })
        val count = 5 + seed % 8
        val tokens = (0 until count).map { session.join(JoinRequest("P$it")).token!! }
        session.host(HostCommand.Deal).getOrThrow()
        tokens.forEach { session.act(it, PlayerAction.Seen) }
        var guard = 0
        while (session.state.game!!.phase !is Phase.GameOver && guard++ < 200) {
            checkAll(session, tokens)
            val game = session.state.game!!
            when (val phase = game.phase) {
                is Phase.Night -> {
                    val actor = game.alive.random(rnd)
                    val token = tokens[actor.id]
                    val targets = Game.validNightTargets(game, actor.id)
                    if (rnd.nextInt(10) == 0) {
                        now += 61_000
                        session.tick()
                    } else {
                        session.act(token, PlayerAction.Pick(targets.random(rnd)))
                    }
                }
                is Phase.Dawn -> session.host(HostCommand.StartTalking).getOrThrow()
                is Phase.Discussion -> session.host(HostCommand.StartVote).getOrThrow()
                is Phase.Voting -> {
                    val voter = game.alive.filter { it.id !in phase.votes }.random(rnd)
                    val target = phase.candidates.filter { it != voter.id }.randomOrNull(rnd)
                    session.act(tokens[voter.id], PlayerAction.Vote(target))
                }
                is Phase.VoteResult -> session.host(HostCommand.Continue).getOrThrow()
                else -> fail("Unexpected phase $phase")
            }
        }
        assertTrue(session.state.game!!.phase is Phase.GameOver, "Game $seed should finish")
    }

    private fun checkAll(session: HostSession, tokens: List<String>) {
        val game = session.state.game!!
        tokens.forEachIndexed { id, token ->
            val json = LanJson.encodeToString(TableView.serializer(), session.view(token)!!)
            checkView(game, id, json)
        }
        val hostJson = LanJson.encodeToString(TableView.serializer(), session.hostView())
        checkView(game, null, hostJson)
    }

    /** What [viewer] (null for the host who isn't playing) must not be told, judged from the JSON alone. */
    private fun checkView(game: GameState, viewer: Int?, json: String) {
        val view = LanJson.decodeFromString(TableView.serializer(), json)
        val g = view.game!!
        val me = viewer?.let(game::player)
        // Roles: only your own, your gang if Mafia, and public reveals.
        for (p in g.players) {
            val real = game.player(p.id)
            if (p.role != null) {
                assertTrue(!real.alive && game.rules.revealRoleOnDeath, "Role of ${p.name} leaked to $viewer")
            }
        }
        if (me == null) {
            assertTrue(g.me == null && g.night?.myPick == null && g.night?.gangPicks.isNullOrEmpty())
        } else {
            assertTrue(g.me!!.role == me.role)
            val gang = if (me.role == Role.MAFIA) game.mafia.map { it.id }.toSet() - me.id else emptySet()
            assertTrue(g.me!!.gang.toSet() == gang, "Gang leaked to $viewer")
            g.night?.let { night ->
                if (me.role != Role.MAFIA) {
                    assertTrue(night.gangPicks.isEmpty() && night.lead == null && !night.agreed, "Mafia plans leaked to $viewer")
                }
                if (me.role != Role.SHERIFF) assertTrue(night.sheriff == null, "Sheriff's report leaked to $viewer")
                val record = game.nights.last()
                assertTrue(night.myPick == record.picks.firstOrNull { it.by == me.id }?.target)
            }
        }
        // Nobody's night choice except your own (and your gang's) is in the JSON.
        if (game.phase is Phase.Night) {
            assertTrue(!json.contains("\"picks\""), "Raw picks leaked")
        }
        assertTrue(!json.contains("\"seed\""), "The deck's seed leaked")
        assertTrue(!json.contains("\"token\""), "A token leaked")
    }
}
