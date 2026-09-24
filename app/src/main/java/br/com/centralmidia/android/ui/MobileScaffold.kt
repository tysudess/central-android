package br.com.centralmidia.android.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import br.com.centralmidia.android.automation.MonitoringScheduler
import br.com.centralmidia.android.core.dp
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Estrutura visual mobile da Central Inteligente de Mídia.
 *
 * Mantém o Android com navegação própria, sem Proxy Geral. O chip de rede
 * informa explicitamente que a conexão é direta.
 */
object MobileScaffold {
    enum class Tab { HOME, NEWS, VIDEOS, NONE }

    fun page(
        activity: BaseActivity,
        title: String,
        subtitle: String,
        selected: Tab,
        showAutomation: Boolean = true,
    ): LinearLayout {
        activity.window.statusBarColor = Color.WHITE
        activity.window.navigationBarColor = Color.WHITE
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) isAppearanceLightNavigationBars = true
        }

        val shell = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MobileUi.BG)
        }

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(16), activity.dp(10), activity.dp(16), activity.dp(18))
        }

        content.addView(header(activity), MobileUi.match())
        content.addView(
            titleBlock(activity, title, subtitle, showAutomation),
            MobileUi.match(activity.dp(8)),
        )

        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        shell.addView(scroll, LinearLayout.LayoutParams(0, 0).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = 0
            weight = 1f
        })
        shell.addView(bottomNav(activity, selected), MobileUi.match())
        activity.setContentView(shell)
        return content
    }

    private fun header(activity: BaseActivity): View {
        val session = activity.auth.session
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, activity.dp(2), 0, activity.dp(8))
        }

        val logo = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            val heights = listOf(16, 24, 32)
            heights.forEachIndexed { index, height ->
                addView(View(activity).apply {
                    background = MobileUi.rounded(
                        when (index) {
                            0 -> Color.rgb(83, 164, 255)
                            1 -> Color.rgb(48, 139, 246)
                            else -> Color.rgb(25, 113, 230)
                        },
                        activity.dp(5).toFloat(),
                    )
                }, LinearLayout.LayoutParams(activity.dp(6), activity.dp(height)).apply {
                    marginEnd = activity.dp(3)
                })
            }
        }
        row.addView(logo, LinearLayout.LayoutParams(activity.dp(35), activity.dp(36)))
        row.addView(MobileUi.text(activity, "Central Inteligente\nde Mídia", 17f, MobileUi.NAVY, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = activity.dp(8)
            })

        val bell = MobileUi.text(activity, "◉", 18f, MobileUi.NAVY, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Notificações"
        }
        row.addView(bell, LinearLayout.LayoutParams(activity.dp(40), activity.dp(40)))

        val initial = session?.user?.name?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
            ?: session?.user?.username?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
            ?: "U"
        val avatar = MobileUi.text(activity, initial, 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.oval(Color.rgb(86, 157, 245))
            setOnClickListener { activity.startActivity(Intent(activity, AccountActivity::class.java)) }
            contentDescription = "Minha conta"
        }
        row.addView(avatar, LinearLayout.LayoutParams(activity.dp(42), activity.dp(42)).apply {
            marginStart = activity.dp(4)
        })
        return row
    }

    private fun titleBlock(
        activity: BaseActivity,
        title: String,
        subtitle: String,
        showAutomation: Boolean,
    ): View {
        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val copy = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(MobileUi.text(activity, title, 28f, MobileUi.NAVY, true))
        copy.addView(MobileUi.text(activity, subtitle, 14f, MobileUi.MUTED, false).apply {
            setPadding(0, activity.dp(2), activity.dp(8), 0)
        })
        outer.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val chips = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        chips.addView(
            MobileUi.statusChip(activity, "●  Conexão direta", MobileUi.GREEN),
            MobileUi.wrap(),
        )
        if (showAutomation) {
            val automation = MobileUi.statusChip(activity, "●  Automação pronta", MobileUi.GREEN)
            chips.addView(automation, MobileUi.wrap(activity.dp(6)))
            WorkManager.getInstance(activity)
                .getWorkInfosForUniqueWorkLiveData("central-monitoring")
                .observe(activity) { infos ->
                    val active = infos.any {
                        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                    }
                    automation.text = if (active) "●  Automação ativa" else "●  Automação pronta"
                }
        }
        outer.addView(chips, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return outer
    }

    private fun bottomNav(activity: BaseActivity, selected: Tab): View {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(activity.dp(8), activity.dp(6), activity.dp(8), activity.dp(8))
            background = MobileUi.rounded(Color.WHITE, 0f)
            elevation = activity.dp(10).toFloat()
        }
        val items = listOf(
            Triple(Tab.HOME, "⌂", "Início"),
            Triple(Tab.NEWS, "▤", "Notícias"),
            Triple(Tab.VIDEOS, "▶", "Vídeos"),
            Triple(Tab.NONE, "•••", "Mais"),
        )
        items.forEach { (tab, icon, label) ->
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(activity.dp(8), activity.dp(6), activity.dp(8), activity.dp(5))
                if (tab == selected) background = MobileUi.rounded(MobileUi.BLUE_TINT, activity.dp(28).toFloat())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    when (tab) {
                        Tab.HOME -> open(activity, MainActivity::class.java)
                        Tab.NEWS -> open(activity, NewsActivity::class.java)
                        Tab.VIDEOS -> open(activity, VideosActivity::class.java)
                        Tab.NONE -> showMore(activity)
                    }
                }
            }
            item.addView(MobileUi.text(activity, icon, 20f, if (tab == selected) MobileUi.BLUE else MobileUi.NAVY, true).apply {
                gravity = Gravity.CENTER
            })
            item.addView(MobileUi.text(activity, label, 12f, if (tab == selected) MobileUi.BLUE else MobileUi.MUTED, tab == selected).apply {
                gravity = Gravity.CENTER
            })
            bar.addView(item, LinearLayout.LayoutParams(0, activity.dp(64), 1f).apply {
                marginStart = activity.dp(2)
                marginEnd = activity.dp(2)
            })
        }
        return bar
    }

    private fun open(activity: BaseActivity, cls: Class<*>) {
        if (activity::class.java == cls) return
        activity.startActivity(Intent(activity, cls))
    }

    private fun showMore(activity: BaseActivity) {
        val session = activity.auth.session ?: return
        data class Item(val label: String, val permission: String?, val action: () -> Unit)
        val all = session.user.profile.equals("ADMIN", true) || session.user.profile.equals("OPERADOR", true)
        fun allowed(permission: String?) = permission == null || all || permission in session.user.permissions
        fun launch(cls: Class<*>) = { activity.startActivity(Intent(activity, cls)) }

        val items = listOf(
            Item("Demandas", "demands", launch(DemandsActivity::class.java)),
            Item("Fontes", "sources", launch(SourcesActivity::class.java)),
            Item("Histórico", "history", launch(HistoryActivity::class.java)),
            Item("Termos", "terms", launch(TermsActivity::class.java)),
            Item("Parar buscas", null) {
                MonitoringScheduler.cancel(activity)
                WorkManager.getInstance(activity).cancelAllWorkByTag("central-monitoring")
                activity.stopService(Intent(activity, br.com.centralmidia.android.recording.ScreenRecordService::class.java))
                activity.toast("Buscas, automações e gravações solicitadas para parar.")
            },
            Item("Extrator de Notícias", "news_extractor", launch(NewsExtractorActivity::class.java)),
            Item("Capas", "covers", launch(CoversActivity::class.java)),
            Item("Editor PDF", "pdf_editor", launch(PdfEditorActivity::class.java)),
            Item("Extrator de Vídeos", "extractor", launch(VideoExtractorActivity::class.java)),
            Item("Editor de Vídeo", "video_editor", launch(VideoEditorActivity::class.java)),
            Item("Gravador de Tela", "video_editor", launch(ScreenRecorderActivity::class.java)),
            Item("Configurações", "settings", launch(SettingsActivity::class.java)),
            Item("Minha conta", null, launch(AccountActivity::class.java)),
        ).filter { allowed(it.permission) }

        MaterialAlertDialogBuilder(activity)
            .setTitle("Mais opções")
            .setItems(items.map { it.label }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                items[which].action()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }
}

