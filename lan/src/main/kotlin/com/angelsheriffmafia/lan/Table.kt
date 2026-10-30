package com.angelsheriffmafia.lan

import com.angelsheriffmafia.engine.GameState
import com.angelsheriffmafia.engine.PlayerId
import com.angelsheriffmafia.engine.Rules
import kotlinx.serialization.Serializable

/** Someone sitting at the host's table, on their own phone or the host's. */
@Serializable
data class Seat(
    val id: Int,
    val name: String,
    val pronunciation: String? = null,
    /** Secret that proves a phone owns this seat. Empty while the host is letting the player back in. */
    val token: String,
    val host: Boolean = false,
    /** Player id in the current game, or null if not dealt in. */
    val player: PlayerId? = null,
    val fromApp: Boolean = false,
)

/** Everything the host keeps, saved after every change so the table survives the app restarting. */
@Serializable
data class TableState(
    val name: String,
    /** In seating order. The host's own seat comes first when the host plays. */
    val seats: List<Seat> = emptyList(),
    val rules: Rules = Rules(),
    val game: GameState? = null,
    val gameNumber: Int = 0,
    /** When the current night's time runs out, on the host's clock. */
    val nightEndsAt: Long? = null,
    /** The discussion deadline the time-up call was last made for, so it's made once. */
    val timeUpCalledFor: Long? = null,
    val nextSeatId: Int = 1,
    val closed: Boolean = false,
) {
    fun seat(id: Int): Seat? = seats.firstOrNull { it.id == id }
    fun seatByToken(token: String): Seat? = if (token.isEmpty()) null else seats.firstOrNull { it.token == token }
    fun seatOfPlayer(player: PlayerId): Seat? = seats.firstOrNull { it.player == player }
    val hostSeat: Seat? get() = seats.firstOrNull { it.host }
}

/** Names are trimmed, single-spaced and kept short enough for a name plank. */
fun cleanName(raw: String): String =
    raw.filter { !it.isISOControl() }.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).trim()

const val MAX_NAME_LENGTH = 16
