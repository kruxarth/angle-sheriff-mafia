package com.angelsheriffmafia.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.angelsheriffmafia.app.R
import com.angelsheriffmafia.app.ui.components.BigButton
import com.angelsheriffmafia.app.ui.components.LinkButton
import com.angelsheriffmafia.app.ui.components.PaperNote
import com.angelsheriffmafia.app.ui.components.PlankButton
import com.angelsheriffmafia.app.ui.components.PlankGrid
import com.angelsheriffmafia.app.ui.components.Ribbon
import com.angelsheriffmafia.app.ui.components.SpeakerIcon
import com.angelsheriffmafia.app.ui.components.SpeechBubble
import com.angelsheriffmafia.app.ui.components.Stamp
import com.angelsheriffmafia.app.ui.components.SunburstBackground
import com.angelsheriffmafia.app.ui.theme.Type
import com.angelsheriffmafia.app.ui.theme.Western
import com.angelsheriffmafia.app.ui.theme.art
import com.angelsheriffmafia.app.ui.theme.color
import com.angelsheriffmafia.app.ui.theme.title
import com.angelsheriffmafia.engine.GameState
import com.angelsheriffmafia.engine.Phase
import com.angelsheriffmafia.engine.PlayerId
import com.angelsheriffmafia.engine.Replay
import com.angelsheriffmafia.engine.StoryLine
import com.angelsheriffmafia.engine.Role as GameRole
import com.angelsheriffmafia.engine.Team
import kotlinx.coroutines.delay

internal fun roleWord(role: GameRole) = when (role) {
    GameRole.MAFIA -> "Mafia"
    GameRole.ANGEL -> "the Angel"
    GameRole.SHERIFF -> "the Sheriff"
    GameRole.VILLAGER -> "a villager"
}

private fun aliveLine(game: GameState): String {
    val left = game.alive.size
    return if (game.winner == null) "$left left in town. The Mafia are still out there." else "$left left in town."
}

/** Orange ground strip at the bottom of day screens, where the actions live. */
@Composable
internal fun DayFooter(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Western.Orange)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/* ---------- Dawn ---------- */

@Composable
fun DawnScreen(game: GameState, onContinue: () -> Unit, onReplay: () -> Unit) {
    val phase = game.phase as Phase.Dawn
    val killed = game.nights.last().killed?.let(game::player)
    DawnContent(
        night = phase.day,
        killedName = killed?.name,
        killedRole = killed?.role?.takeIf { game.rules.revealRoleOnDeath },
        quiet = false,
        aliveLine = aliveLine(game),
    ) {
        val over = game.winner != null
        BigButton(
            if (over) "See who won" else "Start talking · ${formatClock(game.rules.discussionSeconds)}",
            onContinue,
        )
        HearItAgain(onReplay, Modifier.align(Alignment.CenterHorizontally))
    }
}

/**
 * The morning picture: a grave for whoever the Mafia got, or the rooster after a night
 * with no death. [killedRole] is set only when roles are revealed.
 */
