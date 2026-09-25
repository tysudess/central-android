package br.com.centralmidia.android

import android.app.Application
import br.com.centralmidia.android.automation.MonitoringScheduler
import br.com.centralmidia.android.core.NotificationHelper
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class CentralApp : Application() {
    override fun onCreate() {
        super.onCreate()

        NotificationHelper.createChannels(this)

        // Reconfirma as rotinas sempre que o processo do app volta a existir.
        // Isso complementa o BOOT_COMPLETED e a persistência do WorkManager.
        runCatching {
            MonitoringScheduler.apply(this)
        }

        runCatching {
            YoutubeDL.getInstance()
                .init(
                    applicationContext,
                )
        }

        runCatching {
            FFmpeg.getInstance()
                .init(
                    applicationContext,
                )
        }
    }
}
