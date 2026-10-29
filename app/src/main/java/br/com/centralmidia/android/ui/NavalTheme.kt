package br.com.centralmidia.android.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.WeakHashMap

/**
 * Identidade visual naval aplicada às Activities que usam BaseActivity.
 * Não atua no módulo Principais Capas: ele possui tema próprio e fica fora
 * desta classe para preservar a versão de Capas já aprovada pelo usuário.
 */
internal object NavalTheme {
    private val navy = Color.rgb(8, 39, 70)          // #082746
    private val navyDeep = Color.rgb(4, 24, 44)      // #04182C
    private val navyBlue = Color.rgb(23, 74, 115)    // #174A73
    private val gold = Color.rgb(198, 161, 91)       // #C6A15B
    private val goldDeep = Color.rgb(151, 111, 43)   // #976F2B
    private val goldWash = Color.rgb(250, 243, 224)
    private val page = Color.rgb(239, 244, 249)
    private val white = Color.WHITE
    private val muted = Color.rgb(96, 117, 139)
    private val border = Color.rgb(211, 221, 232)
    private val paleIcon = Color.rgb(202, 217, 234)
    private val success = Color.rgb(20, 117, 82)
    private val danger = Color.rgb(179, 49, 59)
    private val dangerWash = Color.rgb(252, 235, 237)

    private const val HEADER_DESCRIPTION = "Cabeçalho da Central Inteligente de Mídia"
    private const val DOCK_DESCRIPTION = "Barra inferior da Central"

    private data class State(
        var signature: Int = Int.MIN_VALUE,
        var listener: ViewTreeObserver.OnGlobalLayoutListener? = null,
        var observer: ViewTreeObserver? = null,
    )

    private val states = WeakHashMap<Activity, State>()

    fun install(activity: Activity) {
        if (isExcluded(activity)) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val state = states.getOrPut(activity) { State() }

        applyWindowChrome(activity)
        styleTree(activity, root)
        state.signature = signature(root)

        if (state.listener == null) {
            val listener = ViewTreeObserver.OnGlobalLayoutListener {
                if (activity.isFinishing || activity.isDestroyed) return@OnGlobalLayoutListener
                val next = signature(root)
                if (next != state.signature) {
                    styleTree(activity, root)
                    state.signature = signature(root)
                }
            }
            state.listener = listener
            state.observer = root.viewTreeObserver
            if (root.viewTreeObserver.isAlive) {
                root.viewTreeObserver.addOnGlobalLayoutListener(listener)
            }
        }
    }

    fun clear(activity: Activity) {
        val state = states.remove(activity) ?: return
        val listener = state.listener ?: return
        val observer = state.observer
        if (observer != null && observer.isAlive) {
            observer.removeOnGlobalLayoutListener(listener)
        } else {
            val decorObserver = activity.window?.decorView?.viewTreeObserver
            if (decorObserver != null && decorObserver.isAlive) {
                decorObserver.removeOnGlobalLayoutListener(listener)
            }
        }
    }

    private fun isExcluded(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return name == "br.com.centralmidia.android.ui.CoversActivity" ||
            name.startsWith("br.com.principaiscapas.")
    }

    private fun applyWindowChrome(activity: Activity) {
        activity.window.statusBarColor = navyDeep
        activity.window.navigationBarColor = navyDeep
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun styleTree(activity: Activity, view: View) {
        when (view) {
            is CheckBox -> styleCheckBox(view, activity)
            is Button -> styleButton(view, activity)
            is MaterialCardView -> {
                view.setCardBackgroundColor(white)
                view.strokeColor = border
                view.strokeWidth = dp(activity, 1)
                view.cardElevation = dp(activity, 1).toFloat()
            }
            is EditText -> {
                view.backgroundTintList = ColorStateList.valueOf(border)
                view.setTextColor(remapTextColor(view.currentTextColor))
                view.setHintTextColor(muted)
            }
            is TextView -> styleText(view)
            is ImageView -> styleImage(view)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                styleTree(activity, view.getChildAt(i))
            }
        }

        when (view.contentDescription?.toString()) {
            HEADER_DESCRIPTION -> styleHeader(activity, view as? ViewGroup)
            DOCK_DESCRIPTION -> styleDock(activity, view as? ViewGroup)
        }

        if (view === activity.findViewById<View>(android.R.id.content)) {
            view.setBackgroundColor(page)
        }
    }

    private fun styleText(view: TextView) {
        view.setTextColor(remapTextColor(view.currentTextColor))
    }

    private fun remapTextColor(color: Int): Int = when (color) {
        Color.rgb(21, 59, 101), Color.rgb(21, 43, 73), Color.rgb(10, 43, 99) -> navy
        Color.rgb(92, 117, 147), Color.rgb(96, 121, 165) -> muted
        Color.rgb(52, 137, 226), Color.rgb(31, 105, 190), Color.rgb(20, 126, 246) -> navyBlue
        else -> color
    }

