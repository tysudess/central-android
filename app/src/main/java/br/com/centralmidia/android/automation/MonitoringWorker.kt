package br.com.centralmidia.android.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.centralmidia.android.core.*

class MonitoringWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            val db = CentralDb(applicationContext)
            val prefs = applicationContext.getSharedPreferences("monitor_cache", Context.MODE_PRIVATE)
            val client = GoogleNewsClient()
            var notified = 0
            for ((_, term) in db.terms().take(8)) {
                val first = client.search(term, 5).firstOrNull() ?: continue
                val key = "last_" + term.hashCode()
                val old = prefs.getString(key, "")
                if (old != first.link) {
                    prefs.edit().putString(key, first.link).apply()
                    NotificationHelper.notify(applicationContext, "Nova notícia • $term", first.title)
                    db.addHistory("automation", first.title, "Termo: $term", first.link)
                    notified++
                }
            }
            ListenableWorker.Result.success(workDataOf("new" to notified))
        } catch (_: Exception) {
            ListenableWorker.Result.retry()
        }
    }
}
