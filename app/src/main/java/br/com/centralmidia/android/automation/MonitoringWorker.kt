package br.com.centralmidia.android.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.centralmidia.android.core.AutomationPreferences
import br.com.centralmidia.android.core.CatalogRepository
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.GoogleNewsClient
import br.com.centralmidia.android.core.Matching
import br.com.centralmidia.android.core.NotificationHelper
import br.com.centralmidia.android.core.SourcePreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MonitoringWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): ListenableWorker.Result {
        val config = AutomationPreferences(applicationContext).load()
        if (!config.general) return ListenableWorker.Result.success()

        return try {
            when (inputData.getString("kind").orEmpty()) {
                "demands" -> runDemands(config)
                "videos" -> runVideos(config)
                else -> runNews(config)
            }
        } catch (_: Exception) {
            ListenableWorker.Result.retry()
        }
    }

    private fun runNews(config: AutomationPreferences.Config): ListenableWorker.Result {
        if (!config.news) return ListenableWorker.Result.success()

        val db = CentralDb(applicationContext)
        val cache = applicationContext.getSharedPreferences("monitor_cache", Context.MODE_PRIVATE)
        val client = GoogleNewsClient()
        val allSources = CatalogRepository.news(applicationContext)
        val sourcePrefs = SourcePreferences(applicationContext)
        val searchAll = sourcePrefs.newsAllSources()
        val selected = sourcePrefs.selectedNewsSources(allSources)
        var notified = 0

        for ((_, term) in db.terms("news")) {
            val first = client.search(term, 8).firstOrNull { item ->
                searchAll || selected.any { Matching.sourceMatchesStrict(item.source, it) }
            } ?: continue

            val key = "last_news_${term.hashCode()}"
            val old = cache.getString(key, "")
            if (old != first.link) {
                cache.edit().putString(key, first.link).apply()
                NotificationHelper.notify(
                    applicationContext,
                    "Nova notícia • $term",
                    first.title,
                )
                db.addHistoryUnique(
                    "automation",
                    first.title,
                    "Notícias • Termo: $term",
                    first.link,
                )
                notified++
            }
        }

        return ListenableWorker.Result.success(workDataOf("new" to notified))
    }

    private fun runDemands(config: AutomationPreferences.Config): ListenableWorker.Result {
        if (!config.demands) return ListenableWorker.Result.success()

        val db = CentralDb(applicationContext)
        val cache = applicationContext.getSharedPreferences("monitor_cache", Context.MODE_PRIVATE)
        val client = GoogleNewsClient()
        var notified = 0

        for ((id, vehicle, subject) in db.demands()) {
            val first = client.search(subject, 10).firstOrNull { item ->
                Matching.demandVehicleMatches(item.source, vehicle) &&
                    Matching.subjectMatches("${item.title} ${item.snippet}", subject)
            } ?: continue

            val key = "last_demand_$id"
            val old = cache.getString(key, "")
            if (old != first.link) {
                cache.edit().putString(key, first.link).apply()
                NotificationHelper.notify(
                    applicationContext,
                    "Demanda • ${vehicle.ifBlank { "Todos" }}",
                    first.title,
                )
                db.addHistoryUnique(
                    "demand",
                    first.title,
                    "$vehicle • $subject",
                    first.link,
                )
                notified++
            }
        }

        return ListenableWorker.Result.success(workDataOf("new" to notified))
    }

    private fun runVideos(config: AutomationPreferences.Config): ListenableWorker.Result {
        if (!config.videos || config.videoTimes.isEmpty()) {
            return ListenableWorker.Result.success()
        }

        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val slot = config.videoTimes.firstOrNull { raw ->
            val parts = raw.split(":")
            val target = parts[0].toInt() * 60 + parts[1].toInt()
            abs(target - nowMinutes) <= 7
        } ?: return ListenableWorker.Result.success()

        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val cache = applicationContext.getSharedPreferences("monitor_cache", Context.MODE_PRIVATE)
        val key = "last_video_slot"
        val current = "$day-$slot"
        if (cache.getString(key, "") == current) {
            return ListenableWorker.Result.success()
        }
        cache.edit().putString(key, current).apply()

        val db = CentralDb(applicationContext)
        val terms = db.terms("video").size
        val allVideoSources = CatalogRepository.videos(applicationContext)
        val selected = SourcePreferences(applicationContext)
            .selectedVideoSources(allVideoSources)
            .size

        NotificationHelper.notify(
            applicationContext,
            "Vídeos • horário automático $slot",
            "$terms termo(s) • $selected fonte(s) configurada(s). Abra Vídeos para revisar as fontes.",
        )
        db.addHistory(
            "video_automation",
            "Busca de vídeos agendada • $slot",
            "$terms termo(s) • $selected fonte(s)",
        )

        return ListenableWorker.Result.success(workDataOf("slot" to slot))
    }
}