    private fun styleCheckBox(view: CheckBox, activity: Activity) {
        view.setTextColor(remapTextColor(view.currentTextColor))
        view.buttonTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(navyBlue, border),
        )
    }

    private fun styleButton(button: Button, activity: Activity) {
        val label = button.text?.toString().orEmpty().trim().uppercase()
        val current = button.currentTextColor
        val blueText = current == Color.rgb(52, 137, 226) ||
            current == Color.rgb(31, 105, 190) || current == Color.rgb(20, 126, 246)
        val hasWhiteText = current == Color.WHITE

        when {
            label.contains("GERAR PDF") || label == "EXPORTAR PDF" || label == "CRIAR PDF" -> {
                button.backgroundTintList = ColorStateList.valueOf(gold)
                button.setTextColor(navyDeep)
                if (button is MaterialButton) {
                    button.cornerRadius = dp(activity, 12)
                    button.strokeWidth = 0
                }
            }
            label.contains("CANCELAR") || label.contains("EXCLUIR") || label.contains("APAGAR") ||
                label == "SAIR" || label.contains("REMOVER") || label == "PARAR" -> {
                if (hasWhiteText) {
                    button.backgroundTintList = ColorStateList.valueOf(danger)
                    button.setTextColor(white)
                } else if (blueText) {
                    button.backgroundTintList = ColorStateList.valueOf(dangerWash)
                    button.setTextColor(danger)
                }
                if (button is MaterialButton) button.strokeColor = ColorStateList.valueOf(danger)
            }
            hasWhiteText -> {
                button.backgroundTintList = ColorStateList.valueOf(navy)
                button.setTextColor(white)
                if (button is MaterialButton) {
                    button.cornerRadius = dp(activity, 12)
                    button.strokeWidth = 0
                }
            }
            blueText -> {
                button.backgroundTintList = ColorStateList.valueOf(white)
                button.setTextColor(navyBlue)
                if (button is MaterialButton) {
                    button.strokeColor = ColorStateList.valueOf(border)
                    button.strokeWidth = dp(activity, 1)
                    button.cornerRadius = dp(activity, 11)
                }
            }
            else -> button.setTextColor(remapTextColor(current))
        }
    }

    private fun styleImage(view: ImageView) {
        val tint = view.imageTintList?.defaultColor ?: return
        val mapped = remapTextColor(tint)
        if (mapped != tint) view.imageTintList = ColorStateList.valueOf(mapped)
    }

    private fun styleHeader(activity: Activity, header: ViewGroup?) {
        header ?: return
        header.background = gradient(navy, navyDeep, dp(activity, 16), gold, dp(activity, 1))
        header.elevation = dp(activity, 2).toFloat()

        visit(header) { child ->
            when (child) {
                is TextView -> {
                    val label = child.text?.toString().orEmpty()
                    if (label.contains("Central Inteligente", true)) {
                        child.setTextColor(white)
                        child.setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
                    } else if (label.contains("Monitoramento", true) || label.contains("produção de informação", true)) {
                        child.setTextColor(Color.rgb(218, 227, 238))
                    }
                }
                is Button -> {
                    val label = child.text?.toString().orEmpty()
                    if (label.contains("PDF", true) || label.contains("CONFIG", true) || label.contains("⚙")) {
                        child.backgroundTintList = ColorStateList.valueOf(white)
                        child.setTextColor(navy)
                        if (child is MaterialButton) child.strokeColor = ColorStateList.valueOf(gold)
                    }
                }
            }
            if (child.contentDescription?.toString().equals("Minha conta", true)) {
                child.background = oval(gold, dp(activity, 1), white)
                if (child is TextView) child.setTextColor(navyDeep)
            }
        }
    }

    private fun styleDock(activity: Activity, dock: ViewGroup?) {
        dock ?: return
        dock.background = gradient(navy, navyDeep, dp(activity, 18), Color.rgb(43, 71, 98), dp(activity, 1))
        dock.elevation = dp(activity, 7).toFloat()

        visit(dock) { child ->
            if (child !is ViewGroup) return@visit
            val description = child.contentDescription?.toString().orEmpty()
            if (!description.contains("selecionada", true)) return@visit

            child.background = rounded(Color.argb(40, Color.red(gold), Color.green(gold), Color.blue(gold)), dp(activity, 16), gold, dp(activity, 1))
            if (child is LinearLayout && child.childCount > 0) {
                val iconBox = child.getChildAt(0) as? ViewGroup
                iconBox?.background = gradient(gold, goldDeep, dp(activity, 13), Color.argb(110, 255, 255, 255), dp(activity, 1))
                iconBox?.let { box ->
                    visit(box) { icon -> if (icon is ImageView) icon.setColorFilter(navyDeep) }
                }
                for (i in 1 until child.childCount) {
                    val part = child.getChildAt(i)
                    if (part is TextView) {
                        part.setTextColor(gold)
                        part.typeface = Typeface.DEFAULT_BOLD
                    } else if (part is View && i == child.childCount - 1) {
                        part.background = rounded(gold, dp(activity, 3), gold, 0)
                    }
                }
            }
        }
    }

    private inline fun visit(root: View, action: (View) -> Unit) {
        action(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) visit(root.getChildAt(i), action)
        }
    }

    private fun signature(root: View): Int {
        var hash = 17
        fun walk(view: View) {
            hash = hash * 31 + view.javaClass.name.hashCode()
            hash = hash * 31 + view.id
            hash = hash * 31 + (view.tag?.hashCode() ?: 0)
            if (view is ViewGroup) {
                hash = hash * 31 + view.childCount
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(root)
        return hash
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(fill: Int, radius: Int, stroke: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = radius.toFloat()
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    private fun gradient(start: Int, end: Int, radius: Int, stroke: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
            cornerRadius = radius.toFloat()
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    private fun oval(fill: Int, strokeWidth: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }
}
