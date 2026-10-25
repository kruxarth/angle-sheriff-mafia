package com.angelsheriffmafia.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.angelsheriffmafia.app.AfterTurn
import com.angelsheriffmafia.app.data.SavedPlayer
import com.angelsheriffmafia.app.data.Setup
import com.angelsheriffmafia.app.ui.screens.AfterTurnScreen
import com.angelsheriffmafia.app.ui.screens.DawnScreen
import com.angelsheriffmafia.app.ui.screens.DiscussionScreen
import com.angelsheriffmafia.app.ui.screens.GameOverScreen
import com.angelsheriffmafia.app.ui.screens.HowToPlayScreen
import com.angelsheriffmafia.app.ui.screens.NightPickScreen
import com.angelsheriffmafia.app.ui.screens.OpenVoteScreen
import com.angelsheriffmafia.app.ui.screens.PassScreen
import com.angelsheriffmafia.app.ui.screens.RoleCardScreen
import com.angelsheriffmafia.app.ui.screens.RulesScreen
import com.angelsheriffmafia.app.ui.screens.SecretVoteScreen
import com.angelsheriffmafia.app.ui.screens.SetupScreen
import com.angelsheriffmafia.app.ui.screens.TitleScreen
import com.angelsheriffmafia.app.ui.screens.VoteResultScreen
import com.angelsheriffmafia.app.ui.theme.WesternTheme
import com.angelsheriffmafia.engine.Action
import com.angelsheriffmafia.engine.Game
import com.angelsheriffmafia.engine.GameState
import com.angelsheriffmafia.engine.NewPlayer
import com.angelsheriffmafia.engine.Phase
import com.angelsheriffmafia.engine.PlayerId
import com.angelsheriffmafia.engine.Role
import com.angelsheriffmafia.engine.Rules
import com.angelsheriffmafia.engine.SheriffCheck
import com.angelsheriffmafia.engine.Team
import com.angelsheriffmafia.engine.VoteMode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private val names = listOf("Riya", "Aarav", "Kabir", "Meera", "Dev", "Sana", "Ishaan", "Tara")

private fun GameState.go(action: Action) = Game.reduce(this, action).getOrThrow().state
private fun GameState.id(role: Role, nth: Int = 0) = players.filter { it.role == role && it.alive }[nth].id

private fun dealt(rules: Rules = Rules()): GameState {
    var s = Game.newGame(names.map { NewPlayer(it) }, rules, seed = 42)
    s.players.forEach { s = s.go(Action.RoleSeen(it.id)) }
    return s
}

/** Plays out turns until [stopWhen], with the Mafia targeting [target] and the Angel guarding [guard]. */
private fun GameState.night(target: PlayerId, guard: PlayerId?, stopWhen: (GameState) -> Boolean = { false }): GameState {
    var s = this
    while (s.phase is Phase.Night && !stopWhen(s)) {
        val by = s.playerOnTurn!!
        val valid = Game.validNightTargets(s, by)
        val pick = when (s.player(by).role) {
            Role.MAFIA -> target
            Role.ANGEL -> guard?.takeIf { it in valid } ?: valid.first()
            else -> valid.first()
        }
        s = s.go(Action.Pick(by, pick))
    }
    return s
}

private fun shot(name: String, content: @Composable () -> Unit) {
    val density = 2.625f
    val scene = ImageComposeScene(width = (390 * density).toInt(), height = (844 * density).toInt(), density = Density(density)) {
        WesternTheme { content() }
    }
    scene.render(0)
    val image = scene.render(1_000_000_000L)
    val out = File("preview/out/$name.png")
    out.parentFile.mkdirs()
    out.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    println("wrote ${out.path}")
}

