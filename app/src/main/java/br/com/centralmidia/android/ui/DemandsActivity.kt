package br.com.centralmidia.android.ui

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.GoogleNewsClient
import br.com.centralmidia.android.core.Matching
import br.com.centralmidia.android.core.NewsItem
import br.com.centralmidia.android.core.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DemandsActivity : BaseActivity() {
    private lateinit var db: CentralDb
    private val client = GoogleNewsClient()

    private lateinit var vehicleInput: android.widget.EditText
    private lateinit var subjectInput: android.widget.EditText
    private lateinit var statusTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var demandCount: TextView
    private lateinit var demandsContainer: LinearLayout
    private lateinit var resultCount: TextView
    private lateinit var resultContainer: LinearLayout

    private var results = listOf<NewsItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = CentralDb(this)

        val root = MobileScaffold.page(
            this,
            "Demandas",
            "Assuntos prioritários acompanhados por veículo.",
            MobileScaffold.Tab.MORE,
            showAutomation = true,
        )

        root.addView(
            heroCard(),
            MobileUi.match(dp(14)),
        )
        root.addView(
            formCard(),
            MobileUi.match(dp(12)),
        )
        root.addView(
            statusCard(),
            MobileUi.match(dp(12)),
        )
        root.addView(
            demandsCard(),
            MobileUi.match(dp(12)),
        )
        root.addView(
            resultsCard(),
            MobileUi.match(dp(12)),
        )

        refreshDemands()
        renderResults()
    }

    private fun heroCard(): View {
        val card = MobileUi.card(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                MobileUi.BLUE_TINT,
                dp(28).toFloat(),
            )
            addView(
                MobileUi.icon(
                    this@DemandsActivity,
                    R.drawable.ic_demands,
                    MobileUi.BLUE,
                    26,
                ),
            )
        }

        row.addView(
            iconBox,
            LinearLayout.LayoutParams(
                dp(58),
                dp(58),
            ).apply {
                marginEnd = dp(11)
            },
        )

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                MobileUi.text(
                    this@DemandsActivity,
                    "Demandas",
                    21f,
                    MobileUi.NAVY,
                    true,
                ),
            )
            addView(
                MobileUi.text(
                    this@DemandsActivity,
                    "Consulte e gerencie as demandas ativas, organizando o monitoramento conforme seus temas de interesse.",
                    11f,
                    MobileUi.MUTED,
                ),
                MobileUi.match(dp(4)),
            )
        }

        row.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        card.addView(row)
        return card
    }

    private fun formCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        box.addView(
            MobileUi.text(
                this,
                "Veículo",
                11f,
                MobileUi.NAVY,
                true,
            ),
        )

        vehicleInput = MobileUi.input(
            this,
            "Selecione ou digite o veículo...",
        )
        box.addView(
            vehicleInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48),
            ).apply {
                topMargin = dp(5)
            },
        )

        box.addView(
            MobileUi.text(
                this,
                "Assunto",
                11f,
                MobileUi.NAVY,
                true,
            ),
            MobileUi.match(dp(10)),
        )

        subjectInput = MobileUi.input(
            this,
            "Digite o assunto da demanda...",
        )
        box.addView(
            subjectInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48),
            ).apply {
                topMargin = dp(5)
            },
        )

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        actions.addView(
            MobileUi.button(
                this,
                "Adicionar",
                true,
                MobileUi.ORANGE,
                R.drawable.ic_add,
            ) {
                addDemand()
            },
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f,
            ).apply {
                marginEnd = dp(4)
            },
        )

        actions.addView(
            MobileUi.button(
                this,
                "Buscar todas",
                true,
                MobileUi.BLUE,
                R.drawable.ic_search,
            ) {
                searchAllDemands()
            },
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f,
            ).apply {
                marginStart = dp(4)
            },
        )

        box.addView(
            actions,
            MobileUi.match(dp(12)),
        )

        card.addView(box)
        return card
    }

    private fun statusCard(): View {
        val card = MobileUi.card(this, 12).apply {
            setCardBackgroundColor(
                MobileUi.GREEN_TINT,
            )
            strokeColor = Color.rgb(
                182,
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

        statusTitle = MobileUi.text(
            this,
            "Status: Pronto",
            13f,
            Color.rgb(
                0,
                116,
                74,
            ),
            true,
        )

        statusText = MobileUi.text(
            this,
            "Sistema disponível para consultar e gerenciar demandas.",
            10.5f,
            Color.rgb(
                64,
                119,
                101,
            ),
        )

        copy.addView(statusTitle)
        copy.addView(
            statusText,
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
            },
        )

        card.addView(row)
        return card
    }

    private fun demandsCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                MobileUi.icon(
                    this@DemandsActivity,
                    R.drawable.ic_demands,
                    MobileUi.NAVY,
                    20,
                ),
            )
            addView(
                MobileUi.text(
                    this@DemandsActivity,
                    "Demandas cadastradas",
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

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        demandCount = MobileUi.text(
            this,
            "0 demandas cadastradas",
            10f,
            MobileUi.MUTED,
        )
        header.addView(demandCount)

        box.addView(header)

        demandsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        box.addView(
            demandsContainer,
            MobileUi.match(dp(8)),
        )

        card.addView(box)
        return card
    }

    private fun resultsCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                MobileUi.icon(
                    this@DemandsActivity,
                    R.drawable.ic_search,
                    MobileUi.NAVY,
                    20,
                ),
            )
            addView(
                MobileUi.text(
                    this@DemandsActivity,
                    "Notícias encontradas pelas demandas",
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

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        resultCount = MobileUi.text(
            this,
            "0 resultados",
            10f,
            MobileUi.MUTED,
        )
        header.addView(resultCount)

        box.addView(header)

        box.addView(
            MobileUi.text(
                this,
                "As matérias encontradas nas buscas de demanda aparecerão aqui automaticamente.",
                10.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(5)),
        )

        resultContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        box.addView(
            resultContainer,
            MobileUi.match(dp(8)),
        )

        card.addView(box)
        return card
    }

    private fun addDemand() {
        val vehicle =
            vehicleInput.text
                ?.toString()
                .orEmpty()
                .trim()
        val subject =
            subjectInput.text
                ?.toString()
                .orEmpty()
                .trim()

        if (
            vehicle.isBlank() ||
            subject.isBlank()
        ) {
            toast("Informe o veículo e o assunto.")
            return
        }

        db.addDemand(
            vehicle,
            subject,
        )

        vehicleInput.setText("")
        subjectInput.setText("")
        refreshDemands()
    }

    private fun refreshDemands() {
        if (!::demandsContainer.isInitialized) {
            return
        }

        val demands = db.demands()

        demandCount.text =
            if (demands.size == 1) {
                "1 demanda cadastrada"
            } else {
                "${demands.size} demandas cadastradas"
            }

        demandsContainer.removeAllViews()

        if (demands.isEmpty()) {
            demandsContainer.addView(
                MobileUi.text(
                    this,
                    "Nenhuma demanda cadastrada.",
                    11.5f,
                    MobileUi.MUTED,
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(8),
                        dp(20),
                        dp(8),
                        dp(20),
                    )
                },
            )
            return
        }

        demands.forEach { demand ->
            demandsContainer.addView(
                demandCard(demand),
                MobileUi.match(dp(7)),
            )
        }
    }

    private fun demandCard(
        demand: Triple<Long, String, String>,
    ): View {
        val card = MobileUi.card(this, 10)
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
                MobileUi.ORANGE_TINT,
                dp(12).toFloat(),
            )
            addView(
                MobileUi.icon(
                    this@DemandsActivity,
                    R.drawable.ic_demands,
                    MobileUi.ORANGE,
                    22,
                ),
            )
        }

        row.addView(
            iconBox,
            LinearLayout.LayoutParams(
                dp(48),
                dp(48),
            ).apply {
                marginEnd = dp(10)
            },
        )

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                MobileUi.text(
                    this@DemandsActivity,
                    "${demand.second} • ${demand.third}",
                    13.5f,
                    MobileUi.NAVY,
                    true,
                ),
            )
            addView(
                MobileUi.text(
                    this@DemandsActivity,
                    "Toque em Buscar para consultar as últimas 24 horas.",
                    10f,
                    MobileUi.MUTED,
                ),
                MobileUi.match(dp(4)),
            )
        }

        row.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
        box.addView(row)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        actions.addView(
            MobileUi.button(
                this,
                "Buscar",
                true,
                MobileUi.BLUE,
                R.drawable.ic_search,
            ) {
                searchDemands(
                    listOf(demand),
                )
            },
            LinearLayout.LayoutParams(
                0,
                dp(44),
                1f,
            ).apply {
                marginEnd = dp(4)
            },
        )

        actions.addView(
            MobileUi.button(
                this,
                "Excluir",
                false,
                MobileUi.PINK,
                R.drawable.ic_delete,
            ) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Excluir demanda")
                    .setMessage(
                        "${demand.second} • ${demand.third}",
                    )
                    .setNegativeButton(
                        "Cancelar",
                        null,
                    )
                    .setPositiveButton(
                        "Excluir",
                    ) { _, _ ->
                        db.deleteDemand(
                            demand.first,
                        )
                        refreshDemands()
                    }
                    .show()
            },
            LinearLayout.LayoutParams(
                0,
                dp(44),
                1f,
            ).apply {
                marginStart = dp(4)
            },
        )

        box.addView(
            actions,
            MobileUi.match(dp(9)),
        )

        card.addView(box)
        return card
    }

    private fun searchAllDemands() {
        val demands = db.demands()

        if (demands.isEmpty()) {
            toast("Nenhuma demanda cadastrada.")
            return
        }

        searchDemands(demands)
    }

    private fun searchDemands(
        demands: List<Triple<Long, String, String>>,
    ) {
        statusTitle.text =
            "Status: Buscando"
        statusText.text =
            "Consultando notícias das demandas pelas últimas 24 horas."

        val started =
            SystemClock.elapsedRealtime()

        io(
            {
                val cutoff =
                    System.currentTimeMillis() -
                        24L * 60L * 60L * 1000L

                val collected =
                    linkedMapOf<String, NewsItem>()

                var failures = 0

                for (demand in demands) {
                    val fetched =
                        try {
                            client.search(
                                demand.third,
                                50,
                            )
                        } catch (_: Throwable) {
                            failures++
                            emptyList()
                        }

                    for (base in fetched) {
                        if (
                            base.publishedAt <
                            cutoff
                        ) {
                            continue
                        }

                        if (
                            !Matching.demandVehicleMatches(
                                base.source,
                                demand.second,
                            )
                        ) {
                            continue
                        }

                        val body =
                            "${base.title} ${base.snippet}"

                        if (
                            !Matching.subjectMatches(
                                body,
                                demand.third,
                            )
                        ) {
                            continue
                        }

                        collected[base.link] =
                            base.copy(
                                pubDate =
                                    formatDate(
                                        base.pubDate,
                                    ),
                                matchedTerm =
                                    demand.third,
                                matchedDemand =
                                    "${demand.second} • ${demand.third}",
                            )
                    }
                }

                Triple(
                    collected.values
                        .sortedByDescending {
                            it.publishedAt
                        },
                    failures,
                    demands.size,
                )
            },
            { outcome ->
                results = outcome.first

                results.forEach { item ->
                    db.addHistoryUnique(
                        type = "news",
                        title = item.title,
                        detail =
                            "${item.source}\n${item.snippet}\nTermo: ${item.matchedDemand}",
                        url = item.link,
                    )
                }

                val elapsed =
                    SystemClock.elapsedRealtime() -
                        started

                statusTitle.text =
                    "Status: Pronto"
                statusText.text =
                    "${outcome.third} demanda(s) consultada(s) • " +
                        "${results.size} resultado(s) • " +
                        "${outcome.second} falha(s) • " +
                        "%02d:%02d".format(
                            elapsed / 60000L,
                            elapsed / 1000L % 60L,
                        )

                renderResults()
            },
            { error ->
                statusTitle.text =
                    "Status: Falha"
                statusText.text =
                    error.message
                        ?: "Não foi possível consultar as demandas."
                toast(
                    error.message
                        ?: "Falha na busca de demandas.",
                )
            },
        )
    }

    private fun renderResults() {
        if (!::resultContainer.isInitialized) {
            return
        }

        resultContainer.removeAllViews()
        resultCount.text =
            if (results.size == 1) {
                "1 resultado"
            } else {
                "${results.size} resultados"
            }

        if (results.isEmpty()) {
            resultContainer.addView(
                MobileUi.text(
                    this,
                    "Nenhuma matéria de demanda exibida.",
                    11.5f,
                    MobileUi.MUTED,
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(8),
                        dp(22),
                        dp(8),
                        dp(22),
                    )
                },
            )
            return
        }

        results.forEach { item ->
            resultContainer.addView(
                NewsCards.create(
                    this,
                    item,
                    true,
                ),
                MobileUi.match(dp(7)),
            )
        }
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
}
