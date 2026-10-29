package br.com.centralmidia.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import br.com.centralmidia.android.core.VideoSearchClient
import br.com.centralmidia.android.core.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Resumo de diagnóstico da busca de vídeos.
 *
 * Limita as linhas de detalhe mostradas para a interface não criar milhares
 * de Views quando uma busca contém muitas combinações de fontes e termos.
 * O relatório copiado agrega informações por fonte e mantém exemplos das
 * consultas relevantes para compartilhar em uma investigação.
 */
object VideoDiagnosticsCard {
    private const val MAX_VISIBLE_DETAILS = 60
    private const val MAX_REPORT_LINES = 240

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
        val totalResolved = diagnostics.sumOf { it.resolvedAttempts }
        val totalResolveFailures = diagnostics.sumOf { it.resolveFailures }
        val totalUnknownDates = diagnostics.sumOf { it.unknownDates }
        val totalOutOfRange = diagnostics.sumOf { it.outOfRange }
        val totalTermMisses = diagnostics.sumOf { it.termMisses }
        val totalDemandMisses = diagnostics.sumOf { it.demandMisses }
        val totalMatched = diagnostics.sumOf { it.matched }
        val totalDuplicates = diagnostics.sumOf { it.duplicates }
        val collectionErrors = diagnostics.count { it.collectionError.isNotBlank() }
        val sourceCount = diagnostics.map { it.sourceId }.distinct().size

        content.addView(
            MobileUi.text(
                context,
                "$sourceCount fonte(s) • ${diagnostics.size} consulta(s) • $totalCandidates candidato(s) • " +
                    "$totalMatched correspondência(s)",
                11f,
                MobileUi.NAVY,
                true,
            ).apply { setPadding(0, context.dp(6), 0, context.dp(4)) },
        )
        content.addView(
            MobileUi.text(
                context,
                "$totalUnknownDates sem data • $totalOutOfRange fora do período • " +
                    "$totalResolveFailures/$totalResolved falha(s) ao abrir página(s) • " +
                    "$collectionErrors erro(s) de coleta • $totalTermMisses sem termo • " +
                    "$totalDemandMisses sem demanda • $totalDuplicates duplicata(s)",
                10.5f,
                MobileUi.MUTED,
            ).apply { setPadding(0, 0, 0, context.dp(8)) },
        )

        val interpretation = when {
            diagnostics.isEmpty() ->
                "A execução não retornou diagnósticos. Repita a busca para coletar detalhes."
            totalCandidates == 0 && collectionErrors > 0 ->
                "Nenhum candidato foi extraído e houve erro de coleta. Confira os erros por fonte e a conectividade."
            totalCandidates == 0 ->
                "As páginas consultadas não forneceram candidatos reconhecidos como vídeos. Confira as fontes e os seletores de coleta."
            totalMatched == 0 && totalUnknownDates > 0 ->
                "Foram coletados candidatos, mas muitos não têm data verificável. O filtro de período não aceita data inventada."
            totalMatched == 0 ->
                "Foram coletados candidatos, mas nenhum passou pelos filtros de período e correspondência dos termos/demandas."
            else ->
                "A coleta encontrou correspondências. Consulte as fontes com mais candidatos ou erros para avaliar cobertura."
        }
        val interpretationBackground =
            if (totalMatched == 0) Color.rgb(255, 246, 230) else Color.rgb(235, 249, 242)
        content.addView(
            MobileUi.text(context, interpretation, 11f, MobileUi.NAVY, true).apply {
                setPadding(context.dp(10), context.dp(9), context.dp(10), context.dp(9))
                background = MobileUi.rounded(
                    interpretationBackground,
                    context.dp(9).toFloat(),
                    MobileUi.BORDER,
                    context.dp(1),
                )
            },
        )

        val ranked = diagnostics.sortedWith(
            compareByDescending<VideoSearchClient.SourceDiagnostic> {
                it.collectionError.isNotBlank()
            }.thenByDescending { it.matched }
                .thenByDescending { it.candidates }
                .thenByDescending { it.resolveFailures }
                .thenByDescending { it.unknownDates },
        )
        val visible = ranked.take(MAX_VISIBLE_DETAILS)

