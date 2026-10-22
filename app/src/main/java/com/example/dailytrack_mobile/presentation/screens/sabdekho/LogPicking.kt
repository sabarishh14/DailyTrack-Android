package com.example.dailytrack_mobile.presentation.screens.sabdekho

import com.example.dailytrack_mobile.data.remote.dto.MediaDetailsDataDto
import com.example.dailytrack_mobile.data.remote.dto.MediaDiaryLogDto

/** One season of a show, and how many episodes it has. */
data class SeasonInfo(val number: Int, val episodeCount: Int)

/** What of a show is logged already: all of it, whole seasons, or single episodes. */
class WatchedEpisodes(logs: List<MediaDiaryLogDto>) {
    private val wholeShow: Boolean = logs.any { it.seasonNumber == null }
    private val wholeSeasons: Set<Int> =
        logs.filter { it.episodeNumber == null }.mapNotNull { it.seasonNumber }.toSet()
    private val episodes: Map<Int, Set<Int>> = logs
        .filter { it.seasonNumber != null && it.episodeNumber != null }
        .groupBy({ it.seasonNumber!! }, { it.episodeNumber!! })
        .mapValues { (_, eps) -> eps.toSet() }

    fun isWatched(season: Int, episode: Int): Boolean =
        wholeShow || season in wholeSeasons || episodes[season]?.contains(episode) == true

    /** How many of a season's [total] episodes are logged. */
    fun watchedIn(season: Int, total: Int): Int =
        if (wholeShow || season in wholeSeasons) total
        else episodes[season].orEmpty().count { it in 1..total }

    fun isSeasonDone(season: Int, total: Int): Boolean = total > 0 && watchedIn(season, total) >= total
}

/**
 * The seasons to offer: TMDB's when known, else the ones logged or picked.
 * Without TMDB a season shows at least ten episodes, more if more are logged.
 */
internal fun seasonsOf(
    details: MediaDetailsDataDto?,
    logs: List<MediaDiaryLogDto>,
    picked: Set<Int>,
    pickedEpisodes: Set<Int>
): List<SeasonInfo> {
    fun fallbackCount(season: Int): Int = maxOf(
        10,
        logs.filter { it.seasonNumber == season }.mapNotNull { it.episodeNumber }.maxOrNull() ?: 0,
        pickedEpisodes.maxOrNull() ?: 0
    )
    val known = details?.seasons.orEmpty()
        .filter { it.season_number > 0 && it.episode_count > 0 }
        .map { SeasonInfo(it.season_number, it.episode_count) }
    if (known.isNotEmpty()) {
        // A logged or picked season TMDB doesn't list still shows.
        val extra = picked.filter { p -> known.none { it.number == p } }.map { SeasonInfo(it, fallbackCount(it)) }
        return (known + extra).sortedBy { it.number }
    }
    val numbers = (logs.mapNotNull { it.seasonNumber } + picked).filter { it > 0 }.distinct().sorted()
    return numbers.ifEmpty { listOf(1) }.map { SeasonInfo(it, fallbackCount(it)) }
}

/** The first episode not logged yet, in order; within [season] when one is given. */
internal fun nextUp(watched: WatchedEpisodes, seasons: List<SeasonInfo>, season: Int? = null): Pair<Int, Int>? {
    for (s in seasons) {
        if (season != null && s.number != season) continue
        for (ep in 1..s.episodeCount) {
            if (!watched.isWatched(s.number, ep)) return s.number to ep
        }
    }
    return null
}

/**
 * One (season, episode) per server log a save makes: a film or the whole
 * show once, each picked season whole, or one season's picked episodes.
 */
internal fun logEntries(isMovie: Boolean, seasons: Set<Int>, episodes: Set<Int>): List<Pair<Int?, Int?>> = when {
    isMovie || seasons.isEmpty() -> listOf(null to null)
    seasons.size > 1 -> seasons.sorted().map { it to null }
    episodes.isEmpty() -> listOf(seasons.first() to null)
    else -> episodes.sorted().map { seasons.first() to it }
}

/** The server logs a diary row stands for, by the (season, episode) each one logs. */
internal fun MediaDiaryLogDto.entryIds(): Map<Pair<Int?, Int?>, List<Int>> {
    val season = seasonNumber
    val wholeShow: Pair<Int?, Int?> = null to null
    if (season == null) return mapOf(wholeShow to allLogIds)
    if (episodeLogIds.isNotEmpty()) {
        return episodeLogIds.entries.associate { (episode, ids) ->
            val key: Pair<Int?, Int?> = season to episode
            key to ids
        }
    }
    val key: Pair<Int?, Int?> = season to episodeNumber
    return mapOf(key to allLogIds)
}

/** What a log covers: "Whole show", "Season 2", "S1, S2", "S02E05" or "S02 E4–6". */
internal fun logTargetLabel(seasons: Set<Int>, episodes: Set<Int>): String = when {
    seasons.isEmpty() -> "Whole show"
    seasons.size > 1 -> seasons.sorted().joinToString(", ") { "S$it" }
    episodes.isEmpty() -> "Season ${seasons.first()}"
    else -> episodeLabel(seasons.first(), episodes.sorted())
}
