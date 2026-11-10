package com.tyshi00.lightpods.playback

import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.audio.DefaultLightAudio
import com.thelightphone.sdk.audio.LightAudioError
import com.thelightphone.sdk.audio.LightAudioErrorKind
import com.thelightphone.sdk.audio.LightAudioItem
import com.thelightphone.sdk.audio.LightAudioPlayback
import com.thelightphone.sdk.audio.LightAudioPlayer
import com.thelightphone.sdk.audio.LightAudioSource
import com.thelightphone.sdk.audio.LightAudioUsage
import com.thelightphone.sdk.audio.LightMediaMetadata
import com.thelightphone.sdk.audio.NO_MEDIA_ITEM
import com.tyshi00.lightpods.data.AutoPlayMode
import com.tyshi00.lightpods.data.Episode
import com.tyshi00.lightpods.data.PlaybackSession
import com.tyshi00.lightpods.data.Pods
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object PlaybackEngine {
    private const val MAX_QUEUE = 25
    private const val SAVE_INTERVAL_MS = 10_000L
    private const val END_TOLERANCE_MS = 2_000L
    private const val USER_SKIP_WINDOW_MS = 2_000L
    private const val MAX_RETRIES = 4

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: LightAudioPlayer? = null
    private var bindJob: Job? = null
    private var seekJob: Job? = null
    private var retryJob: Job? = null
    private var retryCount = 0

    private var queue: List<Episode> = emptyList()
    private var index: Int = NO_MEDIA_ITEM
    private var lastSaveAt = 0L
    private var lastUserSkipAt = 0L
    private var indexChangedAt = 0L

    private val _current = MutableStateFlow<Episode?>(null)
    val current: StateFlow<Episode?> = _current.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _error = MutableStateFlow<LightAudioError?>(null)
    val error: StateFlow<LightAudioError?> = _error.asStateFlow()

    private val _unavailable = MutableStateFlow<String?>(null)
    val unavailable: StateFlow<String?> = _unavailable.asStateFlow()

    fun bind(activity: SealedLightActivity) {
        bindJob?.cancel()
        seekJob?.cancel()
        player?.release()
        player = null
        val created = runCatching {
            DefaultLightAudio(activity).newPlayer(LightAudioUsage.Speech, LightAudioPlayback.Detached)
        }
        val p = created.getOrElse {
            _unavailable.value = it.message ?: "Audio unavailable"
            return
        }
        _unavailable.value = null
        player = p
        bindJob = scope.launch {
            if (!p.awaitReady()) return@launch
            p.speed = Pods.settings.value.speed
            reconcile(p)
            launch { p.isPlaying.collect { onPlayingChanged(p, it) } }
            launch { p.positionMs.collect { onPosition(p, it) } }
            launch { p.durationMs.collect { _durationMs.value = it } }
            launch { p.error.collect { onError(p, it) } }
            launch { p.currentMediaItemIndex.collect { onIndex(it) } }
        }
    }

    private fun reconcile(p: LightAudioPlayer) {
        val session = Pods.session.value
        val liveIndex = p.currentMediaItemIndex.value
        if (liveIndex != NO_MEDIA_ITEM && liveIndex < session.queue.size) {
            if (session.lastIndex in 0 until liveIndex) {
                for (i in session.lastIndex until liveIndex) Pods.setListened(session.queue[i].key, true)
            }
            queue = session.queue
            index = liveIndex
            _loaded.value = true
            _current.value = queue[liveIndex]
            Pods.session.update { it.copy(lastIndex = liveIndex) }
        } else {
            queue = session.queue
            index = NO_MEDIA_ITEM
            _loaded.value = false
            _current.value = session.queue.getOrNull(session.lastIndex)
        }
        _positionMs.value = if (_loaded.value) p.positionMs.value else _current.value?.let { Pods.savedProgress(it.key) } ?: 0L
        _durationMs.value = p.durationMs.value
    }

    private fun onIndex(newIndex: Int) {
        if (newIndex == NO_MEDIA_ITEM || newIndex == index) return
        val previous = index
        val now = System.currentTimeMillis()
        val userSkipped = now - lastUserSkipAt < USER_SKIP_WINDOW_MS
        if (previous >= 0 && newIndex == previous + 1 && !userSkipped) {
            queue.getOrNull(previous)?.let { Pods.setListened(it.key, true) }
        }
        index = newIndex
        indexChangedAt = now
        val episode = queue.getOrNull(newIndex) ?: return
        _current.value = episode
        _loaded.value = true
        Pods.session.update { it.copy(lastIndex = newIndex) }
        val resumeAt = Pods.savedProgress(episode.key)
        if (resumeAt > 0 && !userSkipped) seekWhenPrepared(resumeAt)
    }

    private fun onPosition(p: LightAudioPlayer, position: Long) {
        _positionMs.value = position
        val episode = _current.value ?: return
        if (!_loaded.value) return
        val now = System.currentTimeMillis()
        if (now - indexChangedAt < USER_SKIP_WINDOW_MS) return
        if (p.isPlaying.value && now - lastSaveAt > SAVE_INTERVAL_MS && position > 0) {
            lastSaveAt = now
            Pods.saveProgress(episode.key, position)
        }
    }

    private fun onError(p: LightAudioPlayer, err: LightAudioError?) {
        _error.value = err
        if (err == null || err.kind != LightAudioErrorKind.Source || retryCount >= MAX_RETRIES) return
        if (_current.value == null || !_loaded.value || index < 0) return
        val episode = _current.value ?: return
        val position = p.positionMs.value
        if (position > 0) Pods.saveProgress(episode.key, position)
        retryCount++
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(3_000L * retryCount)
            startQueue(queue.drop(index).ifEmpty { listOf(episode) })
        }
    }

    private fun onPlayingChanged(p: LightAudioPlayer, playing: Boolean) {
        _isPlaying.value = playing
        if (playing) {
            retryCount = 0
            return
        }
        val episode = _current.value ?: return
        if (!_loaded.value) return
        if (System.currentTimeMillis() - indexChangedAt < USER_SKIP_WINDOW_MS) return
        val position = p.positionMs.value
        val duration = p.durationMs.value
        if (duration > 0 && position >= duration - END_TOLERANCE_MS) {
            Pods.setListened(episode.key, true)
        } else if (position > 0) {
            Pods.saveProgress(episode.key, position)
        }
    }

    private fun seekWhenPrepared(target: Long) {
        val p = player ?: return
        seekJob?.cancel()
        val startDuration = p.durationMs.value
        seekJob = scope.launch {
            withTimeoutOrNull(15_000) {
                p.durationMs.first { it > 0 && it != startDuration }
            }
            if (p.durationMs.value > target) runCatching { p.seekTo(target) }
        }
    }

    fun buildQueue(episode: Episode): List<Episode> {
        val mode = Pods.settings.value.autoPlay
        val context = when (mode) {
            AutoPlayMode.Off -> return listOf(episode)
            AutoPlayMode.AllRecents -> Pods.recentEpisodes(Pods.episodes.value, Pods.subscriptions.value)
            AutoPlayMode.SameShow -> Pods.showEpisodes(
                episode.feedUrl,
                Pods.settings.value.oldestFirst[episode.feedUrl] == true,
            )
        }
        val start = context.indexOfFirst { it.key == episode.key }
        if (start < 0) return listOf(episode)
        val following = context.drop(start + 1)
            .filterNot { Pods.isListened(it.key) }
            .take(MAX_QUEUE - 1)
        return listOf(episode) + following
    }

    private fun Episode.toItem(): LightAudioItem {
        val file = Pods.localFile(key)
        val source = if (file != null) LightAudioSource.FileSource(file) else LightAudioSource.UrlSource(audioUrl)
        return LightAudioItem(
            source = source,
            metadata = LightMediaMetadata(
                title = title,
                artist = showTitle,
                album = showTitle,
                durationMs = durationSec.takeIf { it > 0 }?.times(1000),
            ),
        )
    }

    fun play(episode: Episode) {
        val p = player ?: return
        if (_current.value?.key == episode.key && _loaded.value && _error.value == null) {
            runCatching {
                val duration = p.durationMs.value
                if (duration > 0 && p.positionMs.value >= duration - END_TOLERANCE_MS) p.seekTo(0L)
                p.play()
            }
            return
        }
        retryCount = 0
        retryJob?.cancel()
        startQueue(buildQueue(episode))
    }

    private fun startQueue(newQueue: List<Episode>) {
        val p = player ?: return
        val episode = newQueue.firstOrNull() ?: return
        queue = newQueue
        index = 0
        indexChangedAt = System.currentTimeMillis()
        lastUserSkipAt = indexChangedAt
        _current.value = episode
        _loaded.value = true
        _error.value = null
        Pods.session.update { PlaybackSession(queue = newQueue, lastIndex = 0) }
        val resumeAt = Pods.savedProgress(episode.key)
        runCatching {
            p.setMediaQueue(newQueue.map { it.toItem() }, 0)
            p.speed = Pods.settings.value.speed
            p.play()
        }.onFailure { _unavailable.value = it.message }
        if (resumeAt > 0) seekWhenPrepared(resumeAt)
    }

    fun toggle(episode: Episode) {
        val p = player ?: return
        if (_current.value?.key == episode.key && _loaded.value && _isPlaying.value) {
            runCatching { p.pause() }
        } else {
            play(episode)
        }
    }

    fun seekTo(ms: Long) {
        val p = player ?: return
        runCatching { p.seekTo(ms) }
        _positionMs.value = ms
    }

    fun skipBack() {
        runCatching { player?.skipBack() }
    }

    fun skipForward() {
        runCatching { player?.skipForward() }
    }

    fun setSpeed(speed: Float) {
        Pods.settings.update { it.copy(speed = speed) }
        runCatching { player?.speed = speed }
    }

    fun isCurrent(episode: Episode): Boolean = _current.value?.key == episode.key

    fun retry() {
        val episode = _current.value ?: return
        _loaded.value = false
        scope.launch {
            delay(50)
            play(episode)
        }
    }
}
