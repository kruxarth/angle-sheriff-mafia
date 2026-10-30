package com.angelsheriffmafia.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.angelsheriffmafia.app.R
import com.angelsheriffmafia.app.ui.components.BigButton
import com.angelsheriffmafia.app.ui.components.Fingerprint
import com.angelsheriffmafia.app.ui.components.LinkButton
import com.angelsheriffmafia.app.ui.components.NightBackground
import com.angelsheriffmafia.app.ui.components.PaperNote
import com.angelsheriffmafia.app.ui.components.PlankButton
import com.angelsheriffmafia.app.ui.components.PlankGrid
import com.angelsheriffmafia.app.ui.components.QrCode
import com.angelsheriffmafia.app.ui.components.Ribbon
import com.angelsheriffmafia.app.ui.components.Stamp
import com.angelsheriffmafia.app.ui.components.SunburstBackground
import com.angelsheriffmafia.app.ui.components.ThumbPad
import com.angelsheriffmafia.app.ui.components.WoodBackground
import com.angelsheriffmafia.app.ui.components.pressState
import com.angelsheriffmafia.app.ui.theme.Rye
import com.angelsheriffmafia.app.ui.theme.Type
import com.angelsheriffmafia.app.ui.theme.Western
import com.angelsheriffmafia.app.ui.theme.art
import com.angelsheriffmafia.app.ui.theme.title
import com.angelsheriffmafia.engine.PlayerId
import com.angelsheriffmafia.engine.Role as GameRole
import com.angelsheriffmafia.engine.VoteMode
import com.angelsheriffmafia.lan.GameView
import com.angelsheriffmafia.lan.HostCommand
import com.angelsheriffmafia.lan.MeView
import com.angelsheriffmafia.lan.NightView
import com.angelsheriffmafia.lan.PlayerAction
import com.angelsheriffmafia.lan.SeatStatus
import com.angelsheriffmafia.lan.SeatView
import com.angelsheriffmafia.lan.Stage
import com.angelsheriffmafia.lan.TableView
import kotlinx.coroutines.delay

/* ---------- What the table screens are drawn from ---------- */

enum class LinkState { CONNECTED, RECONNECTING }

/** One phone's picture of a table it hosts or sits at. */
data class TableUi(
    val view: TableView,
    /** This phone runs the table: it gets the host's buttons. */
    val isHost: Boolean,
    val link: LinkState = LinkState.CONNECTED,
    val busy: Boolean = false,
    val error: String? = null,
    /** The host's clock minus this phone's, so countdowns match the host's. */
    val clockOffset: Long = 0,
    /** Host only: the address friends open to join, or null with no Wi-Fi or hotspot. */
    val joinUrl: String? = null,
)

/** Everything a table screen can ask for. Each does nothing unless the phone's role supports it. */
interface TableActions {
    fun act(action: PlayerAction) {}
    fun host(command: HostCommand) {}
    fun replayDawn() {}
    fun letBackIn(seat: Int) {}
    fun removeSeat(seat: Int) {}
    fun setPlaying(plays: Boolean) {}
    fun editHost() {}
    fun openRules() {}

    /** A guest gives up their seat. */
    fun leaveTable() {}

    /** The host shuts the table for everyone. */
    fun closeTable() {}

    /** Back to the title. The table carries on and this phone can come back. */
    fun pause() {}
    fun dismissError() {}
}

/* ---------- Helpers ---------- */

private fun GameView.name(id: PlayerId?): String = players.firstOrNull { it.id == id }?.name ?: "?"

internal fun nameList(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}

private fun GameView.aliveCount() = players.count { it.alive }

private fun GameView.aliveLine() = aliveCount().let { left -> if (decided) "$left left in town." else "$left left in town. The Mafia are still out there." }

/** The host's time, ticking a few times a second. */
@Composable
private fun rememberHostNow(offset: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() + offset) }
    LaunchedEffect(offset) {
        while (true) {
            now = System.currentTimeMillis() + offset
            delay(250)
        }
    }
    return now
}

private fun secondsUntil(endsAt: Long, now: Long) = ((endsAt - now + 999) / 1000).coerceAtLeast(0).toInt()

/* ---------- The table ---------- */

/**
 * A table on this phone, for the host or a guest: the lobby, or whichever part of the game
 * is on. Secrets show only for this phone's own seat, and only while a thumb is held down.
 */
