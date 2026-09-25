package br.com.centralmidia.android.ui

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.WindowCompat
import br.com.centralmidia.android.core.dp
import kotlin.math.abs
import kotlin.math.max

class CaptureAreaActivity : BaseActivity() {
    private lateinit var selector: CaptureAreaView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = FrameLayout(this)
        selector = CaptureAreaView(this)
        root.addView(selector, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(12))
            background = MobileUi.rounded(Color.argb(242, 247, 251, 255), dp(16).toFloat(), MobileUi.BORDER, dp(1))
            addView(MobileUi.text(this@CaptureAreaActivity, "Selecione a área que será gravada", 17f, MobileUi.NAVY, true))
            addView(
                MobileUi.text(
                    this@CaptureAreaActivity,
                    "Arraste o retângulo para mover. Use os círculos dos cantos para redimensionar.",
                    10.5f,
                    MobileUi.MUTED,
                ),
                MobileUi.match(dp(4)),
            )
        }
        root.addView(top, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            leftMargin = dp(12); rightMargin = dp(12); topMargin = dp(18)
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = MobileUi.rounded(Color.argb(246, 255, 255, 255), dp(18).toFloat(), MobileUi.BORDER, dp(1))
        }
        actions.addView(
            MobileUi.button(this, "Tela inteira", false, MobileUi.NAVY) { selector.selectFullScreen() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) },
        )
        actions.addView(
            MobileUi.button(this, "Cancelar", false, MobileUi.PINK) { setResult(RESULT_CANCELED); finish() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4); marginEnd = dp(4) },
        )
        actions.addView(
            MobileUi.button(this, "Usar área", true, MobileUi.BLUE) {
                val r = selector.normalizedRegion()
                setResult(
                    RESULT_OK,
                    Intent().apply {
                        putExtra(EXTRA_LEFT, r.left)
                        putExtra(EXTRA_TOP, r.top)
                        putExtra(EXTRA_RIGHT, r.right)
                        putExtra(EXTRA_BOTTOM, r.bottom)
                    },
                )
                finish()
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) },
        )
        root.addView(actions, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            leftMargin = dp(12); rightMargin = dp(12); bottomMargin = dp(24)
        })
        setContentView(root)
    }

    data class Region(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private class CaptureAreaView(context: android.content.Context) : View(context) {
        private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(155, 0, 18, 45) }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MobileUi.BLUE; style = Paint.Style.STROKE; strokeWidth = context.dp(3).toFloat() }
        private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val handleBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MobileUi.BLUE; style = Paint.Style.STROKE; strokeWidth = context.dp(3).toFloat() }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = context.dp(13).toFloat(); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val rect = RectF()
        private val minSize = context.dp(120).toFloat()
        private val handleRadius = context.dp(11).toFloat()
        private val hitRadius = context.dp(34).toFloat()
        private var initialized = false
        private var mode = NONE
        private var lastX = 0f
        private var lastY = 0f

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            if (!initialized && w > 0 && h > 0) {
                rect.set(w * .08f, h * .18f, w * .92f, h * .82f)
                initialized = true
            }
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), rect.top, shade)
            canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), shade)
            canvas.drawRect(0f, rect.top, rect.left, rect.bottom, shade)
            canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, shade)
            canvas.drawRoundRect(rect, context.dp(12).toFloat(), context.dp(12).toFloat(), border)
            drawHandle(canvas, rect.left, rect.top); drawHandle(canvas, rect.right, rect.top)
            drawHandle(canvas, rect.left, rect.bottom); drawHandle(canvas, rect.right, rect.bottom)
            canvas.drawText("${rect.width().toInt()} × ${rect.height().toInt()} px", rect.centerX(), rect.centerY(), label)
        }

        private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
            canvas.drawCircle(x, y, handleRadius, handle)
            canvas.drawCircle(x, y, handleRadius, handleBorder)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    mode = detectMode(event.x, event.y); lastX = event.x; lastY = event.y
                    return mode != NONE
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX; val dy = event.y - lastY
                    when (mode) {
                        MOVE -> move(dx, dy)
                        TL -> { rect.left = event.x.coerceIn(0f, rect.right - minSize); rect.top = event.y.coerceIn(0f, rect.bottom - minSize) }
                        TR -> { rect.right = event.x.coerceIn(rect.left + minSize, width.toFloat()); rect.top = event.y.coerceIn(0f, rect.bottom - minSize) }
                        BL -> { rect.left = event.x.coerceIn(0f, rect.right - minSize); rect.bottom = event.y.coerceIn(rect.top + minSize, height.toFloat()) }
                        BR -> { rect.right = event.x.coerceIn(rect.left + minSize, width.toFloat()); rect.bottom = event.y.coerceIn(rect.top + minSize, height.toFloat()) }
                    }
                    lastX = event.x; lastY = event.y; invalidate(); return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { mode = NONE; return true }
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
            rect.offset(dx.coerceIn(-rect.left, width - rect.right), dy.coerceIn(-rect.top, height - rect.bottom))
        }

        fun selectFullScreen() { rect.set(0f, 0f, width.toFloat(), height.toFloat()); invalidate() }
        fun normalizedRegion(): Region {
            val w = max(1, width).toFloat(); val h = max(1, height).toFloat()
            return Region((rect.left / w).coerceIn(0f, 1f), (rect.top / h).coerceIn(0f, 1f), (rect.right / w).coerceIn(0f, 1f), (rect.bottom / h).coerceIn(0f, 1f))
        }

        companion object { const val NONE = 0; const val MOVE = 1; const val TL = 2; const val TR = 3; const val BL = 4; const val BR = 5 }
    }

    companion object {
        const val EXTRA_LEFT = "capture_left"
        const val EXTRA_TOP = "capture_top"
        const val EXTRA_RIGHT = "capture_right"
        const val EXTRA_BOTTOM = "capture_bottom"
    }
}
