package com.angelsheriffmafia.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import com.angelsheriffmafia.app.AfterTurn
import com.angelsheriffmafia.app.R
import com.angelsheriffmafia.app.ui.components.BigButton
import com.angelsheriffmafia.app.ui.components.HoldButton
import com.angelsheriffmafia.app.ui.components.LinkButton
import com.angelsheriffmafia.app.ui.components.Moon
import com.angelsheriffmafia.app.ui.components.NightBackground
import com.angelsheriffmafia.app.ui.components.PaperNote
import com.angelsheriffmafia.app.ui.components.PlankButton
import com.angelsheriffmafia.app.ui.components.PlankGrid
import com.angelsheriffmafia.app.ui.components.Ribbon
import com.angelsheriffmafia.app.ui.components.Stamp
import com.angelsheriffmafia.app.ui.components.ThumbPad
import com.angelsheriffmafia.app.ui.components.TurnStars
import com.angelsheriffmafia.app.ui.components.WoodBackground
import com.angelsheriffmafia.app.ui.theme.Rye
import com.angelsheriffmafia.app.ui.theme.Type
import com.angelsheriffmafia.app.ui.theme.Western
import com.angelsheriffmafia.app.ui.theme.art
import com.angelsheriffmafia.app.ui.theme.title
import com.angelsheriffmafia.engine.Game
import com.angelsheriffmafia.engine.GameState
import com.angelsheriffmafia.engine.Phase
import com.angelsheriffmafia.engine.Player
import com.angelsheriffmafia.engine.PlayerId
import com.angelsheriffmafia.engine.Role
import kotlinx.coroutines.delay

/* ---------- Pass the phone ---------- */

/** Cover screen between turns. Nothing secret shows until the named player holds the button. */
@Composable
fun PassScreen(ribbon: String, turn: Int, total: Int, name: String, onOpen: () -> Unit, onPause: () -> Unit) {
    NightBackground {
        Moon(60.dp, Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(top = 26.dp, end = 26.dp))
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Ribbon(ribbon)
                if (total <= 16) TurnStars(turn, total)
                Text(
                    "Pass the phone to $name",
                    style = Type.headline,
                    color = Western.Cream,
                    modifier = Modifier.padding(top = 6.dp).semantics { heading() },
                )
                Text("Only $name should look. Everyone else, eyes on the stars.", style = Type.body, color = Western.Lavender)
            }

            // The saloon with the player's name painted on its sign.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                val ratio = 390f / 500f
                val w = minOf(maxWidth, maxHeight * ratio)
                Box(Modifier.fillMaxWidth().height(28.dp).align(Alignment.BottomCenter).background(Western.WoodDeep))
                Box(Modifier.padding(bottom = 0.dp).fillMaxWidth(w / maxWidth).aspectRatio(ratio)) {
                    Image(painterResource(R.drawable.art_saloon), contentDescription = "A saloon at night with its swinging doors shut", modifier = Modifier.fillMaxSize())
                    // The sign sits at about 12% to 29% of the image height, 16% to 84% of its width.
                    Column(
                        Modifier
                            .fillMaxWidth(0.66f)
                            .align(Alignment.TopCenter)
                            .offset(y = w / ratio * 0.125f)
                            .height(w / ratio * 0.16f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("YOUR TURN", style = Type.label.copy(fontSize = 11.sp), color = Western.Stamp)
                        Text(
                            name.uppercase(),
                            style = Type.poster.copy(fontSize = if (name.length > 8) 30.sp else 40.sp, lineHeight = 42.sp),
                            color = Western.Ink,
                            maxLines = 1,
                        )
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().background(Western.WoodDeep).padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HoldButton(title = "I'm $name", subtitle = "Hold down to open the doors", onHeld = onOpen)
                LinkButton("Pause game", onPause, color = Western.Lavender)
            }
        }
    }
}

/* ---------- Role card ---------- */

private data class PosterText(val header: String, val tagline: String, val blurb: String)

