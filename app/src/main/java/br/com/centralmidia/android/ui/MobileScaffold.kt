package br.com.centralmidia.android.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.dp
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
                activity.dp(8),
                activity.dp(16),
                activity.dp(22),
            )
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

        shell.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        val bottom = bottomNav(activity, selected)
        shell.addView(bottom, MobileUi.match())

        ViewCompat.setOnApplyWindowInsetsListener(shell) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            shell.setPadding(0, bars.top, 0, 0)
            bottom.setPadding(
                activity.dp(8),
                activity.dp(6),
                activity.dp(8),
                activity.dp(8) + bars.bottom,
            )
            insets
        }

        installEdgeSwipe(activity, shell, selected)
        activity.setContentView(shell)
        ViewCompat.requestApplyWindowInsets(shell)
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
                addView(
                    View(activity).apply {
                        background = MobileUi.rounded(
                            when (index) {
                                0 -> Color.rgb(83, 164, 255)
                                1 -> Color.rgb(48, 139, 246)
                                else -> Color.rgb(25, 113, 230)
                            },
                            activity.dp(5).toFloat(),
                        )
                    },
                    LinearLayout.LayoutParams(
                        activity.dp(6),
                        activity.dp(height),
                    ).apply {
                        marginEnd = activity.dp(3)
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
                marginStart = activity.dp(8)
            },
        )

        val account = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(
                activity.dp(6),
                activity.dp(4),
                activity.dp(4),
                activity.dp(4),
            )
            setOnClickListener {
                open(activity, AccountActivity::class.java, true)
            }
        }

        val initial =
            session?.user?.name?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
                ?: session?.user?.username?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
                ?: "U"

        val avatar = MobileUi.text(
            activity,
            initial,
            16f,
            Color.WHITE,
            true,
        ).apply {
            gravity = Gravity.CENTER
            background = MobileUi.oval(
                Color.rgb(86, 157, 245),
            )
        }

        account.addView(
            avatar,
            LinearLayout.LayoutParams(
                activity.dp(42),
                activity.dp(42),
            ),
        )

        row.addView(account)
        return row
    }

    private fun titleBlock(
        activity: BaseActivity,
        title: String,
        subtitle: String,
        showAutomation: Boolean,
    ): View {
        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
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

        val titleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        val copy = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        copy.addView(
            MobileUi.text(
                activity,
                title,
                28f,
                MobileUi.NAVY,
                true,
            ),
        )
        copy.addView(
            MobileUi.text(
                activity,
                subtitle,
                13.5f,
                MobileUi.MUTED,
                false,
            ).apply {
                setPadding(
                    0,
                    activity.dp(2),
                    activity.dp(8),
                    0,
                )
            },
        )

        titleRow.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        val chips = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }

        chips.addView(
            MobileUi.statusChip(
                activity,
                "●  Conexão direta",
                MobileUi.GREEN,
            ),
            MobileUi.wrap(),
        )

        if (showAutomation) {
            val automation = MobileUi.statusChip(
                activity,
                "●  Automação pronta",
                MobileUi.GREEN,
            )
            chips.addView(
                automation,
                MobileUi.wrap(activity.dp(6)),
            )

            WorkManager.getInstance(activity)
                .getWorkInfosByTagLiveData("central-monitoring")
                .observe(activity) { infos ->
                    val active = infos.any {
                        it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.RUNNING
                    }
                    automation.text =
                        if (active) "●  Automação ativa" else "●  Automação pronta"
                }
        }

        titleRow.addView(
            chips,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        outer.addView(titleRow, MobileUi.match(activity.dp(3)))
        return outer
    }

    private fun bottomNav(
        activity: BaseActivity,
        selected: Tab,
    ): LinearLayout {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                activity.dp(8),
                activity.dp(6),
                activity.dp(8),
                activity.dp(8),
            )
            background = MobileUi.rounded(Color.WHITE, 0f)
            elevation = activity.dp(14).toFloat()
        }

        data class Nav(
            val tab: Tab,
            val icon: Int,
            val label: String,
            val cls: Class<*>,
        )

        val items = listOf(
            Nav(Tab.HOME, R.drawable.ic_home, "Início", MainActivity::class.java),
            Nav(Tab.NEWS, R.drawable.ic_news, "Notícias", NewsActivity::class.java),
            Nav(Tab.VIDEOS, R.drawable.ic_video, "Vídeos", VideosActivity::class.java),
            Nav(Tab.MORE, R.drawable.ic_more, "Mais", MoreHubActivity::class.java),
        )

        val selectedIndex = items.indexOfFirst { it.tab == selected }.coerceAtLeast(0)

        items.forEachIndexed { index, item ->
            val selectedItem = item.tab == selected

            val itemView = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(
                    activity.dp(8),
                    activity.dp(7),
                    activity.dp(8),
                    activity.dp(6),
                )
                if (selectedItem) {
                    background = MobileUi.rounded(
                        MobileUi.BLUE_TINT,
                        activity.dp(28).toFloat(),
                    )
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (!selectedItem) {
                        open(activity, item.cls, index >= selectedIndex)
                    }
                }
            }

            itemView.addView(
                MobileUi.icon(
                    activity,
                    item.icon,
                    if (selectedItem) MobileUi.BLUE else MobileUi.NAVY,
                    24,
                ),
            )

            itemView.addView(
                MobileUi.text(
                    activity,
                    item.label,
                    11.5f,
                    if (selectedItem) MobileUi.BLUE else MobileUi.MUTED,
                    selectedItem,
                ).apply {
                    gravity = Gravity.CENTER
                },
                MobileUi.match(activity.dp(3)),
            )

            bar.addView(
                itemView,
                LinearLayout.LayoutParams(
                    0,
                    activity.dp(64),
                    1f,
                ).apply {
                    marginStart = activity.dp(2)
                    marginEnd = activity.dp(2)
                },
            )
        }

        return bar
    }

    private fun installEdgeSwipe(
        activity: BaseActivity,
        shell: View,
        selected: Tab,
    ) {
        val order = listOf(
            Tab.HOME to MainActivity::class.java,
            Tab.NEWS to NewsActivity::class.java,
            Tab.VIDEOS to VideosActivity::class.java,
            Tab.MORE to MoreHubActivity::class.java,
        )
        val current = order.indexOfFirst { it.first == selected }
        if (current < 0) return

        val edge = activity.dp(34).toFloat()
        val minimum = activity.dp(90).toFloat()
        var width = 0

        val detector = GestureDetector(
            activity,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    val start = e1 ?: return false
                    if (width <= 0) width = shell.width
                    val dx = e2.x - start.x
                    if (kotlin.math.abs(dx) < minimum || kotlin.math.abs(velocityX) < 550f) {
                        return false
                    }
                    if (kotlin.math.abs(dx) < kotlin.math.abs(e2.y - start.y) * 1.35f) {
                        return false
                    }

                    if (dx < 0 && start.x >= width - edge && current < order.lastIndex) {
                        open(activity, order[current + 1].second, true)
                        return true
                    }
                    if (dx > 0 && start.x <= edge && current > 0) {
                        open(activity, order[current - 1].second, false)
                        return true
                    }
                    return false
                }
            },
        )

        shell.setOnTouchListener { _, event ->
            width = shell.width
            detector.onTouchEvent(event)
            false
        }
    }

    private fun open(
        activity: BaseActivity,
        cls: Class<*>,
        forward: Boolean,
    ) {
        if (activity::class.java == cls) return
        activity.startActivity(
            Intent(activity, cls).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
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
            textSize = 12.5f
            cornerRadius = context.dp(12)
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

            setOnClickListener { onClick() }
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
                if (accent == GREEN) Color.rgb(182, 232, 207) else Color.rgb(190, 218, 249),
                context.dp(1),
            )
        }

    fun match(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = top
        }

    fun wrap(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = top
        }
}
