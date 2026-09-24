package br.com.centralmidia.android

import android.app.Application
import br.com.centralmidia.android.core.NotificationHelper
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class CentralApp : Application() {
    override fun onCreate() {
        super.onCreate()

        NotificationHelper.createChannels(this)

        runCatching {
            YoutubeDL.getInstance().init(applicationContext)
        }

        runCatching {
            FFmpeg.getInstance().init(applicationContext)
        }
    }
}
