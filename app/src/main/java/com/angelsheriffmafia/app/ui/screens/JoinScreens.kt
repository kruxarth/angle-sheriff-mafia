package com.angelsheriffmafia.app.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.angelsheriffmafia.app.ui.components.BigButton
import com.angelsheriffmafia.app.ui.components.LinkButton
import com.angelsheriffmafia.app.ui.components.Ribbon
import com.angelsheriffmafia.app.ui.components.SpeakerIcon
import com.angelsheriffmafia.app.ui.components.SunburstBackground
import com.angelsheriffmafia.app.ui.theme.Type
import com.angelsheriffmafia.app.ui.theme.Western

/** A table found on this Wi-Fi. [address] is "host:port". */
data class FoundTable(val name: String, val address: String, val players: Int? = null, val inGame: Boolean = false)

data class JoinUi(
    val found: List<FoundTable> = emptyList(),
    val address: String = "",
    val name: String = "",
    val pronunciation: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

/** Join a table hosted on another phone: pick one found nearby, or type the address on the host's screen. */
@Composable
fun JoinScreen(
    ui: JoinUi,
    onAddress: (String) -> Unit,
    onName: (String) -> Unit,
    onPronunciation: (String) -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Western.Paper).systemBarsPadding().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ribbon("Join a game")
                Spacer(Modifier.weight(1f))
                LinkButton("Back", onBack)
            }
            Text("Find the table", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
            Text("Join the same Wi-Fi as the host's phone, or its hotspot.", style = Type.body, color = Western.PaperText)
            if (ui.found.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).background(Western.Mustard, CircleShape).border(2.dp, Western.Ink, CircleShape))
                    Text("Looking for tables nearby…", style = Type.bodyStrong.copy(fontSize = 16.sp), color = Western.Ink)
                }
                Text(
                    "Can't see it? Type the address shown on the host's screen.",
                    style = Type.body.copy(fontSize = 15.sp),
                    color = Western.PaperText,
                )
            } else {
                ui.found.forEach { table ->
                    FoundRow(table, selected = table.address == ui.address.trim(), onClick = { onAddress(table.address) })
                }
            }
            OutlinedTextField(
                value = ui.address,
                onValueChange = { onAddress(it.take(64)) },
                label = { Text("Address, like 192.168.43.1:8765") },
                singleLine = true,
                textStyle = Type.bodyStrong,
                colors = inkFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.name,
                onValueChange = { onName(it.take(16)) },
                label = { Text("Your name") },
                singleLine = true,
                textStyle = Type.bodyStrong,
                colors = inkFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.pronunciation,
                onValueChange = { onPronunciation(it.take(32)) },
                label = { Text("Say it like (optional, e.g. Ee-shaan)") },
                singleLine = true,
                textStyle = Type.body,
                colors = inkFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onJoin() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(
            Modifier.fillMaxWidth().background(Western.PaperShade).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ui.error != null) Text(ui.error, style = Type.body, color = Western.Stamp)
            BigButton(
                if (ui.busy) "Joining…" else "Take a seat",
                onJoin,
                enabled = !ui.busy && ui.address.isNotBlank() && ui.name.isNotBlank(),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun FoundRow(table: FoundTable, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(if (selected) Western.Mustard else Western.Cream)
            .border(if (selected) 3.dp else 2.dp, Western.Ink, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = table.name + if (selected) ", selected" else "" }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(table.name, style = Type.bodyStrong, color = Western.Ink, maxLines = 1)
            val details = listOfNotNull(
                table.players?.let { "$it at the table" },
                "game on".takeIf { table.inGame },
                table.address,
            )
            Text(details.joinToString(" · "), style = Type.body.copy(fontSize = 14.sp), color = Western.PaperText, maxLines = 1)
        }
        Box(
            Modifier
                .size(22.dp)
                .background(if (selected) Western.Ink else Color.Transparent, CircleShape)
                .border(3.dp, Western.Ink, CircleShape),
        )
    }
}

/** The host's own name, and whether they play or only run the table. */
@Composable
fun HostStartScreen(
    name: String,
    pronunciation: String,
    plays: Boolean,
    tableOpen: Boolean,
    error: String?,
    onName: (String) -> Unit,
    onPronunciation: (String) -> Unit,
    onPlays: (Boolean) -> Unit,
    onPreview: (String) -> Unit,
    onOpen: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Western.Paper).systemBarsPadding().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ribbon("Host a table")
                Spacer(Modifier.weight(1f))
                LinkButton("Back", onBack)
            }
            Text("This phone runs the game", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
            Text(
                "Friends join over your Wi-Fi or hotspot and play on their own phones. Keep this app open, " +
                    "and plug in if you can: the narrator speaks from this phone.",
                style = Type.body,
                color = Western.PaperText,
            )
            Toggle("I'm playing too", plays, onPlays)
            OutlinedTextField(
                value = name,
                onValueChange = { onName(it.take(16)) },
                label = { Text(if (plays) "Your name" else "Your name (for the table's name)") },
                singleLine = true,
                textStyle = Type.bodyStrong,
                colors = inkFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = pronunciation,
                    onValueChange = { onPronunciation(it.take(32)) },
                    label = { Text("Say it like (optional)") },
                    singleLine = true,
                    textStyle = Type.body,
                    colors = inkFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { onPreview(pronunciation.ifBlank { name }) }
                        .semantics { contentDescription = "Hear how the narrator says your name" },
                    contentAlignment = Alignment.Center,
                ) { SpeakerIcon(24.dp) }
            }
        }
        Column(
            Modifier.fillMaxWidth().background(Western.PaperShade).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (error != null) Text(error, style = Type.body, color = Western.Stamp)
            BigButton(if (tableOpen) "Save" else "Open the table", onOpen, enabled = !plays || name.isNotBlank())
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** While a phone with a seat elsewhere waits for the host's first word. */
@Composable
fun ConnectingScreen(onBack: () -> Unit) {
    SunburstBackground(centerYFraction = 0.5f) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text("Finding your seat…", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
            Text("Still waiting? Check you're on the same Wi-Fi or hotspot as the host.", style = Type.body, color = Western.Ink)
            LinkButton("Back to the title", onBack)
        }
    }
}