        val details = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        visible.forEach { diagnostic ->
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
        if (diagnostics.size > visible.size) {
            details.addView(
                MobileUi.text(
                    context,
                    "Exibindo ${visible.size} de ${diagnostics.size} consultas. Copie o relatório para consultar o resumo por fonte.",
                    10.5f,
                    MobileUi.MUTED,
                ).apply { setPadding(context.dp(4), context.dp(8), context.dp(4), context.dp(4)) },
            )
        }

        lateinit var toggle: MaterialButton
        toggle = MobileUi.button(context, "Ver detalhes por fonte/consulta", false, MobileUi.BLUE) {
            val expanded = details.visibility != View.VISIBLE
            details.visibility = if (expanded) View.VISIBLE else View.GONE
            toggle.text = if (expanded) "Ocultar detalhes" else "Ver detalhes por fonte/consulta"
        }
        content.addView(toggle, MobileUi.match(context.dp(5)))

        val copyButton = MobileUi.button(
            context,
            "Copiar relatório de diagnóstico",
            false,
            MobileUi.NAVY,
        ) {
            val report = buildReport(diagnostics)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico da busca de vídeos", report))
            android.widget.Toast.makeText(context, "Relatório copiado.", android.widget.Toast.LENGTH_SHORT).show()
        }
        content.addView(copyButton, MobileUi.match(context.dp(5)))
        content.addView(details, MobileUi.match(context.dp(6)))
        card.addView(content)
        return card
    }

    private fun buildReport(
        diagnostics: List<VideoSearchClient.SourceDiagnostic>,
    ): String {
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        val grouped = diagnostics.groupBy { it.sourceId to it.sourceName }
        val header = buildString {
            appendLine("Central Inteligente de Mídia — diagnóstico da busca de vídeos")
            appendLine("Gerado em: $now")
            appendLine("Fontes: ${grouped.size} • Consultas: ${diagnostics.size}")
            appendLine("Candidatos: ${diagnostics.sumOf { it.candidates }}")
            appendLine("Correspondências: ${diagnostics.sumOf { it.matched }}")
            appendLine("Sem data: ${diagnostics.sumOf { it.unknownDates }}")
            appendLine("Fora do período: ${diagnostics.sumOf { it.outOfRange }}")
            appendLine("Falhas de resolução: ${diagnostics.sumOf { it.resolveFailures }}/${diagnostics.sumOf { it.resolvedAttempts }}")
            appendLine("Erros de coleta: ${diagnostics.count { it.collectionError.isNotBlank() }}")
            appendLine()
            appendLine("Resumo por fonte:")
        }
        val perSource = grouped.entries
            .map { (key, entries) ->
                val (id, name) = key
                val candidates = entries.sumOf { it.candidates }
                val matched = entries.sumOf { it.matched }
                val unknownDates = entries.sumOf { it.unknownDates }
                val outOfRange = entries.sumOf { it.outOfRange }
                val resolveFailures = entries.sumOf { it.resolveFailures }
                val resolved = entries.sumOf { it.resolvedAttempts }
                val errors = entries.filter { it.collectionError.isNotBlank() }
                buildString {
                    append("$name ($id): ")
                    append("consultas=${entries.size}, candidatos=$candidates, correspondências=$matched, ")
                    append("sem data=$unknownDates, fora do período=$outOfRange, ")
                    append("falhas ao abrir=$resolveFailures/$resolved")
                    if (errors.isNotEmpty()) {
                        append(", erros=")
                        append(errors.map { it.collectionError }.distinct().joinToString(" | ").take(240))
                    }
                }
            }
            .sorted()
        val topDiagnostics = diagnostics.sortedWith(
            compareByDescending<VideoSearchClient.SourceDiagnostic> { it.collectionError.isNotBlank() }
                .thenByDescending { it.candidates }
                .thenByDescending { it.matched }
                .thenByDescending { it.unknownDates },
        ).take(MAX_REPORT_LINES)
        return buildString {
            append(header)
            perSource.forEach { appendLine(it) }
            appendLine()
            appendLine("Consultas mais relevantes (limite $MAX_REPORT_LINES):")
            topDiagnostics.forEach { appendLine(it.summary()) }
            if (diagnostics.size > topDiagnostics.size) {
                appendLine("Relatório resumido: ${diagnostics.size - topDiagnostics.size} consulta(s) omitida(s) da seção detalhada.")
            }
        }
    }
}
