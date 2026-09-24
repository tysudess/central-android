package br.com.centralmidia.android.core

import android.util.Base64
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Resolve links intermediários do Google News para a URL real do veículo. */
class GoogleNewsUrlResolver {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val cache = ConcurrentHashMap<String, String>()

    fun resolve(input: String): String {
        val raw = input.trim()
        if (raw.isBlank() || !isGoogleNews(raw)) return raw
        cache[raw]?.let { return it }

        val id = runCatching {
            URI(raw).path.trimEnd('/').substringAfterLast('/').trim()
        }.getOrDefault("")
        if (id.isBlank()) return raw

        val resolved = legacy(id)
            ?: signed(id)
            ?: idOnly(id)

        if (!resolved.isNullOrBlank() && resolved.startsWith("http") && !isGoogleNews(resolved)) {
            cache[raw] = resolved
            return resolved
        }
        return raw
    }

    fun isGoogleNews(url: String): Boolean = runCatching {
        val host = URI(url).host.orEmpty().lowercase()
        host == "news.google.com" || host.endsWith(".news.google.com")
    }.getOrDefault("news.google.com" in url.lowercase())

    private fun legacy(articleId: String): String? = runCatching {
        val bytes = Base64.decode(
            articleId,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val text = bytes.toString(Charset.forName("ISO-8859-1"))
        val starts = listOf(text.indexOf("https://"), text.indexOf("http://"))
            .filter { it >= 0 }
        if (starts.isEmpty()) return@runCatching null
        val start = starts.minOrNull() ?: return@runCatching null
        var end = text.length
        for (index in start until text.length) {
            val c = text[index]
            if (c.code < 32 || c == '\u0000') {
                end = index
                break
            }
        }
        text.substring(start, end).trim().takeIf {
            it.startsWith("http") && !isGoogleNews(it)
        }
    }.getOrNull()

    private fun signed(articleId: String): String? {
        val urls = listOf(
            "https://news.google.com/articles/$articleId?hl=pt-BR&gl=BR&ceid=BR:pt-419",
            "https://news.google.com/rss/articles/$articleId?hl=pt-BR&gl=BR&ceid=BR:pt-419",
        )

        for (url in urls) {
            val html = getText(url) ?: continue
            val doc = Jsoup.parse(html, url)
            val node = doc.selectFirst("[data-n-a-id='$articleId'][data-n-a-sg][data-n-a-ts]")
                ?: doc.selectFirst("[data-n-a-sg][data-n-a-ts]")
                ?: continue

            val signature = node.attr("data-n-a-sg").trim()
            val timestamp = node.attr("data-n-a-ts").trim().toLongOrNull() ?: continue
            val article = node.attr("data-n-a-id").trim().ifBlank { articleId }
            if (signature.isBlank()) continue

            val inner = JSONArray().apply {
                put("garturlreq")
                put(
                    JSONArray().apply {
                        put(
                            JSONArray().apply {
                                put("pt-BR")
                                put("BR")
                                put(JSONArray().put("FINANCE_TOP_INDICES").put("WEB_TEST_1_0_0"))
                                put(JSONObject.NULL)
                                put(JSONObject.NULL)
                                put(1)
                                put(1)
                                put("BR:pt-419")
                                put(JSONObject.NULL)
                                put(480)
                                repeat(5) { put(JSONObject.NULL) }
                                put(0)
                                put(5)
                            },
                        )
                        put("pt-BR")
                        put("BR")
                        put(1)
                        put(JSONArray().put(2).put(4).put(8))
                        put(1)
                        put(1)
                        put(JSONObject.NULL)
                        put(0)
                        put(0)
                        put(JSONObject.NULL)
                        put(0)
                    },
                )
                put(article)
                put(timestamp)
                put(signature)
            }.toString()

            batch(inner)?.let { return it }
        }
        return null
    }

    private fun idOnly(articleId: String): String? = runCatching {
        val inner = JSONArray().apply {
            put("garturlreq")
            put(
                JSONArray().apply {
                    put(
                        JSONArray().apply {
                            put("en-US")
                            put("US")
                            put(JSONArray().put("FINANCE_TOP_INDICES").put("WEB_TEST_1_0_0"))
                            put(JSONObject.NULL)
                            put(JSONObject.NULL)
                            put(1)
                            put(1)
                            put("US:en")
                            put(JSONObject.NULL)
                            put(180)
                            repeat(5) { put(JSONObject.NULL) }
                            put(0)
                            put(JSONObject.NULL)
                            put(JSONObject.NULL)
                            put(JSONArray().put(1608992183).put(723341000))
                        },
                    )
                    put("en-US")
                    put("US")
                    put(1)
                    put(JSONArray().put(2).put(3).put(4).put(8))
                    put(1)
                    put(0)
                    put("655000234")
                    put(0)
                    put(0)
                    put(JSONObject.NULL)
                    put(0)
                },
            )
            put(articleId)
        }.toString()
        batch(inner)
    }.getOrNull()

    private fun batch(inner: String): String? {
        val payload = JSONArray().put(
            JSONArray().put(
                JSONArray()
                    .put("Fbv4je")
                    .put(inner)
                    .put(JSONObject.NULL)
                    .put("generic"),
            ),
        ).toString()

        val request = Request.Builder()
            .url(BATCH_URL)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://news.google.com/")
            .post(FormBody.Builder().add("f.req", payload).build())
            .build()

        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string().orEmpty()
        }

        extractStructured(body)?.let { return it }

        val normalized = body
            .replace("\\/", "/")
            .replace("\\u003d", "=")
            .replace("\\u0026", "&")
            .replace("\\u0025", "%")

        val regex = Regex("https?://[^\\\"\\s]+")
        return regex.findAll(normalized)
            .map { it.value.trimEnd(',', ']', '}', '\\') }
            .firstOrNull(::isDirectVehicleUrl)
    }

