package br.com.centralmidia.android.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persiste os últimos resultados exibidos nas abas Notícias e Vídeos.
 *
 * O histórico continua existindo separadamente; este armazenamento serve para
 * reconstruir exatamente a tela quando o aplicativo é fechado/reaberto e para
 * alimentar o painel Início com os mesmos números mostrados nas abas.
 */
class ResultStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "central_result_store",
        Context.MODE_PRIVATE,
    )

    fun saveNews(items: List<NewsItem>) {
        val array = JSONArray()
        items.take(MAX_ITEMS).forEach { item ->
            array.put(
                JSONObject().apply {
                    put("title", item.title)
                    put("source", item.source)
                    put("link", item.link)
                    put("pubDate", item.pubDate)
                    put("snippet", item.snippet)
                    put("publishedAt", item.publishedAt)
                    put("matchedTerm", item.matchedTerm)
                    put("matchedDemand", item.matchedDemand)
                    put("isNew", item.isNew)
                },
            )
        }
        prefs.edit()
            .putString(KEY_NEWS, array.toString())
            .putLong(KEY_NEWS_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun loadNews(): List<NewsItem> =
        parseArray(KEY_NEWS) { obj ->
            NewsItem(
                title = obj.optString("title"),
                source = obj.optString("source"),
                link = obj.optString("link"),
                pubDate = obj.optString("pubDate"),
                snippet = obj.optString("snippet"),
                publishedAt = obj.optLong("publishedAt", 0L),
                matchedTerm = obj.optString("matchedTerm"),
                matchedDemand = obj.optString("matchedDemand"),
                isNew = obj.optBoolean("isNew", false),
            )
        }

    fun saveVideos(items: List<VideoSearchClient.VideoItem>) {
        val array = JSONArray()
        items.take(MAX_ITEMS).forEach { item ->
            array.put(
                JSONObject().apply {
                    put("title", item.title)
                    put("sourceId", item.sourceId)
                    put("sourceName", item.sourceName)
                    put("publishedAt", item.publishedAt)
                    put("link", item.link)
                    put("summary", item.summary)
                    put("matchedTerm", item.matchedTerm)
                    put("matchedDemand", item.matchedDemand)
                    put("isNew", item.isNew)
                },
            )
        }
        prefs.edit()
            .putString(KEY_VIDEOS, array.toString())
            .putLong(KEY_VIDEOS_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun loadVideos(): List<VideoSearchClient.VideoItem> =
        parseArray(KEY_VIDEOS) { obj ->
            VideoSearchClient.VideoItem(
                title = obj.optString("title"),
                sourceId = obj.optString("sourceId"),
                sourceName = obj.optString("sourceName"),
                publishedAt = obj.optLong("publishedAt", 0L),
                link = obj.optString("link"),
                summary = obj.optString("summary"),
                matchedTerm = obj.optString("matchedTerm"),
                matchedDemand = obj.optString("matchedDemand"),
                isNew = obj.optBoolean("isNew", false),
            )
        }

    fun newsSavedAt(): Long = prefs.getLong(KEY_NEWS_SAVED_AT, 0L)
    fun videosSavedAt(): Long = prefs.getLong(KEY_VIDEOS_SAVED_AT, 0L)

    private fun <T> parseArray(
        key: String,
        factory: (JSONObject) -> T,
    ): List<T> = runCatching {
        val raw = prefs.getString(key, null).orEmpty()
        if (raw.isBlank()) return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                runCatching { factory(obj) }.getOrNull()?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY_NEWS = "news_items"
        private const val KEY_VIDEOS = "video_items"
        private const val KEY_NEWS_SAVED_AT = "news_saved_at"
        private const val KEY_VIDEOS_SAVED_AT = "videos_saved_at"
        private const val MAX_ITEMS = 800
    }
}
