package br.com.centralmidia.android.recording

import android.Manifest
import android.app.Activity
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.NotificationHelper
import kotlin.math.roundToInt

class ScreenRecordService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var outputUri: android.net.Uri? = null
    private var paused = false
    private var stopping = false

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_START -> startRecording(intent)
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        if (recorder != null) return

        stopping = false
        paused = false
        lastError = ""

        try {
            val code =
                intent.getIntExtra(
                    "resultCode",
                    Activity.RESULT_CANCELED,
                )

            @Suppress("DEPRECATION")
            val data =
                intent.getParcelableExtra<Intent>(
                    "data",
                )
                    ?: error(
                        "Permissão de captura não recebida.",
                    )

            val requestedMic =
                intent.getBooleanExtra(
                    "mic",
                    false,
                )

            val withMic =
                requestedMic &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO,
                    ) ==
                    PackageManager.PERMISSION_GRANTED

            val fps =
                intent.getIntExtra(
                    "fps",
                    30,
                ).coerceIn(
                    15,
                    60,
                )

            val quality =
                intent.getStringExtra(
                    "quality",
                )
                    .orEmpty()
                    .ifBlank {
                        "original"
                    }

            val notification =
                NotificationCompat.Builder(
                    this,
                    NotificationHelper.CHANNEL_RECORDING,
                )
                    .setSmallIcon(
                        R.drawable.ic_stat_central,
                    )
                    .setContentTitle(
                        "Central • Gravando tela",
                    )
                    .setContentText(
                        "Use Pausar ou Parar no aplicativo.",
                    )
                    .setOngoing(true)
                    .build()

            var serviceType = 0

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {
                serviceType =
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION

                if (
                    withMic &&
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.R
                ) {
                    serviceType =
                        serviceType or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
            }

            ServiceCompat.startForeground(
                this,
                991,
                notification,
                serviceType,
            )

            val values =
                ContentValues().apply {
                    put(
                        MediaStore.Video.Media.DISPLAY_NAME,
                        "Central-Gravacao-" +
                            System.currentTimeMillis() +
                            ".mp4",
                    )
                    put(
                        MediaStore.Video.Media.MIME_TYPE,
                        "video/mp4",
                    )
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "Movies/Central Inteligente de Midia/Gravacoes",
                    )
                }

            outputUri =
                contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values,
                )
                    ?: error(
                        "Não foi possível criar o arquivo de gravação.",
                    )

            pfd =
                contentResolver.openFileDescriptor(
                    outputUri!!,
                    "w",
                )
                    ?: error(
                        "Não foi possível abrir o arquivo de gravação.",
                    )

            val dm =
                resources.displayMetrics

            var width =
                dm.widthPixels
            var height =
                dm.heightPixels

            val longSide =
                maxOf(
                    width,
                    height,
                )

            val wanted =
                when (quality) {
                    "720" -> 1280
                    "1080" -> 1920
                    else ->
                        longSide.coerceAtMost(
                            2160,
                        )
                }

            if (longSide > wanted) {
                val factor =
                    wanted.toFloat() /
                        longSide.toFloat()

                width =
                    (
                        width *
                            factor
                        )
                        .roundToInt()
                        .coerceAtLeast(
                            2,
                        )

                height =
                    (
                        height *
                            factor
                        )
                        .roundToInt()
                        .coerceAtLeast(
                            2,
                        )
            }

            if (width % 2 != 0) {
                width -= 1
            }

            if (height % 2 != 0) {
                height -= 1
            }

            val bitrate =
                when (quality) {
                    "720" ->
                        5_000_000

                    "1080" ->
                        8_000_000

                    else ->
                        10_000_000
                }

            recorder =
                createRecorder().apply {
                    if (withMic) {
                        setAudioSource(
                            MediaRecorder.AudioSource.MIC,
                        )
                    }

                    setVideoSource(
                        MediaRecorder.VideoSource.SURFACE,
                    )
                    setOutputFormat(
                        MediaRecorder.OutputFormat.MPEG_4,
                    )
                    setVideoEncoder(
                        MediaRecorder.VideoEncoder.H264,
                    )
                    setVideoEncodingBitRate(
                        bitrate,
                    )
                    setVideoFrameRate(
                        fps,
                    )
                    setVideoSize(
                        width,
                        height,
                    )

                    if (withMic) {
                        setAudioEncoder(
                            MediaRecorder.AudioEncoder.AAC,
                        )
                        setAudioEncodingBitRate(
                            128_000,
                        )
                        setAudioSamplingRate(
                            44_100,
                        )
                    }

                    setOutputFile(
                        pfd?.fileDescriptor,
                    )
                    prepare()
                }

            projection =
                getSystemService(
                    MediaProjectionManager::class.java,
                )
                    .getMediaProjection(
                        code,
                        data,
                    )

            projection?.registerCallback(
                object :
                    MediaProjection.Callback() {
                    override fun onStop() {
                        Handler(
                            Looper.getMainLooper(),
                        ).post {
                            stopRecording()
                        }
                    }
                },
                Handler(
                    Looper.getMainLooper(),
                ),
            )

            display =
                projection?.createVirtualDisplay(
                    "CentralRecorder",
                    width,
                    height,
                    dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    recorder?.surface,
                    null,
                    null,
                )

            recorder?.start()

            isRecording = true
            isPaused = false
            startedAt =
                System.currentTimeMillis()
        } catch (error: Throwable) {
            lastError =
                error.message
                    ?: "Falha ao iniciar a gravação de tela."

            cleanup(
                deleteEmpty = true,
            )
        }
    }

    private fun pauseRecording() {
        if (
            !isRecording ||
            paused ||
            Build.VERSION.SDK_INT <
            24
        ) {
            return
        }

        runCatching {
            recorder?.pause()
        }.onSuccess {
            paused = true
            isPaused = true
        }.onFailure {
            lastError =
                it.message
                    ?: "Não foi possível pausar."
        }
    }

    private fun resumeRecording() {
        if (
            !isRecording ||
            !paused ||
            Build.VERSION.SDK_INT <
            24
        ) {
            return
        }

        runCatching {
            recorder?.resume()
        }.onSuccess {
            paused = false
            isPaused = false
        }.onFailure {
            lastError =
                it.message
                    ?: "Não foi possível retomar."
        }
    }

    private fun stopRecording() {
        if (stopping) return
        stopping = true

        val uri =
            outputUri

        runCatching {
            recorder?.stop()
        }.onFailure {
            lastError =
                it.message
                    ?: "A gravação foi encerrada antes de gerar um arquivo válido."
        }

        cleanup(
            deleteEmpty = false,
        )

        if (uri != null) {
            runCatching {
                CentralDb(this)
                    .addHistory(
                        "screen_record",
                        "Gravação de tela",
                        "MP4 • H.264",
                        uri.toString(),
                    )
            }
        }
    }

    private fun cleanup(
        deleteEmpty: Boolean,
    ) {
        val uri =
            outputUri

        runCatching {
            recorder?.reset()
        }
        runCatching {
            recorder?.release()
        }
        recorder = null

        runCatching {
            display?.release()
        }
        display = null

        runCatching {
            projection?.stop()
        }
        projection = null

        runCatching {
            pfd?.close()
        }
        pfd = null

        outputUri = null
        isRecording = false
        isPaused = false
        startedAt = 0L
        paused = false
        stopping = false

        if (
            deleteEmpty &&
            uri != null
        ) {
            runCatching {
                contentResolver.delete(
                    uri,
                    null,
                    null,
                )
            }
        }

        ServiceCompat.stopForeground(
            this,
            ServiceCompat.STOP_FOREGROUND_REMOVE,
        )
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun createRecorder():
        MediaRecorder =
        if (
            Build.VERSION.SDK_INT >=
            31
        ) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

    override fun onDestroy() {
        if (isRecording) {
            runCatching {
                stopRecording()
            }
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START =
            "central.record.START"
        const val ACTION_STOP =
            "central.record.STOP"
        const val ACTION_PAUSE =
            "central.record.PAUSE"
        const val ACTION_RESUME =
            "central.record.RESUME"

        @Volatile
        var isRecording = false
            private set

        @Volatile
        var isPaused = false
            private set

        @Volatile
        var startedAt = 0L
            private set

        @Volatile
        var lastError = ""
            private set
    }
}