@Composable
fun TableScreen(ui: TableUi, actions: TableActions, previewHolding: Boolean = false) {
    val view = ui.view
    val game = view.game
    var peeking by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(false) }
    val stageKey = game?.let { "${it.number}:${it.stage}:${it.day}:${it.vote?.revote}" } ?: view.status.name
    val haptics = LocalHapticFeedback.current
    val me = game?.me
    val yourMove = game != null && me?.alive == true && (
        (game.stage == Stage.NIGHT && game.night?.canPick == true) ||
            (game.stage == Stage.VOTE && game.vote?.canVote == true)
        )
    LaunchedEffect(stageKey) {
        // A nudge for a phone lying on the table: your pick or vote is waiting.
        if (yourMove) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Box(Modifier.fillMaxSize()) {
        key(stageKey) {
            when {
                view.status == SeatStatus.CLOSED -> ClosedScreen(view, actions)
                game == null -> if (ui.isHost) HostLobbyScreen(ui, actions) else GuestLobbyScreen(ui, actions)
                !ui.isHost && view.status == SeatStatus.WATCHING -> WatchingScreen(view, game, actions)
                else -> {
                    val header: @Composable (String, Boolean) -> Unit = { ribbon, clockLight ->
                        TableHeader(
                            ribbon = ribbon,
                            clock = if (game.stage == Stage.NIGHT) game.night?.endsAt else null,
                            clockOffset = ui.clockOffset,
                            light = clockLight,
                            onPeek = if (me != null && game.stage != Stage.DEAL && game.stage != Stage.OVER) ({ peeking = it }) else null,
                            onPlayers = if (ui.isHost) ({ panel = true }) else null,
                        )
                    }
                    when (game.stage) {
                        Stage.DEAL -> DealStage(ui, game, actions, header, previewHolding)
                        Stage.NIGHT -> NightStage(ui, game, actions, header, previewHolding)
                        Stage.DAWN -> DawnStage(ui, game, actions, header)
                        Stage.DISCUSSION -> DiscussionStage(ui, game, actions, header)
                        Stage.VOTE -> VoteStage(ui, game, actions, header)
                        Stage.RESULT -> ResultStage(ui, game, actions, header)
                        Stage.OVER -> OverStage(ui, game, actions)
                    }
                }
            }
        }
        if (peeking && me != null && game != null) {
            Box(
                Modifier.fillMaxSize().background(Western.NightTop.copy(alpha = 0.94f)).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { WantedPoster(me.role, me.gang.map { game.name(it) }) }
        }
        if (panel && ui.isHost) PlayersPanel(ui, actions, onClose = { panel = false })
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().systemBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp)) {
            if (ui.link == LinkState.RECONNECTING) {
                Notice("Lost touch with the host. Reconnecting…", Western.Ink, Western.Mustard)
            }
            ui.error?.let { error ->
                Notice(error, Western.Cream, Western.Stamp, Modifier.clickable(role = Role.Button, onClick = actions::dismissError))
            }
        }
    }
}

@Composable
private fun Notice(text: String, color: Color, background: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(2.dp, Western.Ink, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(text, style = Type.bodyStrong.copy(fontSize = 15.sp, lineHeight = 20.sp), color = color)
    }
}

/** Ribbon on the left; the night clock, "My card" and the host's "Players" on the right. */
@Composable
private fun TableHeader(
    ribbon: String,
    clock: Long?,
    clockOffset: Long,
    light: Boolean,
    onPeek: ((Boolean) -> Unit)?,
    onPlayers: (() -> Unit)?,
) {
    RibbonRow(ribbon = { Ribbon(ribbon) }) {
        if (clock != null) {
            val now = rememberHostNow(clockOffset)
            val left = secondsUntil(clock, now)
            Text(
                formatClock(left),
                style = Type.bodyStrong.copy(fontSize = 17.sp),
                color = if (left == 0) Western.Red else if (light) Western.Lavender else Western.Ink,
                modifier = Modifier.semantics { contentDescription = "$left seconds left tonight" },
            )
        }
        if (onPeek != null) {
            Chip(
                "My card",
                fingerprint = true,
                modifier = Modifier.pressState(onPeek).semantics {
                    role = Role.Button
                    contentDescription = "Hold to see your card"
                },
            )
        }
        if (onPlayers != null) Chip("Players", fingerprint = false, modifier = Modifier.clickable(role = Role.Button, onClick = onPlayers))
    }
}

/**
 * The ribbon with [trailing] on the same line when both fit, or with [trailing] on its own
 * line underneath, so a long ribbon is never cut short.
 */
