package com.tyshi00.lightpods.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.tyshi00.lightpods.data.Podcast
import com.tyshi00.lightpods.data.Pods
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class PodcastScreen(
    sealedActivity: SealedLightActivity,
    private val podcast: Podcast,
) : SimpleLightScreen<Unit>(sealedActivity) {
    private val filter = MutableStateFlow("")
    private val listState = LazyListState()
    private val refreshError = MutableStateFlow(false)

    private fun refresh() {
        refreshError.value = false
        Pods.scope.launch {
            refreshError.value = Pods.refreshFeed(podcast.feedUrl).isFailure
        }
    }

    @Composable
    override fun Content() {
        val subs by Pods.subscriptions.state.collectAsState()
        val allEpisodes by Pods.episodes.state.collectAsState()
        val listened by Pods.listened.state.collectAsState()
        val downloads by Pods.downloads.state.collectAsState()
        val settings by Pods.settings.state.collectAsState()
        val refreshing by Pods.refreshingFeeds.collectAsState()
        val query by filter.collectAsState()
        val failed by refreshError.collectAsState()
        val show = subs.firstOrNull { it.feedUrl == podcast.feedUrl } ?: podcast
        val oldestFirst = settings.oldestFirst[podcast.feedUrl] == true
        val episodes = remember(allEpisodes, oldestFirst, query) {
            Pods.showEpisodes(podcast.feedUrl, oldestFirst)
                .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
        }

        PodScaffold(
            title = show.title,
            onBack = { goBack() },
            bottom = listOf(
                LightBarButton.LightIcon(icon = LightIcons.REVERSE_ORDER, contentDescription = "Sort", onClick = {
                    Pods.settings.update { it.copy(oldestFirst = it.oldestFirst + (podcast.feedUrl to !oldestFirst)) }
                }),
                LightBarButton.LightIcon(icon = LightIcons.REFRESH, onClick = ::refresh),
                LightBarButton.LightIcon(icon = LightIcons.SEARCH, onClick = {
                    navigateTo(screenFactory = { TextEntryScreen(it, "Search episodes", query, submitLabel = "SEARCH") }) { text ->
                        filter.value = text
                    }
                }),
            ),
            startPadding = LIST_START_UNITS,
        ) {
            val status = when {
                podcast.feedUrl in refreshing -> "Refreshing…"
                failed -> "Refresh failed"
                else -> null
            }
            if (status != null) {
                LightText(
                    text = status,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(start = 1.4f.gridUnitsAsDp()),
                )
            }
            if (query.isNotBlank()) {
                ListRow(
                    title = "Results for “$query”",
                    subtitle = "${episodes.size} episodes · tap to clear",
                    marker = LightIcons.CLOSE,
                    onMarker = { filter.value = "" },
                    onClick = { filter.value = "" },
                )
            }
            if (episodes.isEmpty()) {
                EmptyMessage(if (query.isBlank()) "No episodes." else "No matching episodes.")
            } else {
                LightLazyScrollView(
                    scrollBarPosition = LightScrollBarPosition.Inside,
                    listState = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    uniformItemHeightGridUnits = LIST_ROW_UNITS,
                ) {
                    items(episodes, key = { it.key }) { episode ->
                        val isListened = episode.key in listened
                        EpisodeRow(
                            episode = episode,
                            listened = isListened,
                            showShow = false,
                            download = downloads[episode.key],
                            onToggleListened = { Pods.setListened(episode.key, !isListened) },
                            onClick = { navigateTo(screenFactory = { EpisodeScreen(it, episode) }) },
                        )
                    }
                }
            }
        }
    }
}

class RemovePodcastScreen(
    sealedActivity: SealedLightActivity,
    private val podcast: Podcast,
) : SimpleLightScreen<Boolean>(sealedActivity) {

    @Composable
    override fun Content() {
        val downloads by Pods.downloads.state.collectAsState()
        val count = downloads.values.count { it.episode.feedUrl == podcast.feedUrl }
        PodScaffold(title = "Remove Podcast", onBack = { goBack(false) }) {
            LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LightText(
                    text = podcast.title,
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.padding(end = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
                )
                SettingsRow("Unsubscribe only", onClick = {
                    Pods.removePodcast(lightContext, podcast.feedUrl, deleteDownloads = false)
                    goBack(true)
                })
                if (count > 0) {
                    SettingsRow("Unsubscribe + delete $count download${if (count == 1) "" else "s"}", wrap = true, onClick = {
                        Pods.removePodcast(lightContext, podcast.feedUrl, deleteDownloads = true)
                        goBack(true)
                    })
                }
                SettingsRow("Cancel", onClick = { goBack(false) })
                LightText(
                    text = "Listened history is kept.",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
                )
            }
        }
    }
}
