package br.com.centralmidia.android.ui

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import br.com.centralmidia.android.core.CatalogRepository
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.GoogleNewsClient
import br.com.centralmidia.android.core.NewsItem
import br.com.centralmidia.android.core.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NewsActivity : BaseActivity() {
    private val client = GoogleNewsClient()
    private var items = listOf<NewsItem>()
    private lateinit var resultContainer: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    private lateinit var progress: ProgressBar
    private lateinit var foundValue: TextView
    private lateinit var newValue: TextView
    private lateinit var failureValue: TextView
    private lateinit var stepsValue: TextView
    private lateinit var timeValue: TextView
    private lateinit var resultCountLabel: TextView
    private var selectedPeriod = Period.DAY_24
    private var customAfter: String? = null
    private var customBefore: String? = null

    private enum class Period { TODAY, DAY_24, DAYS_7, DAYS_30, CUSTOM }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = MobileScaffold.page(
            this,
            "Notícias",
            "Acompanhe matérias em tempo real e transforme informação em decisões estratégicas.",
            MobileScaffold.Tab.NEWS,
            showAutomation = true,
        )

        val query = MobileUi.input(this@NewsActivity, "⌕  Buscar nas notícias (título, fonte, termo...)").apply {
            setText(intent.getStringExtra("query").orEmpty())
        }
        root.addView(query, MobileUi.match(14))

        val periodBar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val periodRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val periodButtons = linkedMapOf<Period, com.google.android.material.button.MaterialButton>()
        fun addPeriod(period: Period, label: String) {
            val button = MobileUi.button(this@NewsActivity, label, false) {
                selectedPeriod = period
                if (period == Period.CUSTOM) chooseCustomPeriod {
                    updatePeriodButtons(periodButtons)
                } else {
                    updatePeriodButtons(periodButtons)
                }
            }
            periodButtons[period] = button
            periodRow.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { marginEnd = dp(6) })
        }
        addPeriod(Period.TODAY, "Hoje")
        addPeriod(Period.DAY_24, "24 horas")
        addPeriod(Period.DAYS_7, "7 dias")
        addPeriod(Period.DAYS_30, "30 dias")
        addPeriod(Period.CUSTOM, "▣  Período personalizado")
        periodBar.addView(periodRow)
        root.addView(periodBar, MobileUi.match(10))
        updatePeriodButtons(periodButtons)

        val sources = listOf("Todas as fontes") + CatalogRepository.news(this).map { it.name }.distinct().sorted()
        val sourceSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@NewsActivity, android.R.layout.simple_spinner_dropdown_item, sources)
            background = MobileUi.rounded(Color.WHITE, dp(12).toFloat(), MobileUi.BORDER, dp(1))
            setPadding(dp(8), 0, dp(8), 0)
        }
        val onlyDemands = CheckBox(this).apply {
            text = "Só demandas"
            setTextColor(MobileUi.NAVY)
            textSize = 12f
        }
        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sourceSpinner, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
            addView(onlyDemands, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)))
        }
        root.addView(filters, MobileUi.match(8))

        val searchButton = MobileUi.button(this@NewsActivity, "↻  Buscar últimas 24h", true) {
            performSearch(query.text?.toString().orEmpty(), sourceSpinner, onlyDemands)
        }
        root.addView(searchButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(8) })

        root.addView(statusCard(), MobileUi.match(12))

        val resultsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        resultsHeader.addView(MobileUi.text(this@NewsActivity, "▤  Notícias encontradas", 18f, MobileUi.NAVY, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        resultsHeader.addView(MobileUi.statusChip(this@NewsActivity, "Mais recentes", MobileUi.BLUE))
        root.addView(resultsHeader, MobileUi.match(14))
        resultCountLabel = MobileUi.text(this@NewsActivity, "0 resultado(s) para o período selecionado", 11f, MobileUi.MUTED)
        root.addView(resultCountLabel, MobileUi.match(3))

        resultContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(resultContainer, MobileUi.match(8))
        renderEmpty()

        if (intent.getBooleanExtra("autoSearch", false) || query.text?.isNotBlank() == true) {
            searchButton.performClick()
        }
    }

    private fun statusCard(): View {
        val card = MobileUi.card(this@NewsActivity, 12)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val badge = MobileUi.text(this@NewsActivity, "✓", 25f, MobileUi.GREEN, true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.oval(Color.rgb(222, 248, 236))
        }
        top.addView(badge, LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginEnd = dp(10) })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusTitle = MobileUi.text(this@NewsActivity, "Pronto para buscar", 16f, MobileUi.NAVY, true)
        statusDescription = MobileUi.text(this@NewsActivity, "Escolha os filtros e execute uma busca.", 11f, MobileUi.MUTED)
        copy.addView(statusTitle)
        copy.addView(statusDescription, MobileUi.match(4))
        top.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(MobileUi.text(this@NewsActivity, "100%", 21f, MobileUi.GREEN, true))
        box.addView(top)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 100
            progressTintList = android.content.res.ColorStateList.valueOf(MobileUi.GREEN)
        }
        box.addView(progress, MobileUi.match(8))

        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val done = statValue("100%", "Conclusão", MobileUi.GREEN)
        foundValue = statValue("0", "Encontradas", MobileUi.ORANGE)
        newValue = statValue("0", "Novas", MobileUi.PURPLE)
        failureValue = statValue("0", "Falhas", MobileUi.PINK)
        stepsValue = statValue("0/1", "Etapas", MobileUi.BLUE)
        timeValue = statValue("00:00", "Tempo", MobileUi.BLUE)
        listOf(done, foundValue, newValue, failureValue, stepsValue, timeValue).forEachIndexed { index, view ->
            stats.addView(view, LinearLayout.LayoutParams(0, dp(70), 1f).apply {
                if (index > 0) marginStart = dp(2)
            })
        }
        box.addView(stats, MobileUi.match(8))
        card.addView(box)
        return card
    }

    private fun statValue(value: String, label: String, color: Int): TextView {
        return MobileUi.text(this@NewsActivity, "$value\n$label", 11f, color, true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(Color.rgb(250, 252, 255), dp(10).toFloat(), MobileUi.BORDER, dp(1))
            tag = label
        }
    }

    private fun updatePeriodButtons(buttons: Map<Period, com.google.android.material.button.MaterialButton>) {
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
        DatePickerDialog(this, { _, year, month, day ->
            customAfter = "%04d-%02d-%02d".format(year, month + 1, day)
            val end = Calendar.getInstance()
            DatePickerDialog(this, { _, ey, em, ed ->
                customBefore = "%04d-%02d-%02d".format(ey, em + 1, ed)
                selectedPeriod = Period.CUSTOM
                onDone()
            }, end.get(Calendar.YEAR), end.get(Calendar.MONTH), end.get(Calendar.DAY_OF_MONTH)).show()
        }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun performSearch(rawQuery: String, sourceSpinner: Spinner, onlyDemands: CheckBox) {
        val db = CentralDb(this)
        var base = rawQuery.trim()
        if (onlyDemands.isChecked) {
            val demandQuery = db.demands().joinToString(" OR ") { demand ->
                listOf(demand.second, demand.third).filter { it.isNotBlank() }.joinToString(" ")
            }
            if (demandQuery.isNotBlank()) base = listOf(base, "($demandQuery)").filter { it.isNotBlank() }.joinToString(" ")
        }
        if (base.isBlank()) {
            val terms = db.terms().take(8).joinToString(" OR ") { it.second }
            base = terms.ifBlank { "Brasil" }
        }
        if (sourceSpinner.selectedItemPosition > 0) base += " \"${sourceSpinner.selectedItem}\""
        val periodModifier = when (selectedPeriod) {
            Period.TODAY -> "after:${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}"
            Period.DAY_24 -> "when:1d"
            Period.DAYS_7 -> "when:7d"
            Period.DAYS_30 -> "when:30d"
            Period.CUSTOM -> listOfNotNull(customAfter?.let { "after:$it" }, customBefore?.let { "before:$it" }).joinToString(" ")
        }
        val effective = listOf(base, periodModifier).filter { it.isNotBlank() }.joinToString(" ")
        val started = SystemClock.elapsedRealtime()
        statusTitle.text = "Buscando notícias…"
        statusDescription.text = "Consultando fontes pela internet, sem proxy."
        progress.isIndeterminate = true
        failureValue.text = "0\nFalhas"
        stepsValue.text = "0/1\nEtapas"

        io({ client.search(effective, 50) }, { list ->
            val elapsed = (SystemClock.elapsedRealtime() - started) / 1000
            items = list
            progress.isIndeterminate = false
            progress.progress = 100
            statusTitle.text = "Busca concluída com sucesso"
            statusDescription.text = "A busca foi concluída. ${list.size} notícia(s) encontrada(s) nesta execução."
            foundValue.text = "${list.size}\nEncontradas"
            newValue.text = "0\nNovas"
            failureValue.text = "0\nFalhas"
            stepsValue.text = "1/1\nEtapas"
            timeValue.text = "%02d:%02d\nTempo".format(elapsed / 60, elapsed % 60)
            getSharedPreferences("dashboard_stats", MODE_PRIVATE).edit().putInt("last_news_count", list.size).apply()
            db.addHistory("news", "Busca: $effective", "${list.size} resultados")
            resultCountLabel.text = "${list.size} resultado(s) para o período selecionado"
            renderResults()
        }, { error ->
            progress.isIndeterminate = false
            progress.progress = 0
            statusTitle.text = "Falha na busca"
            statusDescription.text = error.message ?: "Não foi possível concluir a busca."
            failureValue.text = "1\nFalhas"
            stepsValue.text = "0/1\nEtapas"
            toast(error.message ?: "Falha ao buscar notícias")
        })
    }

    private fun renderEmpty() {
        resultContainer.removeAllViews()
        if (::resultCountLabel.isInitialized) resultCountLabel.text = "0 resultado(s) para o período selecionado"
        val empty = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        box.addView(MobileUi.text(this@NewsActivity, "▤", 34f, Color.rgb(164, 187, 218), true).apply { gravity = Gravity.CENTER })
        box.addView(MobileUi.text(this@NewsActivity, "Nenhuma notícia exibida", 16f, MobileUi.NAVY, true).apply { gravity = Gravity.CENTER }, MobileUi.match(8))
        box.addView(MobileUi.text(this@NewsActivity, "Execute uma busca para visualizar as matérias encontradas.", 12f, MobileUi.MUTED).apply { gravity = Gravity.CENTER }, MobileUi.match(4))
        empty.addView(box)
        resultContainer.addView(empty, MobileUi.match())
    }

    private fun renderResults() {
        resultContainer.removeAllViews()
        if (items.isEmpty()) {
            renderEmpty()
            return
        }
        items.forEach { item -> resultContainer.addView(newsCard(item), MobileUi.match(8)) }
    }

    private fun newsCard(item: NewsItem): View {
        val card = MobileUi.card(this@NewsActivity, 10)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val initials = item.source.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "NT" }.take(2)
        val badge = MobileUi.text(this@NewsActivity, initials, 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(MobileUi.BLUE, dp(11).toFloat())
        }
        row.addView(badge, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(10) })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(MobileUi.text(this@NewsActivity, "${item.source.ifBlank { "Fonte" }}  •  ${formatDate(item.pubDate)}", 10.5f, MobileUi.MUTED))
        copy.addView(MobileUi.text(this@NewsActivity, item.title, 14f, MobileUi.NAVY, true), MobileUi.match(4))
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row)

        val actionsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(MobileUi.button(this@NewsActivity, "↗ Abrir matéria") { openUrl(item.link) }, actionLp())
        actions.addView(MobileUi.button(this@NewsActivity, "◉ WhatsApp", false, MobileUi.GREEN) { shareWhatsApp(item) }, actionLp())
        actions.addView(MobileUi.button(this@NewsActivity, "↗ Copiar link") { copyLink(item.link) }, actionLp())
        actions.addView(MobileUi.button(this@NewsActivity, "▤ Extrair matéria", false, MobileUi.PURPLE) {
            startActivity(Intent(this, NewsExtractorActivity::class.java).putExtra("url", item.link))
        }, actionLp())
        actionsScroll.addView(actions)
        box.addView(actionsScroll, MobileUi.match(10))
        card.addView(box)
        return card
    }

    private fun actionLp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)).apply { marginEnd = dp(6) }

    private fun shareWhatsApp(item: NewsItem) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${item.title}\n${item.link}")
            setPackage("com.whatsapp")
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${item.title}\n${item.link}")
            }, "Compartilhar matéria"))
        }
    }

    private fun copyLink(link: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Link da notícia", link))
        toast("Link copiado.")
    }

    private fun formatDate(raw: String): String {
        if (raw.isBlank()) return "Agora"
        val parsers = listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        )
        for (parser in parsers) {
            runCatching { parser.parse(raw) }.getOrNull()?.let {
                return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(it)
            }
        }
        return raw
    }
}
