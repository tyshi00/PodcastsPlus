package com.tyshi00.lightpods.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.tyshi00.lightpods.R
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.tyshi00.lightpods.data.PodNet
import com.tyshi00.lightpods.data.Pods
import com.tyshi00.lightpods.data.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class TextEntryScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initialValue: String = "",
    private val submitLabel: String = "SUBMIT",
    private val singleLine: Boolean = true,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val keyboardOptions = rememberKeyboardOptions()
        val textState = rememberTextFieldState(initialValue)
        PodSurface {
            LightTextInputEditor(
                title = title,
                state = textState,
                keyboardOptionsFlow = keyboardOptions,
                onSubmit = { value ->
                    val text = value.toString().trim()
                    if (text.isEmpty()) goBack() else goBack(text)
                },
                onBack = { goBack() },
                submitLabel = submitLabel,
                singleLine = singleLine,
            )
        }
    }
}

class AddPodcastScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<Unit>(sealedActivity) {
    private val status = MutableStateFlow<String?>(null)

    private fun addFeed(url: String) {
        status.value = "Adding…"
        Pods.scope.launch {
            status.value = Pods.addPodcast(url).fold(
                onSuccess = { "Added: ${it.title}" },
                onFailure = { "Could not add feed. Check the URL and try again." },
            )
        }
    }

    @Composable
    override fun Content() {
        val message by status.collectAsState()
        PodScaffold(title = "Add Podcast", onBack = { goBack() }) {
            LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SettingsRow("Search podcasts", onClick = {
                    navigateTo(screenFactory = { TextEntryScreen(it, "Search podcasts", submitLabel = "SEARCH") }) { query ->
                        navigateTo(screenFactory = { SearchResultsScreen(it, query) })
                    }
                })
                SettingsRow("Enter RSS URL", onClick = {
                    navigateTo(screenFactory = { TextEntryScreen(it, "RSS feed URL", "https://", submitLabel = "ADD") }) { url ->
                        addFeed(url)
                    }
                })
                message?.let {
                    LightText(
                        text = it,
                        variant = LightTextVariant.Copy,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }
}

class SearchResultsScreen(
    sealedActivity: SealedLightActivity,
    initialQuery: String,
) : SimpleLightScreen<Unit>(sealedActivity) {
    private val query = MutableStateFlow(initialQuery)
    private var listState by mutableStateOf(LazyListState())
    private val results = MutableStateFlow<List<SearchResult>>(emptyList())
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val adding = MutableStateFlow<Set<String>>(emptySet())
    private val failed = MutableStateFlow<Set<String>>(emptySet())

    init {
        runSearch(initialQuery)
    }

    private fun runSearch(term: String) {
        query.value = term
        listState = LazyListState()
        loading.value = true
        error.value = null
        Pods.scope.launch {
            runCatching { PodNet.search(term) }
                .onSuccess { results.value = it }
                .onFailure {
                    results.value = emptyList()
                    error.value = "Search failed. Check your connection."
                }
            loading.value = false
        }
    }

    private fun add(result: SearchResult) {
        if (Pods.isSubscribed(result.feedUrl) || result.feedUrl in adding.value) return
        adding.value = adding.value + result.feedUrl
        failed.value = failed.value - result.feedUrl
        Pods.scope.launch {
            if (Pods.addPodcast(result.feedUrl).isFailure) failed.value = failed.value + result.feedUrl
            adding.value = adding.value - result.feedUrl
        }
    }

    private fun confirmRemove(result: SearchResult) {
        val podcast = Pods.subscriptions.value.firstOrNull { it.feedUrl == result.feedUrl } ?: return
        navigateTo(screenFactory = { RemovePodcastScreen(it, podcast) })
    }

    @Composable
    override fun Content() {
        val term by query.collectAsState()
        val list by results.collectAsState()
        val isLoading by loading.collectAsState()
        val errorText by error.collectAsState()
        val inFlight by adding.collectAsState()
        val failedSet by failed.collectAsState()
        val subs by Pods.subscriptions.state.collectAsState()
        val subscribed = remember(subs) { subs.map { it.feedUrl }.toSet() }

        PodScaffold(
            title = "“$term”",
            onBack = { goBack() },
            bottom = listOf(
                LightBarButton.LightIcon(icon = LightIcons.SEARCH, onClick = {
                    navigateTo(screenFactory = { TextEntryScreen(it, "Search podcasts", term, submitLabel = "SEARCH") }) { next ->
                        runSearch(next)
                    }
                }),
            ),
        ) {
            when {
                isLoading -> EmptyMessage("Searching…")
                errorText != null -> EmptyMessage(errorText.orEmpty())
                list.isEmpty() -> EmptyMessage("No podcasts found.")
                else -> LightLazyScrollView(
                    listState = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    uniformItemHeightGridUnits = EPISODE_ROW_UNITS,
                ) {
                    items(list, key = { it.feedUrl }) { result ->
                        val isSubscribed = result.feedUrl in subscribed
                        val action = {
                            if (isSubscribed) confirmRemove(result) else add(result)
                        }
                        TwoLineRow(
                            title = result.title,
                            subtitle = when (result.feedUrl) {
                                in inFlight -> "Adding…"
                                in failedSet -> "Could not add. Tap + to retry."
                                else -> result.author.ifBlank { "unknown" }
                            },
                            trailing = if (isSubscribed) null else LightIcons.ADD,
                            trailingPainter = if (isSubscribed) painterResource(R.drawable.ic_remove_circle) else null,
                            onTrailing = action,
                            onClick = action,
                        )
                    }
                }
            }
        }
    }
}