@Composable
fun DawnContent(
    night: Int,
    killedName: String?,
    killedRole: GameRole?,
    quiet: Boolean,
    aliveLine: String,
    header: @Composable () -> Unit = { Ribbon("After night $night") },
    footer: @Composable ColumnScope.() -> Unit,
) {
    SunburstBackground(centerYFraction = 0.6f) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 22.dp, start = 22.dp, end = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                header()
                Text("Morning in Dry Gulch!", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (killedName != null) {
                    val ratio = 330f / 454f
                    val h = minOf(maxHeight, maxWidth / ratio * 0.8f)
                    Box(Modifier.height(h).aspectRatio(ratio)) {
                        Image(
                            painterResource(R.drawable.art_grave),
                            contentDescription = "A wooden grave marker with a vulture perched on top",
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Lettering on the marker board, roughly 40% down the image.
                        Column(
                            Modifier.align(Alignment.TopCenter).offset(y = h * 0.36f).fillMaxWidth(0.5f).rotate(-3f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("R.I.P.", style = Type.label, color = Western.WoodDeep)
                            Text(
                                killedName.uppercase(),
                                style = Type.poster.copy(fontSize = if (killedName.length > 7) 22.sp else 30.sp, lineHeight = 32.sp),
                                color = Western.WoodDeep,
                                maxLines = 1,
                            )
                            Text("Night $night · Boot Hill", style = Type.body.copy(fontSize = 12.sp), color = Western.WoodDeep)
                        }
                        if (killedRole != null) {
                            Stamp(
                                "Was ${roleWord(killedRole)}",
                                modifier = Modifier.align(Alignment.Center).offset(x = 40.dp, y = h * 0.16f).background(Western.Paper),
                                angle = -8f,
                            )
                        }
                    }
                    Rooster(Modifier.align(Alignment.BottomStart).padding(start = 4.dp), small = true)
                } else {
                    Box(Modifier.size(240.dp).background(Western.Cream, CircleShape).border(4.dp, Western.Ink, CircleShape))
                    Rooster(Modifier.align(Alignment.Center), small = false)
                }
            }
            DayFooter {
                Text(
                    when {
                        killedName != null -> "Last night the Mafia got $killedName."
                        quiet -> "A quiet night. The Mafia didn't strike."
                        else -> "Someone was attacked in the night, but the Angel saved them!"
                    },
                    style = Type.bodyStrong.copy(fontSize = 19.sp),
                    color = Western.Ink,
                )
                Text(aliveLine, style = Type.body, color = Western.Ink)
                Spacer(Modifier.height(4.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { footer() }
            }
        }
    }
}

/** Speaker button that has the narrator read the morning news again. */
@Composable
internal fun HearItAgain(onReplay: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onReplay)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpeakerIcon(22.dp)
        Text("Hear it again", style = Type.bodyStrong.copy(fontSize = 16.sp), color = Western.Ink)
    }
}

@Composable
private fun Rooster(modifier: Modifier, small: Boolean) {
    Box(modifier) {
        Image(
            painterResource(R.drawable.art_rooster),
            contentDescription = "A rooster crowing on a fence post",
            modifier = Modifier.padding(top = 30.dp).size(if (small) 110.dp else 170.dp, if (small) 125.dp else 193.dp),
        )
        SpeechBubble("Cock-a-doodle-doo!", Modifier.offset(x = if (small) 0.dp else 60.dp, y = 0.dp), angle = -6f)
    }
}

fun formatClock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/* ---------- Discussion ---------- */

@Composable
fun DiscussionScreen(
    game: GameState,
    phase: Phase.Discussion,
    onStart: () -> Unit,
    onVote: () -> Unit,
    onTimeUp: () -> Unit,
) {
    var extra by rememberSaveable(phase.day) { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var timeUpFired by rememberSaveable(phase.day) { mutableStateOf(false) }
    val total = game.rules.discussionSeconds + extra
    val started = phase.startedAtMillis
    LaunchedEffect(started) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val left = if (started == null) total else (total - ((now - started) / 1000).toInt()).coerceAtLeast(0)
    LaunchedEffect(left == 0, started) {
        if (started != null && left == 0 && !timeUpFired) {
            timeUpFired = true
            onTimeUp()
        }
    }
    SunburstBackground(centerYFraction = 0.45f) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 22.dp, start = 22.dp, end = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Ribbon("Day ${phase.day} · Town meeting")
                Text("Who's the Mafia?", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
                Text("Talk it out. Accuse, defend, bluff.", style = Type.body, color = Western.Ink)
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                ClockFace(left)
            }
            DayFooter {
                Text(
                    "${game.alive.size} still in town: " + game.alive.joinToString(", ") { it.name },
                    style = Type.body,
                    color = Western.Ink,
                )
                if (started == null) {
                    BigButton("Start the clock", onStart)
                } else {
                    BigButton("We're ready to vote", onVote)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LinkButton("Add a minute", { extra += 60; timeUpFired = false })
                    if (started == null) LinkButton("Skip to the vote", onVote)
                }
            }
        }
    }
}