@Composable
private fun RibbonRow(ribbon: @Composable () -> Unit, trailing: @Composable () -> Unit) {
    val gap = 8.dp
    Layout(
        content = {
            Box { ribbon() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) { trailing() }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val left = measurables[0].measure(loose)
        val right = measurables[1].measure(loose)
        val space = gap.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else left.width + space + right.width
        if (right.width == 0 || left.width + space + right.width <= width) {
            val height = maxOf(left.height, right.height)
            layout(width, height) {
                left.place(0, (height - left.height) / 2)
                right.place(width - right.width, (height - right.height) / 2)
            }
        } else {
            val height = left.height + space + right.height
            layout(width, height) {
                left.place(0, 0)
                right.place(width - right.width, left.height + space)
            }
        }
    }
}

@Composable
private fun Chip(text: String, fingerprint: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .height(40.dp)
            .clip(shape)
            .background(Western.Mustard)
            .border(2.dp, Western.Ink, shape)
            .then(modifier)
            .padding(start = if (fingerprint) 6.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (fingerprint) Fingerprint(26.dp)
        Text(text.uppercase(), style = Type.label, color = Western.Ink, maxLines = 1)
    }
}

/* ---------- Dealing ---------- */

@Composable
private fun DealStage(ui: TableUi, game: GameView, actions: TableActions, header: @Composable (String, Boolean) -> Unit, previewHolding: Boolean) {
    val me = game.me
    val deal = game.deal ?: return
    val waiting = nameList(deal.waiting.map(game::name))
    if (me == null) {
        // The host runs the game without playing in it.
        SunburstBackground(centerYFraction = 0.5f) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                header("Dealing", false)
                Text("The cards are out", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
                Text(
                    if (deal.waiting.isEmpty()) "Everyone has seen their card. Night is falling…" else "Waiting for $waiting to check their card.",
                    style = Type.body,
                    color = Western.Ink,
                )
                Spacer(Modifier.weight(1f))
                BigButton("Start the night now", { actions.host(HostCommand.EndDeal) }, enabled = !ui.busy)
            }
        }
        return
    }
    var holding by remember { mutableStateOf(previewHolding) }
    var looked by remember { mutableStateOf(previewHolding) }
    WoodBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            header("For your eyes only", true)
            Text("${game.name(me.id)}, you are…", style = Type.headline.copy(fontSize = 30.sp), color = Western.Cream, modifier = Modifier.semantics { heading() })
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (holding) WantedPoster(me.role, me.gang.map(game::name)) else PosterBack()
            }
            ThumbPad(
                label = "Hold to see your card",
                hint = "Let go and it hides again." + if (deal.seen) "" else " Then tap Got it.",
                onPressChange = {
                    holding = it
                    if (it) looked = true
                },
            )
            if (deal.seen) {
                Text(
                    if (deal.waiting.isEmpty()) "Night is falling…" else "Waiting for $waiting to check their card.",
                    style = Type.body,
                    color = Western.Cream,
                    textAlign = TextAlign.Center,
                )
            } else {
                BigButton("Got it", { actions.act(PlayerAction.Seen) }, enabled = looked && !holding && !ui.busy, height = 58.dp)
            }
            if (ui.isHost && deal.waiting.isNotEmpty()) {
                LinkButton("Start the night now", { actions.host(HostCommand.EndDeal) }, color = Western.Cream)
            }
        }
    }
}

/* ---------- Night ---------- */

@Composable
private fun NightStage(ui: TableUi, game: GameView, actions: TableActions, header: @Composable (String, Boolean) -> Unit, previewHolding: Boolean) {
    val me = game.me
    val night = game.night ?: return
    when {
        me == null || !me.alive -> TownSleeps(game, me, header)
        night.canPick -> NightPick(ui, game, me, night, actions, header)
        else -> LockedIn(game, me, night, header, previewHolding)
    }
}

@Composable
private fun TownSleeps(game: GameView, me: MeView?, header: @Composable (String, Boolean) -> Unit) {
    NightBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            header("Night ${game.day}", true)
            Text("The town sleeps", style = Type.headline, color = Western.Cream, modifier = Modifier.semantics { heading() })
            Text(
                if (me != null) {
                    "You're out of the game. Rest easy at Boot Hill, and keep what you know to yourself."
                } else {
                    "Everyone is choosing on their own phone. Morning comes once they all have, or when the night runs out."
                },
                style = Type.body,
                color = Western.Lavender,
            )
        }
    }
}

