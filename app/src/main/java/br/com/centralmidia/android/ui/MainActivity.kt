package br.com.centralmidia.android.ui

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CatalogRepository
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.dp
import kotlin.math.max

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = auth.session ?: run {
            startActivity(
                Intent(
                    this,
                    LoginActivity::class.java,
                ),
            )
            finish()
            return
        }

        val root = MobileScaffold.page(
            this,
            "Início",
            "Acompanhe notícias, vídeos, demandas e fontes em tempo real.",
            MobileScaffold.Tab.HOME,
            showAutomation = true,
        )

        val db = CentralDb(this)
        val prefs =
            getSharedPreferences(
                "dashboard_stats",
                MODE_PRIVATE,
            )

        val newsCount =
            prefs.getInt(
                "last_news_count",
                0,
            )
        val videoCount =
            prefs.getInt(
                "last_video_count",
                0,
            )
        val demandsCount =
            db.demands().size
        val sourcesCount =
            CatalogRepository.news(this).size

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        stats.addView(
            statRow(
                statCard(
                    R.drawable.ic_news,
                    "Notícias 24h",
                    newsCount.toString(),
                    "na última busca",
                    Color.rgb(
                        226,
                        241,
                        255,
                    ),
                    MobileUi.BLUE,
                ),
                statCard(
                    R.drawable.ic_video,
                    "Vídeos armazenados",
                    videoCount.toString(),
                    "relevantes na base",
                    MobileUi.PURPLE_TINT,
                    MobileUi.PURPLE,
                ),
            ),
        )

        stats.addView(
            statRow(
                statCard(
                    R.drawable.ic_video,
                    "Vídeos hoje",
                    videoCount.toString(),
                    "capturados hoje",
                    Color.rgb(
                        224,
                        248,
                        240,
                    ),
                    MobileUi.GREEN,
                ),
                statCard(
                    R.drawable.ic_demands,
                    "Demandas",
                    demandsCount.toString(),
                    "$demandsCount ativa(s)",
                    MobileUi.ORANGE_TINT,
                    MobileUi.ORANGE,
                ),
            ),
        )

        stats.addView(
            statCard(
                R.drawable.ic_sources,
                "Fontes",
                sourcesCount.toString(),
                "$sourcesCount especializadas/regionais",
                MobileUi.PINK_TINT,
                MobileUi.PINK,
            ),
            MobileUi.match(dp(8)),
        )

        root.addView(
            stats,
            MobileUi.match(dp(14)),
        )

        root.addView(
            monitoringCard(),
            MobileUi.match(dp(12)),
        )
        root.addView(
            quickActions(),
            MobileUi.match(dp(12)),
        )
        root.addView(
            scheduleCard(),
            MobileUi.match(dp(12)),
        )
        root.addView(
            summaryCard(db),
            MobileUi.match(dp(12)),
        )

        root.addView(
            MobileUi.text(
                this,
                "Sistema operacional • " +
                    "${session.user.name.ifBlank { session.user.username }} • " +
                    session.user.profile,
                10.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(14)),
        )
    }

    private fun statRow(
        left: View,
        right: View,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                left,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    marginEnd = dp(4)
                },
            )
            addView(
                right,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    marginStart = dp(4)
                },
            )
        }

    private fun statCard(
        iconRes: Int,
        title: String,
        value: String,
        caption: String,
        tint: Int,
        accent: Int,
    ): View {
        val card = MobileUi.card(
            this,
            12,
        )

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                tint,
                dp(13).toFloat(),
            )
            addView(
                MobileUi.icon(
                    this@MainActivity,
                    iconRes,
                    accent,
                    23,
                ),
            )
        }

        row.addView(
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
                11.5f,
                MobileUi.NAVY,
                true,
            ),
        )
        copy.addView(
            MobileUi.text(
                this,
                value,
                24f,
                MobileUi.NAVY,
                true,
            ),
        )
        copy.addView(
            MobileUi.text(
                this,
                caption,
                9.5f,
                MobileUi.MUTED,
            ),
        )

        row.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        card.addView(row)
        return card
    }

    private fun monitoringCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                MobileUi.BLUE_TINT,
                dp(30).toFloat(),
            )
            addView(
                MobileUi.icon(
                    this@MainActivity,
                    R.drawable.ic_search,
                    MobileUi.BLUE,
                    28,
                ),
            )
        }

        title.addView(
            icon,
            LinearLayout.LayoutParams(
                dp(58),
                dp(58),
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
                "Monitoramento",
                20f,
                MobileUi.NAVY,
                true,
            ),
        )
        copy.addView(
            MobileUi.text(
                this,
                "As buscas e os resultados são atualizados em tempo real.",
                10.5f,
                MobileUi.MUTED,
            ),
        )

        title.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
        box.addView(title)

        box.addView(
            MobileUi.text(
                this,
                "●  Status: Pronto\n    Monitoramento disponível e funcionando normalmente.",
                11.5f,
                MobileUi.GREEN,
                true,
            ).apply {
                setPadding(
                    dp(12),
                    dp(10),
                    dp(12),
                    dp(10),
                )
                background = MobileUi.rounded(
                    MobileUi.GREEN_TINT,
                    dp(12).toFloat(),
                    Color.rgb(
                        187,
                        233,
                        208,
                    ),
                    dp(1),
                )
            },
            MobileUi.match(dp(10)),
        )

        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        progressRow.addView(
            MobileUi.text(
                this@MainActivity,
                "Busca atual",
                10.5f,
                MobileUi.NAVY,
                true,
            ),
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
        progressRow.addView(
            MobileUi.text(
                this@MainActivity,
                "100%",
                15f,
                MobileUi.BLUE,
                true,
            ),
        )
        box.addView(
            progressRow,
            MobileUi.match(dp(10)),
        )

        box.addView(
            ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal,
            ).apply {
                max = 100
                progress = 100
                progressTintList =
                    android.content.res.ColorStateList.valueOf(
                        MobileUi.GREEN,
                    )
            },
            MobileUi.match(dp(4)),
        )

        box.addView(
            MobileUi.text(
                this,
                "Nenhuma busca em andamento.",
                10.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(4)),
        )

        card.addView(box)
        return card
    }

    private fun quickActions(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        box.addView(
            MobileUi.text(
                this,
                "Ações rápidas",
                18f,
                MobileUi.NAVY,
                true,
            ),
        )
        box.addView(
            MobileUi.text(
                this,
                "Execute as principais buscas com um toque.",
                10.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(3)),
        )

        val first = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        first.addView(
            MobileUi.button(
                this,
                "Buscar notícias",
                true,
                MobileUi.BLUE,
                R.drawable.ic_search,
            ) {
                startActivity(
                    Intent(
                        this,
                        NewsActivity::class.java,
                    ).putExtra(
                        "autoSearch",
                        true,
                    ),
                )
            },
            LinearLayout.LayoutParams(
                0,
                dp(54),
                1f,
            ).apply {
                marginEnd = dp(4)
            },
        )

        first.addView(
            MobileUi.button(
                this,
                "Buscar vídeos",
                false,
                MobileUi.BLUE,
                R.drawable.ic_video,
            ) {
                startActivity(
                    Intent(
                        this,
                        VideosActivity::class.java,
                    ).putExtra(
                        "autoSearch",
                        true,
                    ),
                )
            },
            LinearLayout.LayoutParams(
                0,
                dp(54),
                1f,
            ).apply {
                marginStart = dp(4)
            },
        )

        box.addView(
            first,
            MobileUi.match(dp(10)),
        )

        box.addView(
            MobileUi.button(
                this,
                "Consultar demandas",
                false,
                MobileUi.ORANGE,
                R.drawable.ic_demands,
            ) {
                startActivity(
                    Intent(
                        this,
                        DemandsActivity::class.java,
                    ),
                )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50),
            ).apply {
                topMargin = dp(8)
            },
        )

        card.addView(box)
        return card
    }

    private fun scheduleCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        box.addView(
            MobileUi.text(
                this,
                "Agendamento automático",
                18f,
                MobileUi.NAVY,
                true,
            ),
        )

        box.addView(
            MobileUi.text(
                this,
                "O Android executa o monitoramento em segundo plano com WorkManager.",
                10.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(5)),
        )

        box.addView(
            scheduleChip(
                R.drawable.ic_news,
                "Notícias",
                "monitoramento",
                MobileUi.BLUE_TINT,
                MobileUi.BLUE,
            ),
            MobileUi.match(dp(10)),
        )

        box.addView(
            scheduleChip(
                R.drawable.ic_demands,
                "Demandas",
                "consulta por assunto e veículo",
                MobileUi.ORANGE_TINT,
                MobileUi.ORANGE,
            ),
            MobileUi.match(dp(6)),
        )

        box.addView(
            scheduleChip(
                R.drawable.ic_video,
                "Vídeos",
                "fontes e termos independentes",
                MobileUi.PURPLE_TINT,
                MobileUi.PURPLE,
            ),
            MobileUi.match(dp(6)),
        )

        card.addView(box)
        return card
    }

    private fun scheduleChip(
        iconRes: Int,
        title: String,
        subtitle: String,
        tint: Int,
        accent: Int,
    ): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(9),
                dp(7),
                dp(9),
                dp(7),
            )
            background = MobileUi.rounded(
                Color.rgb(
                    250,
                    252,
                    255,
                ),
                dp(10).toFloat(),
                MobileUi.BORDER,
                dp(1),
            )
        }

        val iconBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                tint,
                dp(9).toFloat(),
            )
            addView(
                MobileUi.icon(
                    this@MainActivity,
                    iconRes,
                    accent,
                    18,
                ),
            )
        }

        item.addView(
            iconBox,
            LinearLayout.LayoutParams(
                dp(36),
                dp(36),
            ).apply {
                marginEnd = dp(8)
            },
        )

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        copy.addView(
            MobileUi.text(
                this,
                title,
                11f,
                MobileUi.NAVY,
                true,
            ),
        )
        copy.addView(
            MobileUi.text(
                this,
                subtitle,
                9f,
                MobileUi.MUTED,
            ),
        )

        item.addView(copy)
        return item
    }

    private fun summaryCard(
        db: CentralDb,
    ): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                MobileUi.icon(
                    this@MainActivity,
                    R.drawable.ic_history,
                    MobileUi.BLUE,
                    20,
                ),
            )
            addView(
                MobileUi.text(
                    this@MainActivity,
                    "Resumo do dia",
                    18f,
                    MobileUi.NAVY,
                    true,
                ),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    marginStart = dp(7)
                },
            )
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        header.addView(
            MobileUi.statusChip(
                this,
                "Últimas 24 horas",
                MobileUi.BLUE,
            ),
        )
        box.addView(header)

        box.addView(
            MobileUi.text(
                this,
                "Panorama das atividades registradas nas últimas 24 horas.",
                10.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(4)),
        )

        val buckets =
            historyBuckets(db)

        box.addView(
            DailySummaryView(
                this,
                buckets.first,
                buckets.second,
                buckets.third,
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(145),
            ).apply {
                topMargin = dp(8)
            },
        )

        box.addView(
            MobileUi.text(
                this,
                "Notícias     Vídeos     Demandas/automação",
                9.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(4)),
        )

        card.addView(box)
        return card
    }

    private fun historyBuckets(
        db: CentralDb,
    ): Triple<IntArray, IntArray, IntArray> {
        val now =
            System.currentTimeMillis()
        val start =
            now -
                24L * 60L * 60L * 1000L

        val news = IntArray(12)
        val videos = IntArray(12)
        val demands = IntArray(12)

        db.history(500)
            .forEach { row ->
                val ts =
                    row.getOrNull(1)
                        ?.toLongOrNull()
                        ?: return@forEach

                if (
                    ts < start ||
                    ts > now
                ) {
                    return@forEach
                }

                val bucket =
                    (
                        (
                            ts - start
                            ) /
                            (
                                2L *
                                    60L *
                                    60L *
                                    1000L
                                )
                        )
                        .toInt()
                        .coerceIn(
                            0,
                            11,
                        )

                when (
                    row.getOrNull(2)
                        .orEmpty()
                ) {
                    "news" ->
                        news[bucket]++

                    "video" ->
                        videos[bucket]++

                    "automation",
                    "demand" ->
                        demands[bucket]++
                }
            }

        return Triple(
            news,
            videos,
            demands,
        )
    }
}

