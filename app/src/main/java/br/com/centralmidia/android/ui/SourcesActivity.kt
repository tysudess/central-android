package br.com.centralmidia.android.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CatalogRepository
import br.com.centralmidia.android.core.NewsSource
import br.com.centralmidia.android.core.SourcePreferences
import br.com.centralmidia.android.core.VideoSource
import br.com.centralmidia.android.core.dp
import com.google.android.material.button.MaterialButton
import java.util.Locale

class SourcesActivity : BaseActivity() {
    private enum class Tab {
        NEWS,
        VIDEOS,
        SPECIALIZED,
    }

    private data class SourceRow(
        val id: String,
        val name: String,
        val group: String,
        val region: String,
        val state: String,
        val aliases: List<String>,
    )

    private lateinit var prefs: SourcePreferences
    private lateinit var query: android.widget.EditText
    private lateinit var regionSpinner: Spinner
    private lateinit var stateSpinner: Spinner
    private lateinit var modeTitle: TextView
    private lateinit var modeSubtitle: TextView
    private lateinit var modeButton: MaterialButton
    private lateinit var info: TextView
    private lateinit var listContainer: LinearLayout

    private var tab = Tab.NEWS
    private var tabButtons = linkedMapOf<Tab, MaterialButton>()

    private val newsSources by lazy {
        CatalogRepository.news(this)
    }
    private val regularNews by lazy {
        CatalogRepository.regularNews(this)
    }
    private val specializedNews by lazy {
        CatalogRepository.specializedNews(this)
    }
    private val videoSources by lazy {
        CatalogRepository.videos(this)
    }

    private val regions = listOf(
        "Todas",
        "Nacional",
        "Norte",
        "Nordeste",
        "Centro-Oeste",
        "Sudeste",
        "Sul",
    )

    private val states = listOf(
        Triple("AC", "Acre", "Norte"),
        Triple("AP", "Amapá", "Norte"),
        Triple("AM", "Amazonas", "Norte"),
        Triple("PA", "Pará", "Norte"),
        Triple("RO", "Rondônia", "Norte"),
        Triple("RR", "Roraima", "Norte"),
        Triple("TO", "Tocantins", "Norte"),
        Triple("AL", "Alagoas", "Nordeste"),
        Triple("BA", "Bahia", "Nordeste"),
        Triple("CE", "Ceará", "Nordeste"),
        Triple("MA", "Maranhão", "Nordeste"),
        Triple("PB", "Paraíba", "Nordeste"),
        Triple("PE", "Pernambuco", "Nordeste"),
        Triple("PI", "Piauí", "Nordeste"),
        Triple("RN", "Rio Grande do Norte", "Nordeste"),
        Triple("SE", "Sergipe", "Nordeste"),
        Triple("DF", "Distrito Federal", "Centro-Oeste"),
        Triple("GO", "Goiás", "Centro-Oeste"),
        Triple("MT", "Mato Grosso", "Centro-Oeste"),
        Triple("MS", "Mato Grosso do Sul", "Centro-Oeste"),
        Triple("ES", "Espírito Santo", "Sudeste"),
        Triple("MG", "Minas Gerais", "Sudeste"),
        Triple("RJ", "Rio de Janeiro", "Sudeste"),
        Triple("SP", "São Paulo", "Sudeste"),
        Triple("PR", "Paraná", "Sul"),
        Triple("SC", "Santa Catarina", "Sul"),
        Triple("RS", "Rio Grande do Sul", "Sul"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SourcePreferences(this)

        val root = MobileScaffold.page(
            this,
            "Fontes",
            "Fontes nacionais, regionais e mídias especializadas.",
            MobileScaffold.Tab.MORE,
            showAutomation = true,
        )

        root.addView(
            filtersCard(),
            MobileUi.match(dp(14)),
        )
        root.addView(
            modeCard(),
            MobileUi.match(dp(10)),
        )
        root.addView(
            controlsCard(),
            MobileUi.match(dp(10)),
        )

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(
            listContainer,
            MobileUi.match(dp(8)),
        )

        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::listContainer.isInitialized) {
            refresh()
        }
    }

