package com.tyshi00.lightpods.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedFeed(
    val podcast: Podcast,
    val episodes: List<Episode>,
)

object RssParser {
    private const val MAX_EPISODES = 300

    private val dateFormats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm Z",
        "dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )

    fun parse(feedUrl: String, input: InputStream): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var channelTitle = ""
        var channelAuthor = ""
        var channelOwner = ""
        val items = mutableListOf<MutableMap<String, String>>()
        var item: MutableMap<String, String>? = null
        val depthPath = ArrayDeque<String>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase(Locale.US)
                    depthPath.addLast(name)
                    val open = item
                    when {
                        name == "item" || name == "entry" -> item = mutableMapOf()
                        name == "enclosure" && open != null -> {
                            parser.getAttributeValue(null, "url")?.let { open["enclosure"] = it.trim() }
                            parser.getAttributeValue(null, "length")?.let { open["length"] = it.trim() }
                        }
                        name == "link" && open != null && parser.getAttributeValue(null, "rel") == "enclosure" -> {
                            parser.getAttributeValue(null, "href")?.let { open["enclosure"] = it.trim() }
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        val tag = depthPath.lastOrNull()
                        val current = item
                        if (current != null && tag != null) {
                            when (tag) {
                                "title", "guid", "id", "pubdate", "published", "itunes:duration" ->
                                    current[tag] = (current[tag].orEmpty() + text)
                            }
                        } else if (current == null && depthPath.size >= 2) {
                            val parent = depthPath[depthPath.size - 2]
                            when {
                                tag == "title" && (parent == "channel" || parent == "feed") && channelTitle.isEmpty() ->
                                    channelTitle = text
                                tag == "itunes:author" && parent == "channel" -> channelAuthor = text
                                tag == "itunes:name" && parent == "itunes:owner" && channelOwner.isEmpty() -> channelOwner = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase(Locale.US)
                    val closing = item
                    if ((name == "item" || name == "entry") && closing != null) {
                        items.add(closing)
                        item = null
                    }
                    depthPath.removeLastOrNull()
                }
            }
            event = parser.next()
        }

        val podcast = Podcast(
            feedUrl = feedUrl,
            title = channelTitle.ifBlank { feedUrl },
            author = channelAuthor.ifBlank { channelOwner },
        )
        val episodes = items.mapNotNull { raw ->
            val audio = raw["enclosure"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Episode(
                feedUrl = feedUrl,
                guid = (raw["guid"] ?: raw["id"])?.takeIf { it.isNotBlank() } ?: audio,
                title = raw["title"]?.decodeEntities()?.takeIf { it.isNotBlank() } ?: "Untitled",
                showTitle = podcast.title,
                audioUrl = audio,
                pubDateMs = parseDate(raw["pubdate"] ?: raw["published"]),
                durationSec = parseDuration(raw["itunes:duration"]),
                lengthBytes = raw["length"]?.toLongOrNull() ?: 0L,
            )
        }
            .distinctBy { it.guid }
            .sortedByDescending { it.pubDateMs }
            .take(MAX_EPISODES)
        return ParsedFeed(podcast, episodes)
    }

    fun parseDate(value: String?): Long {
        val v = value?.trim()?.replace(Regex("\\s+"), " ") ?: return 0L
        for (pattern in dateFormats) {
            val result = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }.parse(v)?.time
            }.getOrNull()
            if (result != null) return result
        }
        return 0L
    }

    fun parseDuration(value: String?): Long {
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return 0L
        val parts = v.split(":").map { it.trim().toDoubleOrNull() ?: return 0L }
        return parts.fold(0.0) { acc, part -> acc * 60 + part }.toLong()
    }

    private fun String.decodeEntities(): String = this
        .replace("&amp;", "&")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
