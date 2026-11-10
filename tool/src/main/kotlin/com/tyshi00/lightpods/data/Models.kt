package com.tyshi00.lightpods.data

import kotlinx.serialization.Serializable

@Serializable
data class Podcast(
    val feedUrl: String,
    val title: String,
    val author: String = "",
) {
    val authorOrUnknown: String get() = author.ifBlank { "unknown" }
}

@Serializable
data class Episode(
    val feedUrl: String,
    val guid: String,
    val title: String,
    val showTitle: String,
    val audioUrl: String,
    val pubDateMs: Long = 0L,
    val durationSec: Long = 0L,
    val lengthBytes: Long = 0L,
) {
    val key: String get() = episodeKey(feedUrl, guid)
}

fun episodeKey(feedUrl: String, guid: String): String = "$feedUrl|$guid"

@Serializable
enum class AutoPlayMode(val label: String) {
    Off("Off"),
    AllRecents("All recents"),
    SameShow("Same show only"),
}

@Serializable
enum class HomeMode(val label: String) {
    Episodes("Episodes"),
    Podcasts("Subscribed podcasts"),
}

@Serializable
data class Settings(
    val autoPlay: AutoPlayMode = AutoPlayMode.SameShow,
    val home: HomeMode = HomeMode.Episodes,
    val invertColors: Boolean = false,
    val showBluetooth: Boolean = false,
    val speed: Float = 1f,
    val oldestFirst: Map<String, Boolean> = emptyMap(),
    val syncNoticeSeen: Boolean = false,
)

@Serializable
data class Download(
    val episode: Episode,
    val fileName: String,
    val complete: Boolean = false,
    val bytesDone: Long = 0L,
    val bytesTotal: Long = 0L,
    val failed: Boolean = false,
) {
    val progress: Float
        get() = if (bytesTotal > 0) (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f
}

@Serializable
data class PlaybackSession(
    val queue: List<Episode> = emptyList(),
    val lastIndex: Int = -1,
)

@Serializable
data class SearchResult(
    val title: String,
    val author: String,
    val feedUrl: String,
)

val SPEEDS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

fun speedLabel(speed: Float): String = when (speed) {
    0.5f -> ".5x"
    1f -> "1x"
    1.25f -> "1.25x"
    1.5f -> "1.5x"
    2f -> "2x"
    else -> "${speed}x"
}