private class DailySummaryView(
    context: android.content.Context,
    private val news: IntArray,
    private val videos: IntArray,
    private val demands: IntArray,
) : View(context) {
    private val gridPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG,
        ).apply {
            color = Color.rgb(
                224,
                233,
                245,
            )
            strokeWidth = 1f
        }

    private val newsPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG,
        ).apply {
            color = MobileUi.BLUE
            strokeWidth =
                context.dp(2).toFloat()
            style = Paint.Style.STROKE
        }

    private val videosPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG,
        ).apply {
            color = MobileUi.PURPLE
            strokeWidth =
                context.dp(2).toFloat()
            style = Paint.Style.STROKE
        }

    private val demandPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG,
        ).apply {
            color = MobileUi.ORANGE
            strokeWidth =
                context.dp(2).toFloat()
            style = Paint.Style.STROKE
        }

    override fun onDraw(
        canvas: Canvas,
    ) {
        super.onDraw(canvas)

        val left =
            context.dp(8).toFloat()
        val right =
            width -
                context.dp(8).toFloat()
        val top =
            context.dp(12).toFloat()
        val bottom =
            height -
                context.dp(16).toFloat()

        repeat(5) { index ->
            val y =
                top +
                    (
                        bottom - top
                        ) *
                    index /
                    4f
            canvas.drawLine(
                left,
                y,
                right,
                y,
                gridPaint,
            )
        }

        drawSeries(
            canvas,
            news,
            newsPaint,
            left,
            right,
            top,
            bottom,
        )
        drawSeries(
            canvas,
            videos,
            videosPaint,
            left,
            right,
            top,
            bottom,
        )
        drawSeries(
            canvas,
            demands,
            demandPaint,
            left,
            right,
            top,
            bottom,
        )
    }

    private fun drawSeries(
        canvas: Canvas,
        data: IntArray,
        paint: Paint,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
    ) {
        val maxValue =
            max(
                1,
                listOf(
                    news.maxOrNull()
                        ?: 0,
                    videos.maxOrNull()
                        ?: 0,
                    demands.maxOrNull()
                        ?: 0,
                ).maxOrNull()
                    ?: 1,
            )

        var px = left
        var py = bottom

        data.forEachIndexed { index, value ->
            val x =
                left +
                    (
                        right - left
                        ) *
                    index /
                    (
                        data.size - 1
                        )
                        .coerceAtLeast(1)

            val y =
                bottom -
                    (
                        bottom - top
                        ) *
                    value /
                    maxValue.toFloat()

            if (index > 0) {
                canvas.drawLine(
                    px,
                    py,
                    x,
                    y,
                    paint,
                )
            }

            canvas.drawCircle(
                x,
                y,
                context.dp(2).toFloat(),
                Paint(paint).apply {
                    style = Paint.Style.FILL
                },
            )

            px = x
            py = y
        }
    }
}
