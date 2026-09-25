package br.com.centralmidia.android.ui

import android.app.Activity
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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import br.com.centralmidia.android.R
import br.com.centralmidia.android.automation.MonitoringScheduler
import br.com.centralmidia.android.core.AuthManager
import br.com.centralmidia.android.core.dp
import br.com.centralmidia.android.recording.ScreenRecordService
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

object MobileScaffold {
    enum class Tab { HOME, NEWS, VIDEOS, MORE }

    fun page(
        activity: BaseActivity,
        title: String,
        subtitle: String,
        selected: Tab,
        showAutomation: Boolean = true,
    ): LinearLayout {
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowCompat.getInsetsController(
            activity.window,
            activity.window.decorView,
        ).apply {
            isAppearanceLightStatusBars = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                isAppearanceLightNavigationBars = true
            }
        }

        val shell = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MobileUi.BG)
        }

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                activity.dp(16),
                activity.dp(if (selected == Tab.HOME) 8 else 4),
                activity.dp(16),
                activity.dp(18),
            )
        }

        content.addView(header(activity), MobileUi.match())
        content.addView(
            titleBlock(
                activity = activity,
                title = title,
                subtitle = subtitle,
                showHomeDetails = selected == Tab.HOME,
                showAutomation = showAutomation,
            ),
            MobileUi.match(activity.dp(if (selected == Tab.HOME) 8 else 2)),
        )

        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        shell.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        val bottom = bottomNav(
            activity,
            activity::class.java,
        )
        shell.addView(bottom, MobileUi.match())

        ViewCompat.setOnApplyWindowInsetsListener(shell) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            shell.setPadding(0, bars.top, 0, 0)
            bottom.setPadding(
                activity.dp(8),
                activity.dp(5),
                activity.dp(8),
                activity.dp(6) + bars.bottom,
            )
            insets
        }

        activity.setContentView(shell)
        ViewCompat.requestApplyInsets(shell)
        return content
    }

    /**
     * Integra módulos importados (Capas/PDF/Extrator/Editor) ao mesmo shell
     * visual da Central, preservando a barra de abas inferior.
     */
    @JvmStatic
    fun attachModule(
        activity: Activity,
        currentCentralClass: Class<*>,
    ) {
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(
            activity.window,
            false,
        )
        WindowCompat.getInsetsController(
            activity.window,
            activity.window.decorView,
        ).apply {
            isAppearanceLightStatusBars = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                isAppearanceLightNavigationBars = true
            }
        }

        val frame =
            activity.findViewById<ViewGroup>(
                android.R.id.content,
            )
                ?: return

        if (
            frame.childCount == 1 &&
            frame.getChildAt(0)
                .tag == MODULE_SHELL_TAG
        ) {
            return
        }

        val existing =
            frame.getChildAt(0)
                ?: return

        frame.removeView(existing)

        val shell = LinearLayout(activity).apply {
            tag = MODULE_SHELL_TAG
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MobileUi.BG)
        }

        shell.addView(
            existing,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        val bottom =
            bottomNav(
                activity,
                currentCentralClass,
            )

        shell.addView(
            bottom,
            MobileUi.match(),
        )

        ViewCompat.setOnApplyWindowInsetsListener(shell) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars(),
                )
            shell.setPadding(
                0,
                bars.top,
                0,
                0,
            )
            bottom.setPadding(
                activity.dp(8),
                activity.dp(5),
                activity.dp(8),
                activity.dp(6) + bars.bottom,
            )
            insets
        }

        frame.addView(
            shell,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        ViewCompat.requestApplyInsets(shell)
    }

    private fun header(
        activity: BaseActivity,
    ): View {
        val session =
            activity.auth.session

        val row =
            LinearLayout(activity).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
                setPadding(
                    0,
                    activity.dp(2),
                    0,
                    activity.dp(5),
                )
            }

        val logo =
            LinearLayout(activity).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.BOTTOM

                listOf(
                    16,
                    24,
                    32,
                ).forEachIndexed {
                        index,
                        height,
                    ->
                    addView(
                        View(activity).apply {
                            background =
                                MobileUi.rounded(
                                    when (index) {
                                        0 ->
                                            Color.rgb(
                                                83,
                                                164,
                                                255,
                                            )

                                        1 ->
                                            Color.rgb(
                                                48,
                                                139,
                                                246,
                                            )

                                        else ->
                                            Color.rgb(
                                                25,
                                                113,
                                                230,
                                            )
                                    },
                                    activity.dp(5)
                                        .toFloat(),
                                )
                        },
                        LinearLayout.LayoutParams(
                            activity.dp(6),
                            activity.dp(height),
                        ).apply {
                            marginEnd =
                                activity.dp(3)
                        },
                    )
                }
            }

        row.addView(
            logo,
            LinearLayout.LayoutParams(
                activity.dp(35),
                activity.dp(36),
            ),
        )

        row.addView(
            MobileUi.text(
                activity,
                "Central Inteligente\nde Mídia",
                17f,
                MobileUi.NAVY,
                true,
            ),
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart =
                    activity.dp(8)
            },
        )

        val initial =
            session?.user?.name
                ?.trim()
                ?.firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                ?: session?.user?.username
                    ?.trim()
                    ?.firstOrNull()
                    ?.uppercaseChar()
                    ?.toString()
                ?: "U"

        val avatar =
            MobileUi.text(
                activity,
                initial,
                16f,
                Color.WHITE,
                true,
            ).apply {
                gravity = Gravity.CENTER
                background =
                    MobileUi.oval(
                        Color.rgb(
                            86,
                            157,
                            245,
                        ),
                    )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    open(
                        activity,
                        AccountActivity::class.java,
                        true,
                    )
                }
            }

        row.addView(
            avatar,
            LinearLayout.LayoutParams(
                activity.dp(42),
                activity.dp(42),
            ),
        )

        return row
    }

    private fun titleBlock(
        activity: BaseActivity,
        title: String,
        subtitle: String,
        showHomeDetails: Boolean,
        showAutomation: Boolean,
    ): View {
        val outer =
            LinearLayout(activity).apply {
                orientation =
                    LinearLayout.VERTICAL
            }

        outer.addView(
            MobileUi.text(
                activity,
                "CENTRAL DE INTELIGÊNCIA DE MÍDIA",
                9.5f,
                MobileUi.BLUE,
                true,
            ),
        )

        val row =
            LinearLayout(activity).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val copy =
            LinearLayout(activity).apply {
                orientation =
                    LinearLayout.VERTICAL
            }

        copy.addView(
            MobileUi.text(
                activity,
                title,
                if (showHomeDetails) 28f else 23f,
                MobileUi.NAVY,
                true,
            ),
        )

        if (
            showHomeDetails &&
            subtitle.isNotBlank()
        ) {
            copy.addView(
                MobileUi.text(
                    activity,
                    subtitle,
                    13f,
                    MobileUi.MUTED,
                ),
                MobileUi.match(
                    activity.dp(2),
                ),
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

        if (showHomeDetails) {
            val chips =
                LinearLayout(activity).apply {
                    orientation =
                        LinearLayout.VERTICAL
                    gravity = Gravity.END
                }

            chips.addView(
                MobileUi.statusChip(
                    activity,
                    "●  Conexão direta",
                    MobileUi.GREEN,
                ),
            )

            if (showAutomation) {
                val automation =
                    MobileUi.statusChip(
                        activity,
                        "●  Automação pronta",
                        MobileUi.GREEN,
                    )

                chips.addView(
                    automation,
                    MobileUi.wrap(
                        activity.dp(6),
                    ),
                )

                WorkManager
                    .getInstance(activity)
                    .getWorkInfosByTagLiveData(
                        "central-monitoring",
                    )
                    .observe(activity) {
                            infos,
                        ->
                        val active =
                            infos.any {
                                it.state ==
                                    WorkInfo.State.ENQUEUED ||
                                    it.state ==
                                    WorkInfo.State.RUNNING
                            }

                        automation.text =
                            if (active) {
                                "●  Automação ativa"
                            } else {
                                "●  Automação pronta"
                            }
                    }
            }

            row.addView(chips)
        }

        outer.addView(
            row,
            MobileUi.match(
                activity.dp(2),
            ),
        )

        return outer
    }

    private data class NavItem(
        val label: String,
        val icon: Int,
        val permission: String? = null,
        val cls: Class<*>? = null,
        val accent: Int = MobileUi.BLUE,
        val action: (() -> Unit)? = null,
    )

    private fun bottomNav(
        activity: Activity,
        currentCentralClass: Class<*>,
    ): LinearLayout {
        val session =
            AuthManager
                .get(activity)
                .session

        val allAccess =
            session?.user?.profile
                .equals(
                    "ADMIN",
                    true,
                ) ||
                session?.user?.profile
                    .equals(
                        "OPERADOR",
                        true,
                    )

        fun allowed(
            permission: String?,
        ): Boolean =
            permission == null ||
                allAccess ||
                permission in
                session?.user
                    ?.permissions
                    .orEmpty()

        val allItems =
            listOf(
                NavItem(
                    "Início",
                    R.drawable.ic_home,
                    cls =
                        MainActivity::class.java,
                ),
                NavItem(
                    "Notícias",
                    R.drawable.ic_news,
                    cls =
                        NewsActivity::class.java,
                ),
                NavItem(
                    "Vídeos",
                    R.drawable.ic_video,
                    cls =
                        VideosActivity::class.java,
                ),
                NavItem(
                    "Demandas",
                    R.drawable.ic_demands,
                    "demands",
                    DemandsActivity::class.java,
                    MobileUi.ORANGE,
                ),
                NavItem(
                    "Fontes",
                    R.drawable.ic_sources,
                    "sources",
                    SourcesActivity::class.java,
                    MobileUi.GREEN,
                ),
                NavItem(
                    "Histórico",
                    R.drawable.ic_history,
                    "history",
                    HistoryActivity::class.java,
                ),
                NavItem(
                    "Termos",
                    R.drawable.ic_terms,
                    "terms",
                    TermsActivity::class.java,
                    MobileUi.PURPLE,
                ),
                NavItem(
                    "Extrator\nNotícias",
                    R.drawable.ic_news,
                    "news_extractor",
                    NewsExtractorActivity::class.java,
                ),
                NavItem(
                    "Capas",
                    R.drawable.ic_news,
                    "covers",
                    CoversActivity::class.java,
                ),
                NavItem(
                    "Editor\nPDF",
                    R.drawable.ic_terms,
                    "pdf_editor",
                    PdfEditorActivity::class.java,
                    MobileUi.PURPLE,
                ),
                NavItem(
                    "Extrator\nVídeos",
                    R.drawable.ic_video,
                    "extractor",
                    VideoExtractorActivity::class.java,
                    MobileUi.PURPLE,
                ),
                NavItem(
                    "Editor\nVídeo",
                    R.drawable.ic_video,
                    "video_editor",
                    VideoEditorActivity::class.java,
                ),
                NavItem(
                    "Gravador",
                    R.drawable.ic_video,
                    "video_editor",
                    ScreenRecorderActivity::class.java,
                    MobileUi.PINK,
                ),
                NavItem(
                    "Config.",
                    R.drawable.ic_more,
                    "settings",
                    SettingsActivity::class.java,
                ),
                NavItem(
                    "Minha\nConta",
                    R.drawable.ic_home,
                    cls =
                        AccountActivity::class.java,
                    accent =
                        MobileUi.GREEN,
                ),
                NavItem(
                    "Parar",
                    R.drawable.ic_delete,
                    accent =
                        MobileUi.PINK,
                    action = {
                        MonitoringScheduler
                            .cancel(activity)

                        WorkManager
                            .getInstance(activity)
                            .cancelAllWorkByTag(
                                "central-monitoring",
                            )

                        activity.stopService(
                            Intent(
                                activity,
                                ScreenRecordService::class.java,
                            ),
                        )

                        Toast.makeText(
                            activity,
                            "Buscas, automações e gravações solicitadas para parar.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ),
            )

        val items =
            allItems.filter {
                allowed(
                    it.permission,
                )
            }

        val currentIndex =
            items.indexOfFirst {
                it.cls ==
                    currentCentralClass
            }

        val wrapper =
            LinearLayout(activity).apply {
                orientation =
                    LinearLayout.VERTICAL
                setBackgroundColor(
                    Color.WHITE,
                )
                elevation =
                    activity.dp(14)
                        .toFloat()
            }

        wrapper.addView(
            View(activity).apply {
                setBackgroundColor(
                    Color.rgb(
                        229,
                        236,
                        246,
                    ),
                )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                activity.dp(1),
            ),
        )

        val scroll =
            HorizontalScrollView(
                activity,
            ).apply {
                isHorizontalScrollBarEnabled =
                    false
                isFillViewport = false
                overScrollMode =
                    View.OVER_SCROLL_NEVER
                isSmoothScrollingEnabled =
                    true
            }

        val row =
            LinearLayout(activity).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
                setPadding(
                    activity.dp(2),
                    activity.dp(4),
                    activity.dp(2),
                    activity.dp(4),
                )
            }

        var selectedView: View? =
            null

        items.forEachIndexed {
                index,
                item,
            ->
            val selected =
                item.cls ==
                    currentCentralClass

            val itemView =
                LinearLayout(activity).apply {
                    orientation =
                        LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    contentDescription =
                        item.label.replace(
                            "\n",
                            " ",
                        )

                    setPadding(
                        activity.dp(7),
                        activity.dp(6),
                        activity.dp(7),
                        activity.dp(5),
                    )

                    background =
                        if (selected) {
                            MobileUi.rounded(
                                when (
                                    item.accent
                                ) {
                                    MobileUi.GREEN ->
                                        MobileUi.GREEN_TINT

                                    MobileUi.PURPLE ->
                                        MobileUi.PURPLE_TINT

                                    MobileUi.ORANGE ->
                                        MobileUi.ORANGE_TINT

                                    MobileUi.PINK ->
                                        MobileUi.PINK_TINT

                                    else ->
                                        MobileUi.BLUE_TINT
                                },
                                activity.dp(22)
                                    .toFloat(),
                            )
                        } else {
                            MobileUi.rounded(
                                Color.TRANSPARENT,
                                activity.dp(22)
                                    .toFloat(),
                            )
                        }

                    setOnClickListener {
                        item.action
                            ?.invoke()
                            ?: item.cls
                                ?.let {
                                        cls,
                                    ->
                                    if (
                                        cls !=
                                        currentCentralClass
                                    ) {
                                        open(
                                            activity,
                                            cls,
                                            currentIndex <
                                                0 ||
                                                index >=
                                                currentIndex,
                                        )
                                    }
                                }
                    }
                }

            itemView.addView(
                MobileUi.icon(
                    activity,
                    item.icon,
                    if (selected) {
                        item.accent
                    } else {
                        MobileUi.NAVY
                    },
                    21,
                ),
            )

            itemView.addView(
                MobileUi.text(
                    activity,
                    item.label,
                    10f,
                    if (selected) {
                        item.accent
                    } else {
                        MobileUi.MUTED
                    },
                    selected,
                ).apply {
                    gravity =
                        Gravity.CENTER
                    maxLines = 2
                    textAlignment =
                        View.TEXT_ALIGNMENT_CENTER
                },
                MobileUi.match(
                    activity.dp(3),
                ),
            )

            row.addView(
                itemView,
                LinearLayout.LayoutParams(
                    activity.dp(88),
                    activity.dp(60),
                ).apply {
                    marginStart =
                        activity.dp(2)
                    marginEnd =
                        activity.dp(2)
                },
            )

            if (selected) {
                selectedView =
                    itemView
            }
        }

        scroll.addView(
            row,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        wrapper.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                activity.dp(68),
            ),
        )

        selectedView?.let {
                active,
            ->
            scroll.post {
                val desired =
                    (
                        active.left -
                            activity.resources
                                .displayMetrics
                                .widthPixels /
                            2 +
                            active.width /
                            2
                        )
                        .coerceAtLeast(
                            0,
                        )

                scroll.smoothScrollTo(
                    desired,
                    0,
                )
            }
        }

        return wrapper
    }

    private fun open(
        activity: Activity,
        cls: Class<*>,
        forward: Boolean,
    ) {
        if (
            activity::class.java ==
            cls
        ) {
            return
        }

        activity.startActivity(
            Intent(
                activity,
                cls,
            ).addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            ),
        )

        if (forward) {
            activity.overridePendingTransition(
                R.anim.central_slide_in_right,
                R.anim.central_slide_out_left,
            )
        } else {
            activity.overridePendingTransition(
                R.anim.central_slide_in_left,
                R.anim.central_slide_out_right,
            )
        }

        if (activity !is BaseActivity) {
            activity.finish()
        }
    }

    private const val MODULE_SHELL_TAG =
        "central-module-shell"
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

    fun text(
        context: Context,
        value: String,
        size: Float,
        color: Int = NAVY,
        bold: Boolean = false,
    ): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            includeFontPadding = false
            setTextColor(color)
            if (bold) {
                setTypeface(typeface, Typeface.BOLD)
            }
        }

    fun icon(
        context: Context,
        res: Int,
        tint: Int,
        sizeDp: Int = 22,
    ): ImageView =
        ImageView(context).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(tint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(
                context.dp(sizeDp),
                context.dp(sizeDp),
            )
        }

    fun rounded(
        color: Int,
        radius: Float,
        strokeColor: Int? = null,
        strokeWidth: Int = 1,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
            if (strokeColor != null) {
                setStroke(strokeWidth, strokeColor)
            }
        }

    fun oval(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    fun card(
        context: Context,
        padding: Int = 14,
    ): MaterialCardView =
        MaterialCardView(context).apply {
            radius = context.dp(18).toFloat()
            cardElevation = context.dp(1).toFloat()
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = context.dp(1)
            strokeColor = BORDER
            setContentPadding(
                context.dp(padding),
                context.dp(padding),
                context.dp(padding),
                context.dp(padding),
            )
        }

    fun input(
        context: Context,
        hint: String,
    ): android.widget.EditText =
        android.widget.EditText(context).apply {
            this.hint = hint
            textSize = 14.5f
            setTextColor(NAVY)
            setHintTextColor(MUTED)
            setSingleLine(true)
            setPadding(
                context.dp(14),
                context.dp(11),
                context.dp(14),
                context.dp(11),
            )
            background = rounded(
                Color.WHITE,
                context.dp(13).toFloat(),
                Color.rgb(143, 190, 246),
                context.dp(1),
            )
        }

    fun button(
        context: Context,
        label: String,
        primary: Boolean = false,
        accent: Int = BLUE,
        iconRes: Int? = null,
        onClick: () -> Unit,
    ): MaterialButton =
        MaterialButton(context).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            includeFontPadding = false
            cornerRadius = context.dp(13)
            letterSpacing = 0.01f
            insetTop = 0
            insetBottom = 0
            minimumHeight = 0
            setPadding(
                context.dp(12),
                context.dp(8),
                context.dp(12),
                context.dp(8),
            )

            if (primary) {
                setTypeface(typeface, Typeface.BOLD)
                backgroundTintList = ColorStateList.valueOf(accent)
                setTextColor(Color.WHITE)
                strokeWidth = 0
                iconTint = ColorStateList.valueOf(Color.WHITE)
            } else {
                backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                setTextColor(accent)
                strokeColor = ColorStateList.valueOf(BORDER)
                strokeWidth = context.dp(1)
                iconTint = ColorStateList.valueOf(accent)
            }

            if (iconRes != null) {
                setIconResource(iconRes)
                iconSize = context.dp(18)
                iconPadding = context.dp(7)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            }

            setOnClickListener {
                onClick()
            }
        }

    fun statusChip(
        context: Context,
        label: String,
        accent: Int,
    ): TextView =
        text(
            context,
            label,
            10.5f,
            accent,
            true,
        ).apply {
            setPadding(
                context.dp(10),
                context.dp(7),
                context.dp(10),
                context.dp(7),
            )
            background = rounded(
                if (accent == GREEN) GREEN_TINT else BLUE_TINT,
                context.dp(14).toFloat(),
                if (accent == GREEN) {
                    Color.rgb(182, 232, 207)
                } else {
                    Color.rgb(190, 218, 249)
                },
                context.dp(1),
            )
        }

    fun match(
        top: Int = 0,
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = top
        }

    fun wrap(
        top: Int = 0,
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = top
        }
}