/** The big round discussion clock. Turns red when time is up. */
@Composable
internal fun ClockFace(secondsLeft: Int) {
    Box(
        Modifier
            .size(250.dp)
            .background(if (secondsLeft == 0) Western.Red else Western.Cream, CircleShape)
            .border(5.dp, Western.Ink, CircleShape)
            .semantics { contentDescription = "${secondsLeft / 60} minutes ${secondsLeft % 60} seconds left" },
        contentAlignment = Alignment.Center,
    ) {
        Text(formatClock(secondsLeft), style = Type.poster.copy(fontSize = 72.sp, lineHeight = 74.sp), color = Western.Ink)
    }
}

/* ---------- Open vote ---------- */

@Composable
fun OpenVoteScreen(game: GameState, phase: Phase.Voting, onResult: (List<PlayerId>) -> Unit) {
    var marked by remember(phase) { mutableStateOf(setOf<PlayerId>()) }
    SunburstBackground(centerYFraction = 0.2f) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Ribbon(if (phase.isRevote) "Revote · day ${phase.day}" else "The vote · day ${phase.day}")
                Text(
                    if (phase.isRevote) "It's a tie. Vote again!" else "On three, everyone point!",
                    style = Type.headline,
                    color = Western.Ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    if (phase.isRevote) "Only the tied players are up. Count down and point again." else "One, two, three... point at who should leave town. Then tap whoever got the most fingers.",
                    style = Type.body,
                    color = Western.Ink,
                )
                Text("Got the same count? Tap all of them for a tie.", style = Type.body.copy(fontSize = 14.sp), color = Western.Ink)
                Spacer(Modifier.height(4.dp))
                PlankGrid(game.players.filter { it.id in phase.candidates }) { p, mod ->
                    PlankButton(
                        name = p.name,
                        selected = p.id in marked,
                        enabled = true,
                        dead = false,
                        onClick = { marked = if (p.id in marked) marked - p.id else marked + p.id },
                        modifier = mod,
                    )
                }
            }
            DayFooter {
                val names = marked.map { game.player(it).name }
                BigButton(
                    when (marked.size) {
                        0 -> "Tap the top pick"
                        1 -> "Run ${names.single()} out of town"
                        else -> "It's a tie"
                    },
                    { onResult(marked.toList()) },
                    enabled = marked.isNotEmpty(),
                )
                if (game.rules.allowSkipVote) {
                    LinkButton("Nobody leaves today", { onResult(emptyList()) }, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

/* ---------- Vote result ---------- */

@Composable
fun VoteResultScreen(game: GameState, onContinue: () -> Unit) {
    val phase = game.phase as Phase.VoteResult
    val day = game.days.last()
    val out = day.eliminated?.let(game::player)
    SunburstBackground(centerYFraction = 0.55f) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 22.dp, start = 22.dp, end = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Ribbon("Day ${phase.day} · The verdict")
                Text(
                    if (out != null) "Run out of town!" else "Nobody leaves today",
                    style = Type.headline,
                    color = Western.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                Image(
                    painterResource(R.drawable.art_desert),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                if (out != null) {
                    val reveal = game.rules.revealRoleOnDeath
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(out.name.uppercase(), style = Type.poster, color = Western.Ink, maxLines = 1)
                        if (reveal) Stamp("Was ${roleWord(out.role)}", modifier = Modifier.background(Western.Paper), angle = -6f)
                        Image(
                            painterResource(if (reveal) out.role.art() else R.drawable.art_villager),
                            contentDescription = if (reveal) "${out.role.title()} character" else "A townsperson leaving town",
                            modifier = Modifier.padding(top = 6.dp).size(150.dp, 232.dp),
                        )
                    }
                } else {
                    Image(painterResource(R.drawable.art_villager), contentDescription = "A relieved villager", modifier = Modifier.size(150.dp, 232.dp))
                }
            }
            DayFooter {
                Text(
                    when {
                        out != null && day.randomTieBreak -> "A tie, settled by the luck of the draw."
                        out != null -> "The town has spoken."
                        day.tied.isNotEmpty() -> "Still tied, so the town lets everyone stay."
                        else -> "The town couldn't agree on anyone."
                    },
                    style = Type.bodyStrong.copy(fontSize = 18.sp),
                    color = Western.Ink,
                )
                Text(aliveLine(game), style = Type.body, color = Western.Ink)
                BigButton(if (game.winner != null) "See who won" else "Night falls...", onContinue, color = if (game.winner != null) Western.Magenta else Western.Night)
            }
        }
    }
}

/* ---------- Game over and replay ---------- */

@Composable
fun GameOverScreen(game: GameState, winner: Team, onPlayAgain: () -> Unit, onDone: () -> Unit) {
    GameOverScreen(game, winner) {
        BigButton("Play again", onPlayAgain)
        LinkButton("Back to the title", onDone)
    }
}

/** Who won, who was who, and the whole story of the game, with [footer] for what comes next. */
@Composable
fun GameOverScreen(game: GameState, winner: Team, footer: @Composable ColumnScope.() -> Unit) {
    val townWon = winner == Team.TOWN
    SunburstBackground(centerYFraction = 0.12f) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Ribbon("Game over")
            OutlinedTitle(
                if (townWon) "TOWN WINS" else "MAFIA WIN",
                50.sp,
                if (townWon) Western.Teal else Western.Red,
                Modifier.rotate(-3f).semantics { heading() },
            )
            Text(
                if (townWon) "Every Mafia member has been run out of Dry Gulch." else "The Mafia have taken over Dry Gulch.",
                style = Type.bodyStrong,
                color = Western.Ink,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy((-24).dp), verticalAlignment = Alignment.Bottom) {
                val winners = if (townWon) listOf(GameRole.ANGEL, GameRole.SHERIFF, GameRole.VILLAGER) else listOf(GameRole.MAFIA)
                winners.forEach { Image(painterResource(it.art()), contentDescription = "${it.title()} character", modifier = Modifier.size(110.dp, 170.dp)) }
            }

            ReplaySection("Who was who") {
                game.players.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(p.role.color(), CircleShape).border(2.dp, Western.Ink, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(p.name, style = Type.bodyStrong.copy(fontSize = 16.sp), color = Western.Ink, modifier = Modifier.weight(1f))
                        Text(
                            p.role.title() + if (p.alive) "" else " · out",
                            style = Type.body.copy(fontSize = 15.sp),
                            color = if (p.alive) Western.Ink else Western.PaperText,
                        )
                    }
                }
            }

            ReplaySection("The story") {
                Replay.story(game).forEach { line ->
                    when (line.style) {
                        StoryLine.Style.HEADING -> Text(line.text, style = Type.title.copy(fontSize = 19.sp), color = Western.Ink)
                        StoryLine.Style.LINE -> ReplayLine(line.text)
                        StoryLine.Style.STRONG -> ReplayLine(line.text, strong = true)
                    }
                }
            }

            val guessers = Replay.guessers(game)
            if (guessers.isNotEmpty()) {
                ReplaySection("Best guessers") {
                    guessers.forEach { ReplayLine(it) }
                }
            }

            footer()
        }
    }
}

@Composable
internal fun ReplaySection(title: String, content: @Composable () -> Unit) {
    PaperNote(Modifier.fillMaxWidth(), angle = 0f) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = Type.title, color = Western.Ink, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun ReplayLine(text: String, strong: Boolean = false) {
    Text(text, style = if (strong) Type.bodyStrong.copy(fontSize = 15.5.sp) else Type.body.copy(fontSize = 15.sp), color = Western.Ink)
}
