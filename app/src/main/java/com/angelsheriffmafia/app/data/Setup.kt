package com.angelsheriffmafia.app.data

import com.angelsheriffmafia.engine.Rules
import kotlinx.serialization.Serializable

/** A player as typed into setup, saved for next time. */
@Serializable
data class SavedPlayer(val name: String, val pronunciation: String = "")

data class Setup(
    val players: List<SavedPlayer> = emptyList(),
    val rules: Rules = Rules(),
    val narratorOn: Boolean = true,
)
