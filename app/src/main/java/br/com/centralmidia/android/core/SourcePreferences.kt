package br.com.centralmidia.android.core

import android.content.Context

class SourcePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "desktop_source_preferences",
        Context.MODE_PRIVATE,
    )

    fun newsAllSources(): Boolean =
        prefs.getBoolean(KEY_NEWS_ALL, true)

    fun setNewsAllSources(value: Boolean) {
        prefs.edit().putBoolean(KEY_NEWS_ALL, value).apply()
    }

    fun selectedNewsIds(): Set<String> =
        prefs.getStringSet(KEY_NEWS_IDS, emptySet())?.toSet().orEmpty()

    fun setSelectedNewsIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_NEWS_IDS, ids.toSet()).apply()
    }

    fun selectedVideoIds(allSources: List<VideoSource>): Set<String> {
        if (!prefs.contains(KEY_VIDEO_IDS)) {
            val defaults = allSources.map { it.id }.toSet()
            setSelectedVideoIds(defaults)
            return defaults
        }
        return prefs.getStringSet(KEY_VIDEO_IDS, emptySet())?.toSet().orEmpty()
    }

    fun setSelectedVideoIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_VIDEO_IDS, ids.toSet()).apply()
    }

    fun selectedNewsSources(allSources: List<NewsSource>): List<NewsSource> =
        if (newsAllSources()) {
            allSources
        } else {
            val selected = selectedNewsIds()
            allSources.filter { it.id in selected }
        }

    fun selectedVideoSources(allSources: List<VideoSource>): List<VideoSource> {
        val selected = selectedVideoIds(allSources)
        return allSources.filter { it.id in selected }
    }

    companion object {
        private const val KEY_NEWS_ALL = "desktop_news_all_sources"
        private const val KEY_NEWS_IDS = "desktop_news_source_ids"
        private const val KEY_VIDEO_IDS = "desktop_video_source_ids"
    }
}