object MobileUi {
    val BG = Color.rgb(246, 249, 253)
    val NAVY = Color.rgb(10, 43, 99)
    val MUTED = Color.rgb(91, 116, 158)
    val BLUE = Color.rgb(20, 126, 246)
    val BLUE_TINT = Color.rgb(229, 241, 255)
    val GREEN = Color.rgb(0, 151, 95)
    val GREEN_TINT = Color.rgb(232, 249, 240)
    val PURPLE = Color.rgb(137, 68, 238)
    val PURPLE_TINT = Color.rgb(245, 235, 255)
    val ORANGE = Color.rgb(244, 158, 0)
    val ORANGE_TINT = Color.rgb(255, 244, 213)
    val PINK = Color.rgb(226, 16, 101)
    val PINK_TINT = Color.rgb(255, 232, 241)
    val BORDER = Color.rgb(216, 228, 243)

    fun text(context: Context, value: String, size: Float, color: Int = NAVY, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun rounded(color: Int, radius: Float, strokeColor: Int? = null, strokeWidth: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
            if (strokeColor != null) setStroke(strokeWidth, strokeColor)
        }

    fun oval(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    fun card(context: Context, padding: Int = 14): MaterialCardView = MaterialCardView(context).apply {
        radius = context.dp(18).toFloat()
        cardElevation = context.dp(1).toFloat()
        setCardBackgroundColor(Color.WHITE)
        strokeWidth = context.dp(1)
        strokeColor = BORDER
        setContentPadding(context.dp(padding), context.dp(padding), context.dp(padding), context.dp(padding))
    }

    fun input(context: Context, hint: String): EditText = EditText(context).apply {
        this.hint = hint
        textSize = 15f
        setTextColor(NAVY)
        setHintTextColor(MUTED)
        setSingleLine(true)
        setPadding(context.dp(14), context.dp(11), context.dp(14), context.dp(11))
        background = rounded(Color.WHITE, context.dp(13).toFloat(), Color.rgb(143, 190, 246), context.dp(1))
    }

    fun button(
        context: Context,
        label: String,
        primary: Boolean = false,
        accent: Int = BLUE,
        onClick: () -> Unit,
    ): MaterialButton = MaterialButton(context).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        cornerRadius = context.dp(12)
        insetTop = 0
        insetBottom = 0
        minimumHeight = 0
        setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
        if (primary) {
            backgroundTintList = ColorStateList.valueOf(accent)
            setTextColor(Color.WHITE)
            strokeWidth = 0
        } else {
            backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            setTextColor(accent)
            strokeColor = ColorStateList.valueOf(BORDER)
            strokeWidth = context.dp(1)
        }
        setOnClickListener { onClick() }
    }

    fun statusChip(context: Context, label: String, accent: Int): TextView = text(context, label, 11f, accent, true).apply {
        setPadding(context.dp(10), context.dp(7), context.dp(10), context.dp(7))
        background = rounded(
            if (accent == GREEN) GREEN_TINT else BLUE_TINT,
            context.dp(14).toFloat(),
            if (accent == GREEN) Color.rgb(182, 232, 207) else Color.rgb(190, 218, 249),
            context.dp(1),
        )
    }

    fun match(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = top
        }

    fun wrap(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = top
        }
}
