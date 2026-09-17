package com.example.dailytrack_mobile

import com.example.dailytrack_mobile.data.remote.dto.MediaStatsResponseDto
import com.example.dailytrack_mobile.data.remote.dto.TvStatsResponseDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses payloads captured from the real /api/tv/stats and /api/movies/stats
 * handlers, using the same Moshi setup as NetworkModule, so a drift between the
 * backend's keys and the app's DTOs fails here rather than as an empty screen.
 */
class StatsPayloadParsingTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `tv stats payload parses into TvStatsResponseDto`() {
        val dto = moshi.adapter(TvStatsResponseDto::class.java).fromJson(TV_JSON)
        assertNotNull(dto)
        dto!!
        assertTrue(dto.success)
        assertEquals(7, dto.episodes_watched)
        assertEquals(2, dto.shows_watched)
        assertEquals(1, dto.shows_completed)
        assertEquals(52, dto.by_week.size)
        assertEquals(12, dto.by_month.size)
        assertEquals(7, dto.by_day.size)
        assertEquals("Bingeable", dto.most_watched.first().name)
        assertEquals(5, dto.most_watched.first().episodes)
        assertEquals(4.6, dto.highest_rated.first().rating!!, 0.001)
        assertEquals(4, dto.biggest_binge!!.episodes)
        assertEquals(3, dto.longest_streak!!.length)
        assertEquals(2, dto.in_progress.size)
        assertEquals("2026-01-03", dto.completed.first().completed_on)
    }

    @Test
    fun `movie extremes carry release and watched dates`() {
        val dto = moshi.adapter(MediaStatsResponseDto::class.java).fromJson(MOVIE_JSON)
        assertNotNull(dto)
        val newest = dto!!.extremes!!.newest!!
        assertEquals("EarlyThisYear", newest.name)
        assertEquals("2026-01-10", newest.release_date)
        assertEquals("2026-09-17", newest.watched_date)
        // release_year arrives as a JSON number but is modelled as String?
        assertEquals("2026", newest.release_year)
    }

    @Test
    fun `older backend without new fields still parses`() {
        val legacy = """{"success": true, "films_logged": 3, "extremes": {"newest": {"id": 1, "name": "Old", "release_year": 2020}}}"""
        val dto = moshi.adapter(MediaStatsResponseDto::class.java).fromJson(legacy)!!
        assertEquals(null, dto.extremes!!.newest!!.release_date)
        val tvLegacy = """{"success": true}"""
        val tvDto = moshi.adapter(TvStatsResponseDto::class.java).fromJson(tvLegacy)!!
        assertEquals(0, tvDto.episodes_watched)
        assertTrue(tvDto.most_watched.isEmpty())
    }

    private companion object {
        const val TV_JSON = """{"available_years": [2026, 2025], "average_rating": 4.33, "avg_per_month": 0.8, "avg_per_week": 0.2, "biggest_binge": {"date": "2026-01-01", "episodes": 4, "name": "Bingeable", "poster_path": "/Bingeable.jpg", "show_id": 17, "tmdb_id": 18}, "by_day": [0, 1, 0, 5, 1, 1, 0], "by_month": [8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], "by_week": [7, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], "completed": [{"completed_on": "2026-01-03", "name": "Bingeable", "poster_path": "/Bingeable.jpg", "show_id": 17, "status": "WATCHED", "tmdb_id": 18}], "episodes_by_year": [{"count": 7, "year": 2026}], "episodes_watched": 7, "highest_rated": [{"episodes": 5, "first_watched": "2026-01-01", "last_watched": "2026-01-02", "logs": 5, "name": "Bingeable", "poster_path": "/Bingeable.jpg", "rating": 4.6, "ratings_count": 5, "show_id": 17, "status": "WATCHED", "tmdb_id": 18}, {"episodes": 2, "first_watched": "2026-01-01", "last_watched": "2026-01-06", "logs": 3, "name": "SlowBurn", "poster_path": "/SlowBurn.jpg", "rating": 3.0, "ratings_count": 1, "show_id": 19, "status": "WATCHING", "tmdb_id": 20}], "in_progress": [{"episodes_watched": 4, "last_watched": "2026-01-06", "name": "SlowBurn", "poster_path": "/SlowBurn.jpg", "show_id": 19, "status": "WATCHING", "tmdb_id": 20}, {"episodes_watched": 4, "last_watched": "2025-06-01", "name": "Other", "poster_path": "/Other.jpg", "show_id": 21, "status": "WATCHING", "tmdb_id": 22}], "longest_streak": {"end": "Jan 03, 2026", "length": 3, "start": "Jan 01, 2026"}, "most_watched": [{"episodes": 5, "first_watched": "2026-01-01", "last_watched": "2026-01-02", "logs": 5, "name": "Bingeable", "poster_path": "/Bingeable.jpg", "show_id": 17, "status": "WATCHED", "tmdb_id": 18}, {"episodes": 2, "first_watched": "2026-01-01", "last_watched": "2026-01-06", "logs": 3, "name": "SlowBurn", "poster_path": "/SlowBurn.jpg", "show_id": 19, "status": "WATCHING", "tmdb_id": 20}], "rating_distribution": {"3.0": 1, "4.5": 4, "5.0": 1}, "seasons_watched": 2, "shows_completed": 1, "shows_watched": 2, "status_counts": {"WATCHED": 1, "WATCHING": 2}, "success": true, "total_entries": 8, "total_likes": 1, "total_reviews": 1, "total_rewatches": 0, "year": "2026"}"""
        const val MOVIE_JSON = """{"available_years": [2026], "avg_per_month": 0.5, "avg_per_week": 0.1, "by_day": [0, 1, 0, 4, 0, 0, 0], "by_month": [0, 0, 0, 0, 0, 0, 0, 0, 5, 0, 0, 0], "by_week": [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], "extremes": {"longest": {"id": 1, "name": "Classic", "poster_path": "/Classic.jpg", "release_date": "1994-09-23", "release_year": 1994, "runtime": 142, "tmdb_id": 2, "watched_date": "2026-09-17"}, "newest": {"id": 3, "name": "EarlyThisYear", "poster_path": "/EarlyThisYear.jpg", "release_date": "2026-01-10", "release_year": 2026, "runtime": 100, "tmdb_id": 4, "watched_date": "2026-09-17"}, "oldest": {"id": 1, "name": "Classic", "poster_path": "/Classic.jpg", "release_date": "1994-09-23", "release_year": 1994, "runtime": 142, "tmdb_id": 2, "watched_date": "2026-09-17"}, "shortest": {"id": 3, "name": "EarlyThisYear", "poster_path": "/EarlyThisYear.jpg", "release_date": "2026-01-10", "release_year": 2026, "runtime": 100, "tmdb_id": 4, "watched_date": "2026-09-17"}}, "films_by_year": [{"count": 4, "year": 2026}], "films_logged": 4, "highest_rated": [{"movie_id": 1, "name": "Classic", "poster_path": "/Classic.jpg", "rating": 4.0, "release_year": 1994, "tmdb_id": 2}, {"movie_id": 3, "name": "EarlyThisYear", "poster_path": "/EarlyThisYear.jpg", "rating": 4.0, "release_year": 2026, "tmdb_id": 4}, {"movie_id": 7, "name": "YearOnly", "poster_path": "/YearOnly.jpg", "rating": 4.0, "release_year": 2026, "tmdb_id": 8}, {"movie_id": 9, "name": "Future", "poster_path": "/Future.jpg", "rating": 4.0, "release_year": 2026, "tmdb_id": 10}], "highest_rated_current": [{"movie_id": 3, "name": "EarlyThisYear", "poster_path": "/EarlyThisYear.jpg", "rating": 4.0, "release_year": 2026, "tmdb_id": 4}, {"movie_id": 7, "name": "YearOnly", "poster_path": "/YearOnly.jpg", "rating": 4.0, "release_year": 2026, "tmdb_id": 8}, {"movie_id": 9, "name": "Future", "poster_path": "/Future.jpg", "rating": 4.0, "release_year": 2026, "tmdb_id": 10}], "highest_rated_older": [{"movie_id": 1, "name": "Classic", "poster_path": "/Classic.jpg", "rating": 4.0, "release_year": 1994, "tmdb_id": 2}], "longest_streak": {"end": "Sep 15, 2026", "length": 1, "start": "Sep 15, 2026"}, "most_rewatched": [{"movie_id": 1, "name": "Classic", "poster_path": "/Classic.jpg", "tmdb_id": 2, "watch_count": 2}], "rating_distribution": {"4.0": 5}, "success": true, "theatre_stats": {"movies": [], "supplementary_tags": {}, "total_visits": 0}, "total_entries": 5, "total_hours": 9.7, "total_likes": 5, "total_reviews": 0, "year": "2026"}"""
    }
}
