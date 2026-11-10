package com.tyshi00.lightpods.data

import com.thelightphone.sdk.LightWork
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.security.MessageDigest

const val JOB_DOWNLOAD = "download-episode"
const val JOB_SYNC = "sync-feeds"
const val TAG_SYNC_PERIODIC = "sync-feeds-periodic"
const val TAG_SYNC_NOW = "sync-feeds-now"

object Pods {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var root: File
    lateinit var subscriptions: JsonStore<List<Podcast>>
        private set
    lateinit var episodes: JsonStore<Map<String, List<Episode>>>
        private set
    lateinit var listened: JsonStore<Set<String>>
        private set
    lateinit var progress: JsonStore<Map<String, Long>>
        private set
    lateinit var settings: JsonStore<Settings>
        private set
    lateinit var downloads: JsonStore<Map<String, Download>>
        private set
    lateinit var session: JsonStore<PlaybackSession>
        private set

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _refreshingFeeds = MutableStateFlow<Set<String>>(emptySet())
    val refreshingFeeds: StateFlow<Set<String>> = _refreshingFeeds.asStateFlow()

    @Synchronized
    fun init(filesDir: File) {
        if (::root.isInitialized) return
        val dir = File(filesDir, "pods").apply { mkdirs() }
        subscriptions = JsonStore(File(dir, "subscriptions.json"), ListSerializer(Podcast.serializer()), emptyList(), scope)
        episodes = JsonStore(
            File(dir, "episodes.json"),
            MapSerializer(String.serializer(), ListSerializer(Episode.serializer())),
            emptyMap(),
            scope,
        )
        listened = JsonStore(File(dir, "listened.json"), SetSerializer(String.serializer()), emptySet(), scope)
        progress = JsonStore(File(dir, "progress.json"), MapSerializer(String.serializer(), Long.serializer()), emptyMap(), scope)
        settings = JsonStore(File(dir, "settings.json"), Settings.serializer(), Settings(), scope)
        downloads = JsonStore(
            File(dir, "downloads.json"),
            MapSerializer(String.serializer(), Download.serializer()),
            emptyMap(),
            scope,
        )
        session = JsonStore(File(dir, "session.json"), PlaybackSession.serializer(), PlaybackSession(), scope)
        root = filesDir
    }

    fun recentEpisodes(map: Map<String, List<Episode>>, subs: List<Podcast>): List<Episode> {
        val subscribed = subs.map { it.feedUrl }.toSet()
        return map.filterKeys { it in subscribed }.values.flatten()
            .sortedByDescending { it.pubDateMs }
            .take(300)
    }

