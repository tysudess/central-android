package br.com.centralmidia.android.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorDrawable
import android.graphics.Paint
import android.graphics.RectF
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import br.com.centralmidia.android.core.dp
import br.com.centralmidia.android.recording.ScreenRecordService
import kotlin.math.abs
import kotlin.math.max

class CaptureAreaActivity : BaseActivity() {
    private lateinit var root: FrameLayout
    private var selector: CaptureAreaView? = null

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private var left = 0f
    private var top = 0f
    private var right = 1f
    private var bottom = 1f
    private var permissionStageRunning = false
    private var recordingWasStarted = false

    private val runtimePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val micRequested = prefs.getBoolean(KEY_MIC, false)
        val micOk = !micRequested ||
            result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

        if (!micOk) {
            toast("Autorize o microfone ou desative o áudio no Gravador.")
            finishCancelled()
            return@registerForActivityResult
        }

        requestMediaProjection()
    }

    private val projectionPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            finishCancelled()
            return@registerForActivityResult
        }

        startRecorderService(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        setContentView(root)

        if (prefs.getInt(KEY_AREA_MODE, 0) == 1) {
            showSelector()
        } else {
            beginPermissionFlow()
        }
    }

    private fun showSelector() {
        val capture = CaptureAreaView(this)
        selector = capture
        root.addView(
            capture,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val topCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(11))
            background = MobileUi.rounded(
                Color.argb(245, 247, 251, 255),
                dp(16).toFloat(),
                MobileUi.BORDER,
                dp(1),
            )
            addView(
                MobileUi.text(
                    this@CaptureAreaActivity,
                    "Área da gravação",
                    17f,
                    MobileUi.NAVY,
                    true,
                ),
            )
            addView(
                MobileUi.text(
                    this@CaptureAreaActivity,
                    "Mova o retângulo e arraste os quatro cantos para redimensionar.",
                    10.5f,
                    MobileUi.MUTED,
                ),
                MobileUi.match(dp(3)),
            )
        }
        root.addView(
            topCard,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                topMargin = dp(28)
            },
        )

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = MobileUi.rounded(
                Color.argb(248, 255, 255, 255),
                dp(18).toFloat(),
                MobileUi.BORDER,
                dp(1),
            )
        }

        actions.addView(
            MobileUi.button(this, "Cancelar", false, MobileUi.PINK) {
                finishCancelled()
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) },
        )

        actions.addView(
            MobileUi.button(this, "Tela inteira", false, MobileUi.NAVY) {
                left = 0f
                top = 0f
                right = 1f
                bottom = 1f
                clearSelectorUi()
                beginPermissionFlow()
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            },
        )

        actions.addView(
            MobileUi.button(this, "Usar área", true, MobileUi.BLUE) {
                val region = selector?.normalizedRegion() ?: Region(0f, 0f, 1f, 1f)
                left = region.left
                top = region.top
                right = region.right
                bottom = region.bottom
                clearSelectorUi()
                beginPermissionFlow()
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) },
        )

        root.addView(
            actions,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                bottomMargin = dp(30)
            },
        )
    }

    private fun clearSelectorUi() {
        selector = null
        root.removeAllViews()
        root.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun beginPermissionFlow() {
        if (permissionStageRunning) return
        permissionStageRunning = true

        val needed = mutableListOf<String>()
        if (
            prefs.getBoolean(KEY_MIC, false) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isEmpty()) {
            requestMediaProjection()
        } else {
            runtimePermissions.launch(needed.toTypedArray())
        }
    }

    private fun requestMediaProjection() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionPermission.launch(manager.createScreenCaptureIntent())
    }

    private fun startRecorderService(result: androidx.activity.result.ActivityResult) {
        val fps = when (prefs.getInt(KEY_FPS_INDEX, 1)) {
            0 -> 24
            2 -> 60
            else -> 30
        }
        val quality = when (prefs.getInt(KEY_QUALITY_INDEX, 0)) {
            1 -> "720"
            2 -> "1080"
            else -> "original"
        }
        val countdown = when (prefs.getInt(KEY_COUNTDOWN_INDEX, 0)) {
            1 -> 3
            2 -> 5
            3 -> 10
            else -> 0
        }

        val start = {
            val serviceIntent = Intent(this, ScreenRecordService::class.java)
                .setAction(ScreenRecordService.ACTION_START)
                .putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(ScreenRecordService.EXTRA_RESULT_DATA, result.data)
                .putExtra(ScreenRecordService.EXTRA_MIC, prefs.getBoolean(KEY_MIC, false))
                .putExtra(ScreenRecordService.EXTRA_FPS, fps)
                .putExtra(ScreenRecordService.EXTRA_QUALITY, quality)
                .putExtra(ScreenRecordService.EXTRA_SHOW_OVERLAY, true)
                .putExtra(ScreenRecordService.EXTRA_CROP_LEFT, left)
                .putExtra(ScreenRecordService.EXTRA_CROP_TOP, top)
                .putExtra(ScreenRecordService.EXTRA_CROP_RIGHT, right)
                .putExtra(ScreenRecordService.EXTRA_CROP_BOTTOM, bottom)

            runCatching {
                ContextCompat.startForegroundService(this, serviceIntent)
                recordingWasStarted = true
            }.onFailure {
                toast(it.message ?: "Não foi possível iniciar a gravação.")
            }
            finish()
        }

        if (countdown > 0) {
            root.removeAllViews()
            val counter = TextView(this).apply {
                textSize = 42f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setShadowLayer(10f, 0f, 2f, Color.BLACK)
            }
            root.addView(
                counter,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )

            var remaining = countdown
            fun tick() {
                if (remaining <= 0) {
                    root.removeAllViews()
                    start()
                    return
                }
                counter.text = remaining.toString()
                remaining--
                handler.postDelayed({ tick() }, 1000L)
            }
            tick()
        } else {
            start()
        }
    }

    private fun finishCancelled() {
        restoreWidget()
        finish()
    }

    private fun restoreWidget() {
        if (!recordingWasStarted && prefs.getBoolean(KEY_ENABLED, false)) {
            runCatching {
                startService(
                    Intent(this, ScreenRecordService::class.java)
                        .setAction(ScreenRecordService.ACTION_ENABLE_WIDGET),
                )
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    data class Region(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    private class CaptureAreaView(context: android.content.Context) : View(context) {
        private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(128, 4, 30, 65)
        }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MobileUi.BLUE
            style = Paint.Style.STROKE
            strokeWidth = context.dp(3).toFloat()
        }
        private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val handleBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MobileUi.BLUE
            style = Paint.Style.STROKE
            strokeWidth = context.dp(3).toFloat()
        }
        private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 7, 49, 103)
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = context.dp(13).toFloat()
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val rect = RectF()
        private val minSize = context.dp(100).toFloat()
        private val handleRadius = context.dp(11).toFloat()
        private val hitRadius = context.dp(38).toFloat()

        private var initialized = false
        private var mode = NONE
        private var lastX = 0f
        private var lastY = 0f

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (!initialized && w > 0 && h > 0) {
                rect.set(w * .08f, h * .18f, w * .92f, h * .82f)
                initialized = true
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), rect.top, shade)
            canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), shade)
            canvas.drawRect(0f, rect.top, rect.left, rect.bottom, shade)
            canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, shade)
            canvas.drawRoundRect(rect, context.dp(12).toFloat(), context.dp(12).toFloat(), border)
            drawHandle(canvas, rect.left, rect.top)
            drawHandle(canvas, rect.right, rect.top)
            drawHandle(canvas, rect.left, rect.bottom)
            drawHandle(canvas, rect.right, rect.bottom)

            val value = "${rect.width().toInt()} × ${rect.height().toInt()} px"
            val box = RectF(
                rect.centerX() - context.dp(70),
                rect.centerY() - context.dp(22),
                rect.centerX() + context.dp(70),
                rect.centerY() + context.dp(8),
            )
            canvas.drawRoundRect(box, context.dp(10).toFloat(), context.dp(10).toFloat(), labelBg)
            canvas.drawText(value, rect.centerX(), rect.centerY(), label)
        }

        private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
            canvas.drawCircle(x, y, handleRadius, handle)
            canvas.drawCircle(x, y, handleRadius, handleBorder)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    mode = detectMode(event.x, event.y)
                    lastX = event.x
                    lastY = event.y
                    return mode != NONE
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    when (mode) {
                        MOVE -> move(dx, dy)
                        TL -> {
                            rect.left = event.x.coerceIn(0f, rect.right - minSize)
                            rect.top = event.y.coerceIn(0f, rect.bottom - minSize)
                        }
                        TR -> {
                            rect.right = event.x.coerceIn(rect.left + minSize, width.toFloat())
                            rect.top = event.y.coerceIn(0f, rect.bottom - minSize)
                        }
                        BL -> {
                            rect.left = event.x.coerceIn(0f, rect.right - minSize)
                            rect.bottom = event.y.coerceIn(rect.top + minSize, height.toFloat())
                        }
                        BR -> {
                            rect.right = event.x.coerceIn(rect.left + minSize, width.toFloat())
                            rect.bottom = event.y.coerceIn(rect.top + minSize, height.toFloat())
                        }
                    }
                    lastX = event.x
                    lastY = event.y
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mode = NONE
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun detectMode(x: Float, y: Float): Int {
            fun near(hx: Float, hy: Float) = abs(x - hx) <= hitRadius && abs(y - hy) <= hitRadius
            return when {
                near(rect.left, rect.top) -> TL
                near(rect.right, rect.top) -> TR
                near(rect.left, rect.bottom) -> BL
                near(rect.right, rect.bottom) -> BR
                rect.contains(x, y) -> MOVE
                else -> NONE
            }
        }

        private fun move(dx: Float, dy: Float) {
            rect.offset(
                dx.coerceIn(-rect.left, width - rect.right),
                dy.coerceIn(-rect.top, height - rect.bottom),
            )
        }

        fun normalizedRegion(): Region {
            val w = max(1, width).toFloat()
            val h = max(1, height).toFloat()
            return Region(
                (rect.left / w).coerceIn(0f, 1f),
                (rect.top / h).coerceIn(0f, 1f),
                (rect.right / w).coerceIn(0f, 1f),
                (rect.bottom / h).coerceIn(0f, 1f),
            )
        }

        companion object {
            private const val NONE = 0
            private const val MOVE = 1
            private const val TL = 2
            private const val TR = 3
            private const val BL = 4
            private const val BR = 5
        }
    }

    companion object {
        private const val PREFS = "screen_recorder_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AREA_MODE = "area_mode"
        private const val KEY_FPS_INDEX = "fps_index"
        private const val KEY_QUALITY_INDEX = "quality_index"
        private const val KEY_COUNTDOWN_INDEX = "countdown_index"
        private const val KEY_MIC = "mic"
    }
}
