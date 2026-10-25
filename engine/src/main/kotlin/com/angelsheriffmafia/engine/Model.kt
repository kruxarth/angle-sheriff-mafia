package com.angelsheriffmafia.engine

import kotlinx.serialization.Serializable

typealias PlayerId = Int

@Serializable
enum class Role { MAFIA, ANGEL, SHERIFF, VILLAGER }

@Serializable
enum class Team { TOWN, MAFIA }

val Role.team: Team get() = if (this == Role.MAFIA) Team.MAFIA else Team.TOWN

@Serializable
data class Player(
    val id: PlayerId,
    val name: String,
    val role: Role,
    val alive: Boolean = true,
    /** How the narrator should say the name, e.g. "Ee-shaan". Null means say [name] as written. */
    val pronunciation: String? = null,
) {
    val spokenName: String get() = pronunciation?.takeIf { it.isNotBlank() } ?: name
}

@Serializable
enum class VoteMode {
    /** Everyone points at once, and one person taps who got the most votes. */
    OPEN,

    /** The phone goes round once more and each player votes in private. */
    SECRET,
}

@Serializable
enum class TieRule {
    /** Vote again between the tied players. A second tie means nobody leaves. */
    REVOTE,
    NOBODY,
    RANDOM,
}

@Serializable
enum class SheriffInfo { MAFIA_OR_NOT, EXACT_ROLE }

@Serializable
data class Rules(
    /** Null lets the app pick from the player count. */
    val mafiaCount: Int? = null,
    val angelCanSelfProtect: Boolean = true,
    val angelCanRepeatProtect: Boolean = false,
    val sheriffLearns: SheriffInfo = SheriffInfo.MAFIA_OR_NOT,
    val revealRoleOnDeath: Boolean = true,
    val voteMode: VoteMode = VoteMode.OPEN,
    val tieRule: TieRule = TieRule.REVOTE,
    val allowSkipVote: Boolean = true,
    val discussionSeconds: Int = 180,
    val voteWhenTimeRunsOut: Boolean = false,
    /** Seconds before Confirm unlocks on a night turn, so every role takes the same time. */
    val minTurnSeconds: Int = 8,
) {
    companion object {
        const val MIN_PLAYERS = 5
        const val MAX_PLAYERS = 16

        /** About one Mafia per three or four players. */
        fun suggestedMafia(players: Int): Int = when {
            players <= 5 -> 1
            players <= 8 -> 2
            else -> 3
        }
    }

    fun mafiaFor(players: Int): Int = mafiaCount ?: suggestedMafia(players)
}

@Serializable
data class NightPick(val by: PlayerId, val role: Role, val target: PlayerId)

@Serializable
data class SheriffCheck(val target: PlayerId, val isMafia: Boolean, val role: Role?)

/** Everything that happened in one night, kept for dawn and for the end-of-game replay. */
@Serializable
data class NightRecord(
    val night: Int,
    val turnOrder: List<PlayerId>,
    val picks: List<NightPick> = emptyList(),
    val sheriffCheck: SheriffCheck? = null,
    /** Set when the night is resolved. */
    val mafiaTarget: PlayerId? = null,
    val protected: PlayerId? = null,
    val killed: PlayerId? = null,
) {
    val saved: Boolean get() = mafiaTarget != null && killed == null

    fun picksBy(role: Role) = picks.filter { it.role == role }

    /** Villagers' guesses at who the Mafia would go after. */
    val guesses: List<NightPick> get() = picksBy(Role.VILLAGER)
}

@Serializable
data class DayRecord(
    val day: Int,
    /** Secret votes by voter. A null target is a skip. Empty for an open vote. */
    val votes: Map<PlayerId, PlayerId?> = emptyMap(),
    val tied: List<PlayerId> = emptyList(),
    val eliminated: PlayerId? = null,
    val randomTieBreak: Boolean = false,
)

@Serializable
sealed interface Phase {
    /** The phone goes round once and each player sees their role. */
    @Serializable
    data class RoleReveal(val index: Int) : Phase

    @Serializable
    data class Night(val night: Int, val turn: Int) : Phase

    @Serializable
    data class Dawn(val day: Int) : Phase

    @Serializable
    data class Discussion(val day: Int, val startedAtMillis: Long? = null) : Phase

    /**
     * Voting. [candidates] is everyone alive, or only the tied players on a revote.
     * [turn] indexes the living players for a secret vote.
     */
    @Serializable
    data class Voting(
        val day: Int,
        val candidates: List<PlayerId>,
        val isRevote: Boolean = false,
        val turn: Int = 0,
        val votes: Map<PlayerId, PlayerId?> = emptyMap(),
    ) : Phase

    @Serializable
    data class VoteResult(val day: Int) : Phase

    @Serializable
    data class GameOver(val winner: Team) : Phase
}
