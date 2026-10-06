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
 * Mantém toda a tela e a lógica original do módulo. O acabamento aplicado
 * aqui limita o quadro branco ao conteúdo real da lista, evitando que o
 * espaço restante do ScrollView apareça como um grande painel vazio.
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
        val panel = scroll.getChildAt(0) as? ViewGroup ?: return

        // O ScrollView continua ocupando o espaço necessário para rolagem,
        // mas fica transparente e sem contorno próprio.
        scroll.background = null
        scroll.elevation = 0f
        scroll.setPadding(0, 0, 0, 0)
        scroll.clipToPadding = false

        // O quadro acompanha apenas a altura real da lista de capas.
        panel.setBackground(
            MobileUi.rounded(
                Color.WHITE,
                this.dp(14).toFloat(),
                MobileUi.BORDER,
                this.dp(1),
            ),
        )
        panel.setPadding(
            this.dp(4),
            this.dp(4),
            this.dp(4),
            this.dp(8),
        )
        panel.clipToPadding = false
        panel.elevation = this.dp(1).toFloat()
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
