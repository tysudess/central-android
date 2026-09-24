package br.com.centralmidia.android.ui

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CatalogRepository
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.GoogleNewsClient
import br.com.centralmidia.android.core.Matching
import br.com.centralmidia.android.core.NewsItem
import br.com.centralmidia.android.core.NewsSource
import br.com.centralmidia.android.core.SourcePreferences
import br.com.centralmidia.android.core.dp
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NewsActivity : BaseActivity() {
    private val client = GoogleNewsClient()
    private lateinit var db: CentralDb
    private lateinit var sourcePrefs: SourcePreferences

    private var items = listOf<NewsItem>()
    private lateinit var queryInput: android.widget.EditText
    private lateinit var onlyDemands: CheckBox
    private lateinit var resultContainer: LinearLayout

    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    private lateinit var progress: ProgressBar
    private lateinit var pctValue: TextView
    private lateinit var foundValue: TextView
    private lateinit var newValue: TextView
    private lateinit var failureValue: TextView
    private lateinit var stepsValue: TextView
    private lateinit var timeValue: TextView
    private lateinit var resultCountLabel: TextView
    private lateinit var stopButton: MaterialButton

    private var selectedPeriod = Period.TODAY
    private var customFrom: Long? = null
    private var customTo: Long? = null

    @Volatile
    private var cancelRequested = false

    private enum class Period {
        TODAY,
        DAY_24,
        DAYS_7,
        DAYS_30,
        CUSTOM,
    }

    private data class SearchTask(
        val query: String,
        val term: String,
        val sourceLabel: String,
    )

    private data class SearchOutcome(
        val items: List<NewsItem>,
        val failures: Int,
        val totalSteps: Int,
        val completedSteps: Int,
        val cancelled: Boolean,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = CentralDb(this)
        sourcePrefs = SourcePreferences(this)

        val root = MobileScaffold.page(
            this,
            "Notícias",
            "Acompanhe matérias em tempo real e transforme informação em decisões estratégicas.",
            MobileScaffold.Tab.NEWS,
            showAutomation = true,
        )

        queryInput = MobileUi.input(
            this,
            "Buscar nas notícias (título, fonte, termo...)",
        ).apply {
            setText(intent.getStringExtra("query").orEmpty())
            doAfterTextChanged {
                renderFilteredResults()
            }
        }
        root.addView(
            queryInput,
            MobileUi.match(dp(14)),
        )

        var runSelectedPeriod: (() -> Unit)? = null

        val periodBar = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val periodRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val periodButtons =
            linkedMapOf<Period, MaterialButton>()

        fun addPeriod(
            period: Period,
            label: String,
        ) {
            val button = MobileUi.button(
                this,
                label,
                false,
            ) {
                if (period == Period.CUSTOM) {
                    chooseCustomPeriod {
                        selectedPeriod = Period.CUSTOM
                        updatePeriodButtons(periodButtons)
                        runSelectedPeriod?.invoke()
                    }
                } else {
                    selectedPeriod = period
                    updatePeriodButtons(periodButtons)
                    runSelectedPeriod?.invoke()
                }
            }
            periodButtons[period] = button
            periodRow.addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(44),
                ).apply {
                    marginEnd = dp(6)
                },
            )
        }

        addPeriod(
            Period.TODAY,
            "Hoje",
        )
        addPeriod(
            Period.DAY_24,
            "24 horas",
        )
        addPeriod(
            Period.DAYS_7,
            "7 dias",
        )
        addPeriod(
            Period.DAYS_30,
            "30 dias",
        )
        addPeriod(
            Period.CUSTOM,
            "Período personalizado",
        )

        periodBar.addView(periodRow)
        root.addView(
            periodBar,
            MobileUi.match(dp(10)),
        )
        updatePeriodButtons(periodButtons)

        onlyDemands = CheckBox(this).apply {
            text = "Só demandas"
            setTextColor(MobileUi.NAVY)
            textSize = 12f
            setOnCheckedChangeListener { _, _ ->
                renderFilteredResults()
            }
        }

        val sourceSummary = MobileUi.text(
            this,
            sourceSummaryText(),
            10.5f,
            MobileUi.MUTED,
            false,
        ).apply {
            setPadding(
                dp(10),
                dp(8),
                dp(10),
                dp(8),
            )
            background = MobileUi.rounded(
                Color.WHITE,
                dp(11).toFloat(),
                MobileUi.BORDER,
                dp(1),
            )
        }

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(
                sourceSummary,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    marginEnd = dp(8)
                },
            )

            addView(
                onlyDemands,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        root.addView(
            filterRow,
            MobileUi.match(dp(8)),
        )

        val searchButton = MobileUi.button(
            this,
            "Buscar últimas 24h",
            true,
            MobileUi.BLUE,
            R.drawable.ic_search,
        ) {
            selectedPeriod = Period.DAY_24
            updatePeriodButtons(periodButtons)
            performSearch()
        }

        root.addView(
            searchButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52),
            ).apply {
                topMargin = dp(8)
            },
        )

        runSelectedPeriod = {
            performSearch()
        }

        root.addView(
            statusCard(),
            MobileUi.match(dp(12)),
        )

        stopButton = MobileUi.button(
            this,
            "Parar busca",
            false,
            MobileUi.PINK,
        ) {
            cancelRequested = true
            statusTitle.text = "Interrompendo busca…"
            statusDescription.text =
                "A consulta atual será encerrada ao concluir a requisição em andamento."
        }.apply {
            visibility = View.GONE
        }
        root.addView(
            stopButton,
            MobileUi.match(dp(8)),
        )

        val resultsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                MobileUi.icon(
                    this@NewsActivity,
                    R.drawable.ic_news,
                    MobileUi.NAVY,
                    20,
                ),
            )
            addView(
                MobileUi.text(
                    this@NewsActivity,
                    "Notícias encontradas",
                    18f,
                    MobileUi.NAVY,
                    true,
                ),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    marginStart = dp(7)
                },
            )
        }

        resultsHeader.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
        resultsHeader.addView(
            MobileUi.statusChip(
                this,
                "Mais recentes",
                MobileUi.BLUE,
            ),
        )

        root.addView(
            resultsHeader,
            MobileUi.match(dp(14)),
        )

        resultCountLabel = MobileUi.text(
            this,
            "0 resultado(s) para o período selecionado",
            11f,
            MobileUi.MUTED,
        )
        root.addView(
            resultCountLabel,
            MobileUi.match(dp(3)),
        )

        resultContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(
            resultContainer,
            MobileUi.match(dp(8)),
        )
        renderEmpty()

        if (
            intent.getBooleanExtra(
                "autoSearch",
                false,
            )
        ) {
            searchButton.performClick()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::queryInput.isInitialized) {
            renderFilteredResults()
        }
    }

    private fun sourceSummaryText(): String {
        val allSources = CatalogRepository.news(this)
        return if (sourcePrefs.newsAllSources()) {
            "${allSources.size} fontes • pesquisar todos"
        } else {
            val selected =
                sourcePrefs.selectedNewsSources(
                    allSources,
                )
            "${selected.size} fonte(s) selecionada(s)"
        }
    }

    private fun statusCard(): View {
        val card = MobileUi.card(
            this,
            12,
        )
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badge = MobileUi.text(
            this,
            "✓",
            25f,
            MobileUi.GREEN,
            true,
        ).apply {
            gravity = Gravity.CENTER
            background = MobileUi.oval(
                Color.rgb(
                    222,
                    248,
                    236,
                ),
            )
        }

        top.addView(
            badge,
            LinearLayout.LayoutParams(
                dp(50),
                dp(50),
            ).apply {
                marginEnd = dp(10)
            },
        )

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        statusTitle = MobileUi.text(
            this,
            "Busca concluída com sucesso",
            16f,
            MobileUi.NAVY,
            true,
        )
        statusDescription = MobileUi.text(
            this,
            "Pronto para consultar os termos e fontes configurados.",
            11f,
            MobileUi.MUTED,
        )

        copy.addView(statusTitle)
        copy.addView(
            statusDescription,
            MobileUi.match(dp(4)),
        )
        top.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        pctValue = MobileUi.text(
            this,
            "100%",
            21f,
            MobileUi.GREEN,
            true,
        )
        top.addView(pctValue)
        box.addView(top)

        progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal,
        ).apply {
            max = 100
            progress = 100
            progressTintList =
                android.content.res.ColorStateList.valueOf(
                    MobileUi.GREEN,
                )
        }
        box.addView(
            progress,
            MobileUi.match(dp(8)),
        )

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val done = statValue(
            "100%",
            "Conclusão",
            MobileUi.GREEN,
        )
        foundValue = statValue(
            "0",
            "Encontradas",
            MobileUi.ORANGE,
        )
        newValue = statValue(
            "0",
            "Novas",
            MobileUi.PURPLE,
        )
        failureValue = statValue(
            "0",
            "Falhas",
            MobileUi.PINK,
        )
        stepsValue = statValue(
            "0/0",
            "Etapas",
            MobileUi.BLUE,
        )
        timeValue = statValue(
            "00:00",
            "Tempo",
            MobileUi.BLUE,
        )

        listOf(
            done,
            foundValue,
            newValue,
            failureValue,
            stepsValue,
            timeValue,
        ).forEachIndexed { index, view ->
            stats.addView(
                view,
                LinearLayout.LayoutParams(
                    0,
                    dp(70),
                    1f,
                ).apply {
                    if (index > 0) {
                        marginStart = dp(2)
                    }
                },
            )
        }

        box.addView(
            stats,
            MobileUi.match(dp(8)),
        )

        card.addView(box)
        return card
    }

    private fun statValue(
        value: String,
        label: String,
        color: Int,
    ): TextView =
        MobileUi.text(
            this,
            "$value\n$label",
            10.5f,
            color,
            true,
        ).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                Color.rgb(
                    250,
                    252,
                    255,
                ),
                dp(10).toFloat(),
                MobileUi.BORDER,
                dp(1),
            )
        }

    private fun updatePeriodButtons(
        buttons: Map<Period, MaterialButton>,
    ) {
        buttons.forEach { (period, button) ->
            val selected =
                period == selectedPeriod
            button.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    if (selected) {
                        MobileUi.BLUE_TINT
                    } else {
                        Color.WHITE
                    },
                )
            button.setTextColor(
                if (selected) {
                    MobileUi.BLUE
                } else {
                    MobileUi.NAVY
                },
            )
            button.strokeColor =
                android.content.res.ColorStateList.valueOf(
                    if (selected) {
                        MobileUi.BLUE
                    } else {
                        MobileUi.BORDER
                    },
                )
            button.strokeWidth = dp(1)
        }
    }

    private fun chooseCustomPeriod(
        onDone: () -> Unit,
    ) {
        val start = Calendar.getInstance().apply {
            add(
                Calendar.DAY_OF_YEAR,
                -7,
            )
        }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                val from = Calendar.getInstance().apply {
                    set(
                        year,
                        month,
                        day,
                        0,
                        0,
                        0,
                    )
                    set(
                        Calendar.MILLISECOND,
                        0,
                    )
                }

                val end = Calendar.getInstance()

                DatePickerDialog(
                    this,
                    { _, ey, em, ed ->
                        val to =
                            Calendar.getInstance().apply {
                                set(
                                    ey,
                                    em,
                                    ed,
                                    23,
                                    59,
                                    59,
                                )
                                set(
                                    Calendar.MILLISECOND,
                                    999,
                                )
                            }

                        customFrom = from.timeInMillis
                        customTo = to.timeInMillis
                        onDone()
                    },
                    end.get(Calendar.YEAR),
                    end.get(Calendar.MONTH),
                    end.get(
                        Calendar.DAY_OF_MONTH,
                    ),
                ).show()
            },
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH),
            start.get(
                Calendar.DAY_OF_MONTH,
            ),
        ).show()
    }

    private fun selectedRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()

        return when (selectedPeriod) {
            Period.TODAY -> {
                val start =
                    Calendar.getInstance().apply {
                        set(
                            Calendar.HOUR_OF_DAY,
                            0,
                        )
                        set(
                            Calendar.MINUTE,
                            0,
                        )
                        set(
                            Calendar.SECOND,
                            0,
                        )
                        set(
                            Calendar.MILLISECOND,
                            0,
                        )
                    }.timeInMillis
                start to now
            }

            Period.DAY_24 ->
                now - 24L * 60L * 60L * 1000L to now

            Period.DAYS_7 ->
                now - 7L * 24L * 60L * 60L * 1000L to now

            Period.DAYS_30 ->
                now - 30L * 24L * 60L * 60L * 1000L to now

            Period.CUSTOM ->
                (customFrom ?: now - 24L * 60L * 60L * 1000L) to
                    (customTo ?: now)
        }
    }

    private fun performSearch() {
        if (stopButton.visibility == View.VISIBLE) {
            toast("Uma busca já está em andamento.")
            return
        }

        val terms =
            db.terms("news")
                .map { it.second }
                .filter { it.isNotBlank() }

        if (terms.isEmpty()) {
            toast("Cadastre ao menos um termo de Notícias.")
            return
        }

        val allSources =
            CatalogRepository.news(this)
        val searchAll =
            sourcePrefs.newsAllSources()
        val selectedSources =
            sourcePrefs.selectedNewsSources(
                allSources,
            )

        if (!searchAll && selectedSources.isEmpty()) {
            toast("Selecione ao menos uma fonte na aba Fontes.")
            return
        }

        val (fromMs, toMs) =
            selectedRange()

        val tasks =
            buildTasks(
                terms,
                selectedSources,
                searchAll,
            )

        cancelRequested = false
        stopButton.visibility = View.VISIBLE

        statusTitle.text =
            "Buscando notícias…"
        statusDescription.text =
            "${terms.size} termo(s) • " +
                if (searchAll) {
                    "${allSources.size} fontes habilitadas"
                } else {
                    "${selectedSources.size} fonte(s) selecionada(s)"
                }

        progress.isIndeterminate = true
        pctValue.text = "0%"
        failureValue.text = "0\nFalhas"
        stepsValue.text =
            "0/${tasks.size}\nEtapas"

        val started =
            SystemClock.elapsedRealtime()

        io(
            {
                runSearch(
                    tasks = tasks,
                    terms = terms,
                    demands = db.demands(),
                    selectedSources = selectedSources,
                    searchAll = searchAll,
                    fromMs = fromMs,
                    toMs = toMs,
                )
            },
            { outcome ->
                val elapsed =
                    SystemClock.elapsedRealtime() -
                        started

                stopButton.visibility = View.GONE
                progress.isIndeterminate = false

                if (outcome.cancelled) {
                    progress.progress =
                        if (outcome.totalSteps == 0) {
                            0
                        } else {
                            (
                                outcome.completedSteps *
                                    100 /
                                    outcome.totalSteps
                                )
                                .coerceIn(
                                    0,
                                    100,
                                )
                        }

                    statusTitle.text =
                        "Busca interrompida"
                    statusDescription.text =
                        "A pesquisa foi interrompida pelo usuário."
                } else {
                    progress.progress = 100
                    statusTitle.text =
                        "Busca concluída com sucesso"
                    statusDescription.text =
                        "A busca foi concluída. " +
                            "${outcome.items.size} notícia(s) encontrada(s) nesta execução."
                }

                val seenPrefs =
                    getSharedPreferences(
                        "news_seen_links",
                        MODE_PRIVATE,
                    )
                val oldSeen =
                    seenPrefs.getStringSet(
                        "links",
                        emptySet(),
                    )?.toSet().orEmpty()

                val decorated =
                    outcome.items.map { item ->
                        item.copy(
                            isNew =
                                item.link !in oldSeen,
                        )
                    }

                val newCount =
                    decorated.count {
                        it.isNew
                    }

                val mergedSeen =
                    (
                        decorated
                            .map { it.link } +
                            oldSeen
                        )
                        .filter {
                            it.isNotBlank()
                        }
                        .distinct()
                        .take(
                            1500,
                        )
                        .toSet()

                seenPrefs.edit()
                    .putStringSet(
                        "links",
                        mergedSeen,
                    )
                    .apply()

                items = decorated

                pctValue.text =
                    if (outcome.cancelled) {
                        "${progress.progress}%"
                    } else {
                        "100%"
                    }

                foundValue.text =
                    "${items.size}\nEncontradas"
                newValue.text =
                    "$newCount\nNovas"
                failureValue.text =
                    "${outcome.failures}\nFalhas"
                stepsValue.text =
                    "${outcome.completedSteps}/${outcome.totalSteps}\nEtapas"
                timeValue.text =
                    formatDuration(elapsed)

                getSharedPreferences(
                    "dashboard_stats",
                    MODE_PRIVATE,
                ).edit()
                    .putInt(
                        "last_news_count",
                        items.size,
                    )
                    .apply()

                items.forEach { item ->
                    db.addHistoryUnique(
                        type = "news",
                        title = item.title,
                        detail = buildString {
                            append(
                                item.source,
                            )
                            if (
                                item.snippet.isNotBlank()
                            ) {
                                append("\n")
                                append(
                                    item.snippet,
                                )
                            }
                            val tag =
                                item.matchedDemand
                                    .ifBlank {
                                        item.matchedTerm
                                    }
                            if (tag.isNotBlank()) {
                                append("\nTermo: ")
                                append(tag)
                            }
                        },
                        url = item.link,
                    )
                }

                renderFilteredResults()
            },
            { error ->
                stopButton.visibility = View.GONE
                progress.isIndeterminate = false
                progress.progress = 0
                pctValue.text = "0%"
                statusTitle.text = "Falha na busca"
                statusDescription.text =
                    error.message
                        ?: "Não foi possível concluir a busca."
                failureValue.text = "1\nFalhas"
                toast(
                    error.message
                        ?: "Falha ao buscar notícias",
                )
            },
        )
    }

    private fun buildTasks(
        terms: List<String>,
        selectedSources: List<NewsSource>,
        searchAll: Boolean,
    ): List<SearchTask> {
        val tasks =
            terms.map {
                SearchTask(
                    query = it,
                    term = it,
                    sourceLabel = "Google Notícias",
                )
            }.toMutableList()

        if (
            !searchAll &&
            selectedSources.isNotEmpty() &&
            selectedSources.size <= 24
        ) {
            selectedSources
                .chunked(8)
                .forEach { batch ->
                    val clause =
                        batch.joinToString(
                            " OR ",
                        ) {
                            "\"${it.name}\""
                        }

                    val label =
                        batch
                            .joinToString(", ") {
                                it.name
                            }
                            .take(70)

                    terms.forEach { term ->
                        tasks += SearchTask(
                            query =
                                "\"$term\" ($clause)",
                            term = term,
                            sourceLabel = label,
                        )
                    }
                }
        }

        return tasks
    }

    private fun runSearch(
        tasks: List<SearchTask>,
        terms: List<String>,
        demands: List<Triple<Long, String, String>>,
        selectedSources: List<NewsSource>,
        searchAll: Boolean,
        fromMs: Long,
        toMs: Long,
    ): SearchOutcome {
        val collected =
            linkedMapOf<String, NewsItem>()
        var failures = 0
        var completed = 0

        for (task in tasks) {
            if (cancelRequested) break

            val fetched = try {
                client.search(
                    task.query,
                    50,
                )
            } catch (_: Throwable) {
                failures++
                completed++
                continue
            }

            if (cancelRequested) break

            for (base in fetched) {
                if (
                    base.publishedAt <
                    fromMs ||
                    base.publishedAt >
                    toMs
                ) {
                    continue
                }

                if (
                    !searchAll &&
                    selectedSources.none {
                        Matching.sourceMatchesStrict(
                            base.source,
                            it,
                        )
                    }
                ) {
                    continue
                }

                val body =
                    "${base.title} ${base.snippet}"

                val actualTerms =
                    terms.filter {
                        Matching.subjectMatches(
                            body,
                            it,
                        )
                    }

                val demand =
                    demands.firstOrNull {
                        Matching.demandVehicleMatches(
                            base.source,
                            it.second,
                        ) &&
                            Matching.subjectMatches(
                                body,
                                it.third,
                            )
                    }

                val incoming =
                    base.copy(
                        pubDate =
                            formatDate(
                                base.pubDate,
                            ),
                        matchedTerm =
                            (
                                actualTerms
                                    .ifEmpty {
                                        listOf(
                                            task.term,
                                        )
                                    }
                                )
                                .distinct()
                                .joinToString(", "),
                        matchedDemand =
                            if (demand != null) {
                                "${demand.second} • ${demand.third}"
                            } else {
                                ""
                            },
                    )

                val previous =
                    collected[incoming.link]

                collected[incoming.link] =
                    if (previous == null) {
                        incoming
                    } else {
                        mergeNews(
                            previous,
                            incoming,
                        )
                    }
            }

            completed++
        }

        val sorted =
            collected.values
                .sortedByDescending {
                    it.publishedAt
                }

        return SearchOutcome(
            items = sorted,
            failures = failures,
            totalSteps = tasks.size,
            completedSteps = completed,
            cancelled = cancelRequested,
        )
    }

    private fun mergeNews(
        previous: NewsItem,
        incoming: NewsItem,
    ): NewsItem {
        val terms =
            (
                previous.matchedTerm
                    .split(",") +
                    incoming.matchedTerm
                        .split(",")
                )
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        return incoming.copy(
            matchedTerm =
                terms.joinToString(", "),
            matchedDemand =
                incoming.matchedDemand
                    .ifBlank {
                        previous.matchedDemand
                    },
        )
    }

    private fun renderFilteredResults() {
        if (!::resultContainer.isInitialized) {
            return
        }

        val query =
            queryInput.text
                ?.toString()
                .orEmpty()
                .trim()
                .lowercase(
                    Locale.getDefault(),
                )

        val visible =
            items.filter { item ->
                val matchesText =
                    query.isBlank() ||
                        query in
                        (
                            "${item.title} " +
                                "${item.source} " +
                                "${item.matchedTerm} " +
                                item.matchedDemand
                            )
                            .lowercase(
                                Locale.getDefault(),
                            )

                val matchesDemand =
                    !onlyDemands.isChecked ||
                        item.matchedDemand.isNotBlank()

                matchesText &&
                    matchesDemand
            }

        resultContainer.removeAllViews()
        resultCountLabel.text =
            "${visible.size} resultado(s) para o período selecionado"

        if (visible.isEmpty()) {
            renderEmpty(
                updateCount = false,
            )
            return
        }

        visible.forEach { item ->
            resultContainer.addView(
                NewsCards.create(
                    this,
                    item,
                    true,
                ),
                MobileUi.match(dp(8)),
            )
        }
    }

    private fun renderEmpty(
        updateCount: Boolean = true,
    ) {
        resultContainer.removeAllViews()

        if (
            updateCount &&
            ::resultCountLabel.isInitialized
        ) {
            resultCountLabel.text =
                "0 resultado(s) para o período selecionado"
        }

        val empty = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                dp(12),
                dp(22),
                dp(12),
                dp(22),
            )
        }

        box.addView(
            MobileUi.icon(
                this,
                R.drawable.ic_news,
                Color.rgb(
                    164,
                    187,
                    218,
                ),
                36,
            ).apply {
                setPadding(
                    dp(8),
                    dp(8),
                    dp(8),
                    dp(8),
                )
            },
        )

        box.addView(
            MobileUi.text(
                this,
                "Nenhuma notícia exibida",
                16f,
                MobileUi.NAVY,
                true,
            ).apply {
                gravity = Gravity.CENTER
            },
            MobileUi.match(dp(10)),
        )

        box.addView(
            MobileUi.text(
                this,
                "Execute uma busca ou ajuste os filtros locais.",
                12f,
                MobileUi.MUTED,
            ).apply {
                gravity = Gravity.CENTER
            },
            MobileUi.match(dp(4)),
        )

        empty.addView(box)
        resultContainer.addView(empty)
    }

    private fun formatDate(
        raw: String,
    ): String {
        if (raw.isBlank()) {
            return SimpleDateFormat(
                "dd/MM/yyyy HH:mm",
                Locale(
                    "pt",
                    "BR",
                ),
            ).format(Date())
        }

        val parsers = listOf(
            SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z",
                Locale.US,
            ),
            SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss Z",
                Locale.US,
            ),
        )

        for (parser in parsers) {
            val parsed =
                runCatching {
                    parser.parse(raw)
                }.getOrNull()

            if (parsed != null) {
                return SimpleDateFormat(
                    "dd/MM/yyyy HH:mm",
                    Locale(
                        "pt",
                        "BR",
                    ),
                ).format(parsed)
            }
        }

        return raw
    }

    private fun formatDuration(
        elapsedMs: Long,
    ): String {
        val seconds =
            (elapsedMs / 1000L)
                .coerceAtLeast(0L)

        return "%02d:%02d\nTempo".format(
            seconds / 60L,
            seconds % 60L,
        )
    }
}
