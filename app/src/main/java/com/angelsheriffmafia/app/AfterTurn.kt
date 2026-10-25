package com.angelsheriffmafia.app

import com.angelsheriffmafia.engine.SheriffCheck

/** What the player who just confirmed sees before passing the phone on. */
data class AfterTurn(
    val playerName: String,
    val kind: Kind,
    val sheriffCheck: SheriffCheck? = null,
    val targetName: String = "",
    /** The last turn of the round: the phone goes to the middle next, not to a person. */
    val lastTurn: Boolean,
) {
    enum class Kind { NIGHT, VOTE }
}
