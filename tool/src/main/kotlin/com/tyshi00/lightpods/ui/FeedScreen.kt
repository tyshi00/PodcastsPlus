package com.tyshi00.lightpods.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.LightWork
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightModalManager
import com.tyshi00.lightpods.data.HomeMode
import com.tyshi00.lightpods.data.JOB_SYNC
import com.tyshi00.lightpods.data.Pods
import com.tyshi00.lightpods.data.TAG_SYNC_NOW
import com.tyshi00.lightpods.data.TAG_SYNC_PERIODIC
import com.tyshi00.lightpods.playback.PlaybackEngine
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@EntryPoint
object PodsEntryPoint : LightEntryPoint

fun startSync(ctx: SealedLightContext) {
    LightWork.enqueue(ctx, JOB_SYNC, tag = TAG_SYNC_NOW)
    LightModalManager.show(SyncNoticeModal(), 60.seconds)
}

open class PodListScreen(
    sealedActivity: SealedLightActivity,
    private val fixedMode: HomeMode?,
) : SimpleLightScreen<Unit>(sealedActivity) {
    private val episodesListState = LazyListState()
    private val podcastsListState = LazyListState()


    @Composable
    override fun Content() {
        val settings by Pods.settings.state.collectAsState()
        val current by PlaybackEngine.current.collectAsState()
        val isHome = fixedMode == null
        val mode = fixedMode ?: settings.home

        val title = if (mode == HomeMode.Episodes) "Recent Episodes" else "Subscribed Podcasts"
        val bottom = if (!isHome) {
            emptyList()
        } else {
            buildList<LightBarButton> {
                add(LightBarButton.LightIcon(icon = LightIcons.SETTINGS, onClick = {
                    navigateTo(screenFactory = { SettingsScreen(it) })
                }))
                add(LightBarButton.LightIcon(icon = LightIcons.DOWNLOADED_ARROW, contentDescription = "Downloads", onClick = {
                    navigateTo(screenFactory = { DownloadsScreen(it) })
                }))
                add(LightBarButton.LightIcon(icon = LightIcons.REFRESH, onClick = { startSync(lightContext) }))
                add(
                    LightBarButton.LightIcon(
                        icon = LightIcons.LIST,
                        contentDescription = if (mode == HomeMode.Episodes) "Subscribed podcasts" else "Recent episodes",
                        onClick = {
                            val other = if (mode == HomeMode.Episodes) HomeMode.Podcasts else HomeMode.Episodes
                            navigateTo(screenFactory = { PodListScreen(it, other) })
                        },
                    ),
                )
                if (settings.showBluetooth) {
                    add(LightBarButton.LightIcon(icon = LightIcons.BLUETOOTH, onClick = {
                        navigateTo(screenFactory = { BluetoothScreen(it) })
                    }))
                } else current?.let { episode ->
                    add(LightBarButton.LightIcon(icon = LightIcons.AUDIO_MESSAGE, contentDescription = "Now playing", onClick = {
                        navigateTo(screenFactory = { EpisodeScreen(it, episode) })
                    }))
                }
            }
        }

        PodScaffold(
            title = title,
            onBack = if (isHome) null else ({ goBack() }),
            right = if (mode == HomeMode.Podcasts) {
                LightBarButton.LightIcon(icon = LightIcons.ADD, contentDescription = "Add podcast", onClick = {
                    navigateTo(screenFactory = { AddPodcastScreen(it) })
                })
            } else {
                null
            },
            bottom = bottom,
            startPadding = LIST_START_UNITS,
            topGap = if (mode == HomeMode.Podcasts) 1f else 0f,
            showTopBar = !(isHome && mode == HomeMode.Episodes),
        ) {
            when (mode) {
                HomeMode.Episodes -> EpisodeList()
                HomeMode.Podcasts -> PodcastList()
            }
        }
    }

    @Composable
    private fun ColumnScope.EpisodeList() {
        val subs by Pods.subscriptions.state.collectAsState()
        val episodes by Pods.episodes.state.collectAsState()
        val listened by Pods.listened.state.collectAsState()
        val downloads by Pods.downloads.state.collectAsState()
        val recent = remember(subs, episodes) { Pods.recentEpisodes(episodes, subs) }
        if (recent.isEmpty()) {
            EmptyMessage(if (subs.isEmpty()) "No podcasts yet.\nAdd one from Settings." else "No episodes yet.")
            return
        }
        LightLazyScrollView(
            scrollBarPosition = LightScrollBarPosition.Inside,
            listState = episodesListState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            uniformItemHeightGridUnits = LIST_ROW_UNITS,
        ) {
            items(recent, key = { it.key }) { episode ->
                val isListened = episode.key in listened
                EpisodeRow(
                    episode = episode,
                    listened = isListened,
                    showShow = true,
                    download = downloads[episode.key],
                    onToggleListened = { Pods.setListened(episode.key, !isListened) },
                    onClick = { navigateTo(screenFactory = { EpisodeScreen(it, episode) }) },
                )
            }
        }
    }

    @Composable
    private fun ColumnScope.PodcastList() {
        val subs by Pods.subscriptions.state.collectAsState()
        val sorted = remember(subs) { subs.sortedBy { it.title.lowercase().removePrefix("the ") } }
        if (sorted.isEmpty()) {
            EmptyMessage("No subscriptions.\nTap + to add a podcast.")
            return
        }
        LightLazyScrollView(
            scrollBarPosition = LightScrollBarPosition.Inside,
            listState = podcastsListState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            uniformItemHeightGridUnits = LIST_ROW_UNITS,
        ) {
            items(sorted, key = { it.feedUrl }) { podcast ->
                ListRow(
                    title = podcast.title,
                    subtitle = podcast.authorOrUnknown,
                    onLongClick = { navigateTo(screenFactory = { RemovePodcastScreen(it, podcast) }) },
                    onClick = { navigateTo(screenFactory = { PodcastScreen(it, podcast) }) },
                )
            }
        }
    }
}

@InitialScreen
class FeedScreen(sealedActivity: SealedLightActivity) : PodListScreen(sealedActivity, null) {
    init {
        Pods.init(lightContext.filesDir)
        applyTheme(Pods.settings.value.invertColors)
        PlaybackEngine.bind(sealedActivity)
        LightWork.enqueuePeriodic(lightContext, JOB_SYNC, 6.hours, tag = TAG_SYNC_PERIODIC)
        Pods.resumeDownloads(lightContext)
    }
}
