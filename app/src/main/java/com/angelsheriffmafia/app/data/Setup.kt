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
    /** This phone owner's name, for hosting or joining a table. */
    val myName: String = "",
    val myPronunciation: String = "",
    /** Whether the host plays at their own table, or only runs it. */
    val hostPlays: Boolean = true,
)
