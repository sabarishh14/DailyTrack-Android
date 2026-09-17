package com.example.dailytrack_mobile.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MediaLibraryResponseDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "shows") val shows: List<MediaShowDto>?,
    @Json(name = "total_count") val totalCount: Int? = null,
    @Json(name = "hasMore") val hasMore: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class MediaShowDto(
    @Json(name = "id") val id: Int,
    @Json(name = "tmdb_id") val tmdbId: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "added_on") val addedOn: String? = null
)

@JsonClass(generateAdapter = true)
data class MediaSearchResponseDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "data") val data: MediaSearchDataDto? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class MediaSearchDataDto(
    @Json(name = "page") val page: Int? = null,
    @Json(name = "results") val results: List<MediaSearchResultDto>? = null,
    @Json(name = "total_pages") val totalPages: Int? = null,
    @Json(name = "total_results") val totalResults: Int? = null
)

@JsonClass(generateAdapter = true)
data class MediaSearchResultDto(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "media_type") val mediaType: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "overview") val overview: String? = null
) {
    val displayTitle: String
        get() = title ?: name ?: "Unknown"

    val year: String
        get() {
            val d = releaseDate ?: firstAirDate ?: ""
            return if (d.length >= 4) d.substring(0, 4) else ""
        }

    val isMovie: Boolean
        get() = mediaType.equals("movie", ignoreCase = true)
}

@JsonClass(generateAdapter = true)
data class AddMovieRequestDto(
    @Json(name = "tmdb_id") val tmdb_id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "poster_path") val poster_path: String? = null,
    @Json(name = "status") val status: String = "TO WATCH",
    @Json(name = "year") val year: Int? = null
)

@JsonClass(generateAdapter = true)
data class AddTvShowRequestDto(
    @Json(name = "tmdb_id") val tmdb_id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "poster_path") val poster_path: String? = null,
    @Json(name = "status") val status: String = "TO WATCH"
)

@JsonClass(generateAdapter = true)
data class AddMediaResponseDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "id") val id: Int? = null,
    @Json(name = "show") val show: MediaShowDto? = null
)

@JsonClass(generateAdapter = true)
data class AddMovieDiaryRequestDto(
    @Json(name = "movie_id") val movie_id: Int,
    @Json(name = "date") val date: String,
    @Json(name = "rating") val rating: Float? = null,
    @Json(name = "review") val review: String? = null,
    @Json(name = "liked") val liked: Boolean = false,
    @Json(name = "rewatch") val rewatch: Boolean = false,
    @Json(name = "tags") val tags: String? = null
)

@JsonClass(generateAdapter = true)
data class AddTvDiaryRequestDto(
    @Json(name = "tv_show_id") val tv_show_id: Int,
    @Json(name = "date") val date: String,
    @Json(name = "rating") val rating: Float? = null,
    @Json(name = "review") val review: String? = null,
    @Json(name = "liked") val liked: Boolean = false,
    @Json(name = "rewatch") val rewatch: Boolean = false,
    @Json(name = "tags") val tags: String? = null,
    @Json(name = "season_number") val season_number: Int? = null,
    @Json(name = "episode_number") val episode_number: Int? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Sabdekho Diary DTOs
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class MediaDiaryResponseDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "logs") val logs: List<MediaDiaryLogDto>? = null,
    @Json(name = "total_count") val totalCount: Int? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class MediaDiaryLogDto(
    @Json(name = "id") val id: Int,
    @Json(name = "show_id") val showId: Int,
    @Json(name = "tmdb_id") val tmdbId: Int? = null,
    @Json(name = "show_name") val showName: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "date") val date: String? = null,
    @Json(name = "rating") val rating: Float? = null,
    @Json(name = "review") val review: String? = null,
    @Json(name = "liked") val liked: Boolean = false,
    @Json(name = "rewatch") val rewatch: Boolean = false,
    @Json(name = "tags") val tags: String? = null,
    @Json(name = "type") val type: String? = "movie",
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episode_number") val episodeNumber: Int? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateDiaryLogRequestDto(
    @Json(name = "log_ids") val log_ids: List<Int>,
    @Json(name = "rating") val rating: Float? = null,
    @Json(name = "review") val review: String? = null,
    @Json(name = "liked") val liked: Boolean? = null,
    @Json(name = "rewatch") val rewatch: Boolean? = null,
    @Json(name = "tags") val tags: String? = null,
    @Json(name = "date") val date: String? = null,
    @Json(name = "season_number") val season_number: Int? = null,
    @Json(name = "episode_number") val episode_number: Int? = null
)

@JsonClass(generateAdapter = true)
data class DeleteDiaryLogRequestDto(
    @Json(name = "log_ids") val log_ids: List<Int>
)

