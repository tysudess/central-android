package br.com.centralmidia.android.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.centralmidia.android.core.NotificationHelper

/**
 * Reaplica as automações depois que o Android termina de iniciar ou depois
 * que o APK é atualizado. O WorkManager já persiste trabalho periódico, mas
 * esta camada torna a intenção explícita e recupera a agenda caso o OEM a
 * tenha descartado.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                NotificationHelper.createChannels(context)
                MonitoringScheduler.apply(context)
            }
        }
    }
}