private fun posterFor(role: Role) = when (role) {
    Role.MAFIA -> PosterText("WANTED", "DEAD OR ALIVE · \$5,000 REWARD", "Each night, pick a target with your gang. By day, look innocent.")
    Role.ANGEL -> PosterText("GUARDIAN", "WATCHES OVER THE TOWN", "Each night, protect one person. If the Mafia come for them, they live.")
    Role.SHERIFF -> PosterText("THE LAW", "BY ORDER OF THE TOWN", "Each night, ask about one person and learn if they're Mafia. Use it wisely by day.")
    Role.VILLAGER -> PosterText("TOWNSFOLK", "HONEST CITIZEN", "Sniff out the Mafia and vote them out of town. At night, guess who they'll go after.")
}

@Composable
fun RoleCardScreen(game: GameState, player: Player, nextPlayer: String?, onDone: () -> Unit, previewHolding: Boolean = false) {
    var holding by remember { mutableStateOf(previewHolding) }
    var seen by remember(player.id) { mutableStateOf(false) }
    WoodBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Ribbon("For your eyes only")
            Text("${player.name}, you are…", style = Type.headline.copy(fontSize = 30.sp), color = Western.Cream, modifier = Modifier.semantics { heading() })
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (holding) {
                    val gang = if (player.role == Role.MAFIA) game.mafia.filter { it.id != player.id }.map { it.name } else emptyList()
                    WantedPoster(player.role, gang)
                } else {
                    PosterBack()
                }
            }
            ThumbPad(
                label = "Hold to see your card",
                hint = "Let go and it hides again." + (nextPlayer?.let { " Then pass the phone to $it." } ?: " Then you're done."),
                onPressChange = {
                    holding = it
                    if (it) seen = true
                },
            )
            BigButton(if (nextPlayer != null) "Done, pass it on" else "Done", onDone, enabled = seen && !holding, height = 58.dp)
        }
    }
}

@Composable
internal fun PosterBack() {
    PaperPanel(Modifier.fillMaxWidth(0.86f).aspectRatio(0.7f)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("?", style = Type.poster.copy(fontSize = 120.sp, lineHeight = 120.sp), color = Western.PaperText)
            Text("Your card is face down", style = Type.bodyStrong, color = Western.PaperText)
        }
    }
}

