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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.angelsheriffmafia.app.R
import com.angelsheriffmafia.app.ui.components.BigButton
import com.angelsheriffmafia.app.ui.components.LinkButton
import com.angelsheriffmafia.app.ui.components.PaperNote
import com.angelsheriffmafia.app.ui.components.Ribbon
import com.angelsheriffmafia.app.ui.components.SpeechBubble
import com.angelsheriffmafia.app.ui.components.SunburstBackground
import com.angelsheriffmafia.app.ui.theme.Rye
import com.angelsheriffmafia.app.ui.theme.Type
import com.angelsheriffmafia.app.ui.theme.Western

/** Big cartoon lettering: ink outline, colored fill, offset ink shadow. */
@Composable
fun OutlinedTitle(text: String, size: TextUnit, fill: Color, modifier: Modifier = Modifier) {
    val base = TextStyle(fontFamily = Rye, fontSize = size, lineHeight = size * 1.02f, letterSpacing = 0.03.em)
    Box(modifier) {
        Text(text, style = base.copy(drawStyle = Stroke(width = 8f)), color = Western.Ink, modifier = Modifier.offset(4.dp, 5.dp))
        Text(text, style = base, color = Western.Ink, modifier = Modifier.offset(4.dp, 5.dp))
        Text(text, style = base.copy(drawStyle = Stroke(width = 9f)), color = Western.Ink)
        Text(text, style = base, color = fill)
    }
}

@Composable
fun TitleScreen(canContinue: Boolean, onContinue: () -> Unit, onNewGame: () -> Unit, onHowToPlay: () -> Unit) {
    SunburstBackground(centerYFraction = 0.62f) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Ribbon("One phone · whole town")
                Column(
                    Modifier.rotate(-4f).padding(top = 6.dp).semantics { heading() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OutlinedTitle("ANGEL", 58.sp, Western.Cream)
                    OutlinedTitle("SHERIFF", 54.sp, Western.Teal)
                    OutlinedTitle("MAFIA", 66.sp, Western.Red)
                }
                Text("Who's the Mafia?", style = Type.title, color = Western.Ink)
            }

            // The town lineup on the desert.
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(190.dp)
                        .background(Western.Cream, CircleShape)
                        .border(4.dp, Western.Ink, CircleShape),
                )
                Image(
                    painterResource(R.drawable.art_desert),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.BottomCenter,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(180.dp),
                )
                // Four characters overlapping in a lineup, scaled to the screen width.
                BoxWithConstraints(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    val unit = maxWidth / 100
                    val stage = unit * 33 * 348 / 224 + 8.dp
                    Character(R.drawable.art_angel, "Angel", unit * 29, stage, Modifier.offset(x = unit * 0))
                    Character(R.drawable.art_sheriff, "Sheriff", unit * 33, stage, Modifier.offset(x = unit * 21))
                    Character(R.drawable.art_villager, "Villager", unit * 27, stage, Modifier.offset(x = unit * 72))
                    Character(R.drawable.art_outlaw, "Mafia outlaw", unit * 31, stage, Modifier.offset(x = unit * 46))
                    SpeechBubble("Me? Never!", Modifier.offset(x = unit * 52, y = -(unit * 2)))
                }
            }

            Column(
                Modifier.fillMaxWidth().background(Western.Dirt).padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (canContinue) {
                    BigButton("Continue game", onContinue, color = Western.Teal, textColor = Western.Ink)
                    Spacer(Modifier.height(2.dp))
                }
                BigButton(if (canContinue) "New game" else "Start a game", onNewGame)
                LinkButton("How to play", onHowToPlay)
            }
        }
    }
}

@Composable
private fun Character(res: Int, description: String, width: Dp, stage: Dp, modifier: Modifier = Modifier) {
    // The art is 224 x 348; characters stand on the bottom edge.
    Box(modifier.width(width).height(stage)) {
        Image(
            painterResource(res),
            contentDescription = description,
            modifier = Modifier.align(Alignment.BottomStart).width(width).height(width * 348 / 224),
        )
    }
}

@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    SunburstBackground(centerYFraction = 0.1f) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Ribbon("How to play")
            Text("One phone runs the whole game", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
            Section(
                "The roles",
                "Mafia know each other and pick one person to get rid of each night.\n" +
                    "The Angel protects one person each night. If it's the Mafia's target, nobody dies.\n" +
                    "The Sheriff asks about one person each night and learns if they're Mafia.\n" +
                    "Villagers talk it out by day and vote someone out of town.",
            )
            Section(
                "Dealing",
                "Sit in a circle and type everyone's names in seating order. The phone goes round once. " +
                    "Each person holds their thumb on the pad to see their card, then passes it on.",
            )
            Section(
                "Night",
                "The phone goes round again and every living player takes a turn on the same screen, so nobody can tell " +
                    "who's who from across the room. Mafia pick a target, the Angel protects, the Sheriff investigates, and " +
                    "villagers guess who the Mafia will go after. Every turn takes at least a few seconds, so a fast tap " +
                    "looks the same as a hard think.",
            )
            Section(
                "Mafia without whispering",
                "The first Mafia member on the phone picks a target. The next one sees that choice and can agree or switch. " +
                    "The last Mafia member to hold the phone decides. A different player starts each night, so the final say moves around.",
            )
            Section(
                "Morning",
                "Put the phone in the middle. The narrator says what happened. Talk it over while the clock runs, then vote: " +
                    "point at once on three and tap who got the most votes, or pass the phone round for a secret vote.",
            )
            Section(
                "Winning",
                "The town wins when every Mafia member is out. The Mafia win when they equal or outnumber everyone else. " +
                    "At the end, the replay shows every move of the game.",
            )
            BigButton("Got it", onBack)
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    PaperNote(Modifier.fillMaxWidth(), angle = 0f) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = Type.title, color = Western.Ink, modifier = Modifier.semantics { heading() })
            Text(body, style = Type.body, color = Western.Ink)
        }
    }
}