@JsonClass(generateAdapter = true)
data class UpdateMediaStatusRequestDto(
    @Json(name = "status") val status: String
)

@JsonClass(generateAdapter = true)
data class RematchMediaRequestDto(
    @Json(name = "tmdb_id") val tmdb_id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "poster_path") val poster_path: String? = null,
    @Json(name = "year") val year: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Sabdekho TMDB Details & Credits DTOs
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class MediaDetailsResponseDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "data") val data: MediaDetailsDataDto? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class MediaDetailsDataDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "runtime") val runtime: Int? = null,
    @Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    @Json(name = "number_of_episodes") val numberOfEpisodes: Int? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "genres") val genres: List<MediaGenreDto>? = null,
    @Json(name = "seasons") val seasons: List<MediaSeasonDto>? = null,
    @Json(name = "credits") val credits: MediaCreditsDto? = null,
    @Json(name = "aggregate_credits") val aggregateCredits: MediaCreditsDto? = null
) {
    val displayTitle: String get() = title ?: name ?: "Unknown"
    val allCast: List<MediaCastMemberDto> get() = aggregateCredits?.cast ?: credits?.cast ?: emptyList()
    val director: String? get() = credits?.crew?.firstOrNull { it.job.equals("Director", ignoreCase = true) }?.name
}

@JsonClass(generateAdapter = true)
data class MediaGenreDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class MediaSeasonDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "season_number") val season_number: Int = 0,
    @Json(name = "name") val name: String? = null,
    @Json(name = "episode_count") val episode_count: Int = 0,
    @Json(name = "poster_path") val poster_path: String? = null
)

@JsonClass(generateAdapter = true)
data class MediaCreditsDto(
    @Json(name = "cast") val cast: List<MediaCastMemberDto>? = null,
    @Json(name = "crew") val crew: List<MediaCrewMemberDto>? = null
)

@JsonClass(generateAdapter = true)
data class MediaCastMemberDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "character") val character: String? = null,
    @Json(name = "roles") val roles: List<MediaRoleDto>? = null,
    @Json(name = "profile_path") val profile_path: String? = null
) {
    val displayCharacter: String get() = character ?: roles?.firstOrNull()?.character ?: ""
}

@JsonClass(generateAdapter = true)
data class MediaRoleDto(
    @Json(name = "character") val character: String? = null
)

@JsonClass(generateAdapter = true)
data class MediaCrewMemberDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "job") val job: String? = null,
    @Json(name = "department") val department: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Sabdekho Letterboxd-Style Stats DTOs
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class MediaStatsResponseDto(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "year") val year: String? = null,
    @Json(name = "available_years") val available_years: List<Int> = emptyList(),
    @Json(name = "films_logged") val films_logged: Int = 0,
    @Json(name = "total_likes") val total_likes: Int = 0,
    @Json(name = "total_hours") val total_hours: Double = 0.0,
    @Json(name = "total_reviews") val total_reviews: Int = 0,
    @Json(name = "avg_per_month") val avg_per_month: Double = 0.0,
    @Json(name = "avg_per_week") val avg_per_week: Double = 0.0,
    @Json(name = "by_month") val by_month: List<Int> = emptyList(),
    @Json(name = "by_day") val by_day: List<Int> = emptyList(),
    @Json(name = "by_week") val by_week: List<Int> = emptyList(),
    @Json(name = "rating_distribution") val rating_distribution: Map<String, Int> = emptyMap(),
    @Json(name = "highest_rated") val highest_rated: List<MediaStatsMovieDto> = emptyList(),
    @Json(name = "highest_rated_current") val highest_rated_current: List<MediaStatsMovieDto> = emptyList(),
    @Json(name = "highest_rated_older") val highest_rated_older: List<MediaStatsMovieDto> = emptyList(),
    @Json(name = "theatre_stats") val theatre_stats: MediaTheatreStatsDto? = null,
    @Json(name = "extremes") val extremes: MediaExtremesDto? = null,
    @Json(name = "most_rewatched") val most_rewatched: List<MediaRewatchedDto> = emptyList(),
    @Json(name = "longest_streak") val longest_streak: MediaStreakDto? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class MediaStatsMovieDto(
    @Json(name = "movie_id") val movie_id: Int = 0,
    @Json(name = "tmdb_id") val tmdb_id: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "poster_path") val poster_path: String? = null,
    @Json(name = "rating") val rating: Float = 0f,
    @Json(name = "release_year") val release_year: String? = null,
    @Json(name = "tags") val tags: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MediaTheatreStatsDto(
    @Json(name = "total_visits") val total_visits: Int = 0,
    @Json(name = "movies") val movies: List<MediaStatsMovieDto> = emptyList(),
    @Json(name = "supplementary_tags") val supplementary_tags: Map<String, Int> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class MediaExtremesDto(
    @Json(name = "longest") val longest: MediaExtremeItemDto? = null,
    @Json(name = "shortest") val shortest: MediaExtremeItemDto? = null,
    @Json(name = "oldest") val oldest: MediaExtremeItemDto? = null,
    @Json(name = "newest") val newest: MediaExtremeItemDto? = null
)

@JsonClass(generateAdapter = true)
data class MediaExtremeItemDto(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "tmdb_id") val tmdb_id: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "poster_path") val poster_path: String? = null,
    @Json(name = "runtime") val runtime: Int? = null,
    @Json(name = "release_year") val release_year: String? = null,
    // Newer backends send the full date and when it was last watched; both are
    // optional so an older server still parses.
    @Json(name = "release_date") val release_date: String? = null,
    @Json(name = "watched_date") val watched_date: String? = null
)

