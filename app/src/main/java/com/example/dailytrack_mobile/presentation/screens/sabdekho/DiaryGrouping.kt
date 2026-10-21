package com.example.dailytrack_mobile.presentation.screens.sabdekho

import com.example.dailytrack_mobile.data.remote.dto.MediaDiaryLogDto

/**
 * The server keeps one log per episode. Same-day logs of one season's
 * episodes that share a rating, review, tags, like and rewatch become one
 * diary row ("S01 E1–3"), the way the web diary shows them.
 */
fun groupDiaryLogs(logs: List<MediaDiaryLogDto>): List<MediaDiaryLogDto> {
    val rows = mutableListOf<MediaDiaryLogDto>()
    val rowIndex = HashMap<List<Any?>, Int>()
    for (log in logs) {
        val episode = log.episodeNumber
        if (log.type.equals("movie", ignoreCase = true) || log.seasonNumber == null || episode == null) {
            rows += log
            continue
        }
        val key = listOf(
            log.date?.take(10), log.showId, log.seasonNumber, log.rating,
            log.review?.takeIf { it.isNotBlank() }, log.tags?.takeIf { it.isNotBlank() },
            log.liked, log.rewatch
        )
        val at = rowIndex[key]
        if (at == null) {
            rowIndex[key] = rows.size
            rows += log.copy(logIds = listOf(log.id), episodes = listOf(episode))
        } else {
            val row = rows[at]
            rows[at] = row.copy(
                logIds = row.logIds + log.id,
                episodes = (row.episodes + episode).distinct().sorted()
            )
        }
    }
    return rows
}

/** Every server log a diary row stands for. */
val MediaDiaryLogDto.allLogIds: List<Int>
    get() = logIds.ifEmpty { listOf(id) }

/** "S01E05" for one episode; "S01 E1–3, 5" for a merged row. */
fun episodeLabel(season: Int, episodes: List<Int>): String {
    if (episodes.size == 1) return "S%02dE%02d".format(season, episodes.first())
    val runs = mutableListOf<String>()
    var start = episodes.first()
    var prev = start
    for (ep in episodes.drop(1)) {
        if (ep != prev + 1) {
            runs += if (start == prev) "$start" else "$start–$prev"
            start = ep
        }
        prev = ep
    }
    runs += if (start == prev) "$start" else "$start–$prev"
    return "S%02d E%s".format(season, runs.joinToString(", "))
}
