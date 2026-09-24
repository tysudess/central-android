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
import android.widget.TextView
import br.com.centralmidia.android.core.CatalogRepository
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.dp
import kotlin.math.max

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = auth.session ?: run {
            startActivity(Intent(this, LoginActivity::class.java))
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
        val prefs = getSharedPreferences("dashboard_stats", MODE_PRIVATE)
        val newsCount = prefs.getInt("last_news_count", 0)
        val videoCount = prefs.getInt("last_video_count", 0)
        val demandsCount = db.demands().size
        val sourcesCount = CatalogRepository.news(this).size

        val stats = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stats.addView(statRow(
            statCard("▤", "Notícias 24h", newsCount.toString(), "na última busca", Color.rgb(226, 241, 255), MobileUi.BLUE),
            statCard("▶", "Vídeos armazenados", videoCount.toString(), "relevantes na base", MobileUi.PURPLE_TINT, MobileUi.PURPLE),
        ))
        stats.addView(statRow(
            statCard("▰", "Vídeos hoje", videoCount.toString(), "capturados hoje", Color.rgb(224, 248, 240), MobileUi.GREEN),
            statCard("▣", "Demandas", demandsCount.toString(), "$demandsCount ativa(s)", MobileUi.ORANGE_TINT, MobileUi.ORANGE),
        ))
        stats.addView(statCard("●", "Fontes", sourcesCount.toString(), "$sourcesCount especializadas", MobileUi.PINK_TINT, MobileUi.PINK), MobileUi.match(8))
        root.addView(stats, MobileUi.match(14))

        root.addView(monitoringCard(), MobileUi.match(12))
        root.addView(quickActions(), MobileUi.match(12))
        root.addView(scheduleCard(), MobileUi.match(12))
        root.addView(summaryCard(db), MobileUi.match(12))

        root.addView(
            MobileUi.text(this@MainActivity, "Sistema operacional • ${session.user.name.ifBlank { session.user.username }} • ${session.user.profile}", 11f, MobileUi.MUTED),
            MobileUi.match(14),
        )
    }

    private fun statRow(left: View, right: View): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
            addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        }
    }

    private fun statCard(icon: String, title: String, value: String, caption: String, tint: Int, accent: Int): View {
        val card = MobileUi.card(this@MainActivity, 12)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val iconView = MobileUi.text(this@MainActivity, icon, 18f, accent, true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(tint, dp(13).toFloat())
        }
        row.addView(iconView, LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginEnd = dp(10) })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(MobileUi.text(this@MainActivity, title, 12f, MobileUi.NAVY, true))
        copy.addView(MobileUi.text(this@MainActivity, value, 25f, MobileUi.NAVY, true))
        copy.addView(MobileUi.text(this@MainActivity, caption, 10f, MobileUi.MUTED))
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        return card
    }

    private fun monitoringCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val icon = MobileUi.text(this@MainActivity, "◎", 28f, MobileUi.BLUE, true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(MobileUi.BLUE_TINT, dp(30).toFloat())
        }
        title.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginEnd = dp(10) })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(MobileUi.text(this@MainActivity, "Monitoramento", 21f, MobileUi.NAVY, true))
        copy.addView(MobileUi.text(this@MainActivity, "As buscas e os resultados são atualizados em tempo real.", 11f, MobileUi.MUTED))
        title.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(title)

        box.addView(MobileUi.text(this@MainActivity, "●  Status: Pronto\n     Monitoramento disponível e funcionando normalmente.", 12f, MobileUi.GREEN, true).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = MobileUi.rounded(MobileUi.GREEN_TINT, dp(12).toFloat(), Color.rgb(187, 233, 208), dp(1))
        }, MobileUi.match(10))

        val progressRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        progressRow.addView(MobileUi.text(this@MainActivity, "Busca atual", 11f, MobileUi.NAVY, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        progressRow.addView(MobileUi.text(this@MainActivity, "100%", 15f, MobileUi.BLUE, true))
        box.addView(progressRow, MobileUi.match(10))
        box.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 100
            progressTintList = android.content.res.ColorStateList.valueOf(MobileUi.GREEN)
        }, MobileUi.match(4))
        box.addView(MobileUi.text(this@MainActivity, "Nenhuma busca em andamento.", 11f, MobileUi.MUTED), MobileUi.match(4))
        card.addView(box)
        return card
    }

    private fun quickActions(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(MobileUi.text(this@MainActivity, "⚡  Ações rápidas", 18f, MobileUi.NAVY, true))
        box.addView(MobileUi.text(this@MainActivity, "Execute as principais buscas com um clique.", 11f, MobileUi.MUTED), MobileUi.match(3))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(MobileUi.button(this@MainActivity, "⌕\nBuscar\nnotícias", true) {
            startActivity(Intent(this, NewsActivity::class.java).putExtra("autoSearch", true))
        }, LinearLayout.LayoutParams(0, dp(88), 1f).apply { marginEnd = dp(4) })
        row.addView(MobileUi.button(this@MainActivity, "▶\nBuscar\nvídeos", false) {
            startActivity(Intent(this, VideosActivity::class.java).putExtra("autoSearch", true))
        }, LinearLayout.LayoutParams(0, dp(88), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        row.addView(MobileUi.button(this@MainActivity, "▣\nBuscar\ndemandas", false) {
            startActivity(Intent(this, DemandsActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(88), 1f).apply { marginStart = dp(4) })
        box.addView(row, MobileUi.match(10))
        card.addView(box)
        return card
    }

    private fun scheduleCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(MobileUi.text(this@MainActivity, "◷  Agendamento automático", 18f, MobileUi.NAVY, true))
        box.addView(MobileUi.text(this@MainActivity, "O Android executa o monitoramento em segundo plano com WorkManager.", 11f, MobileUi.MUTED), MobileUi.match(5))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(scheduleChip("▤", "Notícias", "monitoramento", MobileUi.BLUE_TINT, MobileUi.BLUE), LinearLayout.LayoutParams(0, dp(62), 1f).apply { marginEnd = dp(4) })
        row.addView(scheduleChip("▣", "Demandas", "consulta rápida", MobileUi.ORANGE_TINT, MobileUi.ORANGE), LinearLayout.LayoutParams(0, dp(62), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        row.addView(scheduleChip("▶", "Vídeos", "sob demanda", MobileUi.PURPLE_TINT, MobileUi.PURPLE), LinearLayout.LayoutParams(0, dp(62), 1f).apply { marginStart = dp(4) })
        box.addView(row, MobileUi.match(10))
        card.addView(box)
        return card
    }

    private fun scheduleChip(icon: String, title: String, subtitle: String, tint: Int, accent: Int): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = MobileUi.rounded(Color.rgb(250, 252, 255), dp(10).toFloat(), MobileUi.BORDER, dp(1))
        }
        item.addView(MobileUi.text(this@MainActivity, icon, 14f, accent, true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(tint, dp(9).toFloat())
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(6) })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(MobileUi.text(this@MainActivity, title, 10f, MobileUi.NAVY, true))
        copy.addView(MobileUi.text(this@MainActivity, subtitle, 8.5f, MobileUi.MUTED))
        item.addView(copy)
        return item
    }

    private fun summaryCard(db: CentralDb): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(MobileUi.text(this@MainActivity, "▥  Resumo do dia", 18f, MobileUi.NAVY, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(MobileUi.statusChip(this@MainActivity, "Últimas 24 horas", MobileUi.BLUE))
        box.addView(header)
        box.addView(MobileUi.text(this@MainActivity, "Panorama das atividades registradas nas últimas 24 horas.", 11f, MobileUi.MUTED), MobileUi.match(4))
        val buckets = historyBuckets(db)
        box.addView(DailySummaryView(this, buckets.first, buckets.second, buckets.third), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(145)).apply { topMargin = dp(8) })
        box.addView(MobileUi.text(this@MainActivity, "● Notícias     ● Vídeos     ● Demandas/automação", 10f, MobileUi.MUTED), MobileUi.match(4))
        card.addView(box)
        return card
    }

    private fun historyBuckets(db: CentralDb): Triple<IntArray, IntArray, IntArray> {
        val now = System.currentTimeMillis()
        val start = now - 24L * 60L * 60L * 1000L
        val news = IntArray(12)
        val videos = IntArray(12)
        val demands = IntArray(12)
        db.history(250).forEach { row ->
            val ts = row.getOrNull(1)?.toLongOrNull() ?: return@forEach
            if (ts < start || ts > now) return@forEach
            val bucket = (((ts - start) / (2L * 60L * 60L * 1000L)).toInt()).coerceIn(0, 11)
            when (row.getOrNull(2).orEmpty()) {
                "news" -> news[bucket]++
                "video" -> videos[bucket]++
                "automation", "demand" -> demands[bucket]++
            }
        }
        return Triple(news, videos, demands)
    }
}

private class DailySummaryView(
    context: android.content.Context,
    private val news: IntArray,
    private val videos: IntArray,
    private val demands: IntArray,
) : View(context) {
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(224, 233, 245); strokeWidth = 1f }
    private val newsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MobileUi.BLUE; strokeWidth = context.dp(2).toFloat(); style = Paint.Style.STROKE }
    private val videosPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MobileUi.PURPLE; strokeWidth = context.dp(2).toFloat(); style = Paint.Style.STROKE }
    private val demandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MobileUi.ORANGE; strokeWidth = context.dp(2).toFloat(); style = Paint.Style.STROKE }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = context.dp(8).toFloat()
        val right = width - context.dp(8).toFloat()
        val top = context.dp(12).toFloat()
        val bottom = height - context.dp(16).toFloat()
        repeat(5) { index ->
            val y = top + (bottom - top) * index / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        drawSeries(canvas, news, newsPaint, left, right, top, bottom)
        drawSeries(canvas, videos, videosPaint, left, right, top, bottom)
        drawSeries(canvas, demands, demandPaint, left, right, top, bottom)
    }

    private fun drawSeries(canvas: Canvas, data: IntArray, paint: Paint, left: Float, right: Float, top: Float, bottom: Float) {
        val maxValue = max(1, listOf(news.maxOrNull() ?: 0, videos.maxOrNull() ?: 0, demands.maxOrNull() ?: 0).maxOrNull() ?: 1)
        var px = left
        var py = bottom
        data.forEachIndexed { index, value ->
            val x = left + (right - left) * index / (data.size - 1).coerceAtLeast(1)
            val y = bottom - (bottom - top) * value / maxValue.toFloat()
            if (index > 0) canvas.drawLine(px, py, x, y, paint)
            canvas.drawCircle(x, y, context.dp(2).toFloat(), Paint(paint).apply { style = Paint.Style.FILL })
            px = x
            py = y
        }
    }
}