    private fun extractStructured(body: String): String? {
        for (raw in body.split("\n\n")) {
            var clean = raw.trim()
            if (clean.startsWith(")]}'")) {
                clean = clean.removePrefix(")]}'").trimStart()
            }
            val lineBreak = clean.indexOf('\n')
            if (lineBreak > 0) {
                val firstLine = clean.substring(0, lineBreak).trim()
                if (firstLine.isNotEmpty() && firstLine.all { it.isDigit() }) {
                    clean = clean.substring(lineBreak + 1).trim()
                }
            }
            val parsed = runCatching { JSONTokener(clean).nextValue() }.getOrNull() ?: continue
            findVehicleUrl(parsed)?.let { return it }
        }
        return null
    }

    private fun findVehicleUrl(value: Any?): String? = when (value) {
        is JSONArray -> {
            if (value.length() > 1 && value.optString(0) == "garturlres") {
                value.optString(1).takeIf(::isDirectVehicleUrl)
            } else {
                var found: String? = null
                for (index in 0 until value.length()) {
                    found = findVehicleUrl(value.opt(index))
                    if (found != null) break
                }
                found
            }
        }
        is JSONObject -> {
            var found: String? = null
            val keys = value.keys()
            while (keys.hasNext() && found == null) {
                found = findVehicleUrl(value.opt(keys.next()))
            }
            found
        }
        is String -> {
            val candidate = value.trim()
            when {
                isDirectVehicleUrl(candidate) -> candidate
                candidate.startsWith("[") || candidate.startsWith("{") ->
                    runCatching { findVehicleUrl(JSONTokener(candidate).nextValue()) }.getOrNull()
                else -> null
            }
        }
        else -> null
    }

    private fun isDirectVehicleUrl(candidate: String): Boolean {
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) return false
        val low = candidate.lowercase()
        return !isGoogleNews(candidate) &&
            "googleusercontent.com" !in low &&
            "gstatic.com" !in low &&
            "google.com/" !in low
    }

    private fun getText(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125 Mobile Safari/537.36"
        private const val BATCH_URL =
            "https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je"
    }
}
