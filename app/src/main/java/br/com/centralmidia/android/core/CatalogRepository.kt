package br.com.centralmidia.android.core

import android.content.Context
import org.json.JSONArray

object CatalogRepository {
    fun news(context: Context): List<NewsSource> {
        val arr = JSONArray(context.assets.open("news_sources.json").bufferedReader().use { it.readText() })
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            NewsSource(o.optString("id"), o.optString("name"), o.optString("region"), o.optString("state"), o.optString("group"))
        }
    }

    fun videos(context: Context): List<VideoSource> {
        val arr = JSONArray(context.assets.open("video_sources.json").bufferedReader().use { it.readText() })
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            VideoSource(
                id=o.optString("id"), name=o.optString("name"), group=o.optString("group"),
                region=o.optString("region"), state=o.optString("state"), landingUrl=o.optString("landingUrl"),
                searchUrlTemplate=o.optString("searchUrlTemplate"), searchPrefix=o.optString("searchPrefix")
            )
        }
    }

    fun newspapers(context: Context): List<Newspaper> {
        val arr = JSONArray(context.assets.open("newspapers.json").bufferedReader().use { it.readText() })
        return (0 until arr.length()).map { i ->
            val o=arr.getJSONObject(i); val urls=o.optJSONArray("urls")
            Newspaper(o.optString("name"), buildList {
                if (urls != null) for (j in 0 until urls.length()) add(urls.optString(j))
            })
        }
    }
}
