package br.com.centralmidia.android.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import br.com.centralmidia.android.recording.ScreenRecordService

/**
 * Activity transparente usada somente para as permissões do Android.
 *
 * A seleção do retângulo não acontece mais aqui: ela é um overlay real do
 * ScreenRecordService e aparece diretamente sobre a tela/aplicativo que o
 * usuário quer gravar. Isso elimina o problema em que a área era desenhada
 * sobre a Central em vez da tela alvo.
 */
class CaptureAreaActivity : BaseActivity() {
    private val handler =
        Handler(
            Looper.getMainLooper(),
        )

    private val prefs by lazy {
        getSharedPreferences(
            PREFS,
            MODE_PRIVATE,
        )
    }

    private lateinit var root:
        FrameLayout

    private var recordingWasStarted =
        false

    private val runtimePermissions =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            val micRequested =
                prefs.getBoolean(
                    KEY_MIC,
                    false,
                )

            val micOk =
                !micRequested ||
                    result[
                        Manifest.permission.RECORD_AUDIO
                    ] == true ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO,
                    ) ==
                    PackageManager.PERMISSION_GRANTED

            if (!micOk) {
                toast(
                    "Autorize o microfone ou desative o áudio no Gravador.",
                )

                finishCancelled()
                return@registerForActivityResult
            }

            requestMediaProjection()
        }

    private val projectionPermission =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (
                result.resultCode !=
                Activity.RESULT_OK ||
                result.data == null
            ) {
                finishCancelled()
                return@registerForActivityResult
            }

            startRecorderService(
                result,
            )
        }

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(
            savedInstanceState,
        )

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false,
        )

        window.statusBarColor =
            Color.TRANSPARENT

        window.navigationBarColor =
            Color.TRANSPARENT

        overridePendingTransition(
            0,
            0,
        )

        root =
            FrameLayout(this).apply {
                setBackgroundColor(
                    Color.TRANSPARENT,
                )
            }

        setContentView(
            root,
        )

        beginPermissionFlow()
    }

    private fun beginPermissionFlow() {
        val needed =
            mutableListOf<String>()

        if (
            prefs.getBoolean(
                KEY_MIC,
                false,
            ) &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed +=
                Manifest.permission.RECORD_AUDIO
        }

        if (
            Build.VERSION.SDK_INT >=
            33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed +=
                Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isEmpty()) {
            requestMediaProjection()
        } else {
            runtimePermissions.launch(
                needed.toTypedArray(),
            )
        }
    }

    private fun requestMediaProjection() {
        val manager =
            getSystemService(
                MediaProjectionManager::class.java,
            )

        projectionPermission.launch(
            manager.createScreenCaptureIntent(),
        )
    }

    private fun startRecorderService(
        result:
            androidx.activity.result.ActivityResult,
    ) {
        val fps =
            when (
                prefs.getInt(
                    KEY_FPS_INDEX,
                    1,
                )
            ) {
                0 ->
                    24

                2 ->
                    60

                else ->
                    30
            }

        val quality =
            when (
                prefs.getInt(
                    KEY_QUALITY_INDEX,
                    0,
                )
            ) {
                1 ->
                    "720"

                2 ->
                    "1080"

                else ->
                    "original"
            }

        val countdown =
            when (
                prefs.getInt(
                    KEY_COUNTDOWN_INDEX,
                    0,
                )
            ) {
                1 ->
                    3

                2 ->
                    5

                3 ->
                    10

                else ->
                    0
            }

        val left =
            prefs.getFloat(
                ScreenRecordService.KEY_PENDING_CROP_LEFT,
                0f,
            )

        val top =
            prefs.getFloat(
                ScreenRecordService.KEY_PENDING_CROP_TOP,
                0f,
            )

        val right =
            prefs.getFloat(
                ScreenRecordService.KEY_PENDING_CROP_RIGHT,
                1f,
            )

        val bottom =
            prefs.getFloat(
                ScreenRecordService.KEY_PENDING_CROP_BOTTOM,
                1f,
            )

        val start = {
            val serviceIntent =
                Intent(
                    this,
                    ScreenRecordService::class.java,
                )
                    .setAction(
                        ScreenRecordService.ACTION_START,
                    )
                    .putExtra(
                        ScreenRecordService.EXTRA_RESULT_CODE,
                        result.resultCode,
                    )
                    .putExtra(
                        ScreenRecordService.EXTRA_RESULT_DATA,
                        result.data,
                    )
                    .putExtra(
                        ScreenRecordService.EXTRA_MIC,
                        prefs.getBoolean(
                            KEY_MIC,
                            false,
                        ),
                    )
                    .putExtra(
                        ScreenRecordService.EXTRA_FPS,
                        fps,
                    )
                    .putExtra(
                        ScreenRecordService.EXTRA_QUALITY,
                        quality,
                    )
                    .putExtra(
                        ScreenRecordService.EXTRA_CROP_LEFT,
                        left,
                    )
                    .putExtra(
                        ScreenRecordService.EXTRA_CROP_TOP,
                        top,
                    )
                    .putExtra(
                        ScreenRecordService.EXTRA_CROP_RIGHT,
                        right,
                    )
                    .putExtra(
                        ScreenRecordService.EXTRA_CROP_BOTTOM,
                        bottom,
                    )

            runCatching {
                ContextCompat.startForegroundService(
                    this,
                    serviceIntent,
                )

                recordingWasStarted =
                    true
            }
                .onFailure {
                    toast(
                        it.message
                            ?: "Não foi possível iniciar a gravação.",
                    )
                }

            finish()
            overridePendingTransition(
                0,
                0,
            )
        }

        if (countdown <= 0) {
            start()
            return
        }

        root.removeAllViews()

        val counter =
            TextView(this).apply {
                textSize =
                    46f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE,
                )

                setShadowLayer(
                    12f,
                    0f,
                    3f,
                    Color.BLACK,
                )
            }

        root.addView(
            counter,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        var remaining =
            countdown

        fun tick() {
            if (remaining <= 0) {
                root.removeAllViews()
                start()
                return
            }

            counter.text =
                remaining.toString()

            remaining--

            handler.postDelayed(
                {
                    tick()
                },
                1000L,
            )
        }

        tick()
    }

    private fun finishCancelled() {
        restoreWidget()

        finish()

        overridePendingTransition(
            0,
            0,
        )
    }

    private fun restoreWidget() {
        if (
            !recordingWasStarted &&
            prefs.getBoolean(
                KEY_ENABLED,
                false,
            )
        ) {
            runCatching {
                startService(
                    Intent(
                        this,
                        ScreenRecordService::class.java,
                    )
                        .setAction(
                            ScreenRecordService.ACTION_ENABLE_WIDGET,
                        ),
                )
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(
            null,
        )

        super.onDestroy()
    }

    companion object {
        private const val PREFS =
            "screen_recorder_settings"

        private const val KEY_ENABLED =
            "enabled"

        private const val KEY_FPS_INDEX =
            "fps_index"

        private const val KEY_QUALITY_INDEX =
            "quality_index"

        private const val KEY_COUNTDOWN_INDEX =
            "countdown_index"

        private const val KEY_MIC =
            "mic"
    }
}
