package com.angelsheriffmafia.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.angelsheriffmafia.app.data.SavedPlayer
import com.angelsheriffmafia.app.data.Setup
import com.angelsheriffmafia.app.ui.components.BigButton
import com.angelsheriffmafia.app.ui.components.LinkButton
import com.angelsheriffmafia.app.ui.components.Ribbon
import com.angelsheriffmafia.app.ui.components.SpeakerIcon
import com.angelsheriffmafia.app.ui.components.inkShadow
import com.angelsheriffmafia.app.ui.theme.Type
import com.angelsheriffmafia.app.ui.theme.Western
import com.angelsheriffmafia.engine.Rules
import com.angelsheriffmafia.engine.SheriffInfo
import com.angelsheriffmafia.engine.TieRule
import com.angelsheriffmafia.engine.VoteMode

private enum class Glyph { UP, DOWN, REMOVE }

@Composable
private fun GlyphButton(glyph: Glyph, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val color = if (enabled) Western.Ink else Western.Ink.copy(alpha = 0.25f)
        Canvas(Modifier.size(18.dp)) {
            val w = size.width
            val s = 2.6.dp.toPx()
            when (glyph) {
                Glyph.UP -> {
                    drawLine(color, Offset(w * 0.15f, w * 0.7f), Offset(w * 0.5f, w * 0.3f), s, StrokeCap.Round)
                    drawLine(color, Offset(w * 0.5f, w * 0.3f), Offset(w * 0.85f, w * 0.7f), s, StrokeCap.Round)
                }
                Glyph.DOWN -> {
                    drawLine(color, Offset(w * 0.15f, w * 0.3f), Offset(w * 0.5f, w * 0.7f), s, StrokeCap.Round)
                    drawLine(color, Offset(w * 0.5f, w * 0.7f), Offset(w * 0.85f, w * 0.3f), s, StrokeCap.Round)
                }
                Glyph.REMOVE -> {
                    drawLine(color, Offset(w * 0.2f, w * 0.2f), Offset(w * 0.8f, w * 0.8f), s, StrokeCap.Round)
                    drawLine(color, Offset(w * 0.8f, w * 0.2f), Offset(w * 0.2f, w * 0.8f), s, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun inkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Western.Ink,
    unfocusedTextColor = Western.Ink,
    focusedBorderColor = Western.Ink,
    unfocusedBorderColor = Western.PaperText,
    focusedLabelColor = Western.Ink,
    unfocusedLabelColor = Western.PaperText,
    cursorColor = Western.Magenta,
    focusedContainerColor = Western.Cream,
    unfocusedContainerColor = Western.Cream,
)

fun roleSummary(count: Int, rules: Rules): String {
    if (count < Rules.MIN_PLAYERS) return "Add at least ${Rules.MIN_PLAYERS} players. Up to ${Rules.MAX_PLAYERS} can play."
    val mafia = rules.mafiaFor(count)
    val villagers = count - mafia - 2
    if (villagers < 1 || mafia * 2 >= count) return "$mafia Mafia is too many for $count players. Change it in house rules."
    return "$count players: $mafia Mafia, 1 Angel, 1 Sheriff, $villagers villager${if (villagers == 1) "" else "s"}."
}

@Composable
fun SetupScreen(
    setup: Setup,
    error: String?,
    hasSavedGame: Boolean,
    onChange: (Setup) -> Unit,
    onPreview: (String) -> Unit,
    onRules: () -> Unit,
    onBack: () -> Unit,
    onDeal: () -> Unit,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    val players = setup.players
    fun setPlayers(list: List<SavedPlayer>) = onChange(setup.copy(players = list))
    fun add() {
        val name = newName.trim()
        if (name.isEmpty() || players.size >= Rules.MAX_PLAYERS) return
        setPlayers(players + SavedPlayer(name))
        newName = ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Western.Paper)
            .systemBarsPadding()
            .imePadding(),
    ) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ribbon("New game")
                        Spacer(Modifier.weight(1f))
                        LinkButton("Back", onBack)
                    }
                    Text("Who's playing?", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })
                    Text(
                        "Add names in seating order. The phone travels round the circle in this order.",
                        style = Type.body,
                        color = Western.PaperText,
                    )
                    if (hasSavedGame) {
                        Text("Starting a new game replaces the one in progress.", style = Type.body.copy(fontSize = 14.sp), color = Western.Stamp)
                    }
                }
            }
            itemsIndexed(players, key = { i, p -> "$i-${p.name}" }) { i, p ->
                PlayerRow(
                    index = i,
                    player = p,
                    isFirst = i == 0,
                    isLast = i == players.lastIndex,
                    onChange = { updated -> setPlayers(players.toMutableList().also { it[i] = updated }) },
                    onMove = { delta ->
                        val list = players.toMutableList()
                        val j = i + delta
                        list[i] = list[j].also { list[j] = list[i] }
                        setPlayers(list)
                    },
                    onRemove = { setPlayers(players.filterIndexed { j, _ -> j != i }) },
                    onPreview = { onPreview(p.pronunciation.ifBlank { p.name }) },
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(16) },
                        label = { Text("Add a player") },
                        singleLine = true,
                        enabled = players.size < Rules.MAX_PLAYERS,
                        textStyle = Type.bodyStrong,
                        colors = inkFieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(56.dp)
                            .inkShadow(offsetY = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Western.Teal)
                            .border(3.dp, Western.Ink, RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button, onClick = ::add)
                            .semantics { contentDescription = "Add player" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", style = Type.button.copy(fontSize = 30.sp), color = Western.Ink)
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(Western.PaperShade)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(roleSummary(players.size, setup.rules), style = Type.bodyStrong.copy(fontSize = 16.sp), color = Western.Ink)
            if (error != null) Text(error, style = Type.body, color = Western.Stamp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinkButton("House rules", onRules)
                Spacer(Modifier.weight(1f))
            }
            BigButton("Deal the roles", onDeal, enabled = players.size >= Rules.MIN_PLAYERS)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PlayerRow(
    index: Int,
    player: SavedPlayer,
    isFirst: Boolean,
    isLast: Boolean,
    onChange: (SavedPlayer) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
) {
    var showSay by rememberSaveable(index) { mutableStateOf(player.pronunciation.isNotEmpty()) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Western.Cream, RoundedCornerShape(10.dp))
            .border(2.dp, Western.Ink, RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", style = Type.button.copy(fontSize = 18.sp), color = Western.PaperText, modifier = Modifier.width(28.dp))
            Text(player.name, style = Type.bodyStrong, color = Western.Ink, modifier = Modifier.weight(1f), maxLines = 1)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onPreview)
                    .semantics { contentDescription = "Hear how the narrator says ${player.name}" },
                contentAlignment = Alignment.Center,
            ) { SpeakerIcon(22.dp) }
            GlyphButton(Glyph.UP, "Move ${player.name} up", enabled = !isFirst) { onMove(-1) }
            GlyphButton(Glyph.DOWN, "Move ${player.name} down", enabled = !isLast) { onMove(1) }
            GlyphButton(Glyph.REMOVE, "Remove ${player.name}", onClick = onRemove)
        }
        if (showSay) {
            OutlinedTextField(
                value = player.pronunciation,
                onValueChange = { onChange(player.copy(pronunciation = it.take(32))) },
                label = { Text("Say it like (e.g. Ee-shaan)") },
                singleLine = true,
                textStyle = Type.body,
                colors = inkFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp),
            )
        } else {
            Text(
                "Fix how the narrator says it",
                style = Type.body.copy(fontSize = 14.sp),
                color = Western.PaperText,
                modifier = Modifier
                    .padding(start = 28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button) { showSay = true }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
            )
        }
    }
}

