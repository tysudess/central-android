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
import br.com.centralmidia.android.core.GoogleNewsUrlResolver
import br.com.centralmidia.android.core.NewsItem
import br.com.centralmidia.android.core.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object NewsCards {
    private val resolver = GoogleNewsUrlResolver()

    fun create(
        activity: BaseActivity,
        item: NewsItem,
        allowExtract: Boolean = true,
    ): View {
        val card = MobileUi.card(activity, 10)
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        val initials = item.source
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "NT" }
            .take(2)

        val badge = MobileUi.text(activity, initials, 14f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(MobileUi.BLUE, activity.dp(11).toFloat())
        }
        row.addView(
            badge,
            LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)).apply {
                marginEnd = activity.dp(10)
            },
        )

        val copy = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val meta = buildString {
            append(item.source.ifBlank { "Fonte" })

            val localDate = formatLocalPublicationTime(item)
            if (localDate.isNotBlank()) {
                append("  •  ")
                append(localDate)
            }

            if (item.isNew) {
                append("  •  NOVO")
            }
        }
        copy.addView(MobileUi.text(activity, meta, 10f, MobileUi.MUTED, false))
        copy.addView(
            MobileUi.text(activity, item.title, 14f, MobileUi.NAVY, true).apply {
                setLineSpacing(0f, 1.08f)
            },
            MobileUi.match(activity.dp(4)),
        )

        val tag = item.matchedDemand.ifBlank { item.matchedTerm }.trim()
        if (tag.isNotBlank()) {
            copy.addView(
                MobileUi.text(activity, tag, 9.5f, MobileUi.BLUE, true).apply {
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
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        box.addView(row)

        val actionsScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
        }
        val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }

        fun addAction(label: String, accent: Int, icon: Int, action: (String) -> Unit) {
            actions.addView(
                MobileUi.button(activity, label, false, accent, icon) {
                    withDirectLink(activity, item.link, action)
                },
                actionLp(activity),
            )
        }

        addAction("Abrir matéria", MobileUi.NAVY, R.drawable.ic_open) { direct ->
            open(activity, direct)
        }
        addAction("WhatsApp", MobileUi.GREEN, R.drawable.ic_share) { direct ->
            share(activity, item.title, direct)
        }
        addAction("Copiar link", MobileUi.NAVY, R.drawable.ic_copy) { direct ->
            copy(activity, direct)
        }

        if (allowExtract) {
            addAction("Extrair matéria", MobileUi.PURPLE, R.drawable.ic_news) { direct ->
                activity.startActivity(
                    Intent(activity, NewsExtractorActivity::class.java)
                        .putExtra("url", direct),
                )
            }
        }

        actionsScroll.addView(actions)
        box.addView(actionsScroll, MobileUi.match(activity.dp(10)))
        card.addView(box)
        return card
    }

    private fun withDirectLink(
        activity: BaseActivity,
        original: String,
        action: (String) -> Unit,
    ) {
        if (original.isBlank()) return
        if (!resolver.isGoogleNews(original)) {
            action(original)
            return
        }

        activity.toast("Obtendo link direto da matéria…")
        activity.io(
            { resolver.resolve(original) },
            { direct ->
                if (direct.isBlank() || resolver.isGoogleNews(direct)) {
                    activity.toast(
                        "Não foi possível obter o link direto do veículo agora. Tente novamente.",
                    )
                } else {
                    action(direct)
                }
            },
            {
                activity.toast(
                    "Não foi possível obter o link direto do veículo agora. Tente novamente.",
                )
            },
        )
    }

    /**
     * O Google News entrega pubDate em GMT/UTC.
     *
     * A ordenação continua usando publishedAt (epoch UTC), mas a interface
     * sempre converte esse instante para o fuso configurado no celular.
     * Isso também corrige resultados antigos salvos com a string RFC-822
     * "Fri, 25 Sep 2026 ... GMT".
     */
    private fun formatLocalPublicationTime(item: NewsItem): String {
        val timestamp =
            if (item.publishedAt > 0L) {
                item.publishedAt
            } else {
                parseLegacyPublicationTime(item.pubDate)
            }

        if (timestamp <= 0L) {
            return item.pubDate
        }

        return SimpleDateFormat(
            "dd/MM/yyyy HH:mm",
            Locale(
                "pt",
                "BR",
            ),
        ).apply {
            timeZone = TimeZone.getDefault()
        }.format(
            Date(timestamp),
        )
    }

    private fun parseLegacyPublicationTime(raw: String): Long {
        if (raw.isBlank()) {
            return 0L
        }

        val patterns =
            listOf(
                "EEE, dd MMM yyyy HH:mm:ss z",
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "dd/MM/yyyy HH:mm",
            )

        for (pattern in patterns) {
            val parsed =
                runCatching {
                    SimpleDateFormat(
                        pattern,
                        if (pattern.startsWith("EEE")) {
                            Locale.US
                        } else {
                            Locale(
                                "pt",
                                "BR",
                            )
                        },
                    ).parse(raw)
                }.getOrNull()

            if (parsed != null) {
                return parsed.time
            }
        }

        return 0L
    }

    private fun actionLp(context: Context): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            context.dp(42),
        ).apply {
            marginEnd = context.dp(6)
        }

    private fun open(activity: BaseActivity, url: String) {
        activity.startActivity(
            Intent(activity, BrowserActivity::class.java).putExtra("url", url),
        )
    }

    private fun share(activity: BaseActivity, title: String, link: String) {
        val text = "$title\n$link"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
        }
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            activity.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    "Compartilhar matéria",
                ),
            )
        }
    }

    private fun copy(activity: BaseActivity, link: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Link da notícia", link))
        activity.toast("Link direto da matéria copiado.")
    }
}
