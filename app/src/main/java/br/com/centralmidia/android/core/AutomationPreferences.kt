package br.com.centralmidia.android.core

import android.content.Context

class AutomationPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "central_settings",
        Context.MODE_PRIVATE,
    )

    data class Config(
        val general: Boolean,
        val news: Boolean,
        val newsInterval: Int,
        val demands: Boolean,
        val demandInterval: Int,
        val videos: Boolean,
        val videoTimes: Set<String>,
    )

    fun load(): Config = Config(
        general = prefs.getBoolean("auto_general", prefs.getBoolean("auto", false)),
        news = prefs.getBoolean("auto_news", true),
        newsInterval = prefs.getInt("auto_news_interval", prefs.getInt("interval", 30)).coerceAtLeast(15),
        demands = prefs.getBoolean("auto_demands", true),
        demandInterval = prefs.getInt("auto_demand_interval", 60).coerceAtLeast(15),
        videos = prefs.getBoolean("auto_videos", true),
        videoTimes = parseTimes(
            prefs.getString("auto_video_times", "08:00,12:00,15:00,19:00,21:00").orEmpty(),
        ),
    )

    fun save(config: Config) {
        prefs.edit()
            .putBoolean("auto_general", config.general)
            .putBoolean("auto_news", config.news)
            .putInt("auto_news_interval", config.newsInterval.coerceAtLeast(15))
            .putBoolean("auto_demands", config.demands)
            .putInt("auto_demand_interval", config.demandInterval.coerceAtLeast(15))
            .putBoolean("auto_videos", config.videos)
            .putString("auto_video_times", config.videoTimes.sorted().joinToString(","))
            .apply()
    }

    companion object {
        fun parseTimes(raw: String): Set<String> = raw
            .split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.matches(Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")) }
            .toSet()
    }
}
