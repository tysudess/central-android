package br.com.centralmidia.android.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.dp
import com.google.android.material.button.MaterialButton

class TermsActivity : BaseActivity() {
    private class Section(
        val kind: String,
        val title: String,
        val subtitle: String,
        val accent: Int,
        val tint: Int,
        val icon: Int,
    ) {
        val selected: MutableSet<String> = linkedSetOf()
        lateinit var count: TextView
        lateinit var input: android.widget.EditText
        lateinit var list: LinearLayout
        lateinit var delete: MaterialButton
    }

    private lateinit var db: CentralDb
    private lateinit var news: Section
    private lateinit var videos: Section

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = CentralDb(this)

        val root = MobileScaffold.page(
            this,
            "Termos",
            "Termos independentes para notícias e vídeos.",
            MobileScaffold.Tab.MORE,
            showAutomation = true,
        )

        news = Section(
            kind = "news",
            title = "Termos de Notícias",
            subtitle = "Usados na varredura de matérias",
            accent = MobileUi.BLUE,
            tint = MobileUi.BLUE_TINT,
            icon = R.drawable.ic_news,
        )
        videos = Section(
            kind = "video",
            title = "Termos de Vídeos",
            subtitle = "Lista independente para vídeos",
            accent = MobileUi.PURPLE,
            tint = MobileUi.PURPLE_TINT,
            icon = R.drawable.ic_video,
        )

        root.addView(
            termCard(news),
            MobileUi.match(dp(14)),
        )
        root.addView(
            termCard(videos),
            MobileUi.match(dp(12)),
        )

        refresh(news)
        refresh(videos)
    }

    private fun termCard(
        section: Section,
    ): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                section.tint,
                dp(13).toFloat(),
            )
            addView(
                MobileUi.icon(
                    this@TermsActivity,
                    section.icon,
                    section.accent,
                    25,
                ),
            )
        }

        header.addView(
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
                section.title,
                18f,
                MobileUi.NAVY,
                true,
            ),
        )
        copy.addView(
            MobileUi.text(
                this,
                section.subtitle,
                10f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(3)),
        )

        header.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        section.count = MobileUi.text(
            this,
            "0 termo(s)",
            10f,
            MobileUi.BLUE,
            true,
        ).apply {
            setPadding(
                dp(10),
                dp(8),
                dp(10),
                dp(8),
            )
            background = MobileUi.rounded(
                MobileUi.BLUE_TINT,
                dp(9).toFloat(),
            )
        }

        header.addView(section.count)
        box.addView(header)

        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        section.input = MobileUi.input(
            this,
            "Novo termo",
        )

        addRow.addView(
            section.input,
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f,
            ).apply {
                marginEnd = dp(6)
            },
        )

        addRow.addView(
            MobileUi.button(
                this,
                "Adicionar",
                true,
                MobileUi.BLUE,
                R.drawable.ic_add,
            ) {
                addTerm(section)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(48),
            ),
        )

        box.addView(
            addRow,
            MobileUi.match(dp(10)),
        )

        section.list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        box.addView(
            section.list,
            MobileUi.match(dp(10)),
        )

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        footer.addView(
            MobileUi.text(
                this,
                "Toque para selecionar • é possível excluir vários termos.",
                9f,
                MobileUi.MUTED,
            ),
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginEnd = dp(8)
            },
        )

        section.delete = MobileUi.button(
            this,
            "Excluir selecionado",
            false,
            MobileUi.PINK,
            R.drawable.ic_delete,
        ) {
            deleteSelected(section)
        }

        footer.addView(
            section.delete,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44),
            ),
        )

        box.addView(
            footer,
            MobileUi.match(dp(8)),
        )

        card.addView(box)
        return card
    }

    private fun addTerm(
        section: Section,
    ) {
        val value =
            section.input.text
                ?.toString()
                .orEmpty()
                .trim()

        if (value.isBlank()) {
            return
        }

        val exists =
            db.terms(section.kind)
                .any {
                    it.second.equals(
                        value,
                        ignoreCase = true,
                    )
                }

        if (!exists) {
            db.addTerm(
                value,
                section.kind,
            )
        }

        section.input.setText("")
        refresh(section)
    }

    private fun deleteSelected(
        section: Section,
    ) {
        if (section.selected.isEmpty()) {
            return
        }

        val selected =
            section.selected.toSet()

        db.terms(section.kind)
            .filter {
                it.second in selected
            }
            .forEach {
                db.deleteTerm(
                    it.first,
                    section.kind,
                )
            }

        section.selected.clear()
        refresh(section)
    }

    private fun refresh(
        section: Section,
    ) {
        val rows =
            db.terms(section.kind)

        val valid =
            rows.map {
                it.second
            }.toSet()

        section.selected.retainAll(valid)

        section.count.text =
            "${rows.size} termo(s)"

        section.list.removeAllViews()

        rows.forEach { (_, value) ->
            val selected =
                value in section.selected

            val row = MobileUi.text(
                this,
                value,
                11.5f,
                if (selected) {
                    MobileUi.BLUE
                } else {
                    MobileUi.NAVY
                },
                selected,
            ).apply {
                setPadding(
                    dp(12),
                    dp(10),
                    dp(12),
                    dp(10),
                )
                background = MobileUi.rounded(
                    if (selected) {
                        Color.rgb(
                            221,
                            238,
                            255,
                        )
                    } else {
                        Color.rgb(
                            248,
                            251,
                            255,
                        )
                    },
                    dp(8).toFloat(),
                    if (selected) {
                        MobileUi.BLUE
                    } else {
                        MobileUi.BORDER
                    },
                    if (selected) {
                        dp(2)
                    } else {
                        dp(1)
                    },
                )
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    if (
                        value in section.selected
                    ) {
                        section.selected.remove(
                            value,
                        )
                    } else {
                        section.selected.add(
                            value,
                        )
                    }
                    refresh(section)
                }
            }

            section.list.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(5)
                },
            )
        }

        val count =
            section.selected.size

        section.delete.isEnabled =
            count > 0

        section.delete.text =
            when {
                count <= 1 ->
                    "Excluir selecionado"

                else ->
                    "Excluir $count selecionados"
            }

        section.delete.iconTint =
            ColorStateList.valueOf(
                if (count > 0) {
                    MobileUi.PINK
                } else {
                    Color.rgb(
                        170,
                        180,
                        195,
                    )
                },
            )

        section.delete.alpha =
            if (count > 0) {
                1f
            } else {
                0.55f
            }
    }
}