@Composable
private fun NightPick(ui: TableUi, game: GameView, me: MeView, night: NightView, actions: TableActions, header: @Composable (String, Boolean) -> Unit) {
    var selected by remember { mutableStateOf<PlayerId?>(null) }
    val shown = selected ?: night.myPick
    NightBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            header("Night ${game.day}", true)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(prompt(me.role), style = Type.headline, color = Western.Cream, minLines = 2, maxLines = 2, modifier = Modifier.semantics { heading() })
                Text("${game.name(me.id)}, you're ${roleWord(me.role)}. Pick one name.", style = Type.body, color = Western.Lavender)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 6.dp)) {
                PlankGrid(game.players) { p, mod ->
                    PlankButton(
                        name = p.name,
                        selected = shown == p.id,
                        enabled = p.id in night.targets,
                        dead = !p.alive,
                        onClick = { selected = p.id },
                        modifier = mod,
                        tag = when {
                            p.id in me.gang -> "Partner"
                            p.id == me.id -> "You"
                            night.gangPicks.any { it.target == p.id } -> "Picked"
                            else -> null
                        },
                    )
                }
            }
            PaperNote(Modifier.fillMaxWidth()) {
                Row(Modifier.heightIn(min = 62.dp), verticalAlignment = Alignment.CenterVertically) {
                    Stamp(if (me.role == GameRole.MAFIA && night.gangPicks.isNotEmpty()) "Wire" else "Note", angle = -5f)
                    Spacer(Modifier.width(12.dp))
                    Text(nightNote(game, me, night, ui.view.rules.angelCanRepeatProtect), style = Type.body.copy(fontSize = 15.sp, lineHeight = 20.sp), color = Western.Ink)
                }
            }
            val pick = selected
            BigButton(
                text = when {
                    pick == null && night.myPick != null -> "Waiting for your gang"
                    shown == null -> "Pick a name"
                    night.myPick != null -> "Switch to " + game.name(shown)
                    else -> "Confirm " + game.name(shown)
                },
                onClick = {
                    if (pick != null) {
                        actions.act(PlayerAction.Pick(pick))
                        selected = null
                    }
                },
                enabled = pick != null && pick != night.myPick && !ui.busy,
            )
        }
    }
}

