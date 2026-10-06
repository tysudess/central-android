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
import android.view.MotionEvent
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

/**
 * Design system visual compartilhado da Central.
 *
 * Objetivo:
 * - uma única linguagem visual nas telas Android;
 * - cabeçalho compacto;
 * - navegação inferior horizontal sem "Mais";
 * - área de conteúdo respeitando status/navigation bars;
 * - feedback tátil discreto;
 * - transições suaves entre telas.
 */
object MobileScaffold {
    enum class Tab { HOME, NEWS, VIDEOS, MORE }

    fun page(
        activity: BaseActivity,
        title: String,
        subtitle: String,
        selected: Tab,
        showAutomation: Boolean = true,
    ): LinearLayout {
        configureBars(activity)

        val shell = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = MobileUi.appBackground()
        }

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            isVerticalFadingEdgeEnabled = false
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                activity.dp(14),
                activity.dp(7),
                activity.dp(14),
                activity.dp(14),
            )
        }

        content.addView(
            header(activity),
            MobileUi.match(),
        )

        content.addView(
            titleBlock(
                activity,
                title,
                subtitle,
                selected == Tab.HOME,
                showAutomation,
            ),
            MobileUi.match(
                if (selected == Tab.HOME) activity.dp(9) else activity.dp(6),
            ),
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

        shell.addView(
            bottomNav(
                activity,
                activity::class.java,
            ),
            MobileUi.match(),
        )

        applyInsets(
            shell,
        )

        activity.setContentView(shell)
        ViewCompat.requestApplyInsets(shell)
        return content
    }

    @JvmStatic
    fun attachModule(
        activity: Activity,
        currentCentralClass: Class<*>,
    ) {
        configureBars(activity)

        val frame =
            activity.findViewById<ViewGroup>(
                android.R.id.content,
            )
                ?: return

        if (
            frame.childCount == 1 &&
            frame.getChildAt(0).tag ==
            MODULE_SHELL_TAG
        ) {
            return
        }

        val existing =
            frame.getChildAt(0)
                ?: return

        frame.removeView(existing)

        val shell =
            LinearLayout(activity).apply {
                tag =
                    MODULE_SHELL_TAG

                orientation =
                    LinearLayout.VERTICAL

                background =
                    MobileUi.appBackground()

                clipChildren =
                    true
            }

        shell.addView(
            existing,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        shell.addView(
            bottomNav(
                activity,
                currentCentralClass,
            ),
            MobileUi.match(),
        )

        applyInsets(
            shell,
        )

        frame.addView(
            shell,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        ViewCompat.requestApplyInsets(shell)
    }

    private fun configureBars(
        activity: Activity,
    ) {
        activity.window.statusBarColor =
            Color.TRANSPARENT

        activity.window.navigationBarColor =
            Color.TRANSPARENT

        WindowCompat.setDecorFitsSystemWindows(
            activity.window,
            false,
        )

        WindowCompat.getInsetsController(
            activity.window,
            activity.window.decorView,
        ).apply {
            isAppearanceLightStatusBars =
                true

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                isAppearanceLightNavigationBars =
                    true
            }
        }
    }

    private fun applyInsets(
        shell: View,
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(
            shell,
        ) { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars(),
                )

            view.setPadding(
                0,
                bars.top,
                0,
                0,
            )

            if (
                view is ViewGroup &&
                view.childCount > 0
            ) {
                val bottom =
                    view.getChildAt(
                        view.childCount - 1,
                    )

                bottom.setPadding(
                    view.context.dp(7),
                    view.context.dp(4),
                    view.context.dp(7),
                    view.context.dp(5) +
                        bars.bottom,
                )
            }

            insets
        }
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
                    activity.dp(9),
                    activity.dp(6),
                    activity.dp(9),
                    activity.dp(6),
                )

                background =
                    MobileUi.headerBackground(
                        activity,
                    )

                elevation =
                    activity.dp(1).toFloat()

                contentDescription =
                    "Cabeçalho da Central Inteligente de Mídia"
            }

        val logo =
            ImageView(activity).apply {
                setImageResource(
                    R.mipmap.ic_launcher,
                )

                scaleType =
                    ImageView.ScaleType.CENTER_INSIDE

                alpha =
                    0.98f

                contentDescription =
                    "Logo da Central Inteligente de Mídia"
            }

        row.addView(
            logo,
            LinearLayout.LayoutParams(
                activity.dp(38),
                activity.dp(38),
            ),
        )

        val brand =
            MobileUi.text(
                activity,
                "Central Inteligente\nde Mídia",
                16.5f,
                MobileUi.NAVY,
                true,
            ).apply {
                maxLines =
                    2

                setTypeface(
                    typeface,
                    Typeface.BOLD,
                )
            }

        row.addView(
            brand,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart =
                    activity.dp(9)
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
                14.5f,
                Color.WHITE,
                true,
            ).apply {
                gravity =
                    Gravity.CENTER

                background =
                    MobileUi.avatarBackground()

                isClickable =
                    true

                isFocusable =
                    true

                elevation =
                    activity.dp(1).toFloat()

                contentDescription =
                    "Minha conta"

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
                activity.dp(39),
                activity.dp(39),
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

        if (
            showHomeDetails
        ) {
            outer.addView(
                MobileUi.text(
                    activity,
                    "CENTRAL DE INTELIGÊNCIA DE MÍDIA",
                    9.0f,
                    MobileUi.BLUE,
                    true,
                ).apply {
                    letterSpacing =
                        0.025f
                },
                MobileUi.match(),
            )
        }

        outer.addView(
            MobileUi.text(
                activity,
                title,
                if (showHomeDetails) 27f else 23f,
                MobileUi.NAVY,
                true,
            ),
            MobileUi.match(
                if (showHomeDetails) activity.dp(2) else 0,
            ),
        )

        if (
            showHomeDetails &&
            subtitle.isNotBlank()
        ) {
            outer.addView(
                MobileUi.text(
                    activity,
                    subtitle,
                    12.3f,
                    MobileUi.MUTED,
                ),
                MobileUi.match(
                    activity.dp(2),
                ),
            )
        }

        if (
            showHomeDetails
        ) {
            val chipsScroll =
                HorizontalScrollView(
                    activity,
                ).apply {
                    isHorizontalScrollBarEnabled =
                        false

                    overScrollMode =
                        View.OVER_SCROLL_NEVER

                    clipToPadding =
                        false
                }

            val chips =
                LinearLayout(activity).apply {
                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        Gravity.CENTER_VERTICAL
                }

            chips.addView(
                MobileUi.statusChip(
                    activity,
                    "●  Conexão direta",
                    MobileUi.GREEN,
                ),
            )

            if (
                showAutomation
            ) {
                val automation =
                    MobileUi.statusChip(
                        activity,
                        "●  Automação pronta",
                        MobileUi.GREEN,
                    )

                chips.addView(
                    automation,
                    MobileUi.wrap(
                        activity.dp(5),
                    ),
                )

                WorkManager
                    .getInstance(activity)
                    .getWorkInfosByTagLiveData(
                        "central-monitoring",
                    )
                    .observe(
                        activity,
                    ) { infos ->
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

            chipsScroll.addView(
                chips,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )

            outer.addView(
                chipsScroll,
                MobileUi.match(
                    activity.dp(5),
                ),
            )
        }

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
            AuthManager.get(
                activity,
            ).session

        val allAccess =
            session?.user?.profile.equals(
                "ADMIN",
                true,
            ) ||
                session?.user?.profile.equals(
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
                    R.drawable.ic_news_extract,
                    "news_extractor",
                    NewsExtractorActivity::class.java,
                ),
                NavItem(
                    "Capas",
                    R.drawable.ic_covers,
                    "covers",
                    CoversActivity::class.java,
                ),
                NavItem(
                    "Editor\nPDF",
                    R.drawable.ic_pdf,
                    "pdf_editor",
                    PdfEditorActivity::class.java,
                    MobileUi.PURPLE,
                ),
                NavItem(
                    "Extrator\nVídeos",
                    R.drawable.ic_video_download,
                    "extractor",
                    VideoExtractorActivity::class.java,
                    MobileUi.PURPLE,
                ),
                NavItem(
                    "Editor\nVídeo",
                    R.drawable.ic_video_edit,
                    "video_editor",
                    VideoEditorActivity::class.java,
                ),
                NavItem(
                    "Gravador",
                    R.drawable.ic_record,
                    "video_editor",
                    ScreenRecorderActivity::class.java,
                    MobileUi.PINK,
                ),
                NavItem(
                    "Config.",
                    R.drawable.ic_settings_modern,
                    "settings",
                    SettingsActivity::class.java,
                ),
                NavItem(
                    "Minha\nConta",
                    R.drawable.ic_account,
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
                        MonitoringScheduler.cancel(
                            activity,
                        )

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

                background =
                    MobileUi.navigationBackground()

                elevation =
                    activity.dp(9).toFloat()
            }

        wrapper.addView(
            View(activity).apply {
                background =
                    MobileUi.navigationIndicator()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                activity.dp(2),
            ),
        )

        val scroll =
            HorizontalScrollView(
                activity,
            ).apply {
                isHorizontalScrollBarEnabled =
                    false

                isFillViewport =
                    false

                overScrollMode =
                    View.OVER_SCROLL_NEVER

                isSmoothScrollingEnabled =
                    true

                clipToPadding =
                    false

                contentDescription =
                    "Navegação das áreas da Central"
            }

        val row =
            LinearLayout(activity).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    activity.dp(3),
                    activity.dp(4),
                    activity.dp(3),
                    activity.dp(4),
                )
            }

        var selectedView:
            View? =
            null

        items.forEachIndexed {
                index,
                item,
            ->
            val selected =
                item.cls ==
                    currentCentralClass

            val itemView =
                LinearLayout(
                    activity,
                ).apply {
                    orientation =
                        LinearLayout.VERTICAL

                    gravity =
                        Gravity.CENTER

                    isClickable =
                        true

                    isFocusable =
                        true

                    contentDescription =
                        item.label.replace(
                            "\n",
                            " ",
                        )

                    val selectedTint =
                        MobileUi.selectionTint(
                            item.accent,
                        )

                    setPadding(
                        activity.dp(6),
                        activity.dp(5),
                        activity.dp(6),
                        activity.dp(4),
                    )

                    background =
                        if (selected) {
                            MobileUi.rounded(
                                selectedTint,
                                activity.dp(16).toFloat(),
                                MobileUi.mixWithWhite(
                                    item.accent,
                                    0.54f,
                                ),
                                activity.dp(1),
                            )
                        } else {
                            MobileUi.rounded(
                                Color.TRANSPARENT,
                                activity.dp(16).toFloat(),
                            )
                        }

                    elevation =
                        if (selected) {
                            activity.dp(1).toFloat()
                        } else {
                            0f
                        }

                    setOnTouchListener {
                            v,
                            event,
                        ->
                        when (
                            event.actionMasked
                        ) {
                            MotionEvent.ACTION_DOWN -> {
                                v.animate()
                                    .scaleX(0.96f)
                                    .scaleY(0.96f)
                                    .setDuration(75)
                                    .start()
                            }

                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL,
                            -> {
                                v.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(125)
                                    .start()
                            }
                        }

                        false
                    }

                    setOnClickListener {
                        item.action
                            ?.invoke()
                            ?: item.cls?.let {
                                cls ->
                                if (
                                    cls !=
                                    currentCentralClass
                                ) {
                                    open(
                                        activity,
                                        cls,
                                        currentIndex < 0 ||
                                            index >= currentIndex,
                                    )
                                }
                            }
                    }
                }

            val iconView =
                MobileUi.icon(
                    activity,
                    item.icon,
                    if (selected) {
                        item.accent
                    } else {
                        MobileUi.NAVY
                    },
                    20,
                ).apply {
                    alpha =
                        if (selected) {
                            1f
                        } else {
                            0.76f
                        }

                    contentDescription =
                        item.label.replace(
                            "\n",
                            " ",
                        )
                }

            itemView.addView(
                iconView,
            )

            itemView.addView(
                MobileUi.text(
                    activity,
                    item.label,
                    9.4f,
                    if (selected) {
                        item.accent
                    } else {
                        MobileUi.MUTED
                    },
                    selected,
                ).apply {
                    gravity =
                        Gravity.CENTER

                    maxLines =
                        2

                    textAlignment =
                        View.TEXT_ALIGNMENT_CENTER
                },
                MobileUi.match(
                    activity.dp(2),
                ),
            )

            row.addView(
                itemView,
                LinearLayout.LayoutParams(
                    activity.dp(74),
                    activity.dp(58),
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
                activity.dp(65),
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
    }

    private const val MODULE_SHELL_TAG =
        "central-module-shell"
}

object MobileUi {
    val BG =
        Color.rgb(
            244,
            248,
            253,
        )

    val SURFACE =
        Color.rgb(
            255,
            255,
            255,
        )

    val NAVY =
        Color.rgb(
            21,
            59,
            101,
        )

    val MUTED =
        Color.rgb(
            92,
            117,
            147,
        )

    val BLUE =
        Color.rgb(
            42,
            132,
            229,
        )

    val BLUE_DEEP =
        Color.rgb(
            28,
            105,
            193,
        )

    val BLUE_TINT =
        Color.rgb(
            232,
            243,
            253,
        )

    val GREEN =
        Color.rgb(
            21,
            145,
            101,
        )

    val GREEN_TINT =
        Color.rgb(
            232,
            248,
            240,
        )

    val PURPLE =
        Color.rgb(
            126,
            82,
            207,
        )

    val PURPLE_TINT =
        Color.rgb(
            244,
            238,
            253,
        )

    val ORANGE =
        Color.rgb(
            225,
            145,
            24,
        )

    val ORANGE_TINT =
        Color.rgb(
            255,
            246,
            226,
        )

    val PINK =
        Color.rgb(
            211,
            65,
            120,
        )

    val PINK_TINT =
        Color.rgb(
            252,
            237,
            244,
        )

    val BORDER =
        Color.rgb(
            210,
            226,
            241,
        )

    val BORDER_LIGHT =
        Color.rgb(
            227,
            236,
            246,
        )

    val BORDER_FOCUS =
        Color.rgb(
            145,
            192,
            237,
        )

    fun text(
        context: Context,
        value: String,
        size: Float,
        color: Int = NAVY,
        bold: Boolean = false,
    ): TextView =
        TextView(
            context,
        ).apply {
            text =
                value

            textSize =
                size

            includeFontPadding =
                false

            setTextColor(
                color,
            )

            if (
                size >=
                10f
            ) {
                letterSpacing =
                    0.001f
            }

            if (
                bold
            ) {
                setTypeface(
                    typeface,
                    Typeface.BOLD,
                )
            }
        }

    fun icon(
        context: Context,
        res: Int,
        tint: Int,
        sizeDp: Int = 22,
    ): ImageView =
        ImageView(
            context,
        ).apply {
            setImageResource(
                res,
            )

            imageTintList =
                ColorStateList.valueOf(
                    tint,
                )

            scaleType =
                ImageView.ScaleType.CENTER_INSIDE

            layoutParams =
                LinearLayout.LayoutParams(
                    context.dp(
                        sizeDp,
                    ),
                    context.dp(
                        sizeDp,
                    ),
                )
        }

    fun rounded(
        color: Int,
        radius: Float,
        strokeColor: Int? = null,
        strokeWidth: Int = 1,
    ): GradientDrawable =
        GradientDrawable()
            .apply {
                shape =
                    GradientDrawable.RECTANGLE

                setColor(
                    color,
                )

                cornerRadius =
                    radius

                if (
                    strokeColor !=
                    null
                ) {
                    setStroke(
                        strokeWidth,
                        strokeColor,
                    )
                }
            }

    fun oval(
        color: Int,
    ): GradientDrawable =
        GradientDrawable()
            .apply {
                shape =
                    GradientDrawable.OVAL

                setColor(
                    color,
                )
            }

    fun avatarBackground():
        GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                BLUE,
                BLUE_DEEP,
            ),
        )

    fun appBackground():
        GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(
                    250,
                    252,
                    255,
                ),
                Color.rgb(
                    246,
                    250,
                    254,
                ),
                Color.rgb(
                    238,
                    246,
                    253,
                ),
            ),
        )

    fun navigationBackground():
        GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.WHITE,
                Color.rgb(
                    247,
                    250,
                    254,
                ),
            ),
        ).apply {
            cornerRadius =
                0f
        }

    fun navigationIndicator():
        GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.rgb(
                    117,
                    181,
                    242,
                ),
                BLUE,
                Color.rgb(
                    117,
                    181,
                    242,
                ),
            ),
        ).apply {
            cornerRadius =
                8f
        }

    fun headerBackground(
        context: Context,
    ): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.argb(
                    249,
                    255,
                    255,
                    255,
                ),
                Color.argb(
                    241,
                    247,
                    251,
                    255,
                ),
            ),
        ).apply {
            cornerRadius =
                context.dp(
                    18,
                ).toFloat()

            setStroke(
                context.dp(
                    1,
                ),
                BORDER_LIGHT,
            )
        }

    fun selectionTint(
        accent: Int,
    ): Int =
        when (
            accent
        ) {
            GREEN ->
                GREEN_TINT

            PURPLE ->
                PURPLE_TINT

            ORANGE ->
                ORANGE_TINT

            PINK ->
                PINK_TINT

            else ->
                BLUE_TINT
        }

    fun mixWithWhite(
        color: Int,
        whiteRatio: Float,
    ): Int {
        val ratio =
            whiteRatio.coerceIn(
                0f,
                1f,
            )

        fun channel(
            value: Int,
        ): Int =
            (
                value *
                    (
                        1f -
                            ratio
                        ) +
                    255f *
                    ratio
                )
                .toInt()
                .coerceIn(
                    0,
                    255,
                )

        return Color.rgb(
            channel(
                Color.red(
                    color,
                ),
            ),
            channel(
                Color.green(
                    color,
                ),
            ),
            channel(
                Color.blue(
                    color,
                ),
            ),
        )
    }

    fun card(
        context: Context,
        padding: Int = 13,
    ): MaterialCardView =
        MaterialCardView(
            context,
        ).apply {
            radius =
                context.dp(
                    18,
                ).toFloat()

            cardElevation =
                context.dp(
                    2,
                ).toFloat()

            maxCardElevation =
                context.dp(
                    3,
                ).toFloat()

            setCardBackgroundColor(
                SURFACE,
            )

            strokeWidth =
                context.dp(
                    1,
                )

            strokeColor =
                BORDER

            preventCornerOverlap =
                false

            useCompatPadding =
                false

            setContentPadding(
                context.dp(
                    padding,
                ),
                context.dp(
                    padding,
                ),
                context.dp(
                    padding,
                ),
                context.dp(
                    padding,
                ),
            )
        }

    fun input(
        context: Context,
        hint: String,
    ): android.widget.EditText =
        android.widget.EditText(
            context,
        ).apply {
            this.hint =
                hint

            textSize =
                14f

            setTextColor(
                NAVY,
            )

            setHintTextColor(
                MUTED,
            )

            setSingleLine(
                true,
            )

            minHeight =
                context.dp(
                    50,
                )

            setPadding(
                context.dp(
                    14,
                ),
                context.dp(
                    11,
                ),
                context.dp(
                    14,
                ),
                context.dp(
                    11,
                ),
            )

            background =
                rounded(
                    SURFACE,
                    context.dp(
                        14,
                    ).toFloat(),
                    BORDER_FOCUS,
                    context.dp(
                        1,
                    ),
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
        MaterialButton(
            context,
        ).apply {
            text =
                label

            isAllCaps =
                false

            textSize =
                12.5f

            includeFontPadding =
                false

            cornerRadius =
                context.dp(
                    13,
                )

            letterSpacing =
                0.002f

            insetTop =
                0

            insetBottom =
                0

            minimumHeight =
                context.dp(
                    45,
                )

            setPadding(
                context.dp(
                    12,
                ),
                context.dp(
                    9,
                ),
                context.dp(
                    12,
                ),
                context.dp(
                    9,
                ),
            )

            elevation =
                context.dp(
                    if (primary) 2 else 0,
                ).toFloat()

            rippleColor =
                ColorStateList.valueOf(
                    mixWithWhite(
                        accent,
                        0.72f,
                    ),
                )

            if (
                primary
            ) {
                setTypeface(
                    typeface,
                    Typeface.BOLD,
                )

                backgroundTintList =
                    ColorStateList.valueOf(
                        accent,
                    )

                setTextColor(
                    Color.WHITE,
                )

                strokeWidth =
                    0

                iconTint =
                    ColorStateList.valueOf(
                        Color.WHITE,
                    )
            } else {
                backgroundTintList =
                    ColorStateList.valueOf(
                        SURFACE,
                    )

                setTextColor(
                    accent,
                )

                strokeColor =
                    ColorStateList.valueOf(
                        BORDER,
                    )

                strokeWidth =
                    context.dp(
                        1,
                    )

                iconTint =
                    ColorStateList.valueOf(
                        accent,
                    )
            }

            if (
                iconRes !=
                null
            ) {
                setIconResource(
                    iconRes,
                )

                iconSize =
                    context.dp(
                        18,
                    )

                iconPadding =
                    context.dp(
                        6,
                    )

                iconGravity =
                    MaterialButton.ICON_GRAVITY_TEXT_START
            }

            stateListAnimator =
                null

            setOnTouchListener {
                    v,
                    event,
                ->
                when (
                    event.actionMasked
                ) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate()
                            .scaleX(
                                0.985f,
                            )
                            .scaleY(
                                0.985f,
                            )
                            .setDuration(
                                70,
                            )
                            .start()
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                    -> {
                        v.animate()
                            .scaleX(
                                1f,
                            )
                            .scaleY(
                                1f,
                            )
                            .setDuration(
                                120,
                            )
                            .start()
                    }
                }

                false
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
            10f,
            accent,
            true,
        ).apply {
            setPadding(
                context.dp(
                    9,
                ),
                context.dp(
                    6,
                ),
                context.dp(
                    9,
                ),
                context.dp(
                    6,
                ),
            )

            val backgroundColor =
                when (
                    accent
                ) {
                    GREEN ->
                        GREEN_TINT

                    PURPLE ->
                        PURPLE_TINT

                    ORANGE ->
                        ORANGE_TINT

                    PINK ->
                        PINK_TINT

                    else ->
                        BLUE_TINT
                }

            val borderColor =
                mixWithWhite(
                    accent,
                    0.58f,
                )

            background =
                rounded(
                    backgroundColor,
                    context.dp(
                        12,
                    ).toFloat(),
                    borderColor,
                    context.dp(
                        1,
                    ),
                )
        }

    fun match(
        top: Int = 0,
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin =
                top
        }

    fun wrap(
        top: Int = 0,
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin =
                top
        }
}
