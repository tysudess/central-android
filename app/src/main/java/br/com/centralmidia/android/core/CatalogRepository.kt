package br.com.centralmidia.android.core

import android.content.Context
import org.json.JSONArray

object CatalogRepository {
    private fun stringList(o: org.json.JSONObject, key: String): List<String> {
        val arr = o.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val value = arr.optString(i).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun aliases(o: org.json.JSONObject): List<String> = stringList(o, "aliases")

    fun news(context: Context): List<NewsSource> {
        val arr = JSONArray(
            context.assets.open("news_sources.json")
                .bufferedReader()
                .use { it.readText() }
        )
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            NewsSource(
                id = o.optString("id"),
                name = o.optString("name"),
                region = o.optString("region"),
                state = o.optString("state"),
                group = o.optString("group"),
                aliases = aliases(o),
            )
        }
    }

    fun videos(context: Context): List<VideoSource> {
        val arr = JSONArray(
            context.assets.open("video_sources.json")
                .bufferedReader()
                .use { it.readText() }
        )
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            VideoSource(
                id = o.optString("id"),
                name = o.optString("name"),
                group = o.optString("group"),
                region = o.optString("region"),
                state = o.optString("state"),
                landingUrl = o.optString("landingUrl"),
                searchUrlTemplate = o.optString("searchUrlTemplate"),
                searchPrefix = o.optString("searchPrefix"),
                aliases = aliases(o),
                linkHints = stringList(o, "linkHints"),
                youtubeHandle = o.optString("youtubeHandle"),
            )
        }
    }

    fun regularNews(context: Context): List<NewsSource> =
        news(context).filterNot { it.group.equals("Mídia especializada", ignoreCase = true) }

    fun specializedNews(context: Context): List<NewsSource> =
        news(context).filter { it.group.equals("Mídia especializada", ignoreCase = true) }

    fun newspapers(context: Context): List<Newspaper> {
        val arr = JSONArray(
            context.assets.open("newspapers.json")
                .bufferedReader()
                .use { it.readText() }
        )
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val urls = o.optJSONArray("urls")
            Newspaper(
                o.optString("name"),
                buildList {
                    if (urls != null) {
                        for (j in 0 until urls.length()) add(urls.optString(j))
                    }
                },
            )
        }
    }
}
