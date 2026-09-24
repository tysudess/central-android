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
            "",
            MobileScaffold.Tab.MORE,
            showAutomation = false,
        )

        root.addView(
            generalCard(),
            MobileUi.match(dp(8)),
        )
        root.addView(
            serviceCard(
                icon = R.drawable.ic_news,
                title = "Notícias",
                subtitle = "Varredura dos termos e fontes selecionadas",
                accent = MobileUi.BLUE,
                tint = MobileUi.BLUE_TINT,
                newsCard = true,
            ),
            MobileUi.match(dp(9)),
        )
        root.addView(
            serviceCard(
                icon = R.drawable.ic_demands,
                title = "Demandas",
                subtitle = "Pesquisa de todas as demandas ativas",
                accent = MobileUi.ORANGE,
                tint = MobileUi.ORANGE_TINT,
                newsCard = false,
            ),
            MobileUi.match(dp(9)),
        )
        root.addView(
            videoCard(),
            MobileUi.match(dp(9)),
        )

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
        root.addView(status, MobileUi.match(dp(9)))

        root.addView(
            MobileUi.button(
                this,
                "Aplicar automação",
                true,
                MobileUi.BLUE,
                R.drawable.ic_search,
            ) {
                save()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52),
            ).apply {
                topMargin = dp(9)
            },
        )

        root.addView(
            MobileUi.text(
                this,
                "Conexões diretas pela internet. O Android usa WorkManager para manter as rotinas automáticas.",
                9.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(7)),
        )

        load()
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

        general = SwitchMaterial(this).apply {
            text = "Buscas automáticas"
            textSize = 12.5f
            setTextColor(MobileUi.NAVY)
        }
        box.addView(
            general,
            MobileUi.match(dp(8)),
        )

        box.addView(
            MobileUi.text(
                this,
                "As rotinas são retomadas pelo WorkManager após reinicializações, respeitando as regras do Android.",
                9.5f,
                MobileUi.GREEN,
                true,
            ).apply {
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = MobileUi.rounded(
                    MobileUi.GREEN_TINT,
                    dp(9).toFloat(),
                )
            },
            MobileUi.match(dp(6)),
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
        }
        copy.addView(
            MobileUi.text(
                this,
                title,
                17f,
                MobileUi.NAVY,
                true,
            ),
        )
        copy.addView(
            MobileUi.text(
                this,
                subtitle,
                9.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(3)),
        )
        top.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        val toggle = SwitchMaterial(this).apply {
            text = "Automático"
            textSize = 11.5f
            setTextColor(MobileUi.NAVY)
        }
        if (newsCard) {
            news = toggle
        } else {
            demands = toggle
        }
        top.addView(toggle)
        box.addView(top)

        val interval = intervalSpinner()
        if (newsCard) {
            newsInterval = interval
        } else {
            demandInterval = interval
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            MobileUi.text(
                this,
                "Intervalo",
                11f,
                MobileUi.NAVY,
                true,
            ),
        )
        row.addView(
            interval,
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f,
            ).apply {
                marginStart = dp(10)
            },
        )

        box.addView(
            row,
            MobileUi.match(dp(9)),
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
        }
        copy.addView(
            MobileUi.text(
                this,
                "Vídeos",
                17f,
                MobileUi.NAVY,
                true,
            ),
        )
        copy.addView(
            MobileUi.text(
                this,
                "Execução nos horários definidos abaixo",
                9.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(3)),
        )
        top.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        videos = SwitchMaterial(this).apply {
            text = "Automático"
            textSize = 11.5f
            setTextColor(MobileUi.NAVY)
        }
        top.addView(videos)
        box.addView(top)

        box.addView(
            MobileUi.text(
                this,
                "Horários automáticos",
                10.5f,
                MobileUi.NAVY,
                true,
            ),
            MobileUi.match(dp(9)),
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
                topMargin = dp(5)
            },
        )

        box.addView(
            MobileUi.text(
                this,
                "Use HH:MM separados por vírgula.",
                9f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(4)),
        )

        card.addView(box)
        return card
    }

    private fun intervalSpinner(): Spinner =
        Spinner(this).apply {
            val labels = intervals.map { "$it min" }
            adapter =
                object : ArrayAdapter<String>(
                    this@SettingsActivity,
                    android.R.layout.simple_spinner_item,
                    labels,
                ) {
                    init {
                        setDropDownViewResource(
                            android.R.layout.simple_spinner_dropdown_item,
                        )
                    }

                    override fun getView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup,
                    ): View =
                        super.getView(
                            position,
                            convertView,
                            parent,
                        ).apply {
                            if (this is TextView) {
                                setTextColor(MobileUi.NAVY)
                                textSize = 15f
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(
                                    dp(14),
                                    0,
                                    dp(14),
                                    0,
                                )
                            }
                        }

                    override fun getDropDownView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup,
                    ): View =
                        super.getDropDownView(
                            position,
                            convertView,
                            parent,
                        ).apply {
                            if (this is TextView) {
                                setTextColor(MobileUi.NAVY)
                                textSize = 14f
                                setPadding(
                                    dp(14),
                                    dp(12),
                                    dp(14),
                                    dp(12),
                                )
                                setBackgroundColor(Color.WHITE)
                            }
                        }
                }

            background = MobileUi.rounded(
                Color.WHITE,
                dp(10).toFloat(),
                MobileUi.BORDER,
                dp(1),
            )
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
            c.videoTimes
                .sorted()
                .joinToString(", "),
        )
    }

    private fun save() {
        val times =
            AutomationPreferences.parseTimes(
                videoTimes.text
                    ?.toString()
                    .orEmpty(),
            )

        if (
            videos.isChecked &&
            times.isEmpty()
        ) {
            toast(
                "Informe ao menos um horário de vídeo no formato HH:MM.",
            )
            return
        }

        store.save(
            AutomationPreferences.Config(
                general = general.isChecked,
                news = news.isChecked,
                newsInterval =
                    intervals[
                        newsInterval.selectedItemPosition
                    ],
                demands = demands.isChecked,
                demandInterval =
                    intervals[
                        demandInterval.selectedItemPosition
                    ],
                videos = videos.isChecked,
                videoTimes = times,
            ),
        )

        MonitoringScheduler.apply(this)

        status.text =
            if (general.isChecked) {
                "Automação salva e aplicada. Notícias: ${intervals[newsInterval.selectedItemPosition]} min • Demandas: ${intervals[demandInterval.selectedItemPosition]} min."
            } else {
                "Automação salva. Todas as rotinas automáticas estão pausadas."
            }

        toast("Configurações salvas.")
    }
}
