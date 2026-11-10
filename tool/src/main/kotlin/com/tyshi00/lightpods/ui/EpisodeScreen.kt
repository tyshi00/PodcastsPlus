package com.tyshi00.lightpods.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTouchableProgressBar
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.tyshi00.lightpods.data.Episode
import com.tyshi00.lightpods.data.Podcast
import com.tyshi00.lightpods.data.Pods
import com.tyshi00.lightpods.data.SPEEDS
import com.tyshi00.lightpods.data.speedLabel
import com.tyshi00.lightpods.playback.PlaybackEngine

class EpisodeScreen(
    sealedActivity: SealedLightActivity,
    private val episode: Episode,
) : SimpleLightScreen<Unit>(sealedActivity) {

    private fun openPodcast() {
        val podcast = Pods.subscriptions.value.firstOrNull { it.feedUrl == episode.feedUrl }
            ?: Podcast(feedUrl = episode.feedUrl, title = episode.showTitle)
        navigateTo(screenFactory = { PodcastScreen(it, podcast) })
    }

    @Composable
    override fun Content() {
        val current by PlaybackEngine.current.collectAsState()
        val loaded by PlaybackEngine.loaded.collectAsState()
        val playing by PlaybackEngine.isPlaying.collectAsState()
        val position by PlaybackEngine.positionMs.collectAsState()
        val duration by PlaybackEngine.durationMs.collectAsState()
        val error by PlaybackEngine.error.collectAsState()
        val unavailable by PlaybackEngine.unavailable.collectAsState()
        val listened by Pods.listened.state.collectAsState()
        val progressMap by Pods.progress.state.collectAsState()
        val downloads by Pods.downloads.state.collectAsState()
        val settings by Pods.settings.state.collectAsState()

        val isCurrent = current?.key == episode.key && loaded
        val isListened = episode.key in listened
        val download = downloads[episode.key]
        val knownDuration = if (isCurrent && duration > 0) duration else episode.durationSec * 1000
        val shownPosition = if (isCurrent) position else progressMap[episode.key] ?: 0L
        val fraction = if (knownDuration > 0) shownPosition.toFloat() / knownDuration else 0f
        val lengthLabel = formatDuration(if (episode.durationSec > 0) episode.durationSec else knownDuration / 1000)

        val status = when {
            unavailable != null -> "playback unavailable"
            isCurrent && error != null -> "playback error"
            download != null && !download.complete && !download.failed && !(isCurrent && playing) -> "downloading…"
            download?.failed == true && shownPosition <= 0 -> "download failed"
            shownPosition > 0 -> formatClock(shownPosition)
            else -> ""
        }

        PodSurface {
            Column(modifier = Modifier.fillMaxSize()) {
                LightTopBar(leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 2.35f.gridUnitsAsDp()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(1.25f.gridUnitsAsDp()))
                    PodText(
                        text = episode.showTitle,
                        size = 30f,
                        lineHeight = 1.35f,
                        align = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.lightClickable(onClickLabel = "Open podcast") { openPodcast() },
                    )
                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                    PodText(
                        text = episode.title,
                        size = 30f,
                        lineHeight = 1.35f,
                        align = TextAlign.Center,
                        maxLines = 2,
                    )
                    Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
                    PodText(text = formatDate(episode.pubDateMs), size = 20f, lineHeight = 1.45f, align = TextAlign.Center)
                    if (lengthLabel.isNotEmpty()) {
                        PodText(text = lengthLabel, size = 20f, lineHeight = 1.45f, align = TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.height(0.9f.gridUnitsAsDp()))
                    LightTouchableProgressBar(
                        colors = LightThemeTokens.colors,
                        progress = fraction.coerceIn(0f, 1f),
                        onValueChange = { value ->
                            if (isCurrent && knownDuration > 0) PlaybackEngine.seekTo((value * knownDuration).toLong())
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(0.35f.gridUnitsAsDp()))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 1.5f.gridUnitsAsDp()),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LightIcon(
                            icon = LightIcons.SKIP_BACKWARD_FIFTEEN,
                            size = 2.35f,
                            modifier = Modifier.lightClickable(enabled = isCurrent) { PlaybackEngine.skipBack() },
                        )
                        LightIcon(
                            icon = if (isCurrent && playing) LightIcons.PAUSE else LightIcons.PLAY,
                            size = 2f,
                            modifier = Modifier.lightClickable {
                                if (isCurrent && error != null) PlaybackEngine.retry() else PlaybackEngine.toggle(episode)
                            },
                        )
                        LightIcon(
                            icon = LightIcons.SKIP_FORWARD_FIFTEEN,
                            size = 2.35f,
                            modifier = Modifier.lightClickable(enabled = isCurrent) { PlaybackEngine.skipForward() },
                        )
                    }
                    Spacer(modifier = Modifier.height(2.35f.gridUnitsAsDp()))
                    PodText(text = status, size = 24.5f, lineHeight = 1.25f, align = TextAlign.Center)
                    Spacer(modifier = Modifier.weight(1f))
                }
                PodBottomBar {
                    LightIcon(
                        icon = LightIcons.SETTINGS,
                        modifier = Modifier.lightClickable { navigateTo(screenFactory = { SettingsScreen(it) }) },
                    )
                    LightIcon(
                        icon = if (download?.complete == true) LightIcons.DOWNLOADED_ARROW else LightIcons.DOWNLOAD_ARROW,
                        contentDescription = if (download?.complete == true) "Downloaded" else "Download",
                        modifier = Modifier.lightClickable {
                            if (download == null || download.failed) Pods.requestDownload(lightContext, episode)
                        },
                    )
                    LightText(
                        text = speedLabel(settings.speed),
                        variant = LightTextVariant.Button,
                        modifier = Modifier.lightClickable(onClickLabel = "Playback speed") {
                            navigateTo(screenFactory = { SpeedScreen(it) })
                        },
                    )
                    CheckCircle(
                        checked = isListened,
                        size = 2f,
                        modifier = Modifier.lightClickable(
                            role = Role.Checkbox,
                            onClickLabel = if (isListened) "Mark not listened" else "Mark listened",
                        ) {
                            Pods.setListened(episode.key, !isListened)
                        },
                    )
                }
            }
        }
    }
}

class SpeedScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        PodScaffold(title = "Playback Speed", onBack = { goBack() }) {
            LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SPEEDS.forEach { speed ->
                    SettingsRow(
                        title = speedLabel(speed),
                        onClick = {
                            PlaybackEngine.setSpeed(speed)
                            goBack()
                        },
                    )
                }
            }
        }
    }
}
