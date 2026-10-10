package br.com.centralmidia.android.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        val shell = content.getChildAt(0) as? ViewGroup ?: return
        val moduleRoot = shell.getChildAt(0) as? LinearLayout ?: return

        compactTopAreaAndRestoreListHeight(moduleRoot)

        val scroll = findFirstScrollView(moduleRoot) ?: return
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


    /**
     * O módulo Capas tem sua própria rolagem e já fica acima da navegação
     * inferior. O scaffold estava reservando mais 78 dp dentro da tela,
     * encurtando a lista e deixando uma faixa vazia sem utilidade.
     * Aproveita também para compactar o cabeçalho em telas estreitas.
     */
    private fun compactTopAreaAndRestoreListHeight(moduleRoot: LinearLayout) {
        // O padding original do módulo é 12 dp. Não reservar novamente o
        // espaço da navegação, que já é um irmão da tela dentro do scaffold.
        moduleRoot.setPadding(
            moduleRoot.paddingLeft,
            moduleRoot.paddingTop,
            moduleRoot.paddingRight,
            this.dp(12),
        )

        if (moduleRoot.childCount < 6) return

        val toolbar = moduleRoot.getChildAt(0) as? LinearLayout
        toolbar?.let { row ->
            row.layoutParams = row.layoutParams.apply {
                height = this@CoversActivity.dp(54)
            }
            row.setPadding(
                this.dp(10),
                this.dp(5),
                this.dp(8),
                this.dp(5),
            )

            findTextView(row, "PRINCIPAIS CAPAS")?.apply {
                setTextSize(18.5f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        }

        val dateCard = moduleRoot.getChildAt(1) as? LinearLayout
        dateCard?.let { card ->
            val params = card.layoutParams as? LinearLayout.LayoutParams
            params?.let {
                it.height = this.dp(58)
                it.topMargin = this.dp(5)
                it.bottomMargin = this.dp(4)
                card.layoutParams = it
            }
        }

        val actions = moduleRoot.getChildAt(2) as? LinearLayout
        actions?.let { row ->
            row.layoutParams = row.layoutParams.apply {
                height = this@CoversActivity.dp(46)
            }
            for (index in 0 until row.childCount) {
                val button = row.getChildAt(index) as? Button ?: continue
                val params = button.layoutParams as? LinearLayout.LayoutParams ?: continue
                params.height = this.dp(44)
                button.layoutParams = params
            }
        }

        // O título da lista e o seletor continuam acessíveis, com menos altura.
        val sectionHeader = moduleRoot.getChildAt(4)
        sectionHeader.layoutParams = sectionHeader.layoutParams.apply {
            height = this@CoversActivity.dp(36)
        }
    }

    private fun findTextView(view: View, wanted: String): TextView? {
        if (view is TextView && view.text?.toString()?.trim().equals(wanted, ignoreCase = true)) {
            return view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findTextView(view.getChildAt(index), wanted)
                if (found != null) return found
            }
        }
        return null
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
