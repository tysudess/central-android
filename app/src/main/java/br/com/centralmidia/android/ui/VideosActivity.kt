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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CatalogRepository
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.SourcePreferences
import br.com.centralmidia.android.core.VideoSearchClient
import br.com.centralmidia.android.core.dp
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class VideosActivity : BaseActivity() {
    private lateinit var db: CentralDb
    private lateinit var sourcePrefs: SourcePreferences
    private val searchClient = VideoSearchClient()

    private lateinit var queryInput: android.widget.EditText
    private lateinit var resultContainer: LinearLayout
    private lateinit var shownLabel: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    private lateinit var progress: ProgressBar
    private lateinit var pctValue: TextView
    private lateinit var foundValue: TextView
    private lateinit var newValue: TextView
    private lateinit var failureValue: TextView
    private lateinit var stepsValue: TextView
    private lateinit var timeValue: TextView
    private lateinit var stopButton: MaterialButton

    private var selectedPeriod = Period.DAY_24
    private var customFrom: Long? = null
    private var customTo: Long? = null
    private var items = listOf<VideoSearchClient.VideoItem>()

    @Volatile
    private var cancelRequested = false

    private enum class Period { DAY_24, DAYS_7, DAYS_30, CUSTOM }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = CentralDb(this)
        sourcePrefs = SourcePreferences(this)

        val root = MobileScaffold.page(
            this,
            "Vídeos",
            "Busca os termos cadastrados nas fontes de vídeo e exibe somente resultados encontrados.",
            MobileScaffold.Tab.VIDEOS,
            showAutomation = true,
        )

        queryInput = MobileUi.input(
            this,
            "Filtrar resultados (título, fonte, termo...)",
        ).apply {
            doAfterTextChanged { renderResults() }
        }
        root.addView(queryInput, MobileUi.match(dp(14)))

        val periodButtons = linkedMapOf<Period, MaterialButton>()
        val periodScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val periodRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        fun addPeriod(period: Period, label: String) {
            val button = MobileUi.button(this, label, false) {
                if (period == Period.CUSTOM) {
                    chooseCustomPeriod {
                        selectedPeriod = Period.CUSTOM
                        updatePeriodButtons(periodButtons)
                        performSearch()
                    }
                } else {
                    selectedPeriod = period
                    updatePeriodButtons(periodButtons)
                    performSearch()
                }
            }
            periodButtons[period] = button
            periodRow.addView(
                button,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                    marginEnd = dp(6)
                },
            )
        }

        addPeriod(Period.DAY_24, "24 horas")
        addPeriod(Period.DAYS_7, "7 dias")
        addPeriod(Period.DAYS_30, "30 dias")
        addPeriod(Period.CUSTOM, "Período personalizado")
        periodScroll.addView(periodRow)
        root.addView(periodScroll, MobileUi.match(dp(10)))
        updatePeriodButtons(periodButtons)

        root.addView(
            MobileUi.button(
                this,
                "Buscar vídeos agora",
                true,
                MobileUi.BLUE,
                R.drawable.ic_search,
            ) { performSearch() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                topMargin = dp(10)
            },
        )

        root.addView(searchConfigurationSummary(), MobileUi.match(dp(8)))
        root.addView(statusCard(), MobileUi.match(dp(12)))

        stopButton = MobileUi.button(
            this,
            "Parar busca",
            false,
            MobileUi.PINK,
            R.drawable.ic_delete,
        ) {
            cancelRequested = true
            statusTitle.text = "Interrompendo busca…"
            statusDescription.text = "A consulta atual será encerrada assim que a requisição em andamento terminar."
        }.apply { visibility = View.GONE }
        root.addView(stopButton, MobileUi.match(dp(8)))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(MobileUi.icon(this, R.drawable.ic_video, MobileUi.NAVY, 22))
        header.addView(
            MobileUi.text(this, "Vídeos encontrados", 20f, MobileUi.NAVY, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            },
        )
        shownLabel = MobileUi.text(this, "0 exibido(s)", 10.5f, MobileUi.MUTED)
        header.addView(shownLabel)
        root.addView(header, MobileUi.match(dp(16)))

        resultContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(resultContainer, MobileUi.match(dp(8)))
        renderEmpty()

        if (intent.getBooleanExtra("autoSearch", false)) {
            root.post { performSearch() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::resultContainer.isInitialized && items.isNotEmpty()) renderResults()
    }

    private fun searchConfigurationSummary(): View {
        val sources = sourcePrefs.selectedVideoSources(CatalogRepository.videos(this))
        val terms = db.terms("video")
        return MobileUi.text(
            this,
            "Busca real: ${terms.size} termo(s) × ${sources.size} fonte(s) selecionada(s). " +
                "A lista abaixo mostrará vídeos encontrados, não as fontes.",
            10.5f,
            MobileUi.GREEN,
            true,
        ).apply {
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = MobileUi.rounded(
                MobileUi.GREEN_TINT,
                dp(11).toFloat(),
                Color.rgb(187, 232, 210),
                dp(1),
            )
        }
    }

    private fun statusCard(): View {
        val card = MobileUi.card(this, 12)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badge = MobileUi.text(this, "✓", 25f, MobileUi.GREEN, true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.oval(Color.rgb(222, 248, 236))
        }
        top.addView(badge, LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginEnd = dp(10) })

        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusTitle = MobileUi.text(this, "Vídeos • última execução concluída", 16f, MobileUi.NAVY, true)
        statusDescription = MobileUi.text(this, "Pronto para pesquisar os termos cadastrados.", 11f, MobileUi.MUTED)
        copy.addView(statusTitle)
        copy.addView(statusDescription, MobileUi.match(dp(4)))
        top.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        pctValue = MobileUi.text(this, "100%", 20f, MobileUi.BLUE, true)
        top.addView(pctValue)
        box.addView(top)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 100
            progressTintList = android.content.res.ColorStateList.valueOf(MobileUi.BLUE)
        }
        box.addView(progress, MobileUi.match(dp(8)))

        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val done = metric("100%", "Conclusão")
        foundValue = metric("0", "Encontrados")
        newValue = metric("0", "Novos")
        failureValue = metric("0", "Falhas")
        stepsValue = metric("0/0", "Etapas")
        timeValue = metric("00:00", "Tempo")
        listOf(done, foundValue, newValue, failureValue, stepsValue, timeValue).forEach { view ->
            stats.addView(
                view,
                LinearLayout.LayoutParams(0, dp(68), 1f).apply { marginEnd = dp(2) },
            )
        }
        box.addView(stats, MobileUi.match(dp(8)))
        card.addView(box)
        return card
    }

    private fun metric(value: String, caption: String): TextView =
        MobileUi.text(this, "$value\n$caption", 10f, MobileUi.BLUE, true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                Color.rgb(250, 252, 255),
                dp(10).toFloat(),
                MobileUi.BORDER,
                dp(1),
            )
        }

    private fun performSearch() {
        if (stopButton.visibility == View.VISIBLE) {
            toast("Uma busca de vídeos já está em andamento.")
            return
        }

        val terms = db.terms("video").map { it.second.trim() }.filter { it.isNotBlank() }
        val allSources = CatalogRepository.videos(this)
        val sources = sourcePrefs.selectedVideoSources(allSources)
        val demands = db.demands()

        if (terms.isEmpty() && demands.isEmpty()) {
            toast("Cadastre ao menos um termo de Vídeos ou uma demanda.")
            return
        }
        if (sources.isEmpty()) {
            toast("Selecione ao menos uma fonte de vídeo na aba Fontes.")
            return
        }

        val (fromMs, toMs) = selectedRange()
        cancelRequested = false
        stopButton.visibility = View.VISIBLE
        progress.isIndeterminate = false
        progress.progress = 0
        pctValue.text = "0%"
        foundValue.text = "0\nEncontrados"
        newValue.text = "0\nNovos"
        failureValue.text = "0\nFalhas"
        stepsValue.text = "0/0\nEtapas"
        statusTitle.text = "Vídeos • busca em andamento"
        statusDescription.text = "Preparando ${sources.size} fonte(s) e ${terms.size} termo(s)…"

        val started = SystemClock.elapsedRealtime()

        io(
            {
                searchClient.search(
                    sources = sources,
                    terms = terms,
                    demands = demands,
                    fromMs = fromMs,
                    toMs = toMs,
                    cancelled = { cancelRequested },
                    onProgress = { update ->
                        runOnUiThread {
                            val pct = if (update.total <= 0) 0 else update.completed * 100 / update.total
                            progress.progress = pct.coerceIn(0, 100)
                            pctValue.text = "$pct%"
                            foundValue.text = "${update.found}\nEncontrados"
                            failureValue.text = "${update.errors}\nFalhas"
                            stepsValue.text = "${update.completed}/${update.total}\nEtapas"
                            statusDescription.text = "${update.currentSource} • ${update.currentQuery}"
                        }
                    },
                )
            },
            { outcome ->
                stopButton.visibility = View.GONE
                val elapsed = SystemClock.elapsedRealtime() - started

                val seenPrefs = getSharedPreferences("video_seen_links", MODE_PRIVATE)
                val oldSeen = seenPrefs.getStringSet("links", emptySet())?.toSet().orEmpty()
                items = outcome.items.map { item -> item.copy(isNew = item.link !in oldSeen) }
                val newCount = items.count { it.isNew }
                seenPrefs.edit()
                    .putStringSet(
                        "links",
                        (items.map { it.link } + oldSeen).filter { it.isNotBlank() }.distinct().take(2000).toSet(),
                    )
                    .apply()

                items.forEach { item ->
                    db.addHistoryUnique(
                        type = "video",
                        title = item.title,
                        detail = buildString {
                            append(item.sourceName)
                            val tag = item.matchedDemand.ifBlank { item.matchedTerm }
                            if (tag.isNotBlank()) append("\nTermo: ").append(tag)
                        },
                        url = item.link,
                    )
                }

                val pct = if (outcome.total <= 0) 100 else outcome.completed * 100 / outcome.total
                progress.progress = if (outcome.cancelled) pct.coerceIn(0, 100) else 100
                pctValue.text = "${progress.progress}%"
                foundValue.text = "${items.size}\nEncontrados"
                newValue.text = "$newCount\nNovos"
                failureValue.text = "${outcome.errors}\nFalhas"
                stepsValue.text = "${outcome.completed}/${outcome.total}\nEtapas"
                timeValue.text = formatDuration(elapsed)
                statusTitle.text = if (outcome.cancelled) "Vídeos • busca interrompida" else "Vídeos • última execução concluída"
                statusDescription.text =
                    if (outcome.cancelled) "Busca interrompida pelo usuário."
                    else "A busca foi concluída. ${items.size} vídeo(s) encontrado(s) nesta execução."

                getSharedPreferences("dashboard_stats", MODE_PRIVATE)
                    .edit()
                    .putInt("last_video_count", items.size)
                    .apply()

                renderResults()
            },
            { error ->
                stopButton.visibility = View.GONE
                progress.progress = 0
                pctValue.text = "0%"
                failureValue.text = "1\nFalhas"
                statusTitle.text = "Falha na busca de vídeos"
                statusDescription.text = error.message ?: "Não foi possível concluir a busca."
                toast(statusDescription.text.toString())
            },
        )
    }

    private fun renderResults() {
        if (!::resultContainer.isInitialized) return
        val q = queryInput.text?.toString().orEmpty().trim().lowercase(Locale.getDefault())
        val visible = items.filter { item ->
            q.isBlank() || q in (
                "${item.title} ${item.sourceName} ${item.matchedTerm} ${item.matchedDemand}"
            ).lowercase(Locale.getDefault())
        }

        shownLabel.text = "${visible.size} exibido(s)"
        resultContainer.removeAllViews()
        if (visible.isEmpty()) {
            renderEmpty(false)
            return
        }
        visible.forEach { item ->
            resultContainer.addView(videoCard(item), MobileUi.match(dp(8)))
        }
    }

    private fun videoCard(item: VideoSearchClient.VideoItem): View {
        val card = MobileUi.card(this, 11)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val iconBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(MobileUi.PURPLE_TINT, dp(14).toFloat())
            addView(MobileUi.icon(this@VideosActivity, R.drawable.ic_video, MobileUi.PURPLE, 25))
        }
        row.addView(iconBox, LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(10) })

        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val meta = buildString {
            append(item.sourceName)
            append("  •  ")
            append(formatDate(item.publishedAt))
            if (item.isNew) append("  •  NOVO")
        }
        copy.addView(MobileUi.text(this, meta, 9.5f, MobileUi.MUTED))
        copy.addView(
            MobileUi.text(this, item.title, 13.5f, MobileUi.NAVY, true),
            MobileUi.match(dp(4)),
        )
        val tag = item.matchedDemand.ifBlank { item.matchedTerm }
        if (tag.isNotBlank()) {
            copy.addView(
                MobileUi.text(this, "Termo: $tag", 9.5f, MobileUi.PURPLE, true).apply {
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    background = MobileUi.rounded(MobileUi.PURPLE_TINT, dp(8).toFloat())
                },
                MobileUi.wrap(dp(7)),
            )
        }
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row)

        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun add(button: MaterialButton) {
            actions.addView(
                button,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)).apply { marginEnd = dp(6) },
            )
        }
        add(MobileUi.button(this, "Abrir vídeo", false, MobileUi.NAVY, R.drawable.ic_open) { openUrl(item.link) })
        add(MobileUi.button(this, "WhatsApp", false, MobileUi.GREEN, R.drawable.ic_share) { shareWhatsApp(item) })
        add(MobileUi.button(this, "Copiar link", false, MobileUi.NAVY, R.drawable.ic_copy) { copyLink(item.link) })
        add(MobileUi.button(this, "Extrair vídeo", false, MobileUi.PURPLE, R.drawable.ic_video) {
            startActivity(
                Intent(this, VideoExtractorActivity::class.java)
                    .putExtra("url", item.link),
            )
        })
        scroll.addView(actions)
        box.addView(scroll, MobileUi.match(dp(10)))
        card.addView(box)
        return card
    }

    private fun shareWhatsApp(item: VideoSearchClient.VideoItem) {
        val text = "${item.title}\n${item.link}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    "Compartilhar vídeo",
                ),
            )
        }
    }

    private fun copyLink(link: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Link do vídeo", link))
        toast("Link copiado.")
    }

    private fun renderEmpty(updateLabel: Boolean = true) {
        if (!::resultContainer.isInitialized) return
        resultContainer.removeAllViews()
        if (updateLabel && ::shownLabel.isInitialized) shownLabel.text = "0 exibido(s)"
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(30), dp(10), dp(30))
        }
        box.addView(MobileUi.icon(this, R.drawable.ic_video, Color.rgb(149, 172, 207), 40))
        box.addView(
            MobileUi.text(this, "Nenhum vídeo encontrado", 17f, MobileUi.NAVY, true).apply {
                gravity = Gravity.CENTER
            },
            MobileUi.match(dp(12)),
        )
        box.addView(
            MobileUi.text(
                this,
                "Toque em Buscar vídeos agora. Os resultados encontrados pelos termos aparecerão aqui.",
                11.5f,
                MobileUi.MUTED,
            ).apply { gravity = Gravity.CENTER },
            MobileUi.match(dp(5)),
        )
        card.addView(box)
        resultContainer.addView(card)
    }

    private fun updatePeriodButtons(buttons: Map<Period, MaterialButton>) {
        buttons.forEach { (period, button) ->
            val selected = period == selectedPeriod
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (selected) MobileUi.BLUE_TINT else Color.WHITE,
            )
            button.setTextColor(if (selected) MobileUi.BLUE else MobileUi.NAVY)
            button.strokeColor = android.content.res.ColorStateList.valueOf(
                if (selected) MobileUi.BLUE else MobileUi.BORDER,
            )
            button.strokeWidth = dp(1)
        }
    }

    private fun chooseCustomPeriod(onDone: () -> Unit) {
        val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val from = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val end = Calendar.getInstance()
                DatePickerDialog(
                    this,
                    { _, ey, em, ed ->
                        val to = Calendar.getInstance().apply {
                            set(ey, em, ed, 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                        }
                        customFrom = from.timeInMillis
                        customTo = to.timeInMillis
                        onDone()
                    },
                    end.get(Calendar.YEAR),
                    end.get(Calendar.MONTH),
                    end.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH),
            start.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun selectedRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return when (selectedPeriod) {
            Period.DAY_24 -> now - 24L * 60L * 60L * 1000L to now
            Period.DAYS_7 -> now - 7L * 24L * 60L * 60L * 1000L to now
            Period.DAYS_30 -> now - 30L * 24L * 60L * 60L * 1000L to now
            Period.CUSTOM -> (customFrom ?: now - 24L * 60L * 60L * 1000L) to (customTo ?: now)
        }
    }

    private fun formatDate(value: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(value))

    private fun formatDuration(elapsedMs: Long): String {
        val seconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        return "%02d:%02d\nTempo".format(seconds / 60L, seconds % 60L)
    }
}
