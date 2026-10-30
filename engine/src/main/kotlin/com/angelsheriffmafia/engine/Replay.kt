package com.angelsheriffmafia.engine

import kotlinx.serialization.Serializable

/** One line of the end-of-game story. */
@Serializable
data class StoryLine(val text: String, val style: Style = Style.LINE) {
    @Serializable
    enum class Style { HEADING, LINE, STRONG }
}

/** The end-of-game replay as plain lines, shared by the app's screens and the browser page. */
object Replay {

    fun roleName(role: Role): String = when (role) {
        Role.MAFIA -> "Mafia"
        Role.ANGEL -> "Angel"
        Role.SHERIFF -> "Sheriff"
        Role.VILLAGER -> "Villager"
    }

    /** Night by night and day by day: every pick, check and vote. */
    fun story(game: GameState): List<StoryLine> = buildList {
        fun name(id: PlayerId) = game.player(id).name
        fun line(text: String) = add(StoryLine(text))
        fun strong(text: String) = add(StoryLine(text, StoryLine.Style.STRONG))
        val maxDay = maxOf(game.nights.size, game.days.size)
        for (n in 1..maxDay) {
            game.nights.getOrNull(n - 1)?.let { night ->
                add(StoryLine("Night $n", StoryLine.Style.HEADING))
                night.picksBy(Role.MAFIA).forEach { line("${name(it.by)} (Mafia) went after ${name(it.target)}.") }
                night.picksBy(Role.ANGEL).forEach { line("${name(it.by)} (Angel) protected ${name(it.target)}.") }
                night.sheriffCheck?.let { c ->
                    val sheriff = night.picksBy(Role.SHERIFF).first().by
                    val verdict = c.role?.let { "the ${roleName(it)}" } ?: if (c.isMafia) "Mafia" else "not Mafia"
                    line("${name(sheriff)} (Sheriff) checked ${name(c.target)}: $verdict.")
                }
                when {
                    night.killed != null -> strong("${name(night.killed)} died.")
                    night.saved -> strong("The Angel saved ${name(night.mafiaTarget!!)}!")
                    night.mafiaTarget == null && game.phase !is Phase.Night -> strong("The Mafia didn't strike.")
                }
            }
            game.days.firstOrNull { it.day == n }?.let { day ->
                add(StoryLine("Day $n", StoryLine.Style.HEADING))
                day.votes.forEach { (voter, target) -> line("${name(voter)} voted " + (target?.let(::name) ?: "to skip") + ".") }
                strong(day.eliminated?.let { "${name(it)} was run out of town." } ?: "Nobody was voted out.")
            }
        }
    }

    /** Villagers who called at least one Mafia target right, best first. */
    fun guessers(game: GameState): List<String> = game.players.filter { it.role == Role.VILLAGER }
        .map { it to Game.guessScore(game, it.id) }
        .filter { it.second.first > 0 }
        .sortedByDescending { it.second.first }
        .map { (p, score) -> "${p.name} called ${score.first} of ${score.second} Mafia targets." }
}
