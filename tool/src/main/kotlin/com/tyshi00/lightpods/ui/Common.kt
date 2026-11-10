package com.tyshi00.lightpods.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import com.tyshi00.lightpods.R
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.designVerticalPxToSp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.em
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightBottomBarItem
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightModal
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarButton
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.tyshi00.lightpods.data.Download
import com.tyshi00.lightpods.data.Episode
import kotlinx.coroutines.CompletableDeferred
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val ROW_UNITS = 5f
const val EPISODE_ROW_UNITS = 6f
const val SETTINGS_START_UNITS = 2.1f
const val OPTION_START_UNITS = 2.1f
const val OPTION_TOP_GAP_UNITS = 0f

@Composable
fun OptionRow(
    title: String,
    subtitle: String? = null,
    icon: LightIconConfiguration? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClickLabel = title, role = Role.Button, onClick = onClick)
            .padding(top = 1.3f.gridUnitsAsDp(), bottom = 1.3f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .padding(start = 0.17f.gridUnitsAsDp())
                    .width(2.78f.gridUnitsAsDp())
                    .height((38f * 1.2f).designVerticalPxToDp()),
                contentAlignment = Alignment.CenterStart,
            ) {
                LightIcon(icon = icon, size = 2f, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            PodText(text = title, size = 38f, lineHeight = 1.2f, maxLines = 2)
            if (!subtitle.isNullOrEmpty()) {
                PodText(text = subtitle, size = 20f, lineHeight = 1.3f, maxLines = 1)
            }
        }
    }
}

fun toggleIcon(on: Boolean): LightIconConfiguration =
    if (on) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF
const val LIST_TITLE_SIZE = 30f
const val LIST_SUBTITLE_SIZE = 20f
const val LIST_TITLE_LINE = 0.97f
const val LIST_TITLE_TRACKING = 0.035f

@Composable
fun PodText(
    text: String,
    size: Float,
    lineHeight: Float,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    dimmed: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    letterSpacing: Float = 0f,
) {
    val colors = LightThemeTokens.colors
    val style = LightThemeTokens.typography.copy.copy(
        fontSize = size.designVerticalPxToSp(),
        lineHeight = (size * lineHeight).designVerticalPxToSp(),
        letterSpacing = letterSpacing.em,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    ).let { if (align != null) it.copy(textAlign = align) else it }
    Text(
        text = text,
        modifier = modifier,
        color = if (dimmed) colors.contentSecondary else colors.content,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun CheckCircle(checked: Boolean, size: Float, modifier: Modifier = Modifier) {
    if (checked) {
        Icon(
            painter = painterResource(R.drawable.ic_check_circle_filled),
            contentDescription = null,
            tint = LightThemeTokens.colors.content,
            modifier = modifier.size(size.gridUnitsAsDp()),
        )
    } else {
        LightIcon(icon = LightIcons.CIRCLE, size = size, contentDescription = null, modifier = modifier)
    }
}

fun applyTheme(invert: Boolean) {
    if (invert) LightThemeController.setLightTheme() else LightThemeController.setDarkTheme()
}

@Composable
fun PodSurface(content: @Composable () -> Unit) {
    val colors by LightThemeController.colors.collectAsState()
    LightTheme(colors = colors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LightThemeTokens.colors.background),
        ) {
            content()
        }
    }
}

@Composable
fun PodScaffold(
    title: String?,
    onBack: (() -> Unit)?,
    right: LightTopBarButton? = null,
    bottom: List<LightBottomBarItem?> = emptyList(),
    startPadding: Float = SETTINGS_START_UNITS,
    showTopBar: Boolean = true,
    topGap: Float = 0f,
    rightText: String? = null,
    onRightText: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    PodSurface {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showTopBar) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    LightTopBar(
                        leftButton = onBack?.let { LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = it) },
                        center = title?.let { LightTopBarCenter.Text(it) },
                        rightButton = right,
                    )
                    if (rightText != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .height(3f.gridUnitsAsDp())
                                .padding(end = 1.2f.gridUnitsAsDp())
                                .lightClickable(onClickLabel = rightText, role = Role.Button, onClick = onRightText),
                            contentAlignment = Alignment.Center,
                        ) {
                            LightText(text = rightText, variant = LightTextVariant.Button, maxLines = 1)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = startPadding.gridUnitsAsDp(), top = topGap.gridUnitsAsDp()),
                content = content,
            )
            if (bottom.isNotEmpty()) LightBottomBar(items = bottom)
        }
    }
}

