package br.com.centralmidia.android.recording

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.NotificationHelper
import br.com.centralmidia.android.core.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@UnstableApi
class ScreenRecordService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var transformer: Transformer? = null
    private var rawFile: File? = null
    private var paused = false
    private var stopping = false
    private var showOverlay = true
    private var cropLeft = 0f
    private var cropTop = 0f
    private var cropRight = 1f
    private var cropBottom = 1f

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayTimer: TextView? = null
    private var overlayPause: Button? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val overlayTick = object : Runnable {
        override fun run() {
            updateOverlay()
            if (isRecording) mainHandler.postDelayed(this, 250L)
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_START -> startRecording(intent)
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        if (recorder != null || isRecording || isProcessing) return
        stopping = false; paused = false; lastError = ""; lastOutputUri = null; isProcessing = false
        try {
            val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            @Suppress("DEPRECATION")
            val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA) ?: error("Permissão de captura não recebida.")
            val requestedMic = intent.getBooleanExtra(EXTRA_MIC, false)
            val withMic = requestedMic && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val fps = intent.getIntExtra(EXTRA_FPS, 30).coerceIn(15, 60)
            val quality = intent.getStringExtra(EXTRA_QUALITY).orEmpty().ifBlank { "original" }
            showOverlay = intent.getBooleanExtra(EXTRA_SHOW_OVERLAY, true)
            cropLeft = intent.getFloatExtra(EXTRA_CROP_LEFT, 0f).coerceIn(0f, .98f)
            cropTop = intent.getFloatExtra(EXTRA_CROP_TOP, 0f).coerceIn(0f, .98f)
            cropRight = intent.getFloatExtra(EXTRA_CROP_RIGHT, 1f).coerceIn(cropLeft + .02f, 1f)
            cropBottom = intent.getFloatExtra(EXTRA_CROP_BOTTOM, 1f).coerceIn(cropTop + .02f, 1f)

            startForegroundRecorder(withMic)
            val dm = resources.displayMetrics
            var width = dm.widthPixels; var height = dm.heightPixels
            val longSide = maxOf(width, height)
            val wanted = when (quality) { "720" -> 1280; "1080" -> 1920; else -> longSide.coerceAtMost(2160) }
            if (longSide > wanted) {
                val factor = wanted.toFloat() / longSide.toFloat()
                width = (width * factor).roundToInt().coerceAtLeast(2); height = (height * factor).roundToInt().coerceAtLeast(2)
            }
            if (width % 2 != 0) width--; if (height % 2 != 0) height--
            val bitrate = when (quality) { "720" -> 5_000_000; "1080" -> 8_000_000; else -> 10_000_000 }
            val tempDir = File(cacheDir, "screen-recorder").apply { mkdirs() }
            rawFile = File(tempDir, "raw-${System.currentTimeMillis()}.mp4")

            recorder = createRecorder().apply {
                if (withMic) setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(bitrate); setVideoFrameRate(fps); setVideoSize(width, height)
                if (withMic) { setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setAudioEncodingBitRate(128_000); setAudioSamplingRate(44_100) }
                setOutputFile(rawFile!!.absolutePath); prepare()
            }

            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { mainHandler.post { if (isRecording && !stopping) stopRecording() } }
            }, mainHandler)
            display = projection?.createVirtualDisplay(
                "CentralRecorder", width, height, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder?.surface, null, null,
            ) ?: error("Não foi possível criar a tela virtual de gravação.")
            recorder?.start()

            synchronized(timerLock) {
                accumulatedElapsedMs = 0L
                activeSegmentStartedElapsed = SystemClock.elapsedRealtime()
                lastFinishedElapsedMs = 0L
                startedAt = System.currentTimeMillis()
            }
            isRecording = true; isPaused = false
            if (showOverlay) showFloatingWidget()
            updateNotification()
        } catch (error: Throwable) {
            lastError = error.message ?: "Falha ao iniciar a gravação de tela."
            releaseCaptureResources(); deleteQuietly(rawFile); rawFile = null; hideFloatingWidget(); finishServiceState()
        }
    }

    private fun startForegroundRecorder(withMic: Boolean) {
        var serviceType = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (withMic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), serviceType)
    }

    private fun pauseRecording() {
        if (!isRecording || paused || Build.VERSION.SDK_INT < 24) return
        runCatching { recorder?.pause() }.onSuccess {
            synchronized(timerLock) {
                if (activeSegmentStartedElapsed > 0L) accumulatedElapsedMs += SystemClock.elapsedRealtime() - activeSegmentStartedElapsed
                activeSegmentStartedElapsed = 0L
            }
            paused = true; isPaused = true; updateOverlay(); updateNotification()
        }.onFailure { lastError = it.message ?: "Não foi possível pausar." }
    }

    private fun resumeRecording() {
        if (!isRecording || !paused || Build.VERSION.SDK_INT < 24) return
        runCatching { recorder?.resume() }.onSuccess {
            synchronized(timerLock) { activeSegmentStartedElapsed = SystemClock.elapsedRealtime() }
            paused = false; isPaused = false; updateOverlay(); updateNotification()
        }.onFailure { lastError = it.message ?: "Não foi possível retomar." }
    }

    private fun stopRecording() {
        if (stopping || !isRecording) return
        stopping = true
        synchronized(timerLock) {
            if (!paused && activeSegmentStartedElapsed > 0L) accumulatedElapsedMs += SystemClock.elapsedRealtime() - activeSegmentStartedElapsed
            activeSegmentStartedElapsed = 0L; lastFinishedElapsedMs = accumulatedElapsedMs
        }
        val captured = rawFile
        runCatching { recorder?.stop() }.onFailure { lastError = it.message ?: "A gravação foi encerrada antes de gerar um arquivo válido." }
        releaseCaptureResources(); isRecording = false; isPaused = false; paused = false; hideFloatingWidget()
        if (captured == null || !captured.exists() || captured.length() <= 0L) {
            deleteQuietly(captured); rawFile = null; finishServiceState(); return
        }
        isProcessing = true; updateNotification("Finalizando o vídeo…")
        if (needsCrop()) cropAndPublish(captured) else Thread { publishAndFinish(captured, false) }.start()
    }

    private fun releaseCaptureResources() {
        runCatching { recorder?.reset() }; runCatching { recorder?.release() }; recorder = null
        runCatching { display?.release() }; display = null
        val localProjection = projection; projection = null; runCatching { localProjection?.stop() }
    }

    private fun needsCrop() = cropLeft > .005f || cropTop > .005f || cropRight < .995f || cropBottom < .995f

    private fun cropAndPublish(source: File) {
        val output = File(source.parentFile, "crop-${System.currentTimeMillis()}.mp4")
        val crop = Crop(-1f + 2f * cropLeft, -1f + 2f * cropRight, 1f - 2f * cropBottom, 1f - 2f * cropTop)
        val effects = Effects(emptyList<AudioProcessor>(), listOf<Effect>(crop))
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source))).setEffects(effects).build()
        try {
            transformer = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        transformer = null; Thread { publishAndFinish(output, true, source) }.start()
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        transformer = null; lastError = "Não foi possível recortar a área selecionada. A gravação completa foi preservada."
                        deleteQuietly(output); Thread { publishAndFinish(source, false) }.start()
                    }
                }).build()
            transformer?.start(edited, output.absolutePath)
        } catch (error: Throwable) {
            transformer = null; lastError = "Falha ao preparar o recorte. A gravação completa foi preservada."
            deleteQuietly(output); Thread { publishAndFinish(source, false) }.start()
        }
    }

    private fun publishAndFinish(file: File, cropped: Boolean, extraDelete: File? = null) {
        try {
            val uri = publishVideo(file); lastOutputUri = uri
            val detail = "MP4 • H.264 • ${if (cropped) "área personalizada" else "tela inteira"} • ${formatElapsed(lastFinishedElapsedMs)}"
            runCatching { CentralDb(this).addHistory("screen_record", "Gravação de tela", detail, uri.toString()) }
        } catch (error: Throwable) {
            lastError = error.message ?: "Não foi possível salvar a gravação."
        } finally {
            deleteQuietly(file); if (extraDelete != null && extraDelete != file) deleteQuietly(extraDelete)
            rawFile = null; mainHandler.post { finishServiceState() }
        }
    }

    private fun publishVideo(file: File): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Central-Gravacao-$stamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Central Inteligente de Midia/Gravacoes")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Não foi possível criar o vídeo final.")
        try {
            contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Não foi possível abrir o vídeo final.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (error: Throwable) {
            runCatching { contentResolver.delete(uri, null, null) }; throw error
        }
    }

    private fun finishServiceState() {
        isRecording = false; isPaused = false; isProcessing = false; paused = false; stopping = false
        synchronized(timerLock) { activeSegmentStartedElapsed = 0L; accumulatedElapsedMs = 0L; startedAt = 0L }
        hideFloatingWidget(); ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun buildNotification(forcedText: String? = null): android.app.Notification {
        val pauseAction = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        val pauseLabel = if (isPaused) "Retomar" else "Pausar"
        val pausePending = PendingIntent.getService(this, 3001, Intent(this, ScreenRecordService::class.java).setAction(pauseAction), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopPending = PendingIntent.getService(this, 3002, Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = forcedText ?: when { isProcessing -> "Finalizando o vídeo…"; isPaused -> "Pausado • ${formatElapsed(elapsedRecordingMs())}"; isRecording -> "Gravando • ${formatElapsed(elapsedRecordingMs())}"; else -> "Preparando a captura…" }
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_central).setContentTitle("Central • Gravador de Tela").setContentText(text)
            .setOngoing(isRecording || isProcessing).setOnlyAlertOnce(true)
            .addAction(0, pauseLabel, pausePending).addAction(0, "Parar", stopPending).build()
    }

    private fun updateNotification(forcedText: String? = null) {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(forcedText)) }
    }

    private fun showFloatingWidget() {
        if (!Settings.canDrawOverlays(this)) return
        hideFloatingWidget()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(12); y = dp(110) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(9), dp(7), dp(7), dp(7))
            background = rounded(Color.argb(244, 235, 246, 255), dp(18), Color.rgb(147, 195, 242)); elevation = dp(8).toFloat()
        }
        val drag = TextView(this).apply { text = "●  00:00:00"; textSize = 13f; setTextColor(Color.rgb(7, 49, 103)); gravity = Gravity.CENTER; setPadding(dp(4), 0, dp(7), 0) }
        overlayTimer = drag
        val pause = Button(this).apply {
            text = "Ⅱ"; textSize = 16f; setTextColor(Color.rgb(10, 72, 145)); minWidth = 0; minHeight = 0; setPadding(dp(7), 0, dp(7), 0)
            background = rounded(Color.WHITE, dp(12), Color.rgb(190, 218, 246)); setOnClickListener { if (isPaused) resumeRecording() else pauseRecording() }
        }
        overlayPause = pause
        val stop = Button(this).apply {
            text = "■"; textSize = 15f; setTextColor(Color.rgb(210, 35, 86)); minWidth = 0; minHeight = 0; setPadding(dp(7), 0, dp(7), 0)
            background = rounded(Color.rgb(255, 239, 244), dp(12), Color.rgb(246, 177, 198)); setOnClickListener { stopRecording() }
        }
        root.addView(drag, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
        root.addView(pause, LinearLayout.LayoutParams(dp(46), dp(40)).apply { marginEnd = dp(5) }); root.addView(stop, LinearLayout.LayoutParams(dp(46), dp(40)))
        var touchX = 0f; var touchY = 0f; var startX = 0; var startY = 0
        drag.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { touchX = event.rawX; touchY = event.rawY; startX = params.x; startY = params.y; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX + (touchX - event.rawX).roundToInt()).coerceAtLeast(0)
                    params.y = (startY + (event.rawY - touchY).roundToInt()).coerceAtLeast(0)
                    runCatching { wm.updateViewLayout(root, params) }; true
                }
                else -> false
            }
        }
        runCatching {
            wm.addView(root, params); windowManager = wm; overlayView = root; updateOverlay()
            mainHandler.removeCallbacks(overlayTick); mainHandler.post(overlayTick)
        }
    }

    private fun updateOverlay() {
        overlayTimer?.text = if (isPaused) "●  ${formatElapsed(elapsedRecordingMs())}  PAUSADO" else "●  ${formatElapsed(elapsedRecordingMs())}"
        overlayPause?.text = if (isPaused) "▶" else "Ⅱ"
    }

    private fun hideFloatingWidget() {
        mainHandler.removeCallbacks(overlayTick)
        val view = overlayView; val wm = windowManager
        if (view != null && wm != null) runCatching { wm.removeView(view) }
        overlayView = null; overlayTimer = null; overlayPause = null; windowManager = null
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = radius.toFloat(); setStroke(dp(1), stroke) }
    private fun deleteQuietly(file: File?) { if (file != null) runCatching { file.delete() } }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()

    override fun onDestroy() {
        hideFloatingWidget(); runCatching { transformer?.cancel() }
        if (isRecording && !stopping) runCatching { stopRecording() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "central.record.START"
        const val ACTION_STOP = "central.record.STOP"
        const val ACTION_PAUSE = "central.record.PAUSE"
        const val ACTION_RESUME = "central.record.RESUME"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "data"
        const val EXTRA_MIC = "mic"
        const val EXTRA_FPS = "fps"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_SHOW_OVERLAY = "show_overlay"
        const val EXTRA_CROP_LEFT = "crop_left"
        const val EXTRA_CROP_TOP = "crop_top"
        const val EXTRA_CROP_RIGHT = "crop_right"
        const val EXTRA_CROP_BOTTOM = "crop_bottom"
        private const val NOTIFICATION_ID = 991
        private val timerLock = Any()

        @Volatile
        var isRecording = false
            private set

        @Volatile
        var isPaused = false
            private set

        @Volatile
        var isProcessing = false
            private set

        @Volatile
        var startedAt = 0L
            private set

        @Volatile
        var lastError = ""
            private set

        @Volatile
        var lastOutputUri: Uri? = null
            private set
        @Volatile private var accumulatedElapsedMs = 0L
        @Volatile private var activeSegmentStartedElapsed = 0L
        @Volatile private var lastFinishedElapsedMs = 0L

        fun elapsedRecordingMs(): Long = synchronized(timerLock) {
            when {
                isRecording && !isPaused && activeSegmentStartedElapsed > 0L -> accumulatedElapsedMs + (SystemClock.elapsedRealtime() - activeSegmentStartedElapsed)
                isRecording -> accumulatedElapsedMs
                isProcessing -> lastFinishedElapsedMs
                else -> 0L
            }
        }

        private fun formatElapsed(value: Long): String {
            val seconds = value.coerceAtLeast(0L) / 1000L
            return "%02d:%02d:%02d".format(seconds / 3600L, (seconds / 60L) % 60L, seconds % 60L)
        }
    }
}
