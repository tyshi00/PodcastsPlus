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
import androidx.compose.ui.res.painterResource
import com.tyshi00.lightpods.R
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.tyshi00.lightpods.data.AutoPlayMode
import com.tyshi00.lightpods.data.Download
import com.tyshi00.lightpods.data.HomeMode
import com.tyshi00.lightpods.data.Pods
import kotlinx.coroutines.flow.MutableStateFlow

class SettingsScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val settings by Pods.settings.state.collectAsState()
        PodScaffold(
            title = "Settings",
            onBack = { goBack() },
            startPadding = OPTION_START_UNITS,
            topGap = OPTION_TOP_GAP_UNITS,
        ) {
            LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth()) {
                OptionRow(title = "Invert colors", icon = toggleIcon(settings.invertColors)) {
                    val invert = !settings.invertColors
                    Pods.settings.update { it.copy(invertColors = invert) }
                    applyTheme(invert)
                }
                OptionRow(title = "Auto-Play", subtitle = settings.autoPlay.label.lowercase()) {
                    navigateTo(screenFactory = { AutoPlayScreen(it) })
                }
                OptionRow(title = "Home Screen", subtitle = settings.home.label.lowercase()) {
                    val next = if (settings.home == HomeMode.Episodes) HomeMode.Podcasts else HomeMode.Episodes
                    Pods.settings.update { it.copy(home = next) }
                }
                OptionRow(title = "Add podcast") {
                    navigateTo(screenFactory = { AddPodcastScreen(it) })
                }
                OptionRow(title = "Show Bluetooth", icon = toggleIcon(settings.showBluetooth)) {
                    Pods.settings.update { it.copy(showBluetooth = !it.showBluetooth) }
                }
            }
        }
    }
}

class AutoPlayScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val settings by Pods.settings.state.collectAsState()
        PodScaffold(title = "Auto-Play", onBack = { goBack() }) {
            LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AutoPlayMode.entries.forEach { mode ->
                    ChoiceRow(
                        title = mode.label,
                        selected = settings.autoPlay == mode,
                        onClick = { Pods.settings.update { it.copy(autoPlay = mode) } },
                    )
                }
                LightText(
                    text = when (settings.autoPlay) {
                        AutoPlayMode.Off -> "Playback stops when an episode ends."
                        AutoPlayMode.AllRecents -> "Plays the next unlistened episode in the main feed, from any podcast."
                        AutoPlayMode.SameShow -> "Plays the next unlistened episode of the same podcast, in its list order. Stops when there are none."
                    },
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
                )
            }
        }
    }
}

class DownloadsScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<Unit>(sealedActivity) {
    private val editing = MutableStateFlow(false)
    private val listState = LazyListState()

    private fun status(download: Download): String = when {
        download.complete -> formatSize(download.bytesTotal)
        download.failed -> "download failed"
        download.bytesDone > 0 && download.bytesTotal > 0 -> "downloading ${(download.progress * 100).toInt()}%"
        else -> "queued"
    }

    private fun formatSize(bytes: Long): String =
        if (bytes <= 0) "Downloaded" else "%.0f MB".format(bytes / 1_048_576.0)

    @Composable
    override fun Content() {
        val downloads by Pods.downloads.state.collectAsState()
        val isEditing by editing.collectAsState()
        val list = remember(downloads) { downloads.values.sortedByDescending { it.episode.pubDateMs } }

        PodScaffold(
            title = "Downloads",
            onBack = { goBack() },
            rightText = if (list.isEmpty()) null else if (isEditing) "DONE" else "EDIT",
            onRightText = { editing.value = !isEditing },
            startPadding = DOWNLOADS_START_UNITS,
            topGap = 1f,
        ) {
            if (list.isEmpty()) {
                EmptyMessage("No downloads.")
            } else {
                LightLazyScrollView(
                    scrollBarPosition = LightScrollBarPosition.Inside,
                    listState = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    uniformItemHeightGridUnits = LIST_ROW_UNITS,
                ) {
                    items(list, key = { it.episode.key }) { download ->
                        val episode = download.episode
                        val remove = { Pods.removeDownload(lightContext, episode.key) }
                        ListRow(
                            title = episode.title,
                            subtitle = if (download.complete) episode.showTitle else "${episode.showTitle} · ${status(download)}",
                            marker = if (!isEditing && !download.complete) LightIcons.DOWNLOAD_ARROW else null,
                            markerPainter = when {
                                isEditing -> painterResource(R.drawable.ic_remove_download)
                                download.complete -> painterResource(R.drawable.ic_downloaded_small)
                                else -> null
                            },
                            onMarker = if (isEditing) remove else null,
                            onClick = {
                                if (isEditing) remove() else navigateTo(screenFactory = { EpisodeScreen(it, episode) })
                            },
                        )
                    }
                }
            }
        }
    }
}