    fun showEpisodes(feedUrl: String, oldestFirst: Boolean): List<Episode> {
        val list = episodes.value[feedUrl].orEmpty().sortedByDescending { it.pubDateMs }
        return if (oldestFirst) list.reversed() else list
    }

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }

    suspend fun addPodcast(rawUrl: String): Result<Podcast> = runCatching {
        val url = normalizeUrl(rawUrl)
        val parsed = PodNet.fetchFeed(url)
        subscriptions.update { list -> list.filterNot { it.feedUrl == url } + parsed.podcast }
        episodes.update { it + (url to parsed.episodes) }
        parsed.podcast
    }

    fun isSubscribed(feedUrl: String): Boolean = subscriptions.value.any { it.feedUrl == feedUrl }

    suspend fun refreshFeed(feedUrl: String): Result<Unit> {
        _refreshingFeeds.value = _refreshingFeeds.value + feedUrl
        return try {
            runCatching {
                val parsed = PodNet.fetchFeed(feedUrl)
                if (!isSubscribed(feedUrl)) return@runCatching
                subscriptions.update { list ->
                    list.map { if (it.feedUrl == feedUrl) parsed.podcast else it }
                }
                episodes.update { it + (feedUrl to parsed.episodes) }
            }
        } finally {
            _refreshingFeeds.value = _refreshingFeeds.value - feedUrl
        }
    }

    suspend fun refreshAll() {
        if (_syncing.value) return
        _syncing.value = true
        try {
            val gate = Semaphore(4)
            coroutineScope {
                subscriptions.value.map { podcast ->
                    async { gate.withPermit { refreshFeed(podcast.feedUrl) } }
                }.awaitAll()
            }
        } finally {
            _syncing.value = false
        }
    }

    fun removePodcast(ctx: SealedLightContext, feedUrl: String, deleteDownloads: Boolean) {
        subscriptions.update { list -> list.filterNot { it.feedUrl == feedUrl } }
        episodes.update { it - feedUrl }
        settings.update { it.copy(oldestFirst = it.oldestFirst - feedUrl) }
        if (deleteDownloads) {
            downloads.value.values.filter { it.episode.feedUrl == feedUrl }.forEach {
                removeDownload(ctx, it.episode.key)
            }
        }
    }

    fun isListened(key: String): Boolean = key in listened.value

    fun setListened(key: String, value: Boolean) {
        listened.update { if (value) it + key else it - key }
        if (value) progress.update { it - key }
    }

    fun saveProgress(key: String, positionMs: Long) {
        progress.update { it + (key to positionMs) }
    }

    fun savedProgress(key: String): Long = progress.value[key] ?: 0L

    private fun downloadDir(): File = File(root, "downloads").apply { mkdirs() }

    private fun hash(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun extensionFor(url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext.length in 2..4 && ext.all { it.isLetterOrDigit() }) ext else "mp3"
    }

    private fun downloadTag(key: String) = "dl-" + hash(key)

    fun localFile(key: String): File? {
        val entry = downloads.value[key]?.takeIf { it.complete } ?: return null
        return File(downloadDir(), entry.fileName).takeIf { it.exists() }
    }

    private val activeDownloads = ConcurrentHashMap<String, Deferred<Boolean>>()

    fun requestDownload(ctx: SealedLightContext, episode: Episode) {
        val existing = downloads.value[episode.key]
        if (existing?.complete == true) return
        if (existing == null || existing.failed) {
            val fileName = existing?.fileName ?: (hash(episode.key) + "." + extensionFor(episode.audioUrl))
            downloads.update {
                it + (episode.key to Download(episode = episode, fileName = fileName, bytesTotal = episode.lengthBytes))
            }
        }
        startDownload(ctx, episode.key)
    }

    fun resumeDownloads(ctx: SealedLightContext) {
        downloads.value.values
            .filter { !it.complete && !it.failed }
            .forEach { startDownload(ctx, it.episode.key) }
    }

    private fun startDownload(ctx: SealedLightContext, key: String) {
        downloadAsync(key)
        LightWork.enqueue(ctx, JOB_DOWNLOAD, mapOf("key" to key), tag = downloadTag(key))
    }

    fun downloadAsync(key: String): Deferred<Boolean> = synchronized(activeDownloads) {
        activeDownloads[key]?.takeIf { it.isActive } ?: scope.async { runDownload(key) }.also { job ->
            activeDownloads[key] = job
            job.invokeOnCompletion { synchronized(activeDownloads) { activeDownloads.remove(key, job) } }
        }
    }

    fun removeDownload(ctx: SealedLightContext, key: String) {
        val entry = downloads.value[key] ?: return
        if (!entry.complete) {
            LightWork.cancel(ctx, downloadTag(key))
            activeDownloads.remove(key)?.cancel()
        }
        downloads.update { it - key }
        val file = File(downloadDir(), entry.fileName)
        scope.launch {
            File(file.parentFile, file.name + ".part").delete()
            file.delete()
        }
    }

    private suspend fun runDownload(key: String): Boolean {
        val entry = downloads.value[key] ?: return true
        if (entry.complete) return true
        val target = File(downloadDir(), entry.fileName)
        return try {
            PodNet.download(entry.episode.audioUrl, target) { done, total ->
                downloads.update { map ->
                    val current = map[key] ?: return@update map
                    map + (key to current.copy(bytesDone = done, bytesTotal = if (total > 0) total else current.bytesTotal, failed = false))
                }
            }
            if (downloads.value[key] == null) {
                target.delete()
            } else {
                downloads.update { map ->
                    val current = map[key] ?: return@update map
                    map + (key to current.copy(complete = true, failed = false, bytesDone = target.length(), bytesTotal = target.length()))
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            downloads.update { map ->
                val current = map[key] ?: return@update map
                map + (key to current.copy(failed = true))
            }
            false
        }
    }
}
