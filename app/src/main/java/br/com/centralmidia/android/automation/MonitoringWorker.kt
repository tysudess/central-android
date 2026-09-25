package br.com.centralmidia.android.automation

import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.centralmidia.android.core.AutomationPreferences
import br.com.centralmidia.android.core.CatalogRepository
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.GoogleNewsClient
import br.com.centralmidia.android.core.Matching
import br.com.centralmidia.android.core.NewsItem
import br.com.centralmidia.android.core.NotificationHelper
import br.com.centralmidia.android.core.ResultStore
import br.com.centralmidia.android.core.SourcePreferences
import br.com.centralmidia.android.core.VideoSearchClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MonitoringWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(
    ctx,
    params,
) {
    override suspend fun doWork():
        ListenableWorker.Result {
        NotificationHelper.createChannels(
            applicationContext,
        )

        val config =
            AutomationPreferences(
                applicationContext,
            )
                .load()

        if (
            !config.general
        ) {
            return ListenableWorker.Result.success()
        }

        return try {
            when (
                inputData.getString(
                    "kind",
                )
                    .orEmpty()
            ) {
                "demands" ->
                    runDemands(
                        config,
                    )

                "videos" ->
                    runVideos(
                        config,
                    )

                else ->
                    runNews(
                        config,
                    )
            }
        } catch (
            error: Exception,
        ) {
            ListenableWorker.Result.retry()
        }
    }

    private fun runNews(
        config:
            AutomationPreferences.Config,
    ): ListenableWorker.Result {
        if (
            !config.news
        ) {
            return ListenableWorker.Result.success()
        }

        val db =
            CentralDb(
                applicationContext,
            )

        val terms =
            db.terms(
                "news",
            )
                .map {
                    it.second.trim()
                }
                .filter {
                    it.isNotBlank()
                }

        if (
            terms.isEmpty()
        ) {
            NotificationHelper.notifyNewsSearch(
                applicationContext,
                "Nenhum termo de notícias está ativo.",
            )

            return ListenableWorker.Result.success(
                workDataOf(
                    "found" to 0,
                    "new" to 0,
                ),
            )
        }

        val client =
            GoogleNewsClient()

        val allSources =
            CatalogRepository.news(
                applicationContext,
            )

        val sourcePrefs =
            SourcePreferences(
                applicationContext,
            )

        val searchAll =
            sourcePrefs.newsAllSources()

        val selected =
            sourcePrefs.selectedNewsSources(
                allSources,
            )

        val resultStore =
            ResultStore(
                applicationContext,
            )

        val previous =
            resultStore.loadNews()

        val previousLinks =
            previous
                .map {
                    it.link
                        .trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .toSet()

        val collected =
            linkedMapOf<
                String,
                NewsItem
                >()

        var errors =
            0

        terms.forEach {
                term,
            ->
            if (
                isStopped
            ) {
                return ListenableWorker.Result.retry()
            }

            val items =
                runCatching {
                    client.search(
                        term,
                        20,
                    )
                }
                    .getOrElse {
                        errors++
                        emptyList()
                    }

            items.forEach {
                    item,
                ->
                if (
                    !searchAll &&
                    selected.none {
                            source,
                        ->
                        Matching.sourceMatchesStrict(
                            item.source,
                            source,
                        )
                    }
                ) {
                    return@forEach
                }

                val key =
                    item.link
                        .trim()

                if (
                    key.isBlank()
                ) {
                    return@forEach
                }

                val existing =
                    collected[
                        key
                    ]

                if (
                    existing ==
                    null
                ) {
                    collected[
                        key
                    ] =
                        item.copy(
                            matchedTerm =
                                term,
                            isNew =
                                key !in
                                    previousLinks,
                        )
                } else {
                    val termsMerged =
                        (
                            existing.matchedTerm
                                .split(
                                    ",",
                                ) +
                                term
                            )
                            .map {
                                it.trim()
                            }
                            .filter {
                                it.isNotBlank()
                            }
                            .distinct()
                            .joinToString(
                                ", ",
                            )

                    collected[
                        key
                    ] =
                        existing.copy(
                            matchedTerm =
                                termsMerged,
                            isNew =
                                existing.isNew ||
                                    key !in
                                    previousLinks,
                        )
                }
            }
        }

        if (
            errors ==
            terms.size &&
            collected.isEmpty()
        ) {
            NotificationHelper.notifyNewsSearch(
                applicationContext,
                "A busca não conseguiu acessar o servidor. O Android tentará novamente.",
            )

            return ListenableWorker.Result.retry()
        }

        val current =
            collected.values
                .sortedByDescending {
                    it.publishedAt
                }

        val currentLinks =
            current
                .map {
                    it.link
                }
                .toSet()

        val merged =
            (
                current +
                    previous
                        .filterNot {
                            it.link in
                                currentLinks
                        }
                        .map {
                            it.copy(
                                isNew =
                                    false,
                            )
                        }
                )
                .sortedByDescending {
                    it.publishedAt
                }

        resultStore.saveNews(
            merged,
        )

        val newItems =
            current.filter {
                it.isNew
            }

        newItems
            .take(50)
            .forEach {
                    item,
                ->
                db.addHistoryUnique(
                    "automation",
                    item.title,
                    "Notícias • ${item.matchedTerm}",
                    item.link,
                )
            }

        db.addHistory(
            "automation_news_run",
            "Busca automática de notícias",
            "${current.size} encontrada(s) • ${newItems.size} nova(s) • $errors falha(s)",
        )

        NotificationHelper.notifyNewsSearch(
            applicationContext,
            "${current.size} notícia(s) encontrada(s) • ${newItems.size} nova(s)" +
                if (
                    errors >
                    0
                ) {
                    " • $errors consulta(s) com falha"
                } else {
                    ""
                },
        )

        return ListenableWorker.Result.success(
            workDataOf(
                "found" to current.size,
                "new" to newItems.size,
                "errors" to errors,
            ),
        )
    }

    private fun runDemands(
        config:
            AutomationPreferences.Config,
    ): ListenableWorker.Result {
        if (
            !config.demands
        ) {
            return ListenableWorker.Result.success()
        }

        val db =
            CentralDb(
                applicationContext,
            )

        val cache =
            applicationContext.getSharedPreferences(
                "monitor_cache",
                Context.MODE_PRIVATE,
            )

        val client =
            GoogleNewsClient()

        var notified =
            0

        for (
            (
                id,
                vehicle,
                subject,
            ) in db.demands()
        ) {
            val first =
                client.search(
                    subject,
                    10,
                )
                    .firstOrNull {
                            item,
                        ->
                        Matching.demandVehicleMatches(
                            item.source,
                            vehicle,
                        ) &&
                            Matching.subjectMatches(
                                "${item.title} ${item.snippet}",
                                subject,
                            )
                    }
                    ?: continue

            val key =
                "last_demand_$id"

            val old =
                cache.getString(
                    key,
                    "",
                )

            if (
                old !=
                first.link
            ) {
                cache.edit()
                    .putString(
                        key,
                        first.link,
                    )
                    .apply()

                NotificationHelper.notify(
                    applicationContext,
                    "Demanda • ${
                        vehicle.ifBlank {
                            "Todos"
                        }
                    }",
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

        return ListenableWorker.Result.success(
            workDataOf(
                "new" to notified,
            ),
        )
    }

    private suspend fun runVideos(
        config:
            AutomationPreferences.Config,
    ): ListenableWorker.Result {
        if (
            !config.videos ||
            config.videoTimes.isEmpty()
        ) {
            return ListenableWorker.Result.success()
        }

        val due =
            dueVideoSlot(
                config,
            )
                ?: return ListenableWorker.Result.success()

        val (
            day,
            slot,
        ) =
            due

        val cache =
            applicationContext.getSharedPreferences(
                "monitor_cache",
                Context.MODE_PRIVATE,
            )

        val slotKey =
            "video_slot_${day}_$slot"

        if (
            cache.getBoolean(
                slotKey,
                false,
            )
        ) {
            return ListenableWorker.Result.success()
        }

        // A busca de vídeo pode varrer muitas fontes. Como trabalho longo do
        // WorkManager, promovemos o Worker a foreground para que continue
        // mesmo com a tela apagada e sem depender da Activity aberta.
        val serviceType =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }

        setForeground(
            ForegroundInfo(
                PROGRESS_NOTIFICATION_ID,
                NotificationHelper.buildSearchProgress(
                    applicationContext,
                    "Central • buscando vídeos",
                    "Busca automática das $slot em andamento. Pode apagar a tela.",
                ),
                serviceType,
            ),
        )

        val db =
            CentralDb(
                applicationContext,
            )

        val terms =
            db.terms(
                "video",
            )
                .map {
                    it.second.trim()
                }
                .filter {
                    it.isNotBlank()
                }

        val demands =
            db.demands()

        val allSources =
            CatalogRepository.videos(
                applicationContext,
            )

        val selected =
            SourcePreferences(
                applicationContext,
            )
                .selectedVideoSources(
                    allSources,
                )

        if (
            selected.isEmpty()
        ) {
            NotificationHelper.notifyVideoSearch(
                applicationContext,
                "Nenhuma fonte de vídeo está selecionada.",
            )

            return ListenableWorker.Result.success()
        }

        if (
            terms.isEmpty() &&
            demands.isEmpty()
        ) {
            NotificationHelper.notifyVideoSearch(
                applicationContext,
                "Nenhum termo ou demanda está ativo para pesquisar.",
            )

            return ListenableWorker.Result.success()
        }

        val store =
            ResultStore(
                applicationContext,
            )

        val previous =
            store.loadVideos()

        val previousLinks =
            previous
                .map {
                    it.link
                }
                .toSet()

        val now =
            System.currentTimeMillis()

        val outcome =
            VideoSearchClient()
                .search(
                    sources =
                        selected,
                    terms =
                        terms,
                    demands =
                        demands,
                    fromMs =
                        now -
                            24L *
                            60L *
                            60L *
                            1000L,
                    toMs =
                        now,
                    cancelled = {
                        isStopped
                    },
                    onProgress = {
                        // O widget/Activity não precisa estar aberto. O serviço
                        // foreground do WorkManager mantém a execução viva.
                    },
                )

        if (
            outcome.cancelled ||
            isStopped
        ) {
            return ListenableWorker.Result.retry()
        }

        val current =
            outcome.items
                .map {
                    item,
                ->
                    item.copy(
                        isNew =
                            item.link !in
                                previousLinks,
                    )
                }
                .sortedByDescending {
                    it.publishedAt
                }

        val currentLinks =
            current
                .map {
                    it.link
                }
                .toSet()

        val merged =
            (
                current +
                    previous
                        .filterNot {
                            it.link in
                                currentLinks
                        }
                        .map {
                            it.copy(
                                isNew =
                                    false,
                            )
                        }
                )
                .sortedByDescending {
                    it.publishedAt
                }

        store.saveVideos(
            merged,
        )

        val newItems =
            current.filter {
                it.isNew
            }

        newItems
            .take(50)
            .forEach {
                    item,
                ->
                db.addHistoryUnique(
                    "video_automation",
                    item.title,
                    "Vídeos • ${item.sourceName} • ${item.matchedTerm}",
                    item.link,
                )
            }

        db.addHistory(
            "video_automation_run",
            "Busca automática de vídeos • $slot",
            "${current.size} encontrado(s) • ${newItems.size} novo(s) • ${outcome.errors} falha(s)",
        )

        // Marcamos o horário apenas depois que a busca realmente terminou.
        cache.edit()
            .putBoolean(
                slotKey,
                true,
            )
            .apply()

        NotificationHelper.notifyVideoSearch(
            applicationContext,
            "${current.size} vídeo(s) encontrado(s) • ${newItems.size} novo(s)" +
                if (
                    outcome.errors >
                    0
                ) {
                    " • ${outcome.errors} fonte(s) com falha"
                } else {
                    ""
                },
        )

        return ListenableWorker.Result.success(
            workDataOf(
                "slot" to slot,
                "found" to current.size,
                "new" to newItems.size,
                "errors" to outcome.errors,
            ),
        )
    }

    /**
     * WorkManager não é um despertador de segundo exato. Se o Android estiver
     * em Doze ele pode atrasar a execução. Por isso escolhemos o horário mais
     * recente do dia que já venceu e ainda não foi concluído; assim a busca não
     * é perdida apenas porque a tela ficou apagada.
     */
    private fun dueVideoSlot(
        config:
            AutomationPreferences.Config,
    ): Pair<String, String>? {
        val now =
            Calendar.getInstance()

        val nowMinutes =
            now.get(
                Calendar.HOUR_OF_DAY,
            ) *
                60 +
                now.get(
                    Calendar.MINUTE,
                )

        val due =
            config.videoTimes
                .mapNotNull {
                        raw,
                    ->
                    val parts =
                        raw.split(
                            ":",
                        )

                    if (
                        parts.size !=
                        2
                    ) {
                        null
                    } else {
                        val hour =
                            parts[
                                0
                            ]
                                .toIntOrNull()

                        val minute =
                            parts[
                                1
                            ]
                                .toIntOrNull()

                        if (
                            hour ==
                            null ||
                            minute ==
                            null
                        ) {
                            null
                        } else {
                            Triple(
                                hour *
                                    60 +
                                    minute,
                                hour,
                                minute,
                            )
                        }
                    }
                }
                .filter {
                    it.first <=
                        nowMinutes
                }
                .maxByOrNull {
                    it.first
                }
                ?: return null

        val day =
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US,
            )
                .format(
                    Date(),
                )

        val slot =
            "%02d:%02d"
                .format(
                    due.second,
                    due.third,
                )

        return day to
            slot
    }

    companion object {
        private const val PROGRESS_NOTIFICATION_ID =
            22_000
    }
}