private fun nightNote(game: GameView, me: MeView, night: NightView, repeatProtect: Boolean): AnnotatedString = buildAnnotatedString {
    when (me.role) {
        GameRole.MAFIA -> {
            val partnersAlive = me.gang.any { id -> game.players.firstOrNull { it.id == id }?.alive == true }
            if (!partnersAlive) {
                append("You ride alone tonight. Your choice is final.")
                return@buildAnnotatedString
            }
            night.gangPicks.forEach { pick ->
                append("${game.name(pick.by)} chose ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(game.name(pick.target).uppercase()) }
                append(". ")
            }
            night.myPick?.let {
                append("You chose ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(game.name(it).uppercase()) }
                append(". ")
            }
            if (night.gangPicks.isEmpty() && night.myPick == null) append("Pick first, and your gang will see it. ")
            append("Pick the same name to agree. If the night runs out first, the most picks win, and on a split ")
            append(if (night.lead == me.id) "yours counts." else "${game.name(night.lead)}'s counts.")
        }
        GameRole.ANGEL -> append(
            "Whoever you pick survives a Mafia attack tonight." +
                if (!repeatProtect) " You can't pick the same person two nights running." else "",
        )
        GameRole.SHERIFF -> append("You'll see what you learned right after you confirm. Only you.")
        GameRole.VILLAGER -> append("Just a guess, it doesn't change the game. Right guesses score in the replay at the end.")
    }
}

@Composable
private fun LockedIn(game: GameView, me: MeView, night: NightView, header: @Composable (String, Boolean) -> Unit, previewHolding: Boolean) {
    var holding by remember { mutableStateOf(previewHolding) }
    NightBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            header("Night ${game.day}", true)
            Text("Locked in.", style = Type.headline, color = Western.Cream, modifier = Modifier.semantics { heading() })
            Text(
                "Morning comes when everyone has chosen, or when the night runs out. Hold the pad to check your result.",
                style = Type.body,
                color = Western.Lavender,
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (holding) {
                    PaperPanel(Modifier.fillMaxWidth(0.9f), angle = -1f) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 18.dp).semantics { liveRegion = LiveRegionMode.Assertive },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val check = night.sheriff
                            if (check != null) {
                                Text("SHERIFF'S REPORT", style = Type.label, color = Western.PaperText)
                                Text(game.name(check.target).uppercase(), style = Type.poster.copy(fontSize = 40.sp), color = Western.Ink, textAlign = TextAlign.Center)
                                val verdict = when {
                                    check.role != null -> "is the ${check.role!!.title()}"
                                    check.isMafia -> "is Mafia"
                                    else -> "is not Mafia"
                                }
                                Stamp(verdict, color = if (check.isMafia) Western.Stamp else Western.Ink, angle = -4f)
                            } else {
                                Text("LOCKED IN", style = Type.poster.copy(fontSize = 40.sp), color = Western.Ink)
                                val target = game.name(night.myPick)
                                Text(
                                    when (me.role) {
                                        GameRole.MAFIA -> "Your gang is going after $target. Act natural."
                                        GameRole.ANGEL -> "You're protecting $target. Act natural."
                                        else -> "You guessed $target. Act natural."
                                    },
                                    style = Type.bodyStrong,
                                    color = Western.Ink,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                } else {
                    Text("Hidden", style = Type.poster.copy(fontSize = 36.sp, fontFamily = Rye), color = Western.NightDim)
                }
            }
            ThumbPad(label = "Hold to see", hint = "Let go and it hides again.", onPressChange = { holding = it })
            Text("Keep your phone to yourself until morning.", style = Type.body.copy(fontSize = 14.sp), color = Western.Lavender, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/* ---------- Morning ---------- */

@Composable
private fun DawnStage(ui: TableUi, game: GameView, actions: TableActions, header: @Composable (String, Boolean) -> Unit) {
    val dawn = game.dawn ?: return
    DawnContent(
        night = dawn.night,
        killedName = dawn.killed?.let(game::name),
        killedRole = dawn.killedRole,
        quiet = dawn.quiet,
        aliveLine = game.aliveLine(),
        header = { header("After night ${dawn.night}", false) },
    ) {
        if (ui.isHost) {
            BigButton(
                if (game.decided) "See who won" else "Start talking · ${formatClock(ui.view.rules.discussionSeconds)}",
                { actions.host(HostCommand.StartTalking) },
                enabled = !ui.busy,
            )
            HearItAgain(actions::replayDawn, Modifier.align(Alignment.CenterHorizontally))
        } else {
            Text("The host starts the talking.", style = Type.body.copy(fontSize = 15.sp), color = Western.Ink)
        }
    }
}

@Composable
private fun DiscussionStage(ui: TableUi, game: GameView, actions: TableActions, header: @Composable (String, Boolean) -> Unit) {
    val talk = game.discussion ?: return
    val now = rememberHostNow(ui.clockOffset)
    val left = talk.endsAt?.let { secondsUntil(it, now) } ?: talk.seconds
    SunburstBackground(centerYFraction = 0.45f) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.fillMaxWidth().padding(top = 22.dp, start = 22.dp, end = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                header("Day ${game.day} · Town meeting", false)
                Text("Who's the Mafia?", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
                Text("Talk it out. Accuse, defend, bluff.", style = Type.body, color = Western.Ink)
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { ClockFace(left) }
            DayFooter {
                val alive = game.players.filter { it.alive }
                Text("${alive.size} still in town: " + alive.joinToString(", ") { it.name }, style = Type.body, color = Western.Ink)
                if (ui.isHost) {
                    BigButton("We're ready to vote", { actions.host(HostCommand.StartVote) }, enabled = !ui.busy)
                    LinkButton("Add a minute", { actions.host(HostCommand.AddTime(60)) })
                } else {
                    Text(
                        if (left == 0 && !ui.view.rules.voteWhenTimeRunsOut) "Time's up! The host calls the vote." else "The host calls the vote when you're ready.",
                        style = Type.body.copy(fontSize = 15.sp),
                        color = Western.Ink,
                    )
                }
            }
        }
    }
}

/* ---------- The vote ---------- */

@Composable
private fun VoteStage(ui: TableUi, game: GameView, actions: TableActions, header: @Composable (String, Boolean) -> Unit) {
    val vote = game.vote ?: return
    val me = game.me
    val ribbon = (if (vote.revote) "Revote" else "The vote") + " · day ${game.day}"
    if (me != null && vote.canVote) {
        var selected by remember { mutableStateOf<PlayerId?>(null) }
        val secret = ui.view.rules.voteMode != VoteMode.OPEN
        NightBackground {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                header(ribbon, true)
                Text(if (vote.revote) "It's a tie. Vote again!" else "Who should leave town?", style = Type.headline, color = Western.Cream, modifier = Modifier.semantics { heading() })
                Text(
                    "${game.name(me.id)}, pick one name. " + if (secret) "Only the count will be shown." else "Everyone will see who voted for whom.",
                    style = Type.body,
                    color = Western.Lavender,
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 10.dp)) {
                    PlankGrid(game.players.filter { it.id in vote.candidates || !it.alive }) { p, mod ->
                        PlankButton(
                            name = p.name,
                            selected = selected == p.id,
                            enabled = p.id in vote.candidates && p.id != me.id,
                            dead = !p.alive,
                            onClick = { selected = p.id },
                            modifier = mod,
                            tag = if (p.id == me.id) "You" else null,
                        )
                    }
                }
                if (ui.view.rules.allowSkipVote) {
                    LinkButton("Skip my vote", { actions.act(PlayerAction.Vote(null)) }, color = Western.Lavender, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                BigButton(
                    text = selected?.let { "Vote " + game.name(it) } ?: "Pick a name",
                    onClick = { selected?.let { actions.act(PlayerAction.Vote(it)) } },
                    enabled = selected != null && !ui.busy,
                )
            }
        }
        return
    }
    SunburstBackground(centerYFraction = 0.3f) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            header(ribbon, false)
            Text(if (vote.voted) "Your vote is in." else "The town is voting", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
            when {
                vote.voted -> Text(
                    if (vote.skipped) "You skipped." else "You voted for ${game.name(vote.myVote)}.",
                    style = Type.bodyStrong,
                    color = Western.Ink,
                )
                me != null && !me.alive -> Text("You're out of the game, so you don't vote this time.", style = Type.body, color = Western.Ink)
            }
            Text(
                if (vote.waiting.isEmpty()) "Counting…" else "Waiting for ${nameList(vote.waiting.map(game::name))}.",
                style = Type.body,
                color = Western.Ink,
            )
            Spacer(Modifier.weight(1f))
            if (ui.isHost && vote.waiting.isNotEmpty()) {
                BigButton("Count the votes now", { actions.host(HostCommand.EndVote) }, enabled = !ui.busy, color = Western.Night)
                Text("Votes not in yet won't count.", style = Type.body.copy(fontSize = 14.sp), color = Western.Ink)
            }
        }
    }
}

@Composable
private fun ResultStage(ui: TableUi, game: GameView, actions: TableActions, header: @Composable (String, Boolean) -> Unit) {
    val result = game.result ?: return
    val out = result.eliminated?.let { id -> game.players.firstOrNull { it.id == id } }
    SunburstBackground(centerYFraction = 0.4f) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                header("Day ${game.day} · The verdict", false)
                Text(
                    if (out != null) "Run out of town!" else "Nobody leaves today",
                    style = Type.headline,
                    color = Western.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                if (out != null) {
                    Text(out.name.uppercase(), style = Type.poster, color = Western.Ink, maxLines = 1)
                    result.role?.let { Stamp("Was ${roleWord(it)}", modifier = Modifier.background(Western.Paper), angle = -6f) }
                }
                Image(
                    painterResource(result.role?.art() ?: R.drawable.art_villager),
                    contentDescription = when {
                        result.role != null -> "${result.role!!.title()} character"
                        out != null -> "A townsperson leaving town"
                        else -> "A relieved villager"
                    },
                    modifier = Modifier.size(120.dp, 186.dp),
                )
                if (result.tally.isNotEmpty() || result.skips > 0) {
                    ReplaySection("The count") {
                        val most = result.tally.maxOfOrNull { it.count } ?: 1
                        result.tally.forEach { t ->
                            TallyRow(game.name(t.target), t.count, t.count.toFloat() / most)
                        }
                        if (result.skips > 0) TallyRow("Skipped", result.skips, null)
                        result.ballots?.let { ballots ->
                            Text(
                                ballots.joinToString(" · ") { b -> game.name(b.by) + " → " + (b.target?.let(game::name) ?: "skip") },
                                style = Type.body.copy(fontSize = 14.sp, lineHeight = 19.sp),
                                color = Western.PaperText,
                            )
                        }
                    }
                }
            }
            DayFooter {
                Text(
                    when {
                        out != null && result.randomTieBreak -> "A tie, settled by the luck of the draw."
                        out != null -> "The town has spoken."
                        result.tied.isNotEmpty() -> "Still tied, so the town lets everyone stay."
                        else -> "The town couldn't agree on anyone."
                    },
                    style = Type.bodyStrong.copy(fontSize = 18.sp),
                    color = Western.Ink,
                )
                Text(game.aliveLine(), style = Type.body, color = Western.Ink)
                if (ui.isHost) {
                    BigButton(
                        if (game.decided) "See who won" else "Night falls...",
                        { actions.host(HostCommand.Continue) },
                        enabled = !ui.busy,
                        color = if (game.decided) Western.Magenta else Western.Night,
                    )
                } else {
                    Text(if (game.decided) "The host shows who won." else "The host starts the next night.", style = Type.body.copy(fontSize = 15.sp), color = Western.Ink)
                }
            }
        }
    }
}

@Composable
private fun TallyRow(name: String, count: Int, share: Float?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name, style = Type.bodyStrong.copy(fontSize = 16.sp), color = Western.Ink, modifier = Modifier.weight(1f), maxLines = 1)
        if (share != null) Box(Modifier.width((90 * share).dp).height(10.dp).background(Western.Ink, RoundedCornerShape(5.dp)))
        Text("$count", style = Type.bodyStrong.copy(fontSize = 16.sp), color = Western.Ink)
    }
}