@Composable
internal fun PaperPanel(modifier: Modifier = Modifier, angle: Float = -1.6f, content: @Composable () -> Unit) {
    Box(
        modifier
            .rotate(angle)
            .drawBehind {
                drawRect(Western.Ink, topLeft = Offset(7.dp.toPx(), 9.dp.toPx()), size = size)
            }
            .background(
                Brush.radialGradient(
                    listOf(Western.Paper, Western.Paper, Color(0xFFDDBE86)),
                    radius = 900f,
                ),
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) { content() }
}

@Composable
fun WantedPoster(role: Role, gang: List<String>) {
    val text = posterFor(role)
    PaperPanel(Modifier.fillMaxWidth(0.9f)) {
        Column(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text.header, style = Type.poster.copy(fontSize = 38.sp), color = Western.Ink, maxLines = 1)
            Text(
                text.tagline,
                style = Type.label.copy(fontSize = 11.sp),
                color = Western.PaperText,
                modifier = Modifier
                    .drawBehind {
                        drawLine(Western.Ink, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx())
                        drawLine(Western.Ink, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 210.dp)
                    .aspectRatio(1.25f)
                    .background(Western.PaperShade)
                    .border(3.dp, Western.Ink),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Image(painterResource(role.art()), contentDescription = "${role.title()} character", modifier = Modifier.fillMaxSize().padding(top = 6.dp))
            }
            Text(role.title().uppercase(), style = Type.poster.copy(fontSize = 48.sp, lineHeight = 50.sp), color = Western.Ink)
            Text(text.blurb, style = Type.body.copy(fontSize = 15.sp, lineHeight = 20.sp), color = Western.Ink, textAlign = TextAlign.Center)
            if (gang.isNotEmpty()) {
                Stamp("Your gang: " + gang.joinToString(" · "), angle = -2f)
            } else if (role == Role.MAFIA) {
                Stamp("You ride alone", angle = -2f)
            }
        }
    }
}

/* ---------- Night pick ---------- */

internal fun prompt(role: Role) = when (role) {
    Role.MAFIA -> "Who gets it\ntonight?"
    Role.ANGEL -> "Who will you\nprotect tonight?"
    Role.SHERIFF -> "Who will you\nlook into?"
    Role.VILLAGER -> "Who will the\nMafia go after?"
}

/** Same grid and layout for every role. Only the prompt, the note and the tags change. */
@Composable
fun NightPickScreen(game: GameState, by: PlayerId, openedAt: Long, onConfirm: (PlayerId) -> Unit) {
    val me = game.player(by)
    val phase = game.phase as Phase.Night
    val night = game.nights.last()
    val valid = remember(game, by) { Game.validNightTargets(game, by).toSet() }
    var selected by remember(phase) { mutableStateOf<PlayerId?>(null) }
    val waitMillis = game.rules.minTurnSeconds * 1000L
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(openedAt) {
        while (SystemClock.elapsedRealtime() - openedAt < waitMillis) {
            now = SystemClock.elapsedRealtime()
            delay(100)
        }
        now = SystemClock.elapsedRealtime()
    }
    val elapsed = (now - openedAt).coerceAtLeast(0)
    val waiting = elapsed < waitMillis
    val secondsLeft = ((waitMillis - elapsed + 999) / 1000).toInt()

    val partners = Game.partnerPicks(game, by)
    val teammates = if (me.role == Role.MAFIA) game.mafia.map { it.id }.toSet() - by else emptySet()

    NightBackground {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ribbon("Night ${phase.night}")
                Spacer(Modifier.weight(1f))
                Text("Turn ${phase.turn + 1}/${night.turnOrder.size}", style = Type.bodyStrong.copy(fontSize = 15.sp), color = Western.Lavender)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Two lines for every role, so the layout looks identical from across the room.
                Text(prompt(me.role), style = Type.headline, color = Western.Cream, minLines = 2, maxLines = 2, modifier = Modifier.semantics { heading() })
                Text("${me.name}, you're the ${me.role.title()}. Pick one name.", style = Type.body, color = Western.Lavender)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 6.dp)) {
                PlankGrid(game.players) { p, mod ->
                    PlankButton(
                        name = p.name,
                        selected = selected == p.id,
                        enabled = p.id in valid,
                        dead = !p.alive,
                        onClick = { selected = p.id },
                        modifier = mod,
                        tag = when {
                            p.id in teammates -> "Partner"
                            p.id == by -> "You"
                            else -> null
                        },
                    )
                }
            }
            PaperNote(Modifier.fillMaxWidth()) {
                Row(Modifier.heightIn(min = 62.dp), verticalAlignment = Alignment.CenterVertically) {
                    Stamp(if (me.role == Role.MAFIA && partners.isNotEmpty()) "Wire" else "Note", angle = -5f)
                    Spacer(Modifier.padding(start = 12.dp))
                    Text(nightNote(game, me, partners.map { game.player(it.by).name to game.player(it.target).name }), style = Type.body.copy(fontSize = 15.sp, lineHeight = 20.sp), color = Western.Ink)
                }
            }
            BigButton(
                text = when {
                    waiting -> "Confirm · $secondsLeft"
                    selected == null -> "Pick a name"
                    else -> "Confirm " + game.player(selected!!).name
                },
                onClick = { selected?.let(onConfirm) },
                enabled = !waiting && selected != null,
                progress = if (waiting) elapsed.toFloat() / waitMillis else null,
            )
        }
    }
}

private fun nightNote(game: GameState, me: Player, partners: List<Pair<String, String>>): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    when (me.role) {
        Role.MAFIA -> {
            if (partners.isEmpty()) {
                append("You pick first tonight.")
            } else {
                partners.forEachIndexed { i, (who, target) ->
                    if (i > 0) append(" ")
                    append("$who chose ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(target.uppercase()) }
                    append(".")
                }
            }
            append(if (Game.hasFinalSay(game, me.id)) " Your choice is final." else " A partner decides after you.")
        }
        Role.ANGEL -> append(
            "Whoever you pick survives a Mafia attack tonight." +
                if (!game.rules.angelCanRepeatProtect) " You can't pick the same person two nights running." else "",
        )
        Role.SHERIFF -> append("You'll see what you learned right after you confirm. Only you.")
        Role.VILLAGER -> append("Just a guess, it doesn't change the game. Right guesses score in the replay at the end.")
    }
}