fun main() {
    val base = dealt()
    val setup = Setup(players = names.map { SavedPlayer(it, if (it == "Ishaan") "Ee-shaan" else "") })

    shot("01-title") { TitleScreen(canContinue = false, onContinue = {}, onNewGame = {}, onHowToPlay = {}) }
    shot("02-setup") { SetupScreen(setup, null, false, {}, {}, {}, {}, {}) }
    shot("03-rules") { RulesScreen(setup, {}, {}) }
    shot("04-how-to-play") { HowToPlayScreen {} }

    val reveal = Game.newGame(names.map { NewPlayer(it) }, Rules(), seed = 42)
    val mafiaPlayer = reveal.players.first { it.role == Role.MAFIA }
    shot("05-pass") { PassScreen("Night 2 · turn 5/8", 4, 8, "Riya", {}, {}) }
    shot("06-role-hidden") { RoleCardScreen(reveal, mafiaPlayer, "Aarav", {}) }
    for (role in Role.entries) {
        val p = reveal.players.first { it.role == role }
        shot("07-role-${role.name.lowercase()}") { RoleCardScreen(reveal, p, "Aarav", {}, previewHolding = true) }
    }

    // Night 1, stopped at the second Mafia member's turn so the partner hint shows.
    val victim = base.id(Role.VILLAGER)
    val secondMafia = base.id(Role.MAFIA, 1)
    val firstMafiaIndex = base.nights.last().turnOrder.indexOfFirst { base.player(it).role == Role.MAFIA }
    val atSecondMafia = base.night(victim, base.id(Role.SHERIFF)) { s ->
        s.playerOnTurn == secondMafia && (s.phase as Phase.Night).turn > firstMafiaIndex
    }
    val now = android.os.SystemClock.elapsedRealtime()
    shot("08-night-mafia") { NightPickScreen(atSecondMafia, secondMafia, now - 3000, {}) }
    val villagerTurn = base.night(victim, null) { s -> s.player(s.playerOnTurn!!).role == Role.VILLAGER }
    shot("09-night-villager") { NightPickScreen(villagerTurn, villagerTurn.playerOnTurn!!, now - 9000, {}) }
    shot("10-after-sheriff") {
        AfterTurnScreen(AfterTurn("Meera", AfterTurn.Kind.NIGHT, SheriffCheck(1, true, null), "Kabir", lastTurn = false), "Dev", {}, previewHolding = true)
    }

    val dawnKilled = base.night(victim, base.id(Role.SHERIFF))
    shot("11-dawn-killed") { DawnScreen(dawnKilled, {}, {}) }
    val dawnSaved = base.night(victim, victim)
    shot("12-dawn-saved") { DawnScreen(dawnSaved, {}, {}) }

    val discussion = dawnKilled.go(Action.Advance).go(Action.StartDiscussion(System.currentTimeMillis() - 47_000))
    shot("13-discussion") { DiscussionScreen(discussion, discussion.phase as Phase.Discussion, {}, {}, {}) }
    val voting = discussion.go(Action.StartVote)
    shot("14-open-vote") { OpenVoteScreen(voting, voting.phase as Phase.Voting) {} }

    val secret = dealt(Rules(voteMode = VoteMode.SECRET)).let { it.night(it.id(Role.VILLAGER), it.id(Role.SHERIFF)) }
        .go(Action.Advance).go(Action.StartVote)
    shot("15-secret-vote") { SecretVoteScreen(secret, secret.phase as Phase.Voting, secret.playerOnTurn!!) {} }

    val voted = voting.go(Action.OpenVote(listOf(voting.id(Role.MAFIA))))
    shot("16-vote-result") { VoteResultScreen(voted) {} }

    // Finish the game for the replay: vote out the second Mafia on day 2.
    var end = voted.go(Action.Advance)
    end = end.night(end.id(Role.VILLAGER), end.id(Role.VILLAGER))
    end = end.go(Action.Advance).go(Action.StartVote)
    end = end.go(Action.OpenVote(listOf(end.id(Role.MAFIA)))).go(Action.Advance)
    shot("17-game-over") { GameOverScreen(end, (end.phase as Phase.GameOver).winner, {}, {}) }
    check((end.phase as Phase.GameOver).winner == Team.TOWN)
}
