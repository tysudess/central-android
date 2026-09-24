package br.com.centralmidia.android.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import br.com.centralmidia.android.core.CatalogRepository
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.VideoSource
import br.com.centralmidia.android.core.dp
import java.net.URLEncoder
import java.util.Calendar

class VideosActivity : BaseActivity() {
    private var selectedPeriod = Period.DAY_24
    private var customLabel = ""
    private lateinit var resultContainer: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    private lateinit var progress: ProgressBar
    private lateinit var foundValue: TextView
    private lateinit var timeValue: TextView
    private val sources by lazy { CatalogRepository.videos(this) }

    private enum class Period { DAY_24, DAYS_7, DAYS_30, CUSTOM }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = MobileScaffold.page(
            this,
            "Vídeos",
            "Busca e acompanhamento de vídeos relevantes.",
            MobileScaffold.Tab.VIDEOS,
            showAutomation = false,
        )

        val query = MobileUi.input(this@VideosActivity, "⌕  Buscar nos vídeos (título, fonte, termo...)")
        root.addView(query, MobileUi.match(14))

        val periodBar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val buttons = linkedMapOf<Period, com.google.android.material.button.MaterialButton>()
        fun add(period: Period, label: String) {
            val button = MobileUi.button(this@VideosActivity, label) {
                selectedPeriod = period
                if (period == Period.CUSTOM) chooseCustomPeriod { updateButtons(buttons) } else updateButtons(buttons)
            }
            buttons[period] = button
            row.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { marginEnd = dp(6) })
        }
        add(Period.DAY_24, "24 horas")
        add(Period.DAYS_7, "7 dias")
        add(Period.DAYS_30, "30 dias")
        add(Period.CUSTOM, "Período personalizado")
        periodBar.addView(row)
        root.addView(periodBar, MobileUi.match(10))
        updateButtons(buttons)

        val searchButton = MobileUi.button(this@VideosActivity, "↻  Buscar vídeos agora", true) {
            performSearch(query.text?.toString().orEmpty())
        }
        root.addView(searchButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) })

        root.addView(statusCard(), MobileUi.match(12))

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(MobileUi.text(this@VideosActivity, "▤  Vídeos encontrados", 18f, MobileUi.NAVY, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(MobileUi.text(this@VideosActivity, "${sources.size} fonte(s)", 11f, MobileUi.MUTED))
        root.addView(header, MobileUi.match(14))

        resultContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(resultContainer, MobileUi.match(8))
        renderEmpty()

        if (intent.getBooleanExtra("autoSearch", false)) searchButton.performClick()
    }

    private fun statusCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val badge = MobileUi.text(this@VideosActivity, "✓", 27f, MobileUi.GREEN, true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.oval(Color.rgb(222, 248, 236))
        }
        top.addView(badge, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginEnd = dp(10) })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusTitle = MobileUi.text(this@VideosActivity, "Vídeos • pronto para pesquisar", 18f, MobileUi.NAVY, true)
        statusDescription = MobileUi.text(this@VideosActivity, "Escolha um termo e pesquise nas fontes sincronizadas.", 12f, MobileUi.MUTED)
        copy.addView(statusTitle)
        copy.addView(statusDescription, MobileUi.match(4))
        top.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(top)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 100
            progressTintList = android.content.res.ColorStateList.valueOf(MobileUi.BLUE)
        }
        box.addView(progress, MobileUi.match(10))

        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statValue("100%", "Conclusão", MobileUi.BLUE), LinearLayout.LayoutParams(0, dp(70), 1f))
        foundValue = statValue("0", "Encontrados", MobileUi.BLUE)
        stats.addView(foundValue, LinearLayout.LayoutParams(0, dp(70), 1f))
        stats.addView(statValue("0", "Novos", MobileUi.BLUE), LinearLayout.LayoutParams(0, dp(70), 1f))
        stats.addView(statValue("0", "Falhas", MobileUi.BLUE), LinearLayout.LayoutParams(0, dp(70), 1f))
        stats.addView(statValue("1/1", "Etapas", MobileUi.BLUE), LinearLayout.LayoutParams(0, dp(70), 1f))
        timeValue = statValue("00:00", "Tempo", MobileUi.BLUE)
        stats.addView(timeValue, LinearLayout.LayoutParams(0, dp(70), 1f))
        box.addView(stats, MobileUi.match(10))
        card.addView(box)
        return card
    }

    private fun statValue(value: String, label: String, color: Int) = MobileUi.text(this@VideosActivity, "$value\n$label", 10.5f, color, true).apply {
        gravity = Gravity.CENTER
    }

    private fun updateButtons(buttons: Map<Period, com.google.android.material.button.MaterialButton>) {
        buttons.forEach { (period, button) ->
            val selected = period == selectedPeriod
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selected) MobileUi.BLUE_TINT else Color.WHITE)
            button.setTextColor(if (selected) MobileUi.BLUE else MobileUi.NAVY)
            button.strokeColor = android.content.res.ColorStateList.valueOf(if (selected) MobileUi.BLUE else MobileUi.BORDER)
            button.strokeWidth = dp(1)
        }
    }

    private fun chooseCustomPeriod(onDone: () -> Unit) {
        val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        DatePickerDialog(this, { _, sy, sm, sd ->
            val from = "%02d/%02d/%04d".format(sd, sm + 1, sy)
            val end = Calendar.getInstance()
            DatePickerDialog(this, { _, ey, em, ed ->
                customLabel = "$from a ${"%02d/%02d/%04d".format(ed, em + 1, ey)}"
                selectedPeriod = Period.CUSTOM
                onDone()
            }, end.get(Calendar.YEAR), end.get(Calendar.MONTH), end.get(Calendar.DAY_OF_MONTH)).show()
        }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun performSearch(rawQuery: String) {
        val started = SystemClock.elapsedRealtime()
        statusTitle.text = "Pesquisando fontes de vídeo…"
        statusDescription.text = "Preparando os links de pesquisa pela conexão direta."
        progress.isIndeterminate = true

        val query = rawQuery.trim()
        val periodText = when (selectedPeriod) {
            Period.DAY_24 -> "últimas 24 horas"
            Period.DAYS_7 -> "últimos 7 dias"
            Period.DAYS_30 -> "últimos 30 dias"
            Period.CUSTOM -> customLabel
        }
        val effectiveQuery = listOf(query, periodText).filter { it.isNotBlank() }.joinToString(" ")

        val visibleSources = if (query.isBlank()) {
            sources
        } else {
            sources.sortedByDescending { source ->
                val haystack = "${source.name} ${source.group} ${source.state} ${source.region}".lowercase()
                if (query.lowercase() in haystack) 1 else 0
            }
        }

        val elapsed = (SystemClock.elapsedRealtime() - started) / 1000
        progress.isIndeterminate = false
        progress.progress = 100
        statusTitle.text = "Vídeos • pesquisa preparada"
        statusDescription.text = "${visibleSources.size} fonte(s) disponível(is) para pesquisar ${if (query.isBlank()) "" else "por “$query”"}."
        foundValue.text = "${visibleSources.size}\nEncontrados"
        timeValue.text = "%02d:%02d\nTempo".format(elapsed / 60, elapsed % 60)
        getSharedPreferences("dashboard_stats", MODE_PRIVATE).edit().putInt("last_video_count", visibleSources.size).apply()
        CentralDb(this).addHistory("video", "Busca de vídeos: ${query.ifBlank { "todas as fontes" }}", "${visibleSources.size} fontes")
        renderSources(visibleSources, effectiveQuery)
    }

    private fun renderEmpty() {
        resultContainer.removeAllViews()
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(10), dp(22), dp(10), dp(22)) }
        box.addView(MobileUi.text(this@VideosActivity, "▶", 36f, Color.rgb(140, 171, 215), true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(Color.rgb(235, 242, 252), dp(16).toFloat())
            setPadding(dp(22), dp(16), dp(22), dp(16))
        })
        box.addView(MobileUi.text(this@VideosActivity, "Nenhum vídeo exibido", 17f, MobileUi.NAVY, true).apply { gravity = Gravity.CENTER }, MobileUi.match(14))
        box.addView(MobileUi.text(this@VideosActivity, "Execute uma busca para acessar as fontes e pesquisar os vídeos desejados.", 12f, MobileUi.MUTED).apply { gravity = Gravity.CENTER }, MobileUi.match(6))
        card.addView(box)
        resultContainer.addView(card)
    }

    private fun renderSources(data: List<VideoSource>, effectiveQuery: String) {
        resultContainer.removeAllViews()
        if (data.isEmpty()) {
            renderEmpty()
            return
        }
        data.forEach { source -> resultContainer.addView(sourceCard(source, effectiveQuery), MobileUi.match(8)) }
    }

    private fun sourceCard(source: VideoSource, effectiveQuery: String): View {
        val card = MobileUi.card(this@VideosActivity, 12)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val badge = MobileUi.text(this@VideosActivity, "▶", 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(MobileUi.BLUE, dp(12).toFloat())
        }
        row.addView(badge, LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginEnd = dp(10) })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(MobileUi.text(this@VideosActivity, source.name, 15f, MobileUi.NAVY, true))
        copy.addView(MobileUi.text(this@VideosActivity, listOf(source.group, source.state, source.region).filter { it.isNotBlank() }.joinToString(" • "), 11f, MobileUi.MUTED), MobileUi.match(3))
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(MobileUi.button(this@VideosActivity, if (effectiveQuery.isBlank()) "Abrir fonte" else "Pesquisar nesta fonte", true) {
            val query = listOf(source.searchPrefix, effectiveQuery).filter { it.isNotBlank() }.joinToString(" ")
            val url = when {
                query.isBlank() -> source.landingUrl
                source.searchUrlTemplate.isNotBlank() -> source.searchUrlTemplate.replace("{query}", URLEncoder.encode(query, "UTF-8"))
                else -> source.landingUrl
            }
            openUrl(url)
            CentralDb(this).addHistory("video", "${source.name}: $query", url = url)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
        actions.addView(MobileUi.button(this@VideosActivity, "Extrator", false, MobileUi.PURPLE) {
            startActivity(Intent(this, VideoExtractorActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        box.addView(actions, MobileUi.match(10))
        card.addView(box)
        return card
    }
}
