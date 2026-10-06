package br.com.centralmidia.android.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import br.com.principaiscapas.ModernMainActivity
import br.com.centralmidia.android.core.dp

/**
 * Entrada do módulo Capas.
 *
 * O módulo continua usando a mesma tela e toda a lógica existente. Esta
 * classe apenas aplica um acabamento visual ao painel rolável de resultados,
 * evitando que o espaço restante fique solto no fundo da tela.
 */
class CoversActivity : ModernMainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.post {
            applyResultsPanelStyle()
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post {
            applyResultsPanelStyle()
        }
    }

    private fun applyResultsPanelStyle() {
        val root = findViewById<View>(android.R.id.content) ?: return
        val scroll = findFirstScrollView(root) ?: return

        scroll.setBackground(
            MobileUi.rounded(
                Color.WHITE,
                this.dp(14).toFloat(),
                MobileUi.BORDER,
                this.dp(1),
            ),
        )

        scroll.setPadding(
            this.dp(4),
            this.dp(4),
            this.dp(4),
            this.dp(8),
        )
        scroll.clipToPadding = false
        scroll.elevation = this.dp(1).toFloat()
    }

    private fun findFirstScrollView(view: View): ScrollView? {
        if (view is ScrollView) return view

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findFirstScrollView(view.getChildAt(index))
                if (found != null) return found
            }
        }

        return null
    }
}