@JsonClass(generateAdapter = true)
data class MediaRewatchedDto(
    @Json(name = "movie_id") val movie_id: Int = 0,
    @Json(name = "tmdb_id") val tmdb_id: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "poster_path") val poster_path: String? = null,
    @Json(name = "watch_count") val watch_count: Int = 0
)

@JsonClass(generateAdapter = true)
data class MediaStreakDto(
    @Json(name = "length") val length: Int = 0,
    @Json(name = "start") val start: String? = null,
    @Json(name = "end") val end: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Sabdekho TV Stats DTOs (/api/tv/stats)
// Every field has a default so a partial or older payload still parses.
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class TvStatsResponseDto(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "year") val year: String? = null,
    @Json(name = "available_years") val available_years: List<Int> = emptyList(),
    @Json(name = "episodes_watched") val episodes_watched: Int = 0,
    @Json(name = "shows_watched") val shows_watched: Int = 0,
    @Json(name = "seasons_watched") val seasons_watched: Int = 0,
    @Json(name = "total_entries") val total_entries: Int = 0,
    @Json(name = "total_likes") val total_likes: Int = 0,
    @Json(name = "total_reviews") val total_reviews: Int = 0,
    @Json(name = "total_rewatches") val total_rewatches: Int = 0,
    @Json(name = "average_rating") val average_rating: Double? = null,
    @Json(name = "shows_completed") val shows_completed: Int = 0,
    @Json(name = "avg_per_week") val avg_per_week: Double = 0.0,
    @Json(name = "avg_per_month") val avg_per_month: Double = 0.0,
    @Json(name = "by_week") val by_week: List<Int> = emptyList(),
    @Json(name = "by_month") val by_month: List<Int> = emptyList(),
    @Json(name = "by_day") val by_day: List<Int> = emptyList(),
    @Json(name = "episodes_by_year") val episodes_by_year: List<TvYearCountDto> = emptyList(),
    @Json(name = "rating_distribution") val rating_distribution: Map<String, Int> = emptyMap(),
    @Json(name = "most_watched") val most_watched: List<TvStatsShowDto> = emptyList(),
    @Json(name = "highest_rated") val highest_rated: List<TvStatsShowDto> = emptyList(),
    @Json(name = "biggest_binge") val biggest_binge: TvBingeDto? = null,
    @Json(name = "longest_streak") val longest_streak: MediaStreakDto? = null,
    @Json(name = "completed") val completed: List<TvStatsShowDto> = emptyList(),
    @Json(name = "in_progress") val in_progress: List<TvStatsShowDto> = emptyList(),
    @Json(name = "message") val message: String? = null
)

/** One show in any TV stats list; each list fills the fields relevant to it. */
@JsonClass(generateAdapter = true)
data class TvStatsShowDto(
    @Json(name = "show_id") val show_id: Int = 0,
    @Json(name = "tmdb_id") val tmdb_id: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "poster_path") val poster_path: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "episodes") val episodes: Int = 0,
    @Json(name = "logs") val logs: Int = 0,
    @Json(name = "rating") val rating: Double? = null,
    @Json(name = "ratings_count") val ratings_count: Int = 0,
    @Json(name = "episodes_watched") val episodes_watched: Int = 0,
    @Json(name = "last_watched") val last_watched: String? = null,
    @Json(name = "completed_on") val completed_on: String? = null
)

@JsonClass(generateAdapter = true)
data class TvBingeDto(
    @Json(name = "show_id") val show_id: Int = 0,
    @Json(name = "tmdb_id") val tmdb_id: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "poster_path") val poster_path: String? = null,
    @Json(name = "date") val date: String? = null,
    @Json(name = "episodes") val episodes: Int = 0
)

@JsonClass(generateAdapter = true)
data class TvYearCountDto(
    @Json(name = "year") val year: Int = 0,
    @Json(name = "count") val count: Int = 0
)
