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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
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
import br.com.centralmidia.android.ui.CaptureAreaActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class ScreenRecordService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var transformer: Transformer? = null
    private var rawFile: File? = null

    private var paused = false
    private var stopping = false

    private var cropLeft = 0f
    private var cropTop = 0f
    private var cropRight = 1f
    private var cropBottom = 1f

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayTimer: TextView? = null
    private var overlayArea: Button? = null
    private var overlayPrimary: Button? = null
    private var overlayStop: Button? = null

    private var selectorWindowManager: WindowManager? = null
    private var selectorOverlay: View? = null
    private var selectorAreaView: AreaSelectionView? = null

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val overlayTick = object : Runnable {
        override fun run() {
            updateOverlay()
            if (overlayView != null) {
                mainHandler.postDelayed(this, 250L)
            }
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENABLE_WIDGET -> enableWidget()
            ACTION_DISABLE_WIDGET -> disableWidget()
            ACTION_BEGIN_CAPTURE -> beginCaptureFlow()
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_START -> startRecording(intent)
        }
        return START_NOT_STICKY
    }

    private fun enableWidget() {
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        isWidgetEnabled = true
        if (Settings.canDrawOverlays(this)) {
            showFloatingWidget()
        }
    }

    private fun disableWidget() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        isWidgetEnabled = false
        if (isRecording) {
            stopRecording()
        } else {
            hideFloatingWidget()
            stopSelf()
        }
    }

    private fun startRecording(intent: Intent) {
        if (recorder != null || isRecording || isProcessing) return

        stopping = false
        paused = false
        lastError = ""
        lastOutputUri = null
        isProcessing = false

        try {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            @Suppress("DEPRECATION")
            val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                ?: error("Permissão de captura não recebida.")

            val requestedMic = intent.getBooleanExtra(EXTRA_MIC, false)
            val withMic = requestedMic &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val fps = intent.getIntExtra(EXTRA_FPS, 30).coerceIn(15, 60)
            val quality = intent.getStringExtra(EXTRA_QUALITY).orEmpty().ifBlank { "original" }

            cropLeft = intent.getFloatExtra(EXTRA_CROP_LEFT, 0f).coerceIn(0f, .98f)
            cropTop = intent.getFloatExtra(EXTRA_CROP_TOP, 0f).coerceIn(0f, .98f)
            cropRight = intent.getFloatExtra(EXTRA_CROP_RIGHT, 1f).coerceIn(cropLeft + .02f, 1f)
            cropBottom = intent.getFloatExtra(EXTRA_CROP_BOTTOM, 1f).coerceIn(cropTop + .02f, 1f)

            isWidgetEnabled = prefs.getBoolean(KEY_ENABLED, true)
            startForegroundRecorder(withMic)

            val screenWindowManager = getSystemService(WindowManager::class.java)
            val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                screenWindowManager.maximumWindowMetrics.bounds
            } else {
                @Suppress("DEPRECATION")
                android.graphics.Rect(
                    0,
                    0,
                    resources.displayMetrics.widthPixels,
                    resources.displayMetrics.heightPixels,
                )
            }

            var width = bounds.width().coerceAtLeast(2)
            var height = bounds.height().coerceAtLeast(2)
            val longSide = maxOf(width, height)
            val wanted = when (quality) {
                "720" -> 1280
                "1080" -> 1920
                else -> longSide.coerceAtMost(2160)
            }

            if (longSide > wanted) {
                val factor = wanted.toFloat() / longSide.toFloat()
                width = (width * factor).roundToInt().coerceAtLeast(2)
                height = (height * factor).roundToInt().coerceAtLeast(2)
            }
            if (width % 2 != 0) width--
            if (height % 2 != 0) height--

            val bitrate = when (quality) {
                "720" -> 5_000_000
                "1080" -> 8_000_000
                else -> 10_000_000
            }

            val tempDir = File(cacheDir, "screen-recorder").apply { mkdirs() }
            rawFile = File(tempDir, "raw-${System.currentTimeMillis()}.mp4")

            recorder = createRecorder().apply {
                if (withMic) setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(bitrate)
                setVideoFrameRate(fps)
                setVideoSize(width, height)
                if (withMic) {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128_000)
                    setAudioSamplingRate(44_100)
                }
                setOutputFile(rawFile!!.absolutePath)
                prepare()
            }

            projection = getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(resultCode, resultData)

            projection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        mainHandler.post {
                            if (isRecording && !stopping) stopRecording()
                        }
                    }
                },
                mainHandler,
            )

            display = projection?.createVirtualDisplay(
                "CentralRecorder",
                width,
                height,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder?.surface,
                null,
                null,
            ) ?: error("Não foi possível criar a tela virtual de gravação.")

            recorder?.start()

            synchronized(timerLock) {
                accumulatedElapsedMs = 0L
                activeSegmentStartedElapsed = SystemClock.elapsedRealtime()
                lastFinishedElapsedMs = 0L
                startedAt = System.currentTimeMillis()
            }

            isRecording = true
            isPaused = false

            var widgetVisibleDuringCapture = false

            if (
                isWidgetEnabled &&
                Settings.canDrawOverlays(this) &&
                needsCrop()
            ) {
                widgetVisibleDuringCapture =
                    showFloatingWidget(
                        placeOutsideCapture = true,
                    )

                if (!widgetVisibleDuringCapture) {
                    hideFloatingWidget()
                }
            } else {
                // Em tela inteira o Android não oferece exclusão pública de um
                // overlay da MediaProjection sem gerar artefatos. Por isso o
                // widget some enquanto grava e os controles ficam na notificação.
                hideFloatingWidget()
            }

            if (needsCrop() && !widgetVisibleDuringCapture) {
                updateNotification(
                    "Gravando área • pausar/parar pela notificação",
                )
            } else {
                updateNotification()
            }
        } catch (error: Throwable) {
            lastError = error.message ?: "Falha ao iniciar a gravação de tela."
            releaseCaptureResources()
            deleteQuietly(rawFile)
            rawFile = null
            finishForegroundOnly()
            if (isWidgetEnabled && Settings.canDrawOverlays(this)) {
                showFloatingWidget()
            } else {
                hideFloatingWidget()
            }
        }
    }

    private fun startForegroundRecorder(withMic: Boolean) {
        var serviceType = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (withMic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), serviceType)
    }

    private fun pauseRecording() {
        if (!isRecording || paused || Build.VERSION.SDK_INT < 24) return
        runCatching { recorder?.pause() }
            .onSuccess {
                synchronized(timerLock) {
                    if (activeSegmentStartedElapsed > 0L) {
                        accumulatedElapsedMs += SystemClock.elapsedRealtime() - activeSegmentStartedElapsed
                    }
                    activeSegmentStartedElapsed = 0L
                }
                paused = true
                isPaused = true
                updateOverlay()
                updateNotification()
            }
            .onFailure { lastError = it.message ?: "Não foi possível pausar." }
    }

    private fun resumeRecording() {
        if (!isRecording || !paused || Build.VERSION.SDK_INT < 24) return
        runCatching { recorder?.resume() }
            .onSuccess {
                synchronized(timerLock) {
                    activeSegmentStartedElapsed = SystemClock.elapsedRealtime()
                }
                paused = false
                isPaused = false
                updateOverlay()
                updateNotification()
            }
            .onFailure { lastError = it.message ?: "Não foi possível retomar." }
    }

    private fun stopRecording() {
        if (stopping || !isRecording) return
        stopping = true

        synchronized(timerLock) {
            if (!paused && activeSegmentStartedElapsed > 0L) {
                accumulatedElapsedMs += SystemClock.elapsedRealtime() - activeSegmentStartedElapsed
            }
            activeSegmentStartedElapsed = 0L
            lastFinishedElapsedMs = accumulatedElapsedMs
        }

        val captured = rawFile
        runCatching { recorder?.stop() }
            .onFailure { lastError = it.message ?: "A gravação terminou antes de gerar um arquivo válido." }

        releaseCaptureResources()
        isRecording = false
        isPaused = false
        paused = false

        if (captured == null || !captured.exists() || captured.length() <= 0L) {
            deleteQuietly(captured)
            rawFile = null
            finishAfterCapture()
            return
        }

        isProcessing = true
        updateOverlay()
        updateNotification("Finalizando o vídeo…")

        if (needsCrop()) {
            cropAndPublish(captured)
        } else {
            Thread { publishAndFinish(captured, false) }.start()
        }
    }

    private fun releaseCaptureResources() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { display?.release() }
        display = null
        val localProjection = projection
        projection = null
        runCatching { localProjection?.stop() }
    }

    private fun needsCrop(): Boolean =
        cropLeft > .005f || cropTop > .005f || cropRight < .995f || cropBottom < .995f

    private fun cropAndPublish(source: File) {
        val output = File(source.parentFile, "crop-${System.currentTimeMillis()}.mp4")
        val crop = Crop(
            -1f + 2f * cropLeft,
            -1f + 2f * cropRight,
            1f - 2f * cropBottom,
            1f - 2f * cropTop,
        )
        val effects = Effects(
            emptyList<AudioProcessor>(),
            listOf<Effect>(crop),
        )
        val edited = EditedMediaItem.Builder(
            MediaItem.fromUri(Uri.fromFile(source)),
        ).setEffects(effects).build()

        try {
            transformer = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            transformer = null
                            Thread { publishAndFinish(output, true, source) }.start()
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            transformer = null
                            lastError = "Não foi possível recortar a área selecionada. A gravação completa foi preservada."
                            deleteQuietly(output)
                            Thread { publishAndFinish(source, false) }.start()
                        }
                    },
                )
                .build()
            transformer?.start(edited, output.absolutePath)
        } catch (error: Throwable) {
            transformer = null
            lastError = "Falha ao preparar o recorte. A gravação completa foi preservada."
            deleteQuietly(output)
            Thread { publishAndFinish(source, false) }.start()
        }
    }

    private fun publishAndFinish(file: File, cropped: Boolean, extraDelete: File? = null) {
        try {
            val uri = publishVideo(file)
            lastOutputUri = uri
            val detail = buildString {
                append("MP4 • H.264 • ")
                append(if (cropped) "área personalizada" else "tela inteira")
                append(" • ")
                append(formatElapsed(lastFinishedElapsedMs))
            }
            runCatching {
                CentralDb(this).addHistory(
                    "screen_record",
                    "Gravação de tela",
                    detail,
                    uri.toString(),
                )
            }
        } catch (error: Throwable) {
            lastError = error.message ?: "Não foi possível salvar a gravação."
        } finally {
            deleteQuietly(file)
            if (extraDelete != null && extraDelete != file) deleteQuietly(extraDelete)
            rawFile = null
            mainHandler.post { finishAfterCapture() }
        }
    }

    private fun publishVideo(file: File): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Central-Gravacao-$stamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Central Inteligente de Midia/Gravacoes")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Não foi possível criar o vídeo final.")

        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: error("Não foi possível abrir o vídeo final.")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            return uri
        } catch (error: Throwable) {
            runCatching { contentResolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun finishAfterCapture() {
        isRecording = false
        isPaused = false
        isProcessing = false
        paused = false
        stopping = false

        synchronized(timerLock) {
            activeSegmentStartedElapsed = 0L
            accumulatedElapsedMs = 0L
            startedAt = 0L
        }

        finishForegroundOnly()
        isWidgetEnabled = prefs.getBoolean(KEY_ENABLED, false)

        if (isWidgetEnabled && Settings.canDrawOverlays(this)) {
            showFloatingWidget()
        } else {
            hideFloatingWidget()
            stopSelf()
        }
    }

    private fun finishForegroundOnly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(forcedText: String? = null): android.app.Notification {
        val pauseAction = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        val pauseLabel = if (isPaused) "Retomar" else "Pausar"
        val pausePending = PendingIntent.getService(
            this,
            3001,
            Intent(this, ScreenRecordService::class.java).setAction(pauseAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPending = PendingIntent.getService(
            this,
            3002,
            Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = forcedText ?: when {
            isProcessing -> "Finalizando o vídeo…"
            isPaused -> "Pausado • ${formatElapsed(elapsedRecordingMs())}"
            isRecording -> "Gravando • ${formatElapsed(elapsedRecordingMs())}"
            else -> "Preparando a captura…"
        }

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_central)
            .setContentTitle("Central • Gravador de Tela")
            .setContentText(text)
            .setOngoing(isRecording || isProcessing)
            .setOnlyAlertOnce(true)
            .addAction(0, pauseLabel, pausePending)
            .addAction(0, "Parar", stopPending)
            .build()
    }

    private fun updateNotification(forcedText: String? = null) {
        if (!isRecording && !isProcessing) return
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(forcedText))
        }
    }


    /**
     * Mostra o controle flutuante.
     *
     * Em gravação de tela inteira ele não é exibido: qualquer overlay normal
     * seria capturado e FLAG_SECURE produziria o retângulo preto que vimos.
     *
     * Em área personalizada o widget só é mantido quando cabe completamente
     * fora do retângulo selecionado. Como o arquivo final é recortado para a
     * área escolhida, o widget continua visível para o usuário sem entrar no
     * vídeo final.
     */
    private fun showFloatingWidget(
        placeOutsideCapture: Boolean = false,
    ): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            return false
        }

        hideFloatingWidget()

        val wm =
            getSystemService(
                WINDOW_SERVICE,
            ) as WindowManager

        val type =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val position =
            if (placeOutsideCapture) {
                calculateWidgetPositionOutsideCapture()
                    ?: return false
            } else {
                Pair(
                    dp(12),
                    dp(105),
                )
            }

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
                .apply {
                    gravity =
                        Gravity.TOP or
                            Gravity.START

                    x =
                        position.first

                    y =
                        position.second
                }

        val root =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(8),
                    dp(6),
                    dp(6),
                    dp(6),
                )

                background =
                    rounded(
                        Color.argb(
                            248,
                            232,
                            244,
                            255,
                        ),
                        dp(17),
                        Color.rgb(
                            143,
                            192,
                            241,
                        ),
                    )

                elevation =
                    dp(8).toFloat()
            }

        val drag =
            TextView(this).apply {
                text =
                    "●  PRONTO"

                textSize =
                    11.8f

                setTextColor(
                    Color.rgb(
                        7,
                        49,
                        103,
                    ),
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(3),
                    0,
                    dp(6),
                    0,
                )
            }

        overlayTimer =
            drag

        val areaButton =
            Button(this).apply {
                text =
                    if (
                        prefs.getBoolean(
                            KEY_AREA_SELECTED,
                            false,
                        )
                    ) {
                        "ÁREA ✓"
                    } else {
                        "ÁREA"
                    }

                textSize =
                    10.8f

                isAllCaps =
                    false

                setTextColor(
                    Color.rgb(
                        12,
                        94,
                        184,
                    ),
                )

                minWidth =
                    0

                minHeight =
                    0

                setPadding(
                    dp(5),
                    0,
                    dp(5),
                    0,
                )

                background =
                    rounded(
                        Color.WHITE,
                        dp(11),
                        Color.rgb(
                            174,
                            207,
                            240,
                        ),
                    )

                setOnClickListener {
                    if (
                        !isRecording &&
                        !isProcessing
                    ) {
                        showAreaSelectorOverlay(
                            startAfterSelection = false,
                        )
                    }
                }
            }

        overlayArea =
            areaButton

        val primary =
            Button(this).apply {
                text =
                    "REC"

                textSize =
                    11.5f

                setTextColor(
                    Color.WHITE,
                )

                minWidth =
                    0

                minHeight =
                    0

                setPadding(
                    dp(6),
                    0,
                    dp(6),
                    0,
                )

                background =
                    rounded(
                        Color.rgb(
                            232,
                            38,
                            91,
                        ),
                        dp(11),
                        Color.rgb(
                            232,
                            38,
                            91,
                        ),
                    )

                setOnClickListener {
                    when {
                        isProcessing ->
                            Unit

                        isRecording &&
                        isPaused ->
                            resumeRecording()

                        isRecording ->
                            pauseRecording()

                        else ->
                            beginCaptureFlow()
                    }
                }
            }

        overlayPrimary =
            primary

        val stop =
            Button(this).apply {
                text =
                    "×"

                textSize =
                    17f

                setTextColor(
                    Color.rgb(
                        190,
                        25,
                        70,
                    ),
                )

                minWidth =
                    0

                minHeight =
                    0

                setPadding(
                    dp(5),
                    0,
                    dp(5),
                    0,
                )

                background =
                    rounded(
                        Color.WHITE,
                        dp(11),
                        Color.rgb(
                            211,
                            225,
                            241,
                        ),
                    )

                setOnClickListener {
                    if (isRecording) {
                        stopRecording()
                    } else if (!isProcessing) {
                        prefs.edit()
                            .putBoolean(
                                KEY_ENABLED,
                                false,
                            )
                            .apply()

                        isWidgetEnabled =
                            false

                        hideFloatingWidget()
                        stopSelf()
                    }
                }
            }

        overlayStop =
            stop

        root.addView(
            drag,
            LinearLayout.LayoutParams(
                dp(102),
                dp(38),
            ),
        )

        root.addView(
            areaButton,
            LinearLayout.LayoutParams(
                dp(62),
                dp(38),
            ).apply {
                marginEnd =
                    dp(4)
            },
        )

        root.addView(
            primary,
            LinearLayout.LayoutParams(
                dp(48),
                dp(38),
            ).apply {
                marginEnd =
                    dp(4)
            },
        )

        root.addView(
            stop,
            LinearLayout.LayoutParams(
                dp(40),
                dp(38),
            ),
        )

        var touchX =
            0f

        var touchY =
            0f

        var startX =
            0

        var startY =
            0

        drag.setOnTouchListener {
                _,
                event,
            ->
            when (
                event.actionMasked
            ) {
                MotionEvent.ACTION_DOWN -> {
                    touchX =
                        event.rawX

                    touchY =
                        event.rawY

                    startX =
                        params.x

                    startY =
                        params.y

                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x =
                        (
                            startX +
                                (
                                    event.rawX -
                                        touchX
                                    )
                                    .roundToInt()
                            )
                            .coerceAtLeast(
                                0,
                            )

                    params.y =
                        (
                            startY +
                                (
                                    event.rawY -
                                        touchY
                                    )
                                    .roundToInt()
                            )
                            .coerceAtLeast(
                                0,
                            )

                    runCatching {
                        wm.updateViewLayout(
                            root,
                            params,
                        )
                    }

                    true
                }

                else ->
                    false
            }
        }

        return runCatching {
            wm.addView(
                root,
                params,
            )

            windowManager =
                wm

            overlayView =
                root

            updateOverlay()

            mainHandler.removeCallbacks(
                overlayTick,
            )

            mainHandler.post(
                overlayTick,
            )

            true
        }
            .getOrDefault(
                false,
            )
    }

    private fun calculateWidgetPositionOutsideCapture():
        Pair<Int, Int>? =
        calculateWidgetPositionOutsideRegion(
            AreaRegion(
                cropLeft,
                cropTop,
                cropRight,
                cropBottom,
            ),
        )

    private fun calculateWidgetPositionOutsideRegion(
        region: AreaRegion,
    ): Pair<Int, Int>? {
        val wm =
            getSystemService(
                WindowManager::class.java,
            )

        val bounds =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.R
            ) {
                wm.maximumWindowMetrics
                    .bounds
            } else {
                @Suppress("DEPRECATION")
                android.graphics.Rect(
                    0,
                    0,
                    resources.displayMetrics
                        .widthPixels,
                    resources.displayMetrics
                        .heightPixels,
                )
            }

        val screenW =
            bounds.width()

        val screenH =
            bounds.height()

        // Durante a gravação o botão ÁREA fica oculto, portanto o controle
        // real é menor do que o widget em repouso. Usar essa medida aumenta a
        // chance de mantê-lo fora do recorte sem invadir a área capturada.
        val widgetW =
            dp(178)

        val widgetH =
            dp(48)

        val gap =
            dp(10)

        val leftPx =
            (
                region.left *
                    screenW
                )
                .roundToInt()

        val topPx =
            (
                region.top *
                    screenH
                )
                .roundToInt()

        val rightPx =
            (
                region.right *
                    screenW
                )
                .roundToInt()

        val bottomPx =
            (
                region.bottom *
                    screenH
                )
                .roundToInt()

        val topSpace =
            topPx

        val bottomSpace =
            screenH -
                bottomPx

        val leftSpace =
            leftPx

        val rightSpace =
            screenW -
                rightPx

        return when {
            topSpace >=
                widgetH +
                    gap *
                    2 ->
                Pair(
                    (
                        screenW -
                            widgetW -
                            gap
                        )
                        .coerceAtLeast(
                            gap,
                        ),
                    gap,
                )

            bottomSpace >=
                widgetH +
                    gap *
                    2 ->
                Pair(
                    (
                        screenW -
                            widgetW -
                            gap
                        )
                        .coerceAtLeast(
                            gap,
                        ),
                    (
                        screenH -
                            widgetH -
                            gap
                        )
                        .coerceAtLeast(
                            gap,
                        ),
                )

            leftSpace >=
                widgetW +
                    gap *
                    2 ->
                Pair(
                    gap,
                    (
                        topPx +
                            (
                                bottomPx -
                                    topPx -
                                    widgetH
                                ) /
                            2
                        )
                        .coerceIn(
                            gap,
                            (
                                screenH -
                                    widgetH -
                                    gap
                                )
                                .coerceAtLeast(
                                    gap,
                                ),
                        ),
                )

            rightSpace >=
                widgetW +
                    gap *
                    2 ->
                Pair(
                    (
                        screenW -
                            widgetW -
                            gap
                        )
                        .coerceAtLeast(
                            gap,
                        ),
                    (
                        topPx +
                            (
                                bottomPx -
                                    topPx -
                                    widgetH
                                ) /
                            2
                        )
                        .coerceIn(
                            gap,
                            (
                                screenH -
                                    widgetH -
                                    gap
                                )
                                .coerceAtLeast(
                                    gap,
                                ),
                        ),
                )

            else ->
                null
        }
    }

    private fun beginCaptureFlow() {
        if (
            isRecording ||
            isProcessing
        ) {
            return
        }

        val custom =
            prefs.getInt(
                KEY_AREA_MODE,
                0,
            ) ==
            1

        if (custom) {
            val hasSavedArea =
                prefs.getBoolean(
                    KEY_AREA_SELECTED,
                    false,
                )

            if (hasSavedArea) {
                launchCaptureController()
            } else {
                showAreaSelectorOverlay(
                    startAfterSelection = true,
                )
            }
        } else {
            prefs.edit()
                .putBoolean(
                    KEY_AREA_SELECTED,
                    false,
                )
                .apply()

            savePendingCrop(
                0f,
                0f,
                1f,
                1f,
            )

            launchCaptureController()
        }
    }

    /**
     * Seleção de área como overlay real do sistema, antes da MediaProjection.
     * Assim o usuário marca a região sobre o aplicativo que realmente quer
     * gravar. O overlay é removido antes da autorização e nunca entra no vídeo.
     */
    private fun showAreaSelectorOverlay(
        startAfterSelection: Boolean = true,
    ) {
        if (
            !Settings.canDrawOverlays(
                this,
            )
        ) {
            lastError =
                "Autorize a Central a aparecer sobre outros aplicativos."
            return
        }

        hideFloatingWidget()
        hideAreaSelectorOverlay()

        val wm =
            getSystemService(
                WINDOW_SERVICE,
            ) as WindowManager

        val type =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
                .apply {
                    gravity =
                        Gravity.TOP or
                            Gravity.START
                }

        val root =
            FrameLayout(this).apply {
                setBackgroundColor(
                    Color.TRANSPARENT,
                )
            }

        val area =
            AreaSelectionView(
                this,
            )

        selectorAreaView =
            area

        root.addView(
            area,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val hint =
            TextView(this).apply {
                text =
                    "Selecione a área da gravação\nArraste a moldura ou os cantos\nSe não houver espaço para o widget, os controles ficarão na notificação."

                textSize =
                    14f

                setTextColor(
                    Color.rgb(
                        7,
                        49,
                        103,
                    ),
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(12),
                    dp(9),
                    dp(12),
                    dp(9),
                )

                background =
                    rounded(
                        Color.argb(
                            246,
                            245,
                            250,
                            255,
                        ),
                        dp(14),
                        Color.rgb(
                            183,
                            211,
                            240,
                        ),
                    )
            }

        root.addView(
            hint,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ).apply {
                leftMargin =
                    dp(12)

                rightMargin =
                    dp(12)

                topMargin =
                    dp(26)
            },
        )

        val actions =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(7),
                    dp(7),
                    dp(7),
                    dp(7),
                )

                background =
                    rounded(
                        Color.argb(
                            250,
                            255,
                            255,
                            255,
                        ),
                        dp(16),
                        Color.rgb(
                            190,
                            216,
                            243,
                        ),
                    )
            }

        fun actionButton(
            label: String,
            fill: Int,
            textColor: Int,
            onClick: () -> Unit,
        ): Button =
            Button(this).apply {
                text =
                    label

                isAllCaps =
                    false

                textSize =
                    11.5f

                setTextColor(
                    textColor,
                )

                minHeight =
                    0

                minWidth =
                    0

                setPadding(
                    dp(7),
                    0,
                    dp(7),
                    0,
                )

                background =
                    rounded(
                        fill,
                        dp(11),
                        if (
                            fill ==
                            Color.WHITE
                        ) {
                            Color.rgb(
                                202,
                                221,
                                242,
                            )
                        } else {
                            fill
                        },
                    )

                setOnClickListener {
                    onClick()
                }
            }

        actions.addView(
            actionButton(
                "Cancelar",
                Color.WHITE,
                Color.rgb(
                    190,
                    25,
                    70,
                ),
            ) {
                hideAreaSelectorOverlay()

                if (
                    isWidgetEnabled
                ) {
                    showFloatingWidget()
                }
            },
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f,
            ).apply {
                marginEnd =
                    dp(3)
            },
        )

        actions.addView(
            actionButton(
                "Tela inteira",
                Color.WHITE,
                Color.rgb(
                    7,
                    49,
                    103,
                ),
            ) {
                prefs.edit()
                    .putInt(
                        KEY_AREA_MODE,
                        0,
                    )
                    .putBoolean(
                        KEY_AREA_SELECTED,
                        false,
                    )
                    .apply()

                savePendingCrop(
                    0f,
                    0f,
                    1f,
                    1f,
                )

                hideAreaSelectorOverlay()

                if (startAfterSelection) {
                    launchCaptureController()
                } else if (
                    isWidgetEnabled
                ) {
                    showFloatingWidget()
                }
            },
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f,
            ).apply {
                marginStart =
                    dp(3)

                marginEnd =
                    dp(3)
            },
        )

        actions.addView(
            actionButton(
                "Usar área",
                Color.rgb(
                    20,
                    126,
                    246,
                ),
                Color.WHITE,
            ) {
                val region =
                    selectorAreaView
                        ?.normalizedRegion()
                        ?: AreaRegion(
                            0f,
                            0f,
                            1f,
                            1f,
                        )

                savePendingCrop(
                    region.left,
                    region.top,
                    region.right,
                    region.bottom,
                )

                prefs.edit()
                    .putInt(
                        KEY_AREA_MODE,
                        1,
                    )
                    .putBoolean(
                        KEY_AREA_SELECTED,
                        true,
                    )
                    .apply()

                hideAreaSelectorOverlay()
                lastError = ""

                if (startAfterSelection) {
                    launchCaptureController()
                } else if (
                    isWidgetEnabled
                ) {
                    // A seleção feita pelo botão ÁREA do widget deve sempre
                    // devolver o controle flutuante ao usuário. O encaixe fora
                    // da área só é exigido depois que a gravação realmente começa.
                    showFloatingWidget()
                }
            },
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f,
            ).apply {
                marginStart =
                    dp(3)
            },
        )

        root.addView(
            actions,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply {
                leftMargin =
                    dp(10)

                rightMargin =
                    dp(10)

                bottomMargin =
                    dp(28)
            },
        )

        runCatching {
            wm.addView(
                root,
                params,
            )

            selectorWindowManager =
                wm

            selectorOverlay =
                root
        }
            .onFailure {
                lastError =
                    it.message
                        ?: "Não foi possível abrir a seleção de área."

                hideAreaSelectorOverlay()

                if (
                    isWidgetEnabled
                ) {
                    showFloatingWidget()
                }
            }
    }

    private fun savePendingCrop(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        val safeLeft =
            left.coerceIn(
                0f,
                0.98f,
            )

        val safeTop =
            top.coerceIn(
                0f,
                0.98f,
            )

        val safeRight =
            right.coerceIn(
                safeLeft +
                    0.02f,
                1f,
            )

        val safeBottom =
            bottom.coerceIn(
                safeTop +
                    0.02f,
                1f,
            )

        prefs.edit()
            .putFloat(
                KEY_PENDING_CROP_LEFT,
                safeLeft,
            )
            .putFloat(
                KEY_PENDING_CROP_TOP,
                safeTop,
            )
            .putFloat(
                KEY_PENDING_CROP_RIGHT,
                safeRight,
            )
            .putFloat(
                KEY_PENDING_CROP_BOTTOM,
                safeBottom,
            )
            .apply()
    }

    private fun launchCaptureController() {
        hideFloatingWidget()
        hideAreaSelectorOverlay()

        runCatching {
            val pending =
                PendingIntent.getActivity(
                    this,
                    4401,
                    Intent(
                        this,
                        CaptureAreaActivity::class.java,
                    )
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        ),
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE,
                )

            pending.send()
        }
            .onFailure {
                lastError =
                    it.message
                        ?: "Não foi possível abrir a autorização de gravação."

                if (
                    isWidgetEnabled
                ) {
                    showFloatingWidget()
                }
            }
    }

    private fun hideAreaSelectorOverlay() {
        val view =
            selectorOverlay

        val wm =
            selectorWindowManager

        if (
            view != null &&
            wm != null
        ) {
            runCatching {
                wm.removeView(
                    view,
                )
            }
        }

        selectorOverlay =
            null

        selectorAreaView =
            null

        selectorWindowManager =
            null
    }

    private data class AreaRegion(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    private class AreaSelectionView(
        context:
            android.content.Context,
    ) : View(context) {
        private val shade =
            Paint(
                Paint.ANTI_ALIAS_FLAG,
            )
                .apply {
                    color =
                        Color.argb(
                            112,
                            5,
                            31,
                            68,
                        )
                }

        private val border =
            Paint(
                Paint.ANTI_ALIAS_FLAG,
            )
                .apply {
                    color =
                        Color.rgb(
                            20,
                            126,
                            246,
                        )

                    style =
                        Paint.Style.STROKE

                    strokeWidth =
                        context.dp(3)
                            .toFloat()
                }

        private val handleFill =
            Paint(
                Paint.ANTI_ALIAS_FLAG,
            )
                .apply {
                    color =
                        Color.WHITE
                }

        private val handleStroke =
            Paint(
                Paint.ANTI_ALIAS_FLAG,
            )
                .apply {
                    color =
                        Color.rgb(
                            20,
                            126,
                            246,
                        )

                    style =
                        Paint.Style.STROKE

                    strokeWidth =
                        context.dp(3)
                            .toFloat()
                }

        private val labelBg =
            Paint(
                Paint.ANTI_ALIAS_FLAG,
            )
                .apply {
                    color =
                        Color.argb(
                            220,
                            7,
                            49,
                            103,
                        )
                }

        private val labelPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG,
            )
                .apply {
                    color =
                        Color.WHITE

                    textSize =
                        context.dp(12)
                            .toFloat()

                    textAlign =
                        Paint.Align.CENTER

                    isFakeBoldText =
                        true
                }

        private val rect =
            RectF()

        private val minSize =
            context.dp(96)
                .toFloat()

        private val handleRadius =
            context.dp(10)
                .toFloat()

        private val hitRadius =
            context.dp(40)
                .toFloat()

        private var initialized =
            false

        private var mode =
            MODE_NONE

        private var lastX =
            0f

        private var lastY =
            0f

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(
                w,
                h,
                oldw,
                oldh,
            )

            if (
                !initialized &&
                w > 0 &&
                h > 0
            ) {
                rect.set(
                    w *
                        0.08f,
                    h *
                        0.18f,
                    w *
                        0.92f,
                    h *
                        0.82f,
                )

                initialized =
                    true
            }
        }

        override fun onDraw(
            canvas: Canvas,
        ) {
            super.onDraw(
                canvas,
            )

            canvas.drawRect(
                0f,
                0f,
                width.toFloat(),
                rect.top,
                shade,
            )

            canvas.drawRect(
                0f,
                rect.bottom,
                width.toFloat(),
                height.toFloat(),
                shade,
            )

            canvas.drawRect(
                0f,
                rect.top,
                rect.left,
                rect.bottom,
                shade,
            )

            canvas.drawRect(
                rect.right,
                rect.top,
                width.toFloat(),
                rect.bottom,
                shade,
            )

            canvas.drawRoundRect(
                rect,
                context.dp(12)
                    .toFloat(),
                context.dp(12)
                    .toFloat(),
                border,
            )

            drawHandle(
                canvas,
                rect.left,
                rect.top,
            )

            drawHandle(
                canvas,
                rect.right,
                rect.top,
            )

            drawHandle(
                canvas,
                rect.left,
                rect.bottom,
            )

            drawHandle(
                canvas,
                rect.right,
                rect.bottom,
            )

            val value =
                "${rect.width().roundToInt()} × ${rect.height().roundToInt()} px"

            val labelRect =
                RectF(
                    rect.centerX() -
                        context.dp(68),
                    rect.centerY() -
                        context.dp(20),
                    rect.centerX() +
                        context.dp(68),
                    rect.centerY() +
                        context.dp(10),
                )

            canvas.drawRoundRect(
                labelRect,
                context.dp(9)
                    .toFloat(),
                context.dp(9)
                    .toFloat(),
                labelBg,
            )

            canvas.drawText(
                value,
                rect.centerX(),
                rect.centerY(),
                labelPaint,
            )
        }

        private fun drawHandle(
            canvas: Canvas,
            x: Float,
            y: Float,
        ) {
            canvas.drawCircle(
                x,
                y,
                handleRadius,
                handleFill,
            )

            canvas.drawCircle(
                x,
                y,
                handleRadius,
                handleStroke,
            )
        }

        override fun onTouchEvent(
            event: MotionEvent,
        ): Boolean {
            when (
                event.actionMasked
            ) {
                MotionEvent.ACTION_DOWN -> {
                    mode =
                        detectMode(
                            event.x,
                            event.y,
                        )

                    lastX =
                        event.x

                    lastY =
                        event.y

                    return mode !=
                        MODE_NONE
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx =
                        event.x -
                            lastX

                    val dy =
                        event.y -
                            lastY

                    when (
                        mode
                    ) {
                        MODE_MOVE ->
                            move(
                                dx,
                                dy,
                            )

                        MODE_TL -> {
                            rect.left =
                                event.x.coerceIn(
                                    0f,
                                    rect.right -
                                        minSize,
                                )

                            rect.top =
                                event.y.coerceIn(
                                    0f,
                                    rect.bottom -
                                        minSize,
                                )
                        }

                        MODE_TR -> {
                            rect.right =
                                event.x.coerceIn(
                                    rect.left +
                                        minSize,
                                    width.toFloat(),
                                )

                            rect.top =
                                event.y.coerceIn(
                                    0f,
                                    rect.bottom -
                                        minSize,
                                )
                        }

                        MODE_BL -> {
                            rect.left =
                                event.x.coerceIn(
                                    0f,
                                    rect.right -
                                        minSize,
                                )

                            rect.bottom =
                                event.y.coerceIn(
                                    rect.top +
                                        minSize,
                                    height.toFloat(),
                                )
                        }

                        MODE_BR -> {
                            rect.right =
                                event.x.coerceIn(
                                    rect.left +
                                        minSize,
                                    width.toFloat(),
                                )

                            rect.bottom =
                                event.y.coerceIn(
                                    rect.top +
                                        minSize,
                                    height.toFloat(),
                                )
                        }
                    }

                    lastX =
                        event.x

                    lastY =
                        event.y

                    invalidate()

                    return true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    mode =
                        MODE_NONE

                    return true
                }
            }

            return super.onTouchEvent(
                event,
            )
        }

        private fun detectMode(
            x: Float,
            y: Float,
        ): Int {
            fun near(
                hx: Float,
                hy: Float,
            ): Boolean =
                abs(
                    x -
                        hx,
                ) <=
                    hitRadius &&
                    abs(
                        y -
                            hy,
                    ) <=
                    hitRadius

            return when {
                near(
                    rect.left,
                    rect.top,
                ) ->
                    MODE_TL

                near(
                    rect.right,
                    rect.top,
                ) ->
                    MODE_TR

                near(
                    rect.left,
                    rect.bottom,
                ) ->
                    MODE_BL

                near(
                    rect.right,
                    rect.bottom,
                ) ->
                    MODE_BR

                rect.contains(
                    x,
                    y,
                ) ->
                    MODE_MOVE

                else ->
                    MODE_NONE
            }
        }

        private fun move(
            dx: Float,
            dy: Float,
        ) {
            rect.offset(
                dx.coerceIn(
                    -rect.left,
                    width -
                        rect.right,
                ),
                dy.coerceIn(
                    -rect.top,
                    height -
                        rect.bottom,
                ),
            )
        }

        fun normalizedRegion():
            AreaRegion {
            val safeW =
                max(
                    1,
                    width,
                )
                    .toFloat()

            val safeH =
                max(
                    1,
                    height,
                )
                    .toFloat()

            return AreaRegion(
                left =
                    (
                        rect.left /
                            safeW
                        )
                        .coerceIn(
                            0f,
                            1f,
                        ),
                top =
                    (
                        rect.top /
                            safeH
                        )
                        .coerceIn(
                            0f,
                            1f,
                        ),
                right =
                    (
                        rect.right /
                            safeW
                        )
                        .coerceIn(
                            0f,
                            1f,
                        ),
                bottom =
                    (
                        rect.bottom /
                            safeH
                        )
                        .coerceIn(
                            0f,
                            1f,
                        ),
            )
        }

        companion object {
            private const val MODE_NONE =
                0

            private const val MODE_MOVE =
                1

            private const val MODE_TL =
                2

            private const val MODE_TR =
                3

            private const val MODE_BL =
                4

            private const val MODE_BR =
                5
        }
    }

    private fun updateOverlay() {
        overlayTimer?.text = when {
            isProcessing -> "●  SALVANDO…"
            isPaused -> "●  ${formatElapsed(elapsedRecordingMs())}  PAUSADO"
            isRecording -> "●  ${formatElapsed(elapsedRecordingMs())}"
            else -> "●  PRONTO"
        }
        overlayArea?.apply {
            text =
                if (
                    prefs.getBoolean(
                        KEY_AREA_SELECTED,
                        false,
                    )
                ) {
                    "ÁREA ✓"
                } else {
                    "ÁREA"
                }

            visibility =
                if (
                    isRecording ||
                    isProcessing
                ) {
                    View.GONE
                } else {
                    View.VISIBLE
                }

            isEnabled =
                !isRecording &&
                    !isProcessing
        }

        overlayPrimary?.apply {
            text = when {
                isProcessing -> "…"
                isPaused -> "▶"
                isRecording -> "Ⅱ"
                else -> "REC"
            }
            isEnabled = !isProcessing
        }
        overlayStop?.apply {
            text = if (isRecording) "■" else "×"
            isEnabled = !isProcessing
        }
    }

    private fun hideFloatingWidget() {
        mainHandler.removeCallbacks(overlayTick)
        val view = overlayView
        val wm = windowManager
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
        }
        overlayView = null
        overlayTimer = null
        overlayArea = null
        overlayPrimary = null
        overlayStop = null
        windowManager = null
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
        setStroke(dp(1), stroke)
    }

    private fun deleteQuietly(file: File?) {
        if (file != null) runCatching { file.delete() }
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()

    override fun onDestroy() {
        mainHandler.removeCallbacks(overlayTick)
        runCatching { transformer?.cancel() }
        if (isRecording && !stopping) runCatching { stopRecording() }
        hideAreaSelectorOverlay()
        hideFloatingWidget()
        super.onDestroy()
    }

    companion object {
        const val ACTION_ENABLE_WIDGET = "central.record.ENABLE_WIDGET"
        const val ACTION_DISABLE_WIDGET = "central.record.DISABLE_WIDGET"
        const val ACTION_BEGIN_CAPTURE = "central.record.BEGIN_CAPTURE"
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

        private const val PREFS = "screen_recorder_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AREA_MODE = "area_mode"
        private const val KEY_AREA_SELECTED = "area_selected"
        const val KEY_PENDING_CROP_LEFT = "pending_crop_left"
        const val KEY_PENDING_CROP_TOP = "pending_crop_top"
        const val KEY_PENDING_CROP_RIGHT = "pending_crop_right"
        const val KEY_PENDING_CROP_BOTTOM = "pending_crop_bottom"
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
        var isWidgetEnabled = false
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

        @Volatile
        private var accumulatedElapsedMs = 0L

        @Volatile
        private var activeSegmentStartedElapsed = 0L

        @Volatile
        private var lastFinishedElapsedMs = 0L

        fun elapsedRecordingMs(): Long = synchronized(timerLock) {
            when {
                isRecording && !isPaused && activeSegmentStartedElapsed > 0L ->
                    accumulatedElapsedMs + (SystemClock.elapsedRealtime() - activeSegmentStartedElapsed)
                isRecording -> accumulatedElapsedMs
                isProcessing -> lastFinishedElapsedMs
                else -> 0L
            }
        }

        private fun formatElapsed(value: Long): String {
            val seconds = value.coerceAtLeast(0L) / 1000L
            return "%02d:%02d:%02d".format(
                seconds / 3600L,
                (seconds / 60L) % 60L,
                seconds % 60L,
            )
        }
    }
}