/* ---------- House rules ---------- */

@Composable
fun RulesScreen(setup: Setup, onChange: (Setup) -> Unit, onDone: () -> Unit) {
    val rules = setup.rules
    fun set(r: Rules) = onChange(setup.copy(rules = r))
    val count = setup.players.size
    Column(
        Modifier
            .fillMaxSize()
            .background(Western.Paper)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Ribbon("House rules")
        Text("Play it your way", style = Type.headline, color = Western.Ink, modifier = Modifier.semantics { heading() })

        Choice(
            "Mafia",
            listOf(null, 1, 2, 3),
            rules.mafiaCount,
            label = { if (it == null) "Auto: ${Rules.suggestedMafia(count.coerceAtLeast(Rules.MIN_PLAYERS))}" else "$it" },
        ) { set(rules.copy(mafiaCount = it)) }

        Toggle("Angel can protect themselves", rules.angelCanSelfProtect) { set(rules.copy(angelCanSelfProtect = it)) }
        Toggle("Angel can protect the same person two nights running", rules.angelCanRepeatProtect) { set(rules.copy(angelCanRepeatProtect = it)) }

        Choice("The Sheriff learns", SheriffInfo.entries, rules.sheriffLearns, label = {
            if (it == SheriffInfo.MAFIA_OR_NOT) "Mafia or not" else "The exact role"
        }) { set(rules.copy(sheriffLearns = it)) }

        Toggle("Reveal a player's role when they're out", rules.revealRoleOnDeath) { set(rules.copy(revealRoleOnDeath = it)) }

        Choice("Voting", VoteMode.entries, rules.voteMode, label = {
            if (it == VoteMode.OPEN) "Point on three" else "Secret ballot"
        }) { set(rules.copy(voteMode = it)) }

        Choice("On a tie", TieRule.entries, rules.tieRule, label = {
            when (it) {
                TieRule.REVOTE -> "Vote again"
                TieRule.NOBODY -> "Nobody leaves"
                TieRule.RANDOM -> "Pick at random"
            }
        }) { set(rules.copy(tieRule = it)) }

        Toggle("Players can skip the vote", rules.allowSkipVote) { set(rules.copy(allowSkipVote = it)) }

        Choice("Discussion time", listOf(60, 120, 180, 300, 480), rules.discussionSeconds, label = { "${it / 60} min" }) {
            set(rules.copy(discussionSeconds = it))
        }
        Toggle("Go straight to the vote when time runs out", rules.voteWhenTimeRunsOut) { set(rules.copy(voteWhenTimeRunsOut = it)) }

        Choice("Shortest night turn", listOf(5, 8, 12), rules.minTurnSeconds, label = { "$it sec" }) {
            set(rules.copy(minTurnSeconds = it))
        }
        Text(
            "Every night turn lasts at least this long, so a villager tapping fast looks the same as a Mafia member thinking hard.",
            style = Type.body.copy(fontSize = 14.sp),
            color = Western.PaperText,
        )

        Toggle("Narrator voice", setup.narratorOn) { onChange(setup.copy(narratorOn = it)) }

        BigButton("Done", onDone)
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Switch) { onChange(!value) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.bodyStrong.copy(fontSize = 16.5.sp), color = Western.Ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = value,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Western.Cream,
                checkedTrackColor = Western.Magenta,
                checkedBorderColor = Western.Ink,
                uncheckedThumbColor = Western.PaperText,
                uncheckedTrackColor = Western.Cream,
                uncheckedBorderColor = Western.PaperText,
            ),
        )
    }
}

@Composable
private fun <T> Choice(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val perRow = if (options.size == 4) 4 else 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = Type.bodyStrong.copy(fontSize = 16.5.sp), color = Western.Ink)
        // Wrap chips over as many rows as they need.
        options.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val on = option == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (on) Western.Ink else Western.Cream)
                            .border(2.dp, Western.Ink, RoundedCornerShape(24.dp))
                            .clickable(role = Role.RadioButton) { onSelect(option) }
                            .semantics { contentDescription = "$title: ${label(option)}" + if (on) ", selected" else "" }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label(option), style = Type.body.copy(fontSize = 14.5.sp), color = if (on) Western.Mustard else Western.Ink, maxLines = 1)
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