@Composable
private fun OverStage(ui: TableUi, game: GameView, actions: TableActions) {
    val over = game.over ?: return
    GameOverScreen(over.state, over.winner) {
        if (ui.isHost) {
            BigButton("Deal again", { actions.host(HostCommand.Deal) }, enabled = !ui.busy)
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                LinkButton("Back to the lobby", { actions.host(HostCommand.BackToLobby) })
            }
        } else {
            Text("Waiting for the host to deal again.", style = Type.bodyStrong, color = Western.Ink, textAlign = TextAlign.Center)
            LinkButton("Leave the table", actions::leaveTable)
        }
    }
}

/* ---------- Around the game ---------- */

@Composable
private fun WatchingScreen(view: TableView, game: GameView, actions: TableActions) {
    SunburstBackground(centerYFraction = 0.2f) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Ribbon(view.table)
            Text(if (game.stage == Stage.OVER) "Pull up a chair" else "A game is under way", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
            Text("You'll be dealt in when the host starts the next one.", style = Type.body, color = Western.Ink)
            SeatList(view.seats, view.you?.id)
            LinkButton("Leave the table", actions::leaveTable)
        }
    }
}

@Composable
private fun ClosedScreen(view: TableView, actions: TableActions) {
    SunburstBackground(centerYFraction = 0.5f) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Ribbon(view.table)
            Text("The table is closed", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
            Text("Thanks for playing!", style = Type.body, color = Western.Ink)
            BigButton("Back to the title", actions::leaveTable)
        }
    }
}

