package br.com.centralmidia.android.core

import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class GoogleNewsClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun search(query: String, limit: Int = 50): List<NewsItem> {
        val q = URLEncoder.encode(
            query.trim(),
            StandardCharsets.UTF_8.toString(),
        )
        val url =
            "https://news.google.com/rss/search?q=$q&hl=pt-BR&gl=BR&ceid=BR:pt-419"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 MonitorNoticiasAndroid/3.0")
            .build()

        val xml = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Falha HTTP ${response.code}")
            response.body?.string().orEmpty()
        }

        val parser = XmlPullParserFactory.newInstance()
            .newPullParser()
            .apply { setInput(StringReader(xml)) }

        val out = mutableListOf<NewsItem>()
        var inItem = false
        var title = ""
        var link = ""
        var pub = ""
        var source = ""
        var description = ""
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT && out.size < limit) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        title = ""
                        link = ""
                        pub = ""
                        source = ""
                        description = ""
                    }
                    "title" -> if (inItem) title = parser.nextText()
                    "link" -> if (inItem) link = parser.nextText()
                    "pubDate" -> if (inItem) pub = parser.nextText()
                    "source" -> if (inItem) source = parser.nextText()
                    "description" -> if (inItem) description = parser.nextText()
                }

                XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                    if (title.isNotBlank() && link.isNotBlank()) {
                        out += NewsItem(
                            title = title.trim(),
                            source = source.trim(),
                            link = link.trim(),
                            pubDate = pub.trim(),
                            snippet = stripHtml(description).take(500),
                            publishedAt = parseDate(pub),
                        )
                    }
                    inItem = false
                }
            }
            event = parser.next()
        }

        return out
    }

    private fun stripHtml(value: String): String =
        value
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun parseDate(value: String): Long {
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss Z",
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).parse(value)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return System.currentTimeMillis()
    }
}
