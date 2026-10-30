package com.angelsheriffmafia.app.audio

import com.angelsheriffmafia.engine.Event
import com.angelsheriffmafia.engine.GameState
import com.angelsheriffmafia.engine.Phase
import com.angelsheriffmafia.engine.PlayMode
import com.angelsheriffmafia.engine.PlayerId
import com.angelsheriffmafia.engine.Role
import com.angelsheriffmafia.engine.Team

/** What the narrator says about a game. Only public information is ever spoken: Sheriff results stay on the screen. */
object Narration {

    fun forEvents(state: GameState, events: List<Event>): List<Say> = events.flatMap { event ->
        when (event) {
            is Event.NightFell -> listOf(
                Say.Line(if (state.mode == PlayMode.OWN_PHONES) NarratorLine.NIGHT_FALLS_OWN else NarratorLine.NIGHT_FALLS),
            )
            is Event.Killed -> dawn(state, event.player, event.player)
            is Event.Saved -> dawn(state, null, event.target)
            Event.QuietNight -> dawn(state, null, null)
            is Event.Revote -> listOf(Say.Line(NarratorLine.TIE))
            is Event.VotedOut -> listOf(Say.Text(state.player(event.player).spokenName), Say.Line(NarratorLine.RUN_OUT)) +
                roleLine(state, event.player)
            Event.NobodyVotedOut -> listOf(Say.Line(NarratorLine.NOBODY_OUT))
            is Event.GameWon -> if (state.phase is Phase.GameOver) {
                listOf(Say.Line(if (event.winner == Team.TOWN) NarratorLine.TOWN_WINS else NarratorLine.MAFIA_WINS))
            } else {
                emptyList()
            }
            is Event.SheriffLearned -> emptyList()
        }
    }

    /** The morning news: who died, or that the Angel saved someone, or that nobody was attacked. */
    fun dawn(state: GameState, killed: PlayerId?, target: PlayerId?): List<Say> = buildList {
        add(Say.Line(NarratorLine.MORNING))
        when {
            killed != null -> {
                add(Say.Text(state.player(killed).spokenName))
                add(Say.Line(NarratorLine.WAS_KILLED))
                addAll(roleLine(state, killed))
            }
            target != null -> add(Say.Line(NarratorLine.SAVED))
            else -> add(Say.Line(NarratorLine.QUIET_NIGHT))
        }
    }

    /** The last night's news again, for "Hear it again". */
    fun lastDawn(state: GameState): List<Say> {
        val night = state.nights.lastOrNull() ?: return emptyList()
        return dawn(state, night.killed, night.mafiaTarget)
    }

    private fun roleLine(state: GameState, player: PlayerId): List<Say> {
        if (!state.rules.revealRoleOnDeath) return emptyList()
        val line = when (state.player(player).role) {
            Role.MAFIA -> NarratorLine.WAS_MAFIA
            Role.ANGEL -> NarratorLine.WAS_ANGEL
            Role.SHERIFF -> NarratorLine.WAS_SHERIFF
            Role.VILLAGER -> NarratorLine.WAS_VILLAGER
        }
        return listOf(Say.Line(line))
    }
}