/** Who's at the table, with a dot for whether their phone is connected. */
@Composable
internal fun SeatList(
    seats: List<SeatView>,
    you: Int?,
    onRemove: ((SeatView) -> Unit)? = null,
    onLetBackIn: ((SeatView) -> Unit)? = null,
) {
    PaperNote(Modifier.fillMaxWidth(), angle = 0f) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (seats.isEmpty()) Text("Nobody yet.", style = Type.body, color = Western.PaperText)
            seats.forEach { seat ->
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(if (seat.connected) Western.Teal else Color.Transparent, CircleShape)
                            .border(2.dp, if (seat.connected) Western.Ink else Western.PaperText, CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(seat.name, style = Type.bodyStrong.copy(fontSize = 16.5.sp), color = Western.Ink, maxLines = 1, modifier = Modifier.weight(1f))
                    val tags = listOfNotNull("host".takeIf { seat.host }, "you".takeIf { seat.id == you }, "away".takeIf { !seat.connected })
                    if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = Type.body.copy(fontSize = 14.sp), color = Western.PaperText)
                    if (onLetBackIn != null && !seat.host && !seat.connected) {
                        Text(
                            "Let back in",
                            style = Type.bodyStrong.copy(fontSize = 15.sp),
                            color = Western.Magenta,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(role = Role.Button) { onLetBackIn(seat) }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                        )
                    }
                    if (onRemove != null && !seat.host) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button) { onRemove(seat) }
                                .semantics { contentDescription = "Remove ${seat.name}" },
                            contentAlignment = Alignment.Center,
                        ) { Text("×", style = Type.title, color = Western.Ink) }
                    }
                }
            }
        }
    }
}

/** The host's look at who's connected during a game, with a way back in for a dead phone. */
@Composable
private fun PlayersPanel(ui: TableUi, actions: TableActions, onClose: () -> Unit) {
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Western.Paper)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ribbon(ui.view.table)
            Spacer(Modifier.weight(1f))
            LinkButton("Done", onClose)
        }
        Text("Who's here", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
        Text(
            "Phone died, or switched browser? Tap Let back in, then they join again with the same name from any phone.",
            style = Type.body,
            color = Western.PaperText,
        )
        SeatList(ui.view.seats, ui.view.you?.id, onLetBackIn = { actions.letBackIn(it.id) })
        ui.joinUrl?.let {
            Text("Joining address: ${it.removePrefix("http://").trimEnd('/')}", style = Type.bodyStrong.copy(fontSize = 16.sp), color = Western.Ink)
            Text("Anyone who joins now is dealt in next game.", style = Type.body.copy(fontSize = 14.sp), color = Western.PaperText)
        }
        Spacer(Modifier.height(8.dp))
        BigButton("Back to the game", onClose, color = Western.Teal, textColor = Western.Ink)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LinkButton(if (confirmEnd) "Tap again to end it" else "End this game", {
                if (confirmEnd) {
                    actions.host(HostCommand.BackToLobby)
                    onClose()
                } else {
                    confirmEnd = true
                }
            }, color = Western.Stamp)
            LinkButton(if (confirmClose) "Tap again to close" else "Close the table", {
                if (confirmClose) actions.closeTable() else confirmClose = true
            }, color = Western.Stamp)
        }
    }
}

/* ---------- Lobbies ---------- */

