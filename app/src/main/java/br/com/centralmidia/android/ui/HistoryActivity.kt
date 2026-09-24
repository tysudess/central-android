package br.com.centralmidia.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import br.com.centralmidia.android.core.NewsItem
import br.com.centralmidia.android.core.dp
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : BaseActivity() {
    private enum class Tab {
        NEWS,
        VIDEOS,
    }

    private lateinit var db: CentralDb
    private lateinit var listContainer: LinearLayout
    private lateinit var count: TextView

    private var tab = Tab.NEWS
    private var tabButtons =
        linkedMapOf<Tab, MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = CentralDb(this)

        val root = MobileScaffold.page(
            this,
            "Histórico",
            "Histórico local das buscas e resultados.",
            MobileScaffold.Tab.MORE,
            showAutomation = true,
        )

        root.addView(
            toolbar(),
            MobileUi.match(dp(14)),
        )

        count = MobileUi.text(
            this,
            "",
            10.5f,
            MobileUi.MUTED,
        )
        root.addView(
            count,
            MobileUi.match(dp(8)),
        )

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(
            listContainer,
            MobileUi.match(dp(6)),
        )

        refresh()
    }

    private fun toolbar(): View {
        val card = MobileUi.card(
            this,
            10,
        )
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun addTab(
            value: Tab,
            label: String,
            icon: Int,
        ) {
            val button = MobileUi.button(
                this,
                label,
                false,
                MobileUi.BLUE,
                icon,
            ) {
                tab = value
                updateTabs()
                refresh()
            }

            tabButtons[value] = button

            tabs.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    dp(48),
                    1f,
                ).apply {
                    marginEnd = dp(5)
                },
            )
        }

        addTab(
            Tab.NEWS,
            "Notícias",
            R.drawable.ic_news,
        )
        addTab(
            Tab.VIDEOS,
            "Vídeos",
            R.drawable.ic_video,
        )

        box.addView(tabs)

        box.addView(
            MobileUi.button(
                this,
                "Limpar histórico",
                false,
                MobileUi.PINK,
                R.drawable.ic_delete,
            ) {
                db.clearHistory(
                    if (tab == Tab.NEWS) {
                        "news"
                    } else {
                        "video"
                    },
                )
                refresh()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46),
            ).apply {
                topMargin = dp(8)
            },
        )

        card.addView(box)
        updateTabs()
        return card
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

            button.iconTint =
                ColorStateList.valueOf(
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

    private fun refresh() {
        if (!::listContainer.isInitialized) {
            return
        }

        val type =
            if (tab == Tab.NEWS) {
                "news"
            } else {
                "video"
            }

        val rows =
            db.history(
                1000,
                type,
            )

        count.text =
            if (rows.size == 1) {
                "1 registro"
            } else {
                "${rows.size} registros"
            }

        listContainer.removeAllViews()

        if (rows.isEmpty()) {
            listContainer.addView(
                MobileUi.text(
                    this,
                    "Nenhum item no histórico desta categoria.",
                    11.5f,
                    MobileUi.MUTED,
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(8),
                        dp(28),
                        dp(8),
                        dp(28),
                    )
                },
            )
            return
        }

        rows.forEach { row ->
            listContainer.addView(
                if (
                    tab == Tab.NEWS &&
                    row[5].isNotBlank()
                ) {
                    newsHistoryCard(row)
                } else {
                    genericHistoryCard(row)
                },
                MobileUi.match(dp(7)),
            )
        }
    }

    private fun newsHistoryCard(
        row: Array<String>,
    ): View {
        val detail =
            row[4]
                .split("\n")

        val source =
            detail.firstOrNull()
                .orEmpty()
                .ifBlank {
                    "Notícia"
                }

        val snippet =
            detail.getOrNull(1)
                .orEmpty()

        val termLine =
            detail.firstOrNull {
                it.startsWith(
                    "Termo:",
                    ignoreCase = true,
                )
            }
                .orEmpty()
                .substringAfter(
                    ":",
                    "",
                )
                .trim()

        val item = NewsItem(
            title = row[3],
            source = source,
            link = row[5],
            pubDate = formatTs(
                row[1]
                    .toLongOrNull()
                    ?: 0L,
            ),
            snippet = snippet,
            matchedTerm = termLine,
        )

        return NewsCards.create(
            this,
            item,
            allowExtract = false,
        )
    }

    private fun genericHistoryCard(
        row: Array<String>,
    ): View {
        val card = MobileUi.card(
            this,
            11,
        )
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        val iconBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                if (tab == Tab.NEWS) {
                    MobileUi.BLUE_TINT
                } else {
                    MobileUi.PURPLE_TINT
                },
                dp(12).toFloat(),
            )

            addView(
                MobileUi.icon(
                    this@HistoryActivity,
                    if (tab == Tab.NEWS) {
                        R.drawable.ic_news
                    } else {
                        R.drawable.ic_video
                    },
                    if (tab == Tab.NEWS) {
                        MobileUi.BLUE
                    } else {
                        MobileUi.PURPLE
                    },
                    23,
                ),
            )
        }

        top.addView(
            iconBox,
            LinearLayout.LayoutParams(
                dp(52),
                dp(52),
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
                formatTs(
                    row[1]
                        .toLongOrNull()
                        ?: 0L,
                ),
                9.5f,
                MobileUi.MUTED,
            ),
        )

        copy.addView(
            MobileUi.text(
                this,
                row[3],
                13.5f,
                MobileUi.NAVY,
                true,
            ),
            MobileUi.match(dp(4)),
        )

        if (row[4].isNotBlank()) {
            copy.addView(
                MobileUi.text(
                    this,
                    row[4],
                    10f,
                    MobileUi.MUTED,
                ),
                MobileUi.match(dp(4)),
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

        box.addView(top)

        if (row[5].isNotBlank()) {
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            actions.addView(
                MobileUi.button(
                    this,
                    if (tab == Tab.NEWS) {
                        "Abrir matéria"
                    } else {
                        "Abrir vídeo"
                    },
                    false,
                    MobileUi.NAVY,
                    R.drawable.ic_open,
                ) {
                    openUrl(row[5])
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
                    "Copiar link",
                    false,
                    MobileUi.NAVY,
                    R.drawable.ic_copy,
                ) {
                    copyLink(row[5])
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
        }

        card.addView(box)
        return card
    }

    private fun copyLink(
        link: String,
    ) {
        val clipboard =
            getSystemService(
                Context.CLIPBOARD_SERVICE,
            ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "Link",
                link,
            ),
        )

        toast("Link copiado.")
    }

    private fun formatTs(
        value: Long,
    ): String =
        if (value <= 0L) {
            "—"
        } else {
            SimpleDateFormat(
                "dd/MM/yyyy HH:mm",
                Locale(
                    "pt",
                    "BR",
                ),
            ).format(Date(value))
        }
}
