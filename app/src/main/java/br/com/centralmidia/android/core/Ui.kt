package br.com.centralmidia.android.core

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

fun Context.dp(
    v: Int,
): Int =
    (v * resources.displayMetrics.density)
        .toInt()

fun Context.dp(
    v: Float,
): Int =
    (v * resources.displayMetrics.density)
        .toInt()

object Ui {
    private val BG =
        Color.rgb(
            244,
            248,
            253,
        )

    private val SURFACE =
        Color.WHITE

    private val NAVY =
        Color.rgb(
            21,
            59,
            101,
        )

    private val MUTED =
        Color.rgb(
            92,
            117,
            147,
        )

    private val BLUE =
        Color.rgb(
            42,
            132,
            229,
        )

    private val BORDER =
        Color.rgb(
            210,
            226,
            241,
        )

    fun page(
        activity: AppCompatActivity,
        title: String,
        subtitle: String = "",
    ): LinearLayout {
        val scroll =
            ScrollView(
                activity,
            ).apply {
                isFillViewport =
                    true

                overScrollMode =
                    android.view.View.OVER_SCROLL_NEVER

                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(
                            Color.rgb(250, 252, 255),
                            BG,
                            Color.rgb(239, 246, 253),
                        ),
                    )
            }

        val root =
            LinearLayout(
                activity,
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    activity.dp(
                        16,
                    ),
                    activity.dp(
                        14,
                    ),
                    activity.dp(
                        16,
                    ),
                    activity.dp(
                        22,
                    ),
                )
            }

        if (
            activity !is
            br.com.centralmidia.android.ui.MainActivity &&
            activity !is
            br.com.centralmidia.android.ui.LoginActivity
        ) {
            root.addView(
                button(
                    activity,
                    "←  Voltar",
                ) {
                    activity.finish()
                },
                lp(
                    activity.dp(
                        2,
                    ),
                ),
            )
        }

        root.addView(
            TextView(
                activity,
            ).apply {
                text =
                    title

                textSize =
                    23f

                setTextColor(
                    NAVY,
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD,
                )

                includeFontPadding =
                    false

                setPadding(
                    0,
                    activity.dp(
                        10,
                    ),
                    0,
                    activity.dp(
                        2,
                    ),
                )
            },
        )

        if (
            subtitle.isNotBlank()
        ) {
            root.addView(
                TextView(
                    activity,
                ).apply {
                    text =
                        subtitle

                    textSize =
                        12.5f

                    setTextColor(
                        MUTED,
                    )

                    includeFontPadding =
                        false
                },
                lp(
                    activity.dp(
                        2,
                    ),
                ),
            )
        }

        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        activity.setContentView(
            scroll,
        )

        return root
    }

    fun card(
        context: Context,
    ): MaterialCardView =
        MaterialCardView(
            context,
        ).apply {
            radius =
                context.dp(
                    17,
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

            setContentPadding(
                context.dp(
                    14,
                ),
                context.dp(
                    14,
                ),
                context.dp(
                    14,
                ),
                context.dp(
                    14,
                ),
            )
        }

    fun button(
        context: Context,
        text: String,
        onClick: () -> Unit,
    ): MaterialButton =
        MaterialButton(
            context,
        ).apply {
            this.text =
                text

            isAllCaps =
                false

            textSize =
                12.4f

            minimumHeight =
                context.dp(
                    46,
                )

            cornerRadius =
                context.dp(
                    13,
                )

            backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    SURFACE,
                )

            setTextColor(
                BLUE,
            )

            strokeWidth =
                context.dp(
                    1,
                )

            strokeColor =
                android.content.res.ColorStateList.valueOf(
                    BORDER,
                )

            setOnClickListener {
                onClick()
            }
        }

    fun edit(
        context: Context,
        hint: String,
        password: Boolean = false,
    ): TextInputEditText =
        TextInputEditText(
            context,
        ).apply {
            this.hint =
                hint

            textSize =
                15f

            setTextColor(
                NAVY,
            )

            setHintTextColor(
                MUTED,
            )

            minHeight =
                context.dp(
                    51,
                )

            setOnFocusChangeListener { _, focused ->
                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable.RECTANGLE
                        setColor(
                            Color.WHITE,
                        )
                        cornerRadius =
                            context.dp(
                                14,
                            ).toFloat()
                        setStroke(
                            context.dp(
                                1,
                            ),
                            if (focused) {
                                Color.rgb(
                                    145,
                                    192,
                                    237,
                                )
                            } else {
                                Color.rgb(
                                    210,
                                    226,
                                    241,
                                )
                            },
                        )
                    }
            }

            setPadding(
                context.dp(
                    12,
                ),
                context.dp(
                    10,
                ),
                context.dp(
                    12,
                ),
                context.dp(
                    10,
                ),
            )

            if (
                password
            ) {
                inputType =
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }

    fun label(
        context: Context,
        text: String,
        size: Float = 13.5f,
    ): TextView =
        TextView(
            context,
        ).apply {
            this.text =
                text

            textSize =
                size

            setTextColor(
                NAVY,
            )

            includeFontPadding =
                false

            setTypeface(
                typeface,
                Typeface.BOLD,
            )

            setPadding(
                0,
                context.dp(
                    5,
                ),
                0,
                context.dp(
                    5,
                ),
            )
        }

    fun lp(
        top: Int = 8,
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin =
                top
        }
}