/* ---------- Secret vote ---------- */

@Composable
fun SecretVoteScreen(game: GameState, phase: Phase.Voting, voter: PlayerId, onVote: (PlayerId?) -> Unit) {
    var selected by remember(phase.turn) { mutableStateOf<PlayerId?>(null) }
    val me = game.player(voter)
    NightBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Ribbon(if (phase.isRevote) "Revote · day ${phase.day}" else "Secret vote · day ${phase.day}")
            Text("Who should leave town?", style = Type.headline, color = Western.Cream, modifier = Modifier.semantics { heading() })
            Text("${me.name}, pick one name. Nobody will see your vote.", style = Type.body, color = Western.Lavender)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 10.dp)) {
                PlankGrid(game.players.filter { it.id in phase.candidates || !it.alive }) { p, mod ->
                    PlankButton(
                        name = p.name,
                        selected = selected == p.id,
                        enabled = p.id in phase.candidates && p.id != voter,
                        dead = !p.alive,
                        onClick = { selected = p.id },
                        modifier = mod,
                        tag = if (p.id == voter) "You" else null,
                    )
                }
            }
            if (game.rules.allowSkipVote) {
                LinkButton("Skip my vote", { onVote(null) }, color = Western.Lavender, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            BigButton(
                text = selected?.let { "Vote " + game.player(it).name } ?: "Pick a name",
                onClick = { selected?.let(onVote) },
                enabled = selected != null,
            )
        }
    }
}

/* ---------- After a private turn ---------- */

/**
 * Shown after every night turn and secret vote, for every role. The Sheriff's
 * answer is under the thumb; everyone else sees the same "locked in" card, so
 * the time spent here gives nothing away.
 */
@Composable
fun AfterTurnScreen(after: AfterTurn, nextPlayer: String?, onDone: () -> Unit, previewHolding: Boolean = false) {
    var holding by remember { mutableStateOf(previewHolding) }
    var seen by remember(after) { mutableStateOf(false) }
    NightBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Ribbon(if (after.kind == AfterTurn.Kind.NIGHT) "Turn done" else "Vote cast")
            Text("Thanks, ${after.playerName}.", style = Type.headline, color = Western.Cream, modifier = Modifier.semantics { heading() })
            Text("Hold the pad to check your result, then hand the phone on.", style = Type.body, color = Western.Lavender)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (holding) {
                    PaperPanel(Modifier.fillMaxWidth(0.9f), angle = -1f) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 18.dp).semantics { liveRegion = LiveRegionMode.Assertive },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val check = after.sheriffCheck
                            if (check != null) {
                                Text("SHERIFF'S REPORT", style = Type.label, color = Western.PaperText)
                                Text(after.targetName.uppercase(), style = Type.poster.copy(fontSize = 40.sp), color = Western.Ink, textAlign = TextAlign.Center)
                                val verdict = when {
                                    check.role != null -> "is the ${check.role!!.title()}"
                                    check.isMafia -> "is Mafia"
                                    else -> "is not Mafia"
                                }
                                Stamp(verdict, color = if (check.isMafia) Western.Stamp else Western.Ink, angle = -4f)
                            } else {
                                Text("LOCKED IN", style = Type.poster.copy(fontSize = 40.sp), color = Western.Ink)
                                Text(
                                    if (after.kind == AfterTurn.Kind.NIGHT) "Your choice is in. Act natural." else "Your vote is in.",
                                    style = Type.bodyStrong,
                                    color = Western.Ink,
                                )
                            }
                        }
                    }
                } else {
                    Text("Hidden", style = Type.poster.copy(fontSize = 36.sp, fontFamily = Rye), color = Western.NightDim)
                }
            }
            ThumbPad(
                label = "Hold to see",
                hint = "Let go and it hides again.",
                onPressChange = {
                    holding = it
                    if (it) seen = true
                },
            )
            BigButton(
                if (nextPlayer != null) "Pass to $nextPlayer" else "Put the phone in the middle",
                onDone,
                enabled = seen && !holding,
                height = 58.dp,
                color = Western.Mustard,
                textColor = Western.Ink,
            )
        }
    }
}
