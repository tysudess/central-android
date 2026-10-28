package br.com.centralmidia.android.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import br.com.centralmidia.android.core.VideoSearchClient
import br.com.centralmidia.android.core.dp

/**
 * Cartão reutilizável para expor por que cada fonte/consulta retornou ou descartou vídeos.
 * A tela chamadora decide quando atualizar/remover o cartão após cada busca.
 */
object VideoDiagnosticsCard {
    fun create(
        context: Context,
        diagnostics: List<VideoSearchClient.SourceDiagnostic>,
    ): View {
        val card = MobileUi.card(context, 12)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
        }

        content.addView(
            MobileUi.text(context, "Diagnóstico da busca", 15f, MobileUi.NAVY, true),
        )
        val totalCandidates = diagnostics.sumOf { it.candidates }
        val totalMatched = diagnostics.sumOf { it.matched }
        val totalUnknownDates = diagnostics.sumOf { it.unknownDates }
        val totalResolveFailures = diagnostics.sumOf { it.resolveFailures }
        val totalErrors = diagnostics.count { it.collectionError.isNotBlank() }
        content.addView(
            MobileUi.text(
                context,
                "${diagnostics.size} consulta(s) • $totalCandidates candidato(s) • " +
                    "$totalMatched correspondência(s) • $totalUnknownDates sem data • " +
                    "$totalResolveFailures falha(s) ao abrir páginas • $totalErrors erro(s) de coleta",
                11f,
                MobileUi.MUTED,
            ).apply { setPadding(0, context.dp(5), 0, context.dp(9)) },
        )

        val details = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        diagnostics.forEach { diagnostic ->
            val summary = diagnostic.summary()
            details.addView(
                MobileUi.text(context, summary, 10.5f, MobileUi.NAVY).apply {
                    setPadding(context.dp(9), context.dp(8), context.dp(9), context.dp(8))
                    background = MobileUi.rounded(
                        Color.rgb(247, 249, 253),
                        context.dp(8).toFloat(),
                        MobileUi.BORDER,
                        context.dp(1),
                    )
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = context.dp(5) },
            )
        }
        if (diagnostics.isEmpty()) {
            details.addView(
                MobileUi.text(context, "Nenhum diagnóstico foi recebido nesta execução.", 11f, MobileUi.MUTED),
            )
        }

        lateinit var toggle: MaterialButton
        toggle = MobileUi.button(context, "Ver detalhes por fonte", false, MobileUi.BLUE) {
            val expanded = details.visibility != View.VISIBLE
            details.visibility = if (expanded) View.VISIBLE else View.GONE
            toggle.text = if (expanded) "Ocultar detalhes por fonte" else "Ver detalhes por fonte"
        }
        content.addView(toggle)
        content.addView(details, MobileUi.match(context.dp(6)))
        card.addView(content)
        return card
    }
}