@Composable
fun ColumnScope.EmptyMessage(text: String) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(end = 1f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(text = text, variant = LightTextVariant.Copy, align = TextAlign.Center)
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_UNITS.gridUnitsAsDp())
            .lightClickable(
                onClickLabel = "Toggle $title",
                role = Role.Switch,
                onClick = { onToggle(!checked) },
            )
            .padding(end = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightIcon(
            icon = if (checked) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
            size = 2f,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.size(width = 1f.gridUnitsAsDp(), height = 1f.gridUnitsAsDp()))
        LightText(text = title, variant = LightTextVariant.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SettingsRow(
    title: String,
    onClick: () -> Unit = {},
    wrap: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (wrap) {
                    Modifier.heightIn(min = ROW_UNITS.gridUnitsAsDp()).padding(vertical = 0.5f.gridUnitsAsDp())
                } else {
                    Modifier.height(ROW_UNITS.gridUnitsAsDp())
                },
            )
            .lightClickable(onClickLabel = "Open $title", role = Role.Button, onClick = onClick)
            .padding(end = 1f.gridUnitsAsDp()),
        verticalArrangement = Arrangement.Center,
    ) {
        LightText(text = title, variant = LightTextVariant.Heading, maxLines = if (wrap) 2 else 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_UNITS.gridUnitsAsDp())
            .lightClickable(onClickLabel = "Select $title", role = Role.RadioButton, onClick = onClick)
            .padding(end = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(checked = selected, size = 1.5f)
        Spacer(modifier = Modifier.width(1f.gridUnitsAsDp()))
        LightText(text = title, variant = LightTextVariant.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TwoLineRow(
    title: String,
    subtitle: String,
    dimmed: Boolean = false,
    leading: LightIconConfiguration? = null,
    onLeading: (() -> Unit)? = null,
    trailing: LightIconConfiguration? = null,
    trailingPainter: Painter? = null,
    onTrailing: (() -> Unit)? = null,
    trailingText: String? = null,
    trailingWidth: Float = 3f,
    trailingSize: Float = 1.5f,
    height: Float = EPISODE_ROW_UNITS,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.gridUnitsAsDp())
            .padding(end = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier
                    .height(height.gridUnitsAsDp())
                    .width(3f.gridUnitsAsDp())
                    .lightClickable(role = Role.Checkbox, onClickLabel = leading.name) { onLeading?.invoke() },
                contentAlignment = Alignment.CenterStart,
            ) {
                LightIcon(icon = leading, size = 1.5f)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .lightClickable(role = Role.Button, onClick = onClick),
            verticalArrangement = Arrangement.Center,
        ) {
            LightText(
                text = title,
                variant = LightTextVariant.Copy,
                lighten = dimmed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LightText(
                text = subtitle,
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingText != null) {
            LightText(
                text = trailingText,
                variant = LightTextVariant.Detail,
                modifier = Modifier.padding(start = 1f.gridUnitsAsDp()),
            )
        }
        if (trailing != null || trailingPainter != null) {
            Box(
                modifier = Modifier
                    .height(height.gridUnitsAsDp())
                    .width(trailingWidth.gridUnitsAsDp())
                    .lightClickable(role = Role.Button, onClickLabel = trailing?.name) { onTrailing?.invoke() },
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (trailing != null) {
                    LightIcon(icon = trailing, size = trailingSize)
                } else if (trailingPainter != null) {
                    Icon(
                        painter = trailingPainter,
                        contentDescription = null,
                        tint = LightThemeTokens.colors.content,
                        modifier = Modifier.size(trailingSize.gridUnitsAsDp()),
                    )
                }
            }
        }
    }
}

const val LIST_ROW_UNITS = 4.8f
const val LIST_START_UNITS = 0.3f
const val DOWNLOADS_START_UNITS = 0.55f
const val LIST_END_UNITS = 2.6f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    subtitle: String,
    dimmed: Boolean = false,
    marker: LightIconConfiguration? = null,
    markerPainter: Painter? = null,
    onMarker: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(bottom = 0.92f.gridUnitsAsDp(), end = LIST_END_UNITS.gridUnitsAsDp()),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(1.4f.gridUnitsAsDp())
                .height((LIST_TITLE_SIZE * LIST_TITLE_LINE).designVerticalPxToDp())
                .then(if (onMarker != null) Modifier.lightClickable { onMarker() } else Modifier)
                .padding(top = 0.07f.gridUnitsAsDp()),
            contentAlignment = Alignment.TopStart,
        ) {
            if (marker != null) {
                LightIcon(icon = marker, size = 1f)
            } else if (markerPainter != null) {
                Icon(
                    painter = markerPainter,
                    contentDescription = null,
                    tint = LightThemeTokens.colors.content,
                    modifier = Modifier.size(1f.gridUnitsAsDp()),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            PodText(
                text = title,
                size = LIST_TITLE_SIZE,
                lineHeight = LIST_TITLE_LINE,
                dimmed = dimmed,
                maxLines = 2,
                letterSpacing = LIST_TITLE_TRACKING,
            )
            if (subtitle.isNotEmpty()) {
                PodText(
                    text = subtitle,
                    size = LIST_SUBTITLE_SIZE,
                    lineHeight = 1.3f,
                    dimmed = dimmed,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun EpisodeRow(
    episode: Episode,
    listened: Boolean,
    showShow: Boolean,
    download: Download?,
    onToggleListened: () -> Unit,
    onClick: () -> Unit,
) {
    ListRow(
        title = episode.title,
        subtitle = if (showShow) episode.showTitle else formatDate(episode.pubDateMs),
        dimmed = listened,
        markerPainter = if (download?.complete == true) painterResource(R.drawable.ic_downloaded_small) else null,
        onLongClick = onToggleListened,
        onClick = onClick,
    )
}

@Composable
fun PodBottomBar(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(4f.gridUnitsAsDp())
            .padding(horizontal = 2f.gridUnitsAsDp()),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

fun formatDate(ms: Long): String {
    if (ms <= 0) return ""
    return SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date(ms))
}

fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 && m > 0 -> "$h hr $m min"
        h > 0 -> "$h hr"
        else -> "${m.coerceAtLeast(1)} min"
    }
}

class SyncNoticeModal : LightModal {
    private val closed = CompletableDeferred<Unit>()
    override val onExpired: () -> Unit = {}

    override fun dismiss() {
        closed.complete(Unit)
    }

    override suspend fun awaitDismiss() {
        closed.await()
    }

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        LightTheme(colors = colors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
                    .lightClickable(hapticsEnabled = false) {},
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 2f.gridUnitsAsDp()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PodText(text = "Sync Triggered", size = 38f, lineHeight = 1.2f, align = TextAlign.Center)
                    Spacer(modifier = Modifier.height(2.5f.gridUnitsAsDp()))
                    PodText(
                        text = "Podcast feeds are syncing in the background",
                        size = 30f,
                        lineHeight = 1.7f,
                        align = TextAlign.Center,
                    )
                }
                LightBottomBar(
                    items = listOf(LightBarButton.LightIcon(icon = LightIcons.CLOSE, onClick = { dismiss() })),
                )
            }
        }
    }
}