    private fun filtersCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun addTab(
            value: Tab,
            label: String,
        ) {
            val button = MobileUi.button(
                this,
                label,
                false,
            ) {
                tab = value
                updateTabs()
                refresh()
            }
            tabButtons[value] = button
            tabRow.addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(46),
                ).apply {
                    marginEnd = dp(6)
                },
            )
        }

        addTab(
            Tab.NEWS,
            "Notícias",
        )
        addTab(
            Tab.VIDEOS,
            "Vídeos",
        )
        addTab(
            Tab.SPECIALIZED,
            "Mídia especializada",
        )

        tabScroll.addView(tabRow)
        box.addView(tabScroll)
        updateTabs()

        query = MobileUi.input(
            this,
            "Pesquisar por nome, região, estado ou grupo...",
        ).apply {
            doAfterTextChanged {
                refresh()
            }
        }
        box.addView(
            query,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48),
            ).apply {
                topMargin = dp(10)
            },
        )

        val filtersLabel = MobileUi.text(
            this,
            "Filtros",
            11f,
            MobileUi.NAVY,
            true,
        )
        box.addView(
            filtersLabel,
            MobileUi.match(dp(10)),
        )

        val filtersRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        regionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SourcesActivity,
                android.R.layout.simple_spinner_dropdown_item,
                regions,
            )
            background = MobileUi.rounded(
                Color.WHITE,
                dp(10).toFloat(),
                MobileUi.BORDER,
                dp(1),
            )
            setPadding(
                dp(8),
                0,
                dp(8),
                0,
            )
            onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long,
                    ) {
                        reloadStates()
                        refresh()
                    }

                    override fun onNothingSelected(
                        parent: android.widget.AdapterView<*>?,
                    ) = Unit
                }
        }

        stateSpinner = Spinner(this).apply {
            background = MobileUi.rounded(
                Color.WHITE,
                dp(10).toFloat(),
                MobileUi.BORDER,
                dp(1),
            )
            setPadding(
                dp(8),
                0,
                dp(8),
                0,
            )
            onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long,
                    ) {
                        refresh()
                    }

                    override fun onNothingSelected(
                        parent: android.widget.AdapterView<*>?,
                    ) = Unit
                }
        }

        filtersRow.addView(
            regionSpinner,
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f,
            ).apply {
                marginEnd = dp(4)
            },
        )
        filtersRow.addView(
            stateSpinner,
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f,
            ).apply {
                marginStart = dp(4)
            },
        )

        box.addView(
            filtersRow,
            MobileUi.match(dp(6)),
        )

        box.addView(
            MobileUi.text(
                this,
                "Use os filtros para reduzir a lista sem alterar sua seleção.",
                9.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(6)),
        )

        card.addView(box)
        reloadStates()
        return card
    }

    private fun modeCard(): View {
        val card = MobileUi.card(
            this,
            12,
        ).apply {
            setCardBackgroundColor(
                MobileUi.GREEN_TINT,
            )
            strokeColor = Color.rgb(
                183,
                232,
                207,
            )
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(
            MobileUi.text(
                this,
                "●",
                18f,
                MobileUi.GREEN,
                true,
            ),
        )

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        modeTitle = MobileUi.text(
            this,
            "Pesquisar todos",
            13f,
            Color.rgb(
                4,
                119,
                78,
            ),
            true,
        )

        modeSubtitle = MobileUi.text(
            this,
            "Ative para pesquisar todos os veículos. Desative para usar somente as fontes marcadas abaixo.",
            9.5f,
            Color.rgb(
                75,
                124,
                108,
            ),
        )

        copy.addView(modeTitle)
        copy.addView(
            modeSubtitle,
            MobileUi.match(dp(3)),
        )

        row.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            },
        )

        modeButton = MobileUi.button(
            this,
            "ATIVADO",
            true,
            Color.rgb(
                4,
                128,
                112,
            ),
        ) {
            if (tab == Tab.VIDEOS) {
                return@button
            }

            val next =
                !prefs.newsAllSources()
            prefs.setNewsAllSources(next)
            if (next) {
                prefs.setSelectedNewsIds(
                    emptySet(),
                )
            }
            refresh()
        }

        row.addView(
            modeButton,
            LinearLayout.LayoutParams(
                dp(112),
                dp(44),
            ),
        )

        card.addView(row)
        return card
    }

    private fun controlsCard(): View {
        val card = MobileUi.card(
            this,
            10,
        )
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        info = MobileUi.text(
            this,
            "",
            15f,
            MobileUi.NAVY,
            true,
        )
        box.addView(info)

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        actions.addView(
            MobileUi.button(
                this,
                "Selecionar visíveis",
                true,
                MobileUi.BLUE,
            ) {
                setVisible(true)
            },
            actionLp(),
        )
        actions.addView(
            MobileUi.button(
                this,
                "Limpar visíveis",
            ) {
                setVisible(false)
            },
            actionLp(),
        )
        actions.addView(
            MobileUi.button(
                this,
                "Selecionar todas",
            ) {
                setAll(true)
            },
            actionLp(),
        )
        actions.addView(
            MobileUi.button(
                this,
                "Limpar todas",
            ) {
                setAll(false)
            },
            actionLp(),
        )

        scroll.addView(actions)
        box.addView(
            scroll,
            MobileUi.match(dp(9)),
        )

        card.addView(box)
        return card
    }

    private fun actionLp() =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(44),
        ).apply {
            marginEnd = dp(6)
        }

    private fun updateTabs() {
        tabButtons.forEach { (value, button) ->
            val selected =
                value == tab

            button.backgroundTintList =
                ColorStateList.valueOf(
                    if (selected) {
                        MobileUi.BLUE
                    } else {
                        Color.WHITE
                    },
                )

            button.setTextColor(
                if (selected) {
                    Color.WHITE
                } else {
                    MobileUi.NAVY
                },
            )

            button.strokeColor =
                ColorStateList.valueOf(
                    if (selected) {
                        MobileUi.BLUE
                    } else {
                        MobileUi.BORDER
                    },
                )

            button.strokeWidth = dp(1)
        }
    }

    private fun reloadStates() {
        if (!::stateSpinner.isInitialized) {
            return
        }

        val old =
            stateSpinner.selectedItem
                ?.toString()
                .orEmpty()

        val region =
            regionSpinner.selectedItem
                ?.toString()
                .orEmpty()
                .ifBlank {
                    "Todas"
                }

        val values =
            buildList {
                add("Todos")
                states.forEach { state ->
                    if (
                        region == "Todas" ||
                        region == "Nacional" ||
                        state.third == region
                    ) {
                        add(state.first)
                    }
                }
            }

        stateSpinner.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                values,
            )

        val index =
            values.indexOf(old)
        stateSpinner.setSelection(
            if (index >= 0) {
                index
            } else {
                0
            },
            false,
        )
    }

    private fun allRows(): List<SourceRow> =
        when (tab) {
            Tab.NEWS ->
                regularNews.map {
                    it.toRow()
                }

            Tab.VIDEOS ->
                videoSources.map {
                    it.toRow()
                }

            Tab.SPECIALIZED ->
                specializedNews.map {
                    it.toRow()
                }
        }

    private fun visibleRows(): List<SourceRow> {
        val text =
            query.text
                ?.toString()
                .orEmpty()
                .trim()
                .lowercase(
                    Locale.getDefault(),
                )

        val region =
            regionSpinner.selectedItem
                ?.toString()
                .orEmpty()
                .ifBlank {
                    "Todas"
                }

        val state =
            stateSpinner.selectedItem
                ?.toString()
                .orEmpty()
                .ifBlank {
                    "Todos"
                }

        return allRows()
            .filter { source ->
                val srcRegion =
                    source.region.ifBlank {
                        "Nacional"
                    }
                val srcState =
                    source.state.ifBlank {
                        "BR"
                    }

                if (
                    region != "Todas" &&
                    srcRegion != region
                ) {
                    return@filter false
                }

                if (
                    state != "Todos" &&
                    srcState != state
                ) {
                    return@filter false
                }

                val haystack =
                    (
                        "${source.name} " +
                            "${source.group} " +
                            "$srcRegion " +
                            "$srcState " +
                            source.aliases.joinToString(" ")
                        )
                        .lowercase(
                            Locale.getDefault(),
                        )

                text.isBlank() ||
                    text in haystack
            }
    }

    private fun selectedIds(): Set<String> =
        when (tab) {
            Tab.VIDEOS ->
                prefs.selectedVideoIds(
                    videoSources,
                )

            Tab.NEWS,
            Tab.SPECIALIZED ->
                if (prefs.newsAllSources()) {
                    newsSources
                        .map { it.id }
                        .toSet()
                } else {
                    prefs.selectedNewsIds()
                }
        }

    private fun refresh() {
        if (
            !::listContainer.isInitialized ||
            !::modeButton.isInitialized
        ) {
            return
        }

        val visible =
            visibleRows()
        val selected =
            selectedIds()

        when (tab) {
            Tab.NEWS -> {
                modeTitle.text =
                    "Pesquisar todos"
                modeSubtitle.text =
                    "Ative para pesquisar todos os veículos. Desative para usar somente as fontes marcadas abaixo."
                modeButton.visibility =
                    View.VISIBLE
            }

            Tab.VIDEOS -> {
                modeTitle.text =
                    "Fontes de vídeo"
                modeSubtitle.text =
                    "Escolha quais fontes participam da busca de vídeos."
                modeButton.visibility =
                    View.GONE
            }

            Tab.SPECIALIZED -> {
                modeTitle.text =
                    "Pesquisar todos"
                modeSubtitle.text =
                    "Ative para incluir toda a mídia especializada. Desative para usar somente as fontes marcadas abaixo."
                modeButton.visibility =
                    View.VISIBLE
            }
        }

        if (tab != Tab.VIDEOS) {
            val enabled =
                prefs.newsAllSources()

            modeButton.text =
                if (enabled) {
                    "ATIVADO"
                } else {
                    "DESATIVADO"
                }

            modeButton.backgroundTintList =
                ColorStateList.valueOf(
                    if (enabled) {
                        Color.rgb(
                            4,
                            128,
                            112,
                        )
                    } else {
                        Color.WHITE
                    },
                )

            modeButton.setTextColor(
                if (enabled) {
                    Color.WHITE
                } else {
                    MobileUi.MUTED
                },
            )
        }

        val selectedVisible =
            visible.count {
                it.id in selected
            }

        info.text =
            "${visible.size} fonte(s) visível(is)   " +
                "$selectedVisible selecionada(s) neste filtro"

        listContainer.removeAllViews()

        if (visible.isEmpty()) {
            listContainer.addView(
                MobileUi.text(
                    this,
                    "Nenhuma fonte encontrada para os filtros selecionados.",
                    11.5f,
                    MobileUi.MUTED,
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(8),
                        dp(24),
                        dp(8),
                        dp(24),
                    )
                },
            )
            return
        }

        visible.forEach { source ->
            listContainer.addView(
                sourceCard(
                    source,
                    source.id in selected,
                ),
                MobileUi.match(dp(6)),
            )
        }
    }

    private fun sourceCard(
        source: SourceRow,
        checked: Boolean,
    ): View {
        val card = MobileUi.card(
            this,
            10,
        ).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener {
                toggleSource(
                    source.id,
                    !checked,
                )
            }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val check = MobileUi.text(
            this,
            if (checked) {
                "✓"
            } else {
                ""
            },
            15f,
            Color.WHITE,
            true,
        ).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                if (checked) {
                    Color.rgb(
                        3,
                        123,
                        132,
                    )
                } else {
                    Color.WHITE
                },
                dp(6).toFloat(),
                if (checked) {
                    Color.rgb(
                        3,
                        123,
                        132,
                    )
                } else {
                    Color.rgb(
                        133,
                        151,
                        170,
                    )
                },
                dp(1),
            )
        }

        row.addView(
            check,
            LinearLayout.LayoutParams(
                dp(28),
                dp(28),
            ).apply {
                marginEnd = dp(9)
            },
        )

        val initials =
            source.name
                .split(" ")
                .filter {
                    it.isNotBlank()
                }
                .take(2)
                .joinToString("") {
                    it.first()
                        .uppercaseChar()
                        .toString()
                }
                .ifBlank {
                    "F"
                }
                .take(2)

        val badge = MobileUi.text(
            this,
            initials,
            11f,
            MobileUi.BLUE,
            true,
        ).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                MobileUi.BLUE_TINT,
                dp(9).toFloat(),
            )
        }

        row.addView(
            badge,
            LinearLayout.LayoutParams(
                dp(48),
                dp(40),
            ).apply {
                marginEnd = dp(10)
            },
        )

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        copy.addView(
            MobileUi.text(
                this,
                source.name,
                13f,
                MobileUi.NAVY,
                true,
            ),
        )
        copy.addView(
            MobileUi.text(
                this,
                listOf(
                    source.group,
                    source.region.ifBlank {
                        "Nacional"
                    },
                    source.state.ifBlank {
                        "BR"
                    },
                )
                    .filter {
                        it.isNotBlank()
                    }
                    .joinToString(" • "),
                9.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(3)),
        )

        row.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        row.addView(
            MobileUi.text(
                this,
                "Disponível",
                10f,
                MobileUi.GREEN,
                true,
            ).apply {
                setPadding(
                    dp(10),
                    dp(6),
                    dp(10),
                    dp(6),
                )
                background = MobileUi.rounded(
                    MobileUi.GREEN_TINT,
                    dp(9).toFloat(),
                    Color.rgb(
                        193,
                        235,
                        216,
                    ),
                    dp(1),
                )
            },
        )

        card.addView(row)
        return card
    }

    private fun toggleSource(
        id: String,
        checked: Boolean,
    ) {
        if (tab == Tab.VIDEOS) {
            val ids =
                prefs.selectedVideoIds(
                    videoSources,
                ).toMutableSet()

            if (checked) {
                ids += id
            } else {
                ids -= id
            }

            prefs.setSelectedVideoIds(ids)
            refresh()
            return
        }

        var ids =
            prefs.selectedNewsIds()
                .toMutableSet()

        if (prefs.newsAllSources()) {
            ids =
                newsSources
                    .map { it.id }
                    .toMutableSet()
            prefs.setNewsAllSources(false)
        }

        if (checked) {
            ids += id
        } else {
            ids -= id
        }

        prefs.setSelectedNewsIds(ids)
        refresh()
    }

    private fun setVisible(
        checked: Boolean,
    ) {
        val visibleIds =
            visibleRows()
                .map { it.id }
                .toSet()

        if (tab == Tab.VIDEOS) {
            val ids =
                prefs.selectedVideoIds(
                    videoSources,
                ).toMutableSet()

            if (checked) {
                ids += visibleIds
            } else {
                ids -= visibleIds
            }

            prefs.setSelectedVideoIds(ids)
            refresh()
            return
        }

        var ids =
            prefs.selectedNewsIds()
                .toMutableSet()

        if (
            prefs.newsAllSources() &&
            !checked
        ) {
            ids =
                newsSources
                    .map { it.id }
                    .toMutableSet()
            prefs.setNewsAllSources(false)
        } else if (
            prefs.newsAllSources() &&
            checked
        ) {
            refresh()
            return
        }

        if (checked) {
            ids += visibleIds
        } else {
            ids -= visibleIds
        }

        prefs.setSelectedNewsIds(ids)
        refresh()
    }

    private fun setAll(
        checked: Boolean,
    ) {
        when (tab) {
            Tab.VIDEOS -> {
                prefs.setSelectedVideoIds(
                    if (checked) {
                        videoSources
                            .map { it.id }
                            .toSet()
                    } else {
                        emptySet()
                    },
                )
            }

            Tab.NEWS -> {
                prefs.setNewsAllSources(
                    checked,
                )
                prefs.setSelectedNewsIds(
                    emptySet(),
                )
            }

            Tab.SPECIALIZED -> {
                val specIds =
                    specializedNews
                        .map { it.id }
                        .toSet()

                var ids =
                    prefs.selectedNewsIds()
                        .toMutableSet()

                if (prefs.newsAllSources()) {
                    if (checked) {
                        refresh()
                        return
                    }

                    ids =
                        newsSources
                            .map { it.id }
                            .toMutableSet()
                    prefs.setNewsAllSources(
                        false,
                    )
                }

                if (checked) {
                    ids += specIds
                } else {
                    ids -= specIds
                }

                prefs.setSelectedNewsIds(ids)
            }
        }

        refresh()
    }

    private fun NewsSource.toRow() =
        SourceRow(
            id = id,
            name = name,
            group = group,
            region = region,
            state = state,
            aliases = aliases,
        )

    private fun VideoSource.toRow() =
        SourceRow(
            id = id,
            name = name,
            group = group,
            region = region,
            state = state,
            aliases = aliases,
        )
}
