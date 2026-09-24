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
import androidx.core.widget.doAfterTextChanged
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CatalogRepository
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.Matching
import br.com.centralmidia.android.core.SourcePreferences
import br.com.centralmidia.android.core.VideoSource
import br.com.centralmidia.android.core.dp
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale

class VideosActivity : BaseActivity() {
    private lateinit var db: CentralDb
    private lateinit var sourcePrefs: SourcePreferences
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

    private var selectedPeriod = Period.DAY_24
    private var customLabel = ""
    private var plans = listOf<VideoPlan>()

    private val sources by lazy {
        CatalogRepository.videos(this)
    }

    private enum class Period {
        DAY_24,
        DAYS_7,
        DAYS_30,
        CUSTOM,
    }

    private data class VideoPlan(
        val source: VideoSource,
        val terms: List<String>,
        val demandTerms: List<String>,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = CentralDb(this)
        sourcePrefs = SourcePreferences(this)

        val root = MobileScaffold.page(
            this,
            "Vídeos",
            "Busca e acompanhamento de vídeos relevantes.",
            MobileScaffold.Tab.VIDEOS,
            showAutomation = true,
        )

        queryInput = MobileUi.input(
            this,
            "Buscar nos vídeos (título, fonte, termo...)",
        ).apply {
            doAfterTextChanged {
                renderPlans()
            }
        }
        root.addView(
            queryInput,
            MobileUi.match(dp(14)),
        )

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
                        updatePeriodButtons(
                            periodButtons,
                        )
                        performSearch()
                    }
                } else {
                    selectedPeriod = period
                    updatePeriodButtons(
                        periodButtons,
                    )
                    performSearch()
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

        val searchButton = MobileUi.button(
            this,
            "Buscar vídeos agora",
            true,
            MobileUi.BLUE,
            R.drawable.ic_search,
        ) {
            performSearch()
        }

        root.addView(
            searchButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52),
            ).apply {
                topMargin = dp(10)
            },
        )

        root.addView(
            sourceSummary(),
            MobileUi.match(dp(8)),
        )

        root.addView(
            statusCard(),
            MobileUi.match(dp(12)),
        )

        val resultHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(
                MobileUi.icon(
                    this@VideosActivity,
                    R.drawable.ic_video,
                    MobileUi.NAVY,
                    20,
                ),
            )

            addView(
                MobileUi.text(
                    this@VideosActivity,
                    "Vídeos encontrados",
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

        resultHeader.addView(
            left,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        shownLabel = MobileUi.text(
            this,
            "0 exibido(s)",
            10.5f,
            MobileUi.MUTED,
        )
        resultHeader.addView(shownLabel)

        root.addView(
            resultHeader,
            MobileUi.match(dp(14)),
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
        if (::resultContainer.isInitialized) {
            renderPlans()
        }
    }

    private fun sourceSummary(): View {
        val selected =
            sourcePrefs.selectedVideoSources(
                sources,
            )

        return MobileUi.text(
            this,
            "${selected.size}/${sources.size} fontes de vídeo selecionadas • " +
                "${db.terms("video").size} termos independentes",
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
            "Vídeos • última execução concluída",
            16f,
            MobileUi.NAVY,
            true,
        )

        statusDescription = MobileUi.text(
            this,
            "Pronto",
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
            20f,
            MobileUi.BLUE,
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
                    MobileUi.BLUE,
                )
        }
        box.addView(
            progress,
            MobileUi.match(dp(8)),
        )

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val done = metric(
            "100%",
            "Conclusão",
        )
        foundValue = metric(
            "0",
            "Encontrados",
        )
        newValue = metric(
            "0",
            "Novos",
        )
        failureValue = metric(
            "0",
            "Falhas",
        )
        stepsValue = metric(
            "0/0",
            "Etapas",
        )
        timeValue = metric(
            "00:00",
            "Tempo",
        )

        listOf(
            done,
            foundValue,
            newValue,
            failureValue,
            stepsValue,
            timeValue,
        ).forEach { view ->
            stats.addView(
                view,
                LinearLayout.LayoutParams(
                    0,
                    dp(66),
                    1f,
                ).apply {
                    marginEnd = dp(2)
                },
            )
        }

        box.addView(
            stats,
            MobileUi.match(dp(8)),
        )

        val success = MobileUi.text(
            this,
            "A pesquisa usa os mesmos termos e a mesma seleção de fontes configurada nas abas Termos e Fontes.",
            10.5f,
            MobileUi.GREEN,
            false,
        ).apply {
            setPadding(
                dp(10),
                dp(8),
                dp(10),
                dp(8),
            )
            background = MobileUi.rounded(
                MobileUi.GREEN_TINT,
                dp(10).toFloat(),
                Color.rgb(
                    190,
                    233,
                    210,
                ),
                dp(1),
            )
        }

        box.addView(
            success,
            MobileUi.match(dp(8)),
        )

        card.addView(box)
        return card
    }

    private fun metric(
        value: String,
        caption: String,
    ): TextView =
        MobileUi.text(
            this,
            "$value\n$caption",
            10.5f,
            MobileUi.BLUE,
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
        val start =
            Calendar.getInstance().apply {
                add(
                    Calendar.DAY_OF_YEAR,
                    -7,
                )
            }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                val startText =
                    "%02d/%02d/%04d".format(
                        day,
                        month + 1,
                        year,
                    )

                val end =
                    Calendar.getInstance()

                DatePickerDialog(
                    this,
                    { _, ey, em, ed ->
                        val endText =
                            "%02d/%02d/%04d".format(
                                ed,
                                em + 1,
                                ey,
                            )

                        customLabel =
                            "$startText a $endText"
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

    private fun performSearch() {
        val started =
            SystemClock.elapsedRealtime()

        val terms =
            db.terms("video")
                .map { it.second }
                .filter { it.isNotBlank() }

        val selectedSources =
            sourcePrefs.selectedVideoSources(
                sources,
            )

        if (terms.isEmpty()) {
            toast("Cadastre ao menos um termo de Vídeos.")
            return
        }

        if (selectedSources.isEmpty()) {
            toast("Selecione ao menos uma fonte de vídeo.")
            return
        }

        statusTitle.text =
            "Vídeos • busca preparada"
        statusDescription.text =
            "${selectedSources.size} fonte(s) • ${terms.size} termo(s)"
        progress.isIndeterminate = true
        pctValue.text = "0%"

        val demands =
            db.demands()

        plans =
            selectedSources.map { source ->
                val demandTerms =
                    demands
                        .filter {
                            videoSourceMatchesDemand(
                                source,
                                it.second,
                            )
                        }
                        .map {
                            it.third
                        }
                        .filter {
                            it.isNotBlank()
                        }
                        .distinct()

                VideoPlan(
                    source = source,
                    terms = terms,
                    demandTerms = demandTerms,
                )
            }

        val elapsed =
            SystemClock.elapsedRealtime() -
                started

        progress.isIndeterminate = false
        progress.progress = 100
        pctValue.text = "100%"
        foundValue.text = "0\nEncontrados"
        newValue.text = "0\nNovos"
        failureValue.text = "0\nFalhas"
        stepsValue.text =
            "${plans.size}/${plans.size}\nEtapas"
        timeValue.text =
            formatDuration(elapsed)

        statusTitle.text =
            "Vídeos • última execução concluída"
        statusDescription.text =
            "Rotas prontas para pesquisar os termos nas fontes selecionadas."

        getSharedPreferences(
            "dashboard_stats",
            MODE_PRIVATE,
        ).edit()
            .putInt(
                "last_video_count",
                0,
            )
            .apply()

        renderPlans()
    }

    private fun renderPlans() {
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
            plans.filter { plan ->
                query.isBlank() ||
                    query in
                    (
                        "${plan.source.name} " +
                            "${plan.source.group} " +
                            "${plan.source.region} " +
                            "${plan.source.state} " +
                            plan.terms.joinToString(" ") +
                            " " +
                            plan.demandTerms.joinToString(" ")
                        )
                        .lowercase(
                            Locale.getDefault(),
                        )
            }

        shownLabel.text =
            "${visible.size} fonte(s)"

        resultContainer.removeAllViews()

        if (visible.isEmpty()) {
            renderEmpty()
            return
        }

        visible.forEach { plan ->
            resultContainer.addView(
                sourceCard(plan),
                MobileUi.match(dp(8)),
            )
        }
    }

    private fun renderEmpty() {
        resultContainer.removeAllViews()
        shownLabel.text = "0 exibido(s)"

        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                dp(10),
                dp(24),
                dp(10),
                dp(24),
            )
        }

        box.addView(
            MobileUi.icon(
                this,
                R.drawable.ic_video,
                Color.rgb(
                    140,
                    171,
                    215,
                ),
                40,
            ),
        )

        box.addView(
            MobileUi.text(
                this,
                "Nenhum vídeo encontrado",
                17f,
                MobileUi.NAVY,
                true,
            ).apply {
                gravity = Gravity.CENTER
            },
            MobileUi.match(dp(14)),
        )

        box.addView(
            MobileUi.text(
                this,
                "Execute uma busca para carregar as fontes selecionadas e os termos de vídeo.",
                12f,
                MobileUi.MUTED,
            ).apply {
                gravity = Gravity.CENTER
            },
            MobileUi.match(dp(6)),
        )

        box.addView(
            MobileUi.button(
                this,
                "Buscar vídeos agora",
                false,
                MobileUi.BLUE,
                R.drawable.ic_search,
            ) {
                performSearch()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(46),
            ).apply {
                topMargin = dp(14)
            },
        )

        card.addView(box)
        resultContainer.addView(card)
    }

    private fun sourceCard(
        plan: VideoPlan,
    ): View {
        val card = MobileUi.card(
            this,
            12,
        )

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                MobileUi.PURPLE_TINT,
                dp(14).toFloat(),
            )
            addView(
                MobileUi.icon(
                    this@VideosActivity,
                    R.drawable.ic_video,
                    MobileUi.PURPLE,
                    25,
                ),
            )
        }

        row.addView(
            iconBox,
            LinearLayout.LayoutParams(
                dp(54),
                dp(54),
            ).apply {
                marginEnd = dp(11)
            },
        )

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        copy.addView(
            MobileUi.text(
                this,
                plan.source.name,
                15f,
                MobileUi.NAVY,
                true,
            ),
        )

        val meta =
            listOf(
                plan.source.group,
                plan.source.region,
                plan.source.state,
            )
                .filter {
                    it.isNotBlank()
                }
                .joinToString(" • ")

        copy.addView(
            MobileUi.text(
                this,
                meta,
                10.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(3)),
        )

        copy.addView(
            MobileUi.text(
                this,
                "${plan.terms.size} termo(s)" +
                    if (plan.demandTerms.isNotEmpty()) {
                        " • ${plan.demandTerms.size} demanda(s)"
                    } else {
                        ""
                    },
                10f,
                MobileUi.PURPLE,
                true,
            ),
            MobileUi.match(dp(5)),
        )

        row.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        box.addView(row)

        val actions =
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
            }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        buttons.addView(
            MobileUi.button(
                this,
                "Abrir fonte",
                false,
                MobileUi.NAVY,
                R.drawable.ic_open,
            ) {
                openVideoUrl(
                    plan.source,
                    "",
                )
            },
            actionLp(),
        )

        buttons.addView(
            MobileUi.button(
                this,
                "Pesquisar termos",
                true,
                MobileUi.BLUE,
                R.drawable.ic_search,
            ) {
                chooseTerm(plan)
            },
            actionLp(),
        )

        buttons.addView(
            MobileUi.button(
                this,
                "Extrator",
                false,
                MobileUi.PURPLE,
                R.drawable.ic_video,
            ) {
                startActivity(
                    Intent(
                        this,
                        VideoExtractorActivity::class.java,
                    ),
                )
            },
            actionLp(),
        )

        actions.addView(buttons)
        box.addView(
            actions,
            MobileUi.match(dp(10)),
        )

        card.addView(box)
        return card
    }

    private fun chooseTerm(
        plan: VideoPlan,
    ) {
        val values =
            (
                plan.terms +
                    plan.demandTerms
                )
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        if (values.isEmpty()) {
            toast("Nenhum termo configurado.")
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Pesquisar em ${plan.source.name}",
            )
            .setItems(
                values.toTypedArray(),
            ) { dialog, which ->
                dialog.dismiss()
                openVideoUrl(
                    plan.source,
                    values[which],
                )
            }
            .setNegativeButton(
                "Cancelar",
                null,
            )
            .show()
    }

    private fun openVideoUrl(
        source: VideoSource,
        rawTerm: String,
    ) {
        val period =
            when (selectedPeriod) {
                Period.DAY_24 ->
                    "últimas 24 horas"
                Period.DAYS_7 ->
                    "últimos 7 dias"
                Period.DAYS_30 ->
                    "últimos 30 dias"
                Period.CUSTOM ->
                    customLabel
            }

        val query =
            listOf(
                source.searchPrefix,
                rawTerm,
            )
                .filter {
                    it.isNotBlank()
                }
                .joinToString(" ")

        val url =
            when {
                rawTerm.isBlank() ->
                    source.landingUrl

                source.searchUrlTemplate
                    .isNotBlank() ->
                    source.searchUrlTemplate.replace(
                        "{query}",
                        URLEncoder.encode(
                            query,
                            "UTF-8",
                        ),
                    )

                else ->
                    source.landingUrl
            }

        if (url.isBlank()) {
            toast("Esta fonte não possui URL configurada.")
            return
        }

        db.addHistoryUnique(
            type = "video",
            title =
                if (rawTerm.isBlank()) {
                    source.name
                } else {
                    "${source.name} • $rawTerm"
                },
            detail =
                if (rawTerm.isBlank()) {
                    source.group
                } else {
                    "Termo: $rawTerm • $period"
                },
            url = url,
        )

        openUrl(url)
    }

    private fun videoSourceMatchesDemand(
        source: VideoSource,
        vehicle: String,
    ): Boolean {
        if (vehicle.isBlank()) return true

        val wanted =
            Matching.normalize(vehicle)
        if (wanted.isBlank()) return true

        val candidates =
            listOf(
                source.name,
                source.group,
                source.searchPrefix,
            ) + source.aliases

        return candidates.any {
            val candidate =
                Matching.normalize(it)
            candidate == wanted ||
                candidate.contains(wanted) ||
                wanted.contains(candidate)
        }
    }

    private fun actionLp() =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(42),
        ).apply {
            marginEnd = dp(6)
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
