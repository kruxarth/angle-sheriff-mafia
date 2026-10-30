package com.angelsheriffmafia.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.angelsheriffmafia.app.AfterTurn
import com.angelsheriffmafia.app.data.SavedPlayer
import com.angelsheriffmafia.app.data.Setup
import com.angelsheriffmafia.app.ui.screens.AfterTurnScreen
import com.angelsheriffmafia.app.ui.screens.FoundTable
import com.angelsheriffmafia.app.ui.screens.HostStartScreen
import com.angelsheriffmafia.app.ui.screens.JoinScreen
import com.angelsheriffmafia.app.ui.screens.JoinUi
import com.angelsheriffmafia.app.ui.screens.PlayModeScreen
import com.angelsheriffmafia.app.ui.screens.TableActions
import com.angelsheriffmafia.app.ui.screens.TableScreen
import com.angelsheriffmafia.app.ui.screens.TableUi
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
import com.angelsheriffmafia.lan.HostCommand
import com.angelsheriffmafia.lan.HostSession
import com.angelsheriffmafia.lan.JoinRequest
import com.angelsheriffmafia.lan.PlayerAction
import com.angelsheriffmafia.lan.Stage
import com.angelsheriffmafia.lan.TableState
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

    tableShots(setup)
}

private object NoActions : TableActions

/** A hosted table played through the real host session, seen from different phones. */
private fun tableShots(setup: Setup) {
    shot("20-title-at-table") { TitleScreen(canContinue = true, onContinue = {}, onNewGame = {}, onHowToPlay = {}, atTable = true) }
    shot("21-play-mode") { PlayModeScreen({}, {}, {}) }
    shot("22-host-start") { HostStartScreen("Riya", "", true, false, null, {}, {}, {}, {}, {}, {}) }
    shot("23-join") {
        JoinScreen(
            JoinUi(
                found = listOf(FoundTable("Riya's table", "192.168.43.1:8765", 6, false), FoundTable("Dev's table", "192.168.1.20:8765", 9, true)),
                address = "192.168.43.1:8765",
                name = "Kabir",
            ),
            {}, {}, {}, {}, {},
        )
    }
    shot("24-join-searching") { JoinScreen(JoinUi(name = "Kabir", error = "Couldn't reach a table at 192.168.1.9:8765. Are you on the same Wi-Fi or hotspot?"), {}, {}, {}, {}, {}) }
    shot("25-rules-own-phones") { RulesScreen(setup, {}, {}, ownPhones = true, players = 7) }

    val session = HostSession(TableState(name = HostSession.tableName("Riya")))
    session.setHost("Riya", null, plays = true).getOrThrow()
    val guests = names.drop(1).take(6)
    val tokens: Map<String, String> = guests.associateWith { session.join(JoinRequest(it)).token!! }
    tokens.values.take(4).forEach { t -> session.streamOpened(session.seatIdFor(t)!!) }
    val url = "http://192.168.43.1:8765/"
    fun ui(token: String?) = TableUi(view = token?.let { session.view(it)!! } ?: session.hostView(), isHost = token == null, joinUrl = url)
    fun table(name: String, token: String?, holding: Boolean = false) = shot(name) { TableScreen(ui(token), NoActions, previewHolding = holding) }

    table("26-host-lobby", null)
    shot("27-host-lobby-no-wifi") { TableScreen(ui(null).copy(joinUrl = null), NoActions) }
    table("28-guest-lobby", tokens.getValue("Aarav"))

    session.host(HostCommand.Deal).getOrThrow()
    val seats: List<String?> = listOf(null) + tokens.values
    fun view(token: String?) = token?.let { session.view(it)!! } ?: session.hostView()
    fun me(token: String?) = view(token).game!!.me!!
    fun act(token: String?, action: PlayerAction) {
        if (token == null) session.hostAct(action).getOrThrow() else check(session.act(token, action).ok)
    }
    val byRole = seats.groupBy { me(it).role }
    val mafia = byRole.getValue(Role.MAFIA)
    val sheriff = byRole.getValue(Role.SHERIFF).single()
    table("29-deal", mafia.first())
    table("30-deal-held", mafia.first(), holding = true)
    seats.forEach { act(it, PlayerAction.Seen) }

    // Night 1: one Mafia member picks, so the other sees it on the wire.
    val victim = view(mafia.first()).game!!.night!!.targets.first { t -> view(null).game!!.players.first { it.id == t }.let { p -> seats.none { me(it).id == p.id && me(it).role == Role.MAFIA } } }
    act(mafia.first(), PlayerAction.Pick(victim))
    table("31-night-mafia-wire", mafia.last())
    act(sheriff, PlayerAction.Pick(me(mafia.last()).id))
    table("32-night-sheriff-held", sheriff, holding = true)
    table("33-night-villager", byRole.getValue(Role.VILLAGER).first())
    seats.filter { it !in mafia && it != sheriff }.forEach { s -> act(s, PlayerAction.Pick(view(s).game!!.night!!.targets.first())) }
    act(mafia.last(), PlayerAction.Pick(victim))
    table("34-dawn-host", null)
    table("35-dawn-guest", tokens.values.first { it != null && view(it).game!!.players.first { p -> p.id == me(it).id }.alive })

    session.host(HostCommand.StartTalking).getOrThrow()
    table("36-discussion-host", null)
    session.host(HostCommand.StartVote).getOrThrow()
    val living = seats.filter { view(it).game!!.vote!!.canVote }
    table("37-vote-guest", living.last())
    living.take(3).forEach { act(it, PlayerAction.Vote(me(mafia.first()).id.takeIf { m -> m != me(it).id } ?: me(sheriff).id)) }
    table("38-vote-host-waiting", null)
    session.host(HostCommand.EndVote).getOrThrow()
    table("39-result", living.last())

    // Play it out to the end: the Mafia take the first town member each night, the town votes out nobody.
    var guard = 0
    while (view(null).game!!.stage != Stage.OVER && guard++ < 40) {
        val g = view(null).game!!
        when (g.stage) {
            Stage.RESULT, Stage.DAWN -> session.host(if (g.stage == Stage.DAWN) HostCommand.StartTalking else HostCommand.Continue).getOrThrow()
            Stage.DISCUSSION -> session.host(HostCommand.StartVote).getOrThrow()
            Stage.VOTE -> session.host(HostCommand.EndVote).getOrThrow()
            Stage.NIGHT -> {
                val alive = seats.filter { view(it).game!!.night!!.canPick }
                val town = mafia.map { view(it).game!!.night!! }.first { it.canPick }.targets.first()
                alive.forEach { s -> act(s, PlayerAction.Pick(if (me(s).role == Role.MAFIA) town else view(s).game!!.night!!.targets.first())) }
            }
            else -> error("Stuck at ${g.stage}")
        }
    }
    table("40-over-host", null)
    table("41-over-guest", tokens.values.first())
}
