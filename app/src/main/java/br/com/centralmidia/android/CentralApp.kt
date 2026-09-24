package br.com.centralmidia.android

import android.app.Application
import br.com.centralmidia.android.core.NotificationHelper
import dev.ffmpegkit_maintained.ytdlp.YtDlp

class CentralApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        runCatching { YtDlp.init(this) }
    }
}
