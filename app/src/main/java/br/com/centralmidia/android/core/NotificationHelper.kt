package br.com.centralmidia.android.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import br.com.centralmidia.android.R
import br.com.centralmidia.android.ui.MainActivity

object NotificationHelper {
    const val CHANNEL_MONITOR = "central_monitor"
    const val CHANNEL_RECORDING = "central_recording"

    fun createChannels(context: Context) {
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_MONITOR,"Monitoramento",NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_RECORDING,"Gravação de tela",NotificationManager.IMPORTANCE_LOW))
    }

    fun notify(context:Context, title:String, text:String, id:Int=(System.currentTimeMillis()%100000).toInt()) {
        val pi=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_central).setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(pi).build()
        context.getSystemService(NotificationManager::class.java).notify(id,n)
    }
}
