package com.tyshi00.lightpods.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

object PodNet {
    private const val AGENT = "LightPods/1.0 (Light Phone III)"

    private val client by lazy {
        HttpClient(OkHttp) {
            install(UserAgent) { agent = AGENT }
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                requestTimeoutMillis = 45_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    private val downloadClient by lazy {
        HttpClient(OkHttp) {
            install(UserAgent) { agent = AGENT }
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    suspend fun fetchFeed(url: String): ParsedFeed = withContext(Dispatchers.IO) {
        val response = client.get(url)
        if (!response.status.isSuccess()) throw IOException("HTTP ${response.status.value}")
        val bytes = response.readRawBytes()
        RssParser.parse(url, ByteArrayInputStream(bytes))
    }

    @Serializable
    private data class ItunesResponse(val results: List<ItunesResult> = emptyList())

    @Serializable
    private data class ItunesResult(
        val collectionName: String? = null,
        val trackName: String? = null,
        val artistName: String? = null,
        val feedUrl: String? = null,
    )

    suspend fun search(term: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val response = client.get("https://itunes.apple.com/search") {
            parameter("term", term)
            parameter("media", "podcast")
            parameter("entity", "podcast")
            parameter("limit", 25)
        }
        if (!response.status.isSuccess()) throw IOException("HTTP ${response.status.value}")
        PodJson.decodeFromString(ItunesResponse.serializer(), response.bodyAsText())
            .results
            .mapNotNull { r ->
                val feed = r.feedUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SearchResult(
                    title = r.collectionName ?: r.trackName ?: feed,
                    author = r.artistName.orEmpty(),
                    feedUrl = feed,
                )
            }
            .distinctBy { it.feedUrl }
    }

    suspend fun download(
        url: String,
        target: File,
        onProgress: (done: Long, total: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val partial = File(target.parentFile, target.name + ".part")
        target.parentFile?.mkdirs()
        val existing = if (partial.exists()) partial.length() else 0L
        downloadClient.prepareGet(url) {
            if (existing > 0) header(HttpHeaders.Range, "bytes=$existing-")
        }.execute { response ->
            if (response.status == HttpStatusCode.RequestedRangeNotSatisfiable && existing > 0) {
                onProgress(existing, existing)
                return@execute
            }
            val resumed = response.status == HttpStatusCode.PartialContent
            if (!response.status.isSuccess()) throw IOException("HTTP ${response.status.value}")
            val offset = if (resumed) existing else 0L
            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
            val total = if (contentLength > 0) contentLength + offset else 0L
            var done = offset
            var lastReport = 0L
            response.bodyAsChannel().toInputStream().use { input ->
                FileOutputStream(partial, resumed).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (done - lastReport > 512 * 1024) {
                            lastReport = done
                            onProgress(done, total)
                        }
                    }
                }
            }
            onProgress(done, total)
        }
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) throw IOException("Could not finalize download")
    }
}
