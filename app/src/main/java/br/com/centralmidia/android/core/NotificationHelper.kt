package br.com.centralmidia.android.core

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import br.com.centralmidia.android.R
import br.com.centralmidia.android.ui.MainActivity
import br.com.centralmidia.android.ui.NewsActivity
import br.com.centralmidia.android.ui.VideosActivity

object NotificationHelper {
    const val CHANNEL_MONITOR = "central_monitor"
    const val CHANNEL_SEARCH_RESULTS = "central_search_results"
    const val CHANNEL_SEARCH_PROGRESS = "central_search_progress"
    const val CHANNEL_RECORDING = "central_recording"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager =
            context.getSystemService(
                NotificationManager::class.java,
            )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR,
                "Monitoramento",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SEARCH_RESULTS,
                "Resultados das buscas",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    "Avisos quando buscas automáticas de notícias e vídeos terminam."
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SEARCH_PROGRESS,
                "Buscas em segundo plano",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description =
                    "Mostra buscas longas que continuam com a tela apagada."
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDING,
                "Gravação de tela",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun notify(
        context: Context,
        title: String,
        text: String,
        id: Int =
            (
                System.currentTimeMillis() %
                    100_000
                )
                .toInt(),
    ) {
        notifyTo(
            context,
            title,
            text,
            MainActivity::class.java,
            id,
            CHANNEL_MONITOR,
        )
    }

    fun notifyNewsSearch(
        context: Context,
        text: String,
        id: Int =
            21_001,
    ) {
        notifyTo(
            context,
            "Busca automática de notícias concluída",
            text,
            NewsActivity::class.java,
            id,
            CHANNEL_SEARCH_RESULTS,
        )
    }

    fun notifyVideoSearch(
        context: Context,
        text: String,
        id: Int =
            22_001,
    ) {
        notifyTo(
            context,
            "Busca automática de vídeos concluída",
            text,
            VideosActivity::class.java,
            id,
            CHANNEL_SEARCH_RESULTS,
        )
    }

    fun buildSearchProgress(
        context: Context,
        title: String,
        text: String,
    ): Notification =
        NotificationCompat.Builder(
            context,
            CHANNEL_SEARCH_PROGRESS,
        )
            .setSmallIcon(
                R.drawable.ic_stat_central,
            )
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(text),
            )
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(
                NotificationCompat.PRIORITY_LOW,
            )
            .build()

    private fun notifyTo(
        context: Context,
        title: String,
        text: String,
        destination: Class<out Activity>,
        id: Int,
        channel: String,
    ) {
        if (
            Build.VERSION.SDK_INT >=
            33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createChannels(context)

        val intent =
            Intent(
                context,
                destination,
            )
                .addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )

        val pending =
            PendingIntent.getActivity(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(
                context,
                channel,
            )
                .setSmallIcon(
                    R.drawable.ic_stat_central,
                )
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(text),
                )
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(
                    NotificationCompat.PRIORITY_DEFAULT,
                )
                .build()

        context.getSystemService(
            NotificationManager::class.java,
        )
            .notify(
                id,
                notification,
            )
    }
}
