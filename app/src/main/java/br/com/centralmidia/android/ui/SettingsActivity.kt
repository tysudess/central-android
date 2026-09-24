package br.com.centralmidia.android.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import br.com.centralmidia.android.R
import br.com.centralmidia.android.automation.MonitoringScheduler
import br.com.centralmidia.android.core.AutomationPreferences
import br.com.centralmidia.android.core.dp
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : BaseActivity() {
    private lateinit var store: AutomationPreferences
    private lateinit var general: SwitchMaterial
    private lateinit var news: SwitchMaterial
    private lateinit var demands: SwitchMaterial
    private lateinit var videos: SwitchMaterial
    private lateinit var newsInterval: Spinner
    private lateinit var demandInterval: Spinner
    private lateinit var videoTimes: android.widget.EditText
    private lateinit var status: TextView

    private val intervals = listOf(15, 30, 45, 60, 120)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AutomationPreferences(this)

        val root = MobileScaffold.page(
            this,
            "Configurações",
            "Automação, intervalos e horários do Android. Sem configuração de proxy.",
            MobileScaffold.Tab.MORE,
            showAutomation = true,
        )

        root.addView(heroCard(), MobileUi.match(dp(14)))
        root.addView(generalCard(), MobileUi.match(dp(10)))
        root.addView(
            serviceCard(
                R.drawable.ic_news,
                "Notícias",
                "Varredura dos termos e fontes selecionadas",
                MobileUi.BLUE,
                MobileUi.BLUE_TINT,
                true,
            ),
            MobileUi.match(dp(10)),
        )
        root.addView(
            serviceCard(
                R.drawable.ic_demands,
                "Demandas",
                "Pesquisa de todas as demandas ativas",
                MobileUi.ORANGE,
                MobileUi.ORANGE_TINT,
                false,
            ),
            MobileUi.match(dp(10)),
        )
        root.addView(videoCard(), MobileUi.match(dp(10)))

        status = MobileUi.text(
            this,
            "As alterações entram em vigor ao tocar em Aplicar automação.",
            10.5f,
            MobileUi.GREEN,
            true,
        ).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = MobileUi.rounded(
                MobileUi.GREEN_TINT,
                dp(11).toFloat(),
                Color.rgb(184, 231, 207),
                dp(1),
            )
        }
        root.addView(status, MobileUi.match(dp(10)))

        root.addView(
            MobileUi.button(
                this,
                "Aplicar automação",
                true,
                MobileUi.BLUE,
                R.drawable.ic_search,
            ) { save() },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52),
            ).apply {
                topMargin = dp(10)
            },
        )

        root.addView(
            MobileUi.text(
                this,
                "No Android não existe Proxy Geral nesta tela. As conexões do aplicativo são diretas. O WorkManager preserva as rotinas após reinicializações e pode ajustar alguns minutos para economizar bateria.",
                10f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(10)),
        )

        load()
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
                dp(14).toFloat(),
            )
            addView(
                MobileUi.icon(
                    this@SettingsActivity,
                    R.drawable.ic_more,
                    MobileUi.BLUE,
                    26,
                ),
            )
        }

        row.addView(
            iconBox,
            LinearLayout.LayoutParams(
                dp(56),
                dp(56),
            ).apply {
                marginEnd = dp(11)
            },
        )

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                MobileUi.text(
                    this@SettingsActivity,
                    "Central de configurações",
                    20f,
                    MobileUi.NAVY,
                    true,
                ),
            )
            addView(
                MobileUi.text(
                    this@SettingsActivity,
                    "Controles gerais do monitoramento móvel.",
                    10.5f,
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
        row.addView(
            MobileUi.statusChip(
                this,
                "Conexão direta",
                MobileUi.GREEN,
            ),
        )

        card.addView(row)
        return card
    }

    private fun generalCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        box.addView(
            MobileUi.text(
                this,
                "Automação e inicialização",
                18f,
                MobileUi.NAVY,
                true,
            ),
        )
        box.addView(
            MobileUi.text(
                this,
                "Liga ou pausa todas as rotinas automáticas.",
                10f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(4)),
        )

        general = toggle(
            "Buscas automáticas",
            "Controle geral das rotinas do Android",
        )
        box.addView(
            general,
            MobileUi.match(dp(10)),
        )

        box.addView(
            MobileUi.text(
                this,
                "Retomar após reiniciar o aparelho: automático pelo WorkManager",
                10f,
                MobileUi.GREEN,
                true,
            ).apply {
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = MobileUi.rounded(
                    MobileUi.GREEN_TINT,
                    dp(9).toFloat(),
                )
            },
            MobileUi.match(dp(8)),
        )

        card.addView(box)
        return card
    }

    private fun serviceCard(
        icon: Int,
        title: String,
        subtitle: String,
        accent: Int,
        tint: Int,
        newsCard: Boolean,
    ): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                tint,
                dp(12).toFloat(),
            )
            addView(
                MobileUi.icon(
                    this@SettingsActivity,
                    icon,
                    accent,
                    24,
                ),
            )
        }

        top.addView(
            iconBox,
            LinearLayout.LayoutParams(
                dp(50),
                dp(50),
            ).apply {
                marginEnd = dp(10)
            },
        )

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                MobileUi.text(
                    this@SettingsActivity,
                    title,
                    17f,
                    MobileUi.NAVY,
                    true,
                ),
            )
            addView(
                MobileUi.text(
                    this@SettingsActivity,
                    subtitle,
                    9.5f,
                    MobileUi.MUTED,
                ),
                MobileUi.match(dp(3)),
            )
        }

        top.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        val sw = toggle(
            "Automático",
            "",
        )
        if (newsCard) {
            news = sw
        } else {
            demands = sw
        }

        top.addView(sw)
        box.addView(top)

        val spinner = intervalSpinner()
        if (newsCard) {
            newsInterval = spinner
        } else {
            demandInterval = spinner
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(
            MobileUi.text(
                this,
                "Intervalo",
                10.5f,
                MobileUi.NAVY,
                true,
            ),
        )
        row.addView(
            spinner,
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f,
            ).apply {
                marginStart = dp(10)
            },
        )
        row.addView(
            MobileUi.text(
                this,
                "min",
                10f,
                MobileUi.MUTED,
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = dp(8)
            },
        )

        box.addView(
            row,
            MobileUi.match(dp(10)),
        )

        card.addView(box)
        return card
    }

    private fun videoCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                MobileUi.PURPLE_TINT,
                dp(12).toFloat(),
            )
            addView(
                MobileUi.icon(
                    this@SettingsActivity,
                    R.drawable.ic_video,
                    MobileUi.PURPLE,
                    24,
                ),
            )
        }

        top.addView(
            iconBox,
            LinearLayout.LayoutParams(
                dp(50),
                dp(50),
            ).apply {
                marginEnd = dp(10)
            },
        )

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                MobileUi.text(
                    this@SettingsActivity,
                    "Vídeos",
                    17f,
                    MobileUi.NAVY,
                    true,
                ),
            )
            addView(
                MobileUi.text(
                    this@SettingsActivity,
                    "Execução nos horários definidos abaixo",
                    9.5f,
                    MobileUi.MUTED,
                ),
                MobileUi.match(dp(3)),
            )
        }

        top.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        videos = toggle(
            "Automático",
            "",
        )
        top.addView(videos)
        box.addView(top)

        box.addView(
            MobileUi.text(
                this,
                "Horários automáticos dos vídeos",
                10.5f,
                MobileUi.NAVY,
                true,
            ),
            MobileUi.match(dp(10)),
        )

        videoTimes = MobileUi.input(
            this,
            "08:00, 12:00, 15:00, 19:00, 21:00",
        )

        box.addView(
            videoTimes,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48),
            ).apply {
                topMargin = dp(6)
            },
        )

        box.addView(
            MobileUi.text(
                this,
                "Use HH:MM separados por vírgula.",
                9.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(5)),
        )

        card.addView(box)
        return card
    }

    private fun toggle(
        title: String,
        subtitle: String,
    ): SwitchMaterial =
        SwitchMaterial(this).apply {
            text = title
            textSize = 11.5f
            setTextColor(MobileUi.NAVY)
            if (subtitle.isNotBlank()) {
                contentDescription = subtitle
            }
        }

    private fun intervalSpinner(): Spinner =
        Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                intervals.map { it.toString() },
            )
            background = MobileUi.rounded(
                Color.WHITE,
                dp(10).toFloat(),
                MobileUi.BORDER,
                dp(1),
            )
            setPadding(dp(10), 0, dp(10), 0)
        }

    private fun load() {
        val c = store.load()
        general.isChecked = c.general
        news.isChecked = c.news
        demands.isChecked = c.demands
        videos.isChecked = c.videos
        newsInterval.setSelection(
            intervals.indexOf(c.newsInterval)
                .takeIf { it >= 0 }
                ?: 1,
        )
        demandInterval.setSelection(
            intervals.indexOf(c.demandInterval)
                .takeIf { it >= 0 }
                ?: 3,
        )
        videoTimes.setText(
            c.videoTimes.sorted().joinToString(", "),
        )
    }

    private fun save() {
        val times = AutomationPreferences.parseTimes(
            videoTimes.text?.toString().orEmpty(),
        )

        if (videos.isChecked && times.isEmpty()) {
            toast(
                "Informe ao menos um horário de vídeo no formato HH:MM.",
            )
            return
        }

        store.save(
            AutomationPreferences.Config(
                general = general.isChecked,
                news = news.isChecked,
                newsInterval = intervals[newsInterval.selectedItemPosition],
                demands = demands.isChecked,
                demandInterval = intervals[demandInterval.selectedItemPosition],
                videos = videos.isChecked,
                videoTimes = times,
            ),
        )

        MonitoringScheduler.apply(this)

        status.text =
            if (general.isChecked) {
                "Automação salva e aplicada. Rotinas ativas conforme as opções acima."
            } else {
                "Automação salva. Todas as rotinas automáticas estão pausadas."
            }

        toast("Configurações salvas.")
    }
}
