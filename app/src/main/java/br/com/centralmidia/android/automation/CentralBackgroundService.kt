package br.com.centralmidia.android.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import br.com.centralmidia.android.R
import br.com.centralmidia.android.ui.MainActivity

/**
 * Mantém o processo da Central em primeiro plano do ponto de vista do Android
 * enquanto o usuário usa o aplicativo e permite que operações iniciadas nas
 * abas continuem com o display desligado.
 *
 * O serviço mostra uma notificação permanente e pode ser encerrado pelo usuário.
 * O WakeLock parcial é usado somente enquanto este modo está ativo; por isso,
 * o consumo de bateria será maior. O Android ainda pode aplicar restrições de
 * rede/Doze e o usuário pode forçar a parada do aplicativo.
 */
class CentralBackgroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseWakeLock()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                foregroundServiceType(),
            )
            acquireWakeLock()
        } catch (error: Exception) {
            // Se o sistema recusar o serviço, encerramos sem deixar WakeLock preso.
            releaseWakeLock()
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:CentralBackgroundWork",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Operações em segundo plano",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Indica que a Central está mantendo operações ativas com a tela apagada."
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_central)
        .setContentTitle("Central ativa em segundo plano")
        .setContentText("As operações iniciadas podem continuar com a tela apagada.")
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                OPEN_REQUEST_CODE,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            R.drawable.ic_stat_central,
            "Desativar",
            PendingIntent.getService(
                this,
                STOP_REQUEST_CODE,
                Intent(this, CentralBackgroundService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .build()

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "central-background-operations"
        private const val NOTIFICATION_ID = 23_410
        private const val OPEN_REQUEST_CODE = 23_411
        private const val STOP_REQUEST_CODE = 23_412
        private const val ACTION_STOP = "br.com.centralmidia.android.action.STOP_BACKGROUND_MODE"

        fun start(context: Context) {
            val intent = Intent(context, CentralBackgroundService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, CentralBackgroundService::class.java))
            }
        }
    }
}
