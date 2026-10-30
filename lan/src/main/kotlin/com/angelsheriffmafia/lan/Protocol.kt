package com.angelsheriffmafia.lan

import com.angelsheriffmafia.engine.GameState
import com.angelsheriffmafia.engine.PlayerId
import com.angelsheriffmafia.engine.Role
import com.angelsheriffmafia.engine.Rules
import com.angelsheriffmafia.engine.SheriffCheck
import com.angelsheriffmafia.engine.StoryLine
import com.angelsheriffmafia.engine.Team
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * What travels between the host phone and the players' phones. The host serves a small web
 * page and a JSON API on the local network:
 *
 *   GET  /api/hello                 who is hosting, for discovery
 *   POST /api/join   JoinRequest    take a seat, get a secret token
 *   GET  /api/events?token=…        server-sent events: a TableView whenever anything changes
 *   GET  /api/view?token=…          the same TableView once
 *   POST /api/act    ActRequest     a player's move
 *
 * Every TableView is cut down to what that seat may know: your own role, your gang if you're
 * Mafia, and public information. Nothing secret about other players ever leaves the host.
 */

const val PROTOCOL_VERSION = 1
const val DEFAULT_PORT = 8765
const val APP_ID = "angel-sheriff-mafia"

/** The network service type the host advertises for players who have the app. */
const val SERVICE_TYPE = "_angelsheriff._tcp."

val LanJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class Hello(
    val app: String = APP_ID,
    val protocol: Int = PROTOCOL_VERSION,
    val table: String,
    val players: Int,
    val inGame: Boolean,
    /** True when the host can hand out its own copy of the app. */
    val apk: Boolean = false,
)

@Serializable
data class JoinRequest(
    val name: String,
    val pronunciation: String? = null,
    /** A token from an earlier visit, to get the same seat back. */
    val token: String? = null,
    /** True when joining from the app rather than a browser. */
    val app: Boolean = false,
)

@Serializable
data class JoinResponse(val token: String? = null, val error: String? = null)

@Serializable
data class ActRequest(val token: String, val action: PlayerAction)

@Serializable
data class ActResponse(val ok: Boolean, val error: String? = null)

/** The moves a player can make from their own phone. */
@Serializable
sealed interface PlayerAction {
    /** I've seen my role card. */
    @Serializable
    @SerialName("seen")
    data object Seen : PlayerAction

    /** My night choice: a Mafia target, the Angel's protection, the Sheriff's check or a villager's guess. */
    @Serializable
    @SerialName("pick")
    data class Pick(val target: PlayerId) : PlayerAction

    /** My vote. A null target skips. */
    @Serializable
    @SerialName("vote")
    data class Vote(val target: PlayerId? = null) : PlayerAction

    /** Leave the table. Only between games. */
    @Serializable
    @SerialName("leave")
    data object Leave : PlayerAction
}

/** Sent with a server-sent `gone` event when a token no longer has a seat. */
@Serializable
data class Gone(val reason: String)

/* ---------- The view each seat gets ---------- */

@Serializable
enum class SeatStatus {
    /** Waiting for the host to deal. */
    LOBBY,

    /** Dealt into the game in progress (alive or not). */
    PLAYING,

    /** Sat down while a game was running. Dealt in next game. */
    WATCHING,

    /** The host closed the table. */
    CLOSED,
}

@Serializable
enum class Stage { DEAL, NIGHT, DAWN, DISCUSSION, VOTE, RESULT, OVER }

@Serializable
data class TableView(
    /** Goes up with every change at the table. */
    val version: Long,
    /** The host's clock when this view was made. Deadlines are on the same clock. */
    val now: Long,
    val table: String,
    val status: SeatStatus,
    /** Your seat, or null on the host's phone when the host isn't playing. */
    val you: SeatView? = null,
    val seats: List<SeatView>,
    val rules: Rules,
    val game: GameView? = null,
)

@Serializable
data class SeatView(
    val id: Int,
    val name: String,
    val connected: Boolean,
    val host: Boolean = false,
    /** Your player id in the current game, if dealt in. */
    val player: PlayerId? = null,
)

@Serializable
data class GameView(
    /** Counts games at this table, so a new deal never looks like the last one. */
    val number: Int,
    val stage: Stage,
    /** The night or day number. */
    val day: Int,
    /** Someone has won: the host's next step shows the end of the game. */
    val decided: Boolean = false,
    val players: List<PlayerView>,
    /** You, if you were dealt in. */
    val me: MeView? = null,
    val deal: DealView? = null,
    val night: NightView? = null,
    val dawn: DawnView? = null,
    val discussion: DiscussionView? = null,
    val vote: VoteView? = null,
    val result: ResultView? = null,
    val over: OverView? = null,
)

@Serializable
data class PlayerView(
    val id: PlayerId,
    val name: String,
    val alive: Boolean,
    /** Shown only once it's public: a dead player's role when the house rule reveals it, or everyone at the end. */
    val role: Role? = null,
    val seat: Int? = null,
)

@Serializable
data class MeView(
    val id: PlayerId,
    val role: Role,
    val alive: Boolean,
    /** Your fellow Mafia, if you're Mafia. */
    val gang: List<PlayerId> = emptyList(),
)

@Serializable
data class DealView(
    val seen: Boolean,
    /** Players still to check their card. */
    val waiting: List<PlayerId>,
)

@Serializable
data class Choice(val by: PlayerId, val target: PlayerId)

@Serializable
data class NightView(
    val endsAt: Long? = null,
    /** True while you can still choose (a Mafia member can change until the gang agrees). */
    val canPick: Boolean,
    val targets: List<PlayerId> = emptyList(),
    val myPick: PlayerId? = null,
    /** Mafia only: what the rest of the gang chose. */
    val gangPicks: List<Choice> = emptyList(),
    /** Mafia only: the whole gang picked the same name, so the kill is locked. */
    val agreed: Boolean = false,
    /** Mafia only: whose choice wins a split when time runs out. */
    val lead: PlayerId? = null,
    /** Sheriff only, after choosing. */
    val sheriff: SheriffCheck? = null,
)

@Serializable
data class DawnView(
    val night: Int,
    val killed: PlayerId? = null,
    val killedRole: Role? = null,
    /** Someone was attacked but the Angel saved them. */
    val saved: Boolean = false,
    /** Nobody was attacked. */
    val quiet: Boolean = false,
)

@Serializable
data class DiscussionView(
    val startedAt: Long? = null,
    val endsAt: Long? = null,
    val seconds: Int,
)

@Serializable
data class VoteView(
    val candidates: List<PlayerId>,
    val revote: Boolean,
    val canVote: Boolean,
    val voted: Boolean,
    val myVote: PlayerId? = null,
    val skipped: Boolean = false,
    /** Players who haven't voted yet. */
    val waiting: List<PlayerId>,
)

@Serializable
data class Tally(val target: PlayerId, val count: Int)

@Serializable
data class Ballot(val by: PlayerId, val target: PlayerId?)

@Serializable
data class ResultView(
    val eliminated: PlayerId? = null,
    val role: Role? = null,
    val tied: List<PlayerId> = emptyList(),
    val randomTieBreak: Boolean = false,
    val tally: List<Tally> = emptyList(),
    val skips: Int = 0,
    /** Who voted for whom, only with an open vote. */
    val ballots: List<Ballot>? = null,
)

@Serializable
data class OverView(
    val winner: Team,
    val story: List<StoryLine>,
    val guessers: List<String>,
    /** The whole game. Everything is public once it's over. */
    val state: GameState,
)
