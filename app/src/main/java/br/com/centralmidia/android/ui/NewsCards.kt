package br.com.centralmidia.android.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.NewsItem
import br.com.centralmidia.android.core.dp

object NewsCards {
    fun create(
        activity: BaseActivity,
        item: NewsItem,
        allowExtract: Boolean = true,
    ): View {
        val card = MobileUi.card(activity, 10)

        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        val initials =
            item.source
                .trim()
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") {
                    it.first().uppercaseChar().toString()
                }
                .ifBlank { "NT" }
                .take(2)

        val badge = MobileUi.text(
            activity,
            initials,
            14f,
            Color.WHITE,
            true,
        ).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                MobileUi.BLUE,
                activity.dp(11).toFloat(),
            )
        }

        row.addView(
            badge,
            LinearLayout.LayoutParams(
                activity.dp(48),
                activity.dp(48),
            ).apply {
                marginEnd = activity.dp(10)
            },
        )

        val copy = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        val meta = buildString {
            append(
                item.source.ifBlank {
                    "Fonte"
                },
            )
            if (item.pubDate.isNotBlank()) {
                append("  •  ")
                append(item.pubDate)
            }
            if (item.isNew) {
                append("  •  NOVO")
            }
        }

        copy.addView(
            MobileUi.text(
                activity,
                meta,
                10f,
                MobileUi.MUTED,
                false,
            ),
        )

        copy.addView(
            MobileUi.text(
                activity,
                item.title,
                14f,
                MobileUi.NAVY,
                true,
            ).apply {
                setLineSpacing(
                    0f,
                    1.08f,
                )
            },
            MobileUi.match(activity.dp(4)),
        )

        val tag =
            item.matchedDemand
                .ifBlank {
                    item.matchedTerm
                }
                .trim()

        if (tag.isNotBlank()) {
            copy.addView(
                MobileUi.text(
                    activity,
                    tag,
                    9.5f,
                    MobileUi.BLUE,
                    true,
                ).apply {
                    setPadding(
                        activity.dp(8),
                        activity.dp(4),
                        activity.dp(8),
                        activity.dp(4),
                    )
                    background = MobileUi.rounded(
                        MobileUi.BLUE_TINT,
                        activity.dp(8).toFloat(),
                    )
                },
                MobileUi.wrap(activity.dp(7)),
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

        val actionsScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
        }
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        actions.addView(
            MobileUi.button(
                activity,
                "Abrir matéria",
                false,
                MobileUi.NAVY,
                R.drawable.ic_open,
            ) {
                open(activity, item.link)
            },
            actionLp(activity),
        )

        actions.addView(
            MobileUi.button(
                activity,
                "WhatsApp",
                false,
                MobileUi.GREEN,
                R.drawable.ic_share,
            ) {
                share(activity, item)
            },
            actionLp(activity),
        )

        actions.addView(
            MobileUi.button(
                activity,
                "Copiar link",
                false,
                MobileUi.NAVY,
                R.drawable.ic_copy,
            ) {
                copy(activity, item.link)
            },
            actionLp(activity),
        )

        if (allowExtract) {
            actions.addView(
                MobileUi.button(
                    activity,
                    "Extrair matéria",
                    false,
                    MobileUi.PURPLE,
                    R.drawable.ic_news,
                ) {
                    activity.startActivity(
                        Intent(
                            activity,
                            NewsExtractorActivity::class.java,
                        ).putExtra(
                            "url",
                            item.link,
                        ),
                    )
                },
                actionLp(activity),
            )
        }

        actionsScroll.addView(actions)
        box.addView(
            actionsScroll,
            MobileUi.match(activity.dp(10)),
        )

        card.addView(box)
        return card
    }

    private fun actionLp(
        context: Context,
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            context.dp(42),
        ).apply {
            marginEnd = context.dp(6)
        }

    private fun open(
        activity: BaseActivity,
        url: String,
    ) {
        if (url.isBlank()) return
        activity.startActivity(
            Intent(
                activity,
                BrowserActivity::class.java,
            ).putExtra(
                "url",
                url,
            ),
        )
    }

    private fun share(
        activity: BaseActivity,
        item: NewsItem,
    ) {
        val intent = Intent(
            Intent.ACTION_SEND,
        ).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "${item.title}\n${item.link}",
            )
            setPackage("com.whatsapp")
        }

        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            activity.startActivity(
                Intent.createChooser(
                    Intent(
                        Intent.ACTION_SEND,
                    ).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${item.title}\n${item.link}",
                        )
                    },
                    "Compartilhar matéria",
                ),
            )
        }
    }

    private fun copy(
        activity: BaseActivity,
        link: String,
    ) {
        val clipboard =
            activity.getSystemService(
                Context.CLIPBOARD_SERVICE,
            ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "Link da notícia",
                link,
            ),
        )
        activity.toast("Link copiado.")
    }
}