@Composable
private fun GuestLobbyScreen(ui: TableUi, actions: TableActions) {
    val view = ui.view
    val n = view.seats.size
    SunburstBackground(centerYFraction = 0.15f) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Ribbon(view.table)
            Text("You're in, ${view.you?.name.orEmpty()}!", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
            Text(
                "Waiting for the host to deal the cards. " + if (n < 5) "At least 5 are needed, $n so far." else "$n at the table.",
                style = Type.body,
                color = Western.Ink,
            )
            SeatList(view.seats, view.you?.id)
            RulesNote(view)
            Text("Once the cards are dealt, keep your screen to yourself.", style = Type.body.copy(fontSize = 15.sp), color = Western.Ink)
            LinkButton("Leave the table", actions::leaveTable, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun RulesNote(view: TableView) {
    val r = view.rules
    val n = view.seats.size
    ReplaySection("House rules") {
        val lines = buildList {
            if (n >= 5) add(roleSummary(n, r))
            add("Nights last up to ${formatClock(r.nightSeconds)}. They end sooner once everyone has chosen.")
            add(if (r.voteMode == VoteMode.OPEN) "Votes are shown with names." else "Votes are secret: only the count is shown.")
            add(if (r.revealRoleOnDeath) "A player's role is revealed when they're out." else "Roles stay hidden until the end.")
            if (!r.angelCanSelfProtect) add("The Angel can't protect themselves.")
        }
        lines.forEach { Text(it, style = Type.body.copy(fontSize = 15.sp), color = Western.Ink) }
    }
}

@Composable
private fun HostLobbyScreen(ui: TableUi, actions: TableActions) {
    val view = ui.view
    val hostSeat = view.seats.firstOrNull { it.host }
    var confirmClose by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Western.Paper).systemBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ribbon(view.table)
                Spacer(Modifier.weight(1f))
                LinkButton("Back", actions::pause)
            }
            Text("Gather round", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
            JoinCard(ui.joinUrl)
            Text("At the table", style = Type.title, color = Western.Ink, modifier = Modifier.padding(top = 4.dp))
            SeatList(view.seats, view.you?.id, onRemove = { actions.removeSeat(it.id) })
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Switch) { actions.setPlaying(hostSeat == null) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("I'm playing too", style = Type.bodyStrong.copy(fontSize = 16.5.sp), color = Western.Ink)
                    Text(
                        if (hostSeat != null) "You get a card like everyone else." else "You only run the game and see nothing secret.",
                        style = Type.body.copy(fontSize = 14.sp),
                        color = Western.PaperText,
                    )
                }
                TickBox(hostSeat != null)
            }
            LinkButton(if (hostSeat != null) "Change my name" else "Set my name", actions::editHost)
        }
        Column(
            Modifier.fillMaxWidth().background(Western.PaperShade).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(roleSummary(view.seats.size, view.rules), style = Type.bodyStrong.copy(fontSize = 16.sp), color = Western.Ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinkButton("House rules", actions::openRules)
                Spacer(Modifier.weight(1f))
                LinkButton(if (confirmClose) "Tap again to close" else "Close the table", {
                    if (confirmClose) actions.closeTable() else confirmClose = true
                }, color = Western.Stamp)
            }
            BigButton("Deal the cards", { actions.host(HostCommand.Deal) }, enabled = view.seats.size >= 5 && !ui.busy)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TickBox(on: Boolean) {
    Box(
        Modifier
            .size(30.dp)
            .background(if (on) Western.Magenta else Western.Cream, RoundedCornerShape(6.dp))
            .border(3.dp, Western.Ink, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Text("✓", style = Type.title.copy(fontSize = 20.sp), color = Western.Cream)
    }
}

/** The code and address friends use to join, or what to switch on first. */
@Composable
private fun JoinCard(joinUrl: String?) {
    PaperNote(Modifier.fillMaxWidth(), angle = 0f) {
        if (joinUrl == null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Switch on Wi-Fi or your hotspot", style = Type.title, color = Western.Ink)
                Text(
                    "Friends join over the same Wi-Fi as this phone, or over its hotspot. The joining code shows up here as soon as one is on.",
                    style = Type.body,
                    color = Western.Ink,
                )
            }
            return@PaperNote
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QrCode(joinUrl, Modifier.fillMaxWidth(0.62f))
            Text(
                joinUrl.removePrefix("http://").trimEnd('/'),
                style = Type.title.copy(fontSize = 26.sp),
                color = Western.Ink,
                modifier = Modifier.semantics { contentDescription = "Address: ${joinUrl.removePrefix("http://").trimEnd('/')}" },
            )
            Text(
                "Friends join this phone's Wi-Fi or hotspot, then scan the code with their camera, or type the address into " +
                    "their browser. No app needed. Friends with the app can tap Join a game.",
                style = Type.body.copy(fontSize = 15.sp, lineHeight = 21.sp),
                color = Western.Ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}
