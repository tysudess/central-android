package br.com.centralmidia.android.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.work.WorkManager
import br.com.centralmidia.android.R
import br.com.centralmidia.android.automation.MonitoringScheduler
import br.com.centralmidia.android.core.dp
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class MoreHubActivity : BaseActivity() {
    private data class ActionItem(
        val title: String,
        val subtitle: String,
        val icon: Int,
        val accent: Int,
        val permission: String? = null,
        val action: () -> Unit,
    )

    private data class Page(
        val title: String,
        val subtitle: String,
        val items: List<ActionItem>,
    )

    private lateinit var pager: ViewPager2
    private val tabButtons = mutableListOf<MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = MobileScaffold.page(
            this,
            "Mais",
            "Deslize para os lados para acessar monitoramento, produção e sistema.",
            MobileScaffold.Tab.MORE,
            showAutomation = true,
        )

        val pages = buildPages()
        root.addView(tabStrip(pages), MobileUi.match(dp(14)))

        pager = ViewPager2(this).apply {
            adapter = MorePagerAdapter(pages)
            offscreenPageLimit = pages.size
            isUserInputEnabled = true
            setPageTransformer { page, position ->
                page.alpha = 0.58f + (1f - kotlin.math.abs(position)).coerceIn(0f, 1f) * 0.42f
                page.translationX = -position * dp(16)
                page.scaleY = 0.96f + (1f - kotlin.math.abs(position)).coerceIn(0f, 1f) * 0.04f
            }
            registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        updateTabs(position)
                    }
                },
            )
        }
        root.addView(
            pager,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(620),
            ).apply {
                topMargin = dp(10)
            },
        )

        root.addView(
            MobileUi.text(
                this,
                "Dica: você pode deslizar a tela horizontalmente para trocar de grupo.",
                10.5f,
                MobileUi.MUTED,
            ).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(2))
            },
            MobileUi.match(dp(2)),
        )

        updateTabs(0)
    }

    private fun buildPages(): List<Page> {
        val session = auth.session
        val all = session?.user?.profile.equals("ADMIN", true) ||
            session?.user?.profile.equals("OPERADOR", true)

        fun allowed(permission: String?): Boolean =
            permission == null || all || permission in session?.user?.permissions.orEmpty()

        fun launch(cls: Class<*>) = {
            startActivity(Intent(this, cls))
            overridePendingTransition(R.anim.central_slide_in_right, R.anim.central_slide_out_left)
        }

        fun item(
            title: String,
            subtitle: String,
            icon: Int,
            accent: Int,
            permission: String? = null,
            action: () -> Unit,
        ) = ActionItem(title, subtitle, icon, accent, permission, action)

        val monitor = listOf(
            item("Demandas", "Assuntos prioritários por veículo", R.drawable.ic_demands, MobileUi.ORANGE, "demands", launch(DemandsActivity::class.java)),
            item("Fontes", "Escolha veículos e fontes de vídeo", R.drawable.ic_sources, MobileUi.GREEN, "sources", launch(SourcesActivity::class.java)),
            item("Histórico", "Notícias, vídeos e ações recentes", R.drawable.ic_history, MobileUi.BLUE, "history", launch(HistoryActivity::class.java)),
            item("Termos", "Listas independentes de Notícias e Vídeos", R.drawable.ic_terms, MobileUi.PURPLE, "terms", launch(TermsActivity::class.java)),
            item("Parar buscas", "Interrompe automações e gravações em andamento", R.drawable.ic_delete, MobileUi.PINK, null) {
                MonitoringScheduler.cancel(this)
                WorkManager.getInstance(this).cancelAllWorkByTag("central-monitoring")
                stopService(Intent(this, br.com.centralmidia.android.recording.ScreenRecordService::class.java))
                toast("Buscas, automações e gravações solicitadas para parar.")
            },
        ).filter { allowed(it.permission) }

        val production = listOf(
            item("Extrator de Notícias", "Extraia texto e metadados de matérias", R.drawable.ic_news, MobileUi.BLUE, "news_extractor", launch(NewsExtractorActivity::class.java)),
            item("Capas", "App Principais Capas integrado", R.drawable.ic_news, MobileUi.BLUE, "covers", launch(CoversActivity::class.java)),
            item("Editor PDF", "Editor completo importado para a Central", R.drawable.ic_terms, MobileUi.PURPLE, "pdf_editor", launch(PdfEditorActivity::class.java)),
            item("Extrator de Vídeos", "Download, corte e yt-dlp Android", R.drawable.ic_video, MobileUi.PURPLE, "extractor", launch(VideoExtractorActivity::class.java)),
            item("Editor de Vídeo", "Timeline, cortes, união e exportação", R.drawable.ic_video, MobileUi.BLUE, "video_editor", launch(VideoEditorActivity::class.java)),
            item("Gravador de Tela", "MediaProjection e gravações recentes", R.drawable.ic_video, MobileUi.PINK, "video_editor", launch(ScreenRecorderActivity::class.java)),
        ).filter { allowed(it.permission) }

        val system = listOf(
            item("Configurações", "Automação e horários • sem proxy", R.drawable.ic_more, MobileUi.BLUE, "settings", launch(SettingsActivity::class.java)),
            item("Minha conta", "Senha, sessão e dados do usuário", R.drawable.ic_home, MobileUi.GREEN, null, launch(AccountActivity::class.java)),
            item("Sair da conta", "Revoga a sessão deste aparelho", R.drawable.ic_delete, MobileUi.PINK, null) {
                io(
                    { auth.logout() },
                    {
                        startActivity(
                            Intent(this, LoginActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        )
                        finishAffinity()
                    },
                    {
                        startActivity(
                            Intent(this, LoginActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        )
                        finishAffinity()
                    },
                )
            },
        ).filter { allowed(it.permission) }

        return listOf(
            Page("Monitoramento", "Busca, fontes, termos e histórico", monitor),
            Page("Produção", "Ferramentas para conteúdo, PDF e vídeo", production),
            Page("Sistema", "Configurações, conta e sessão", system),
        )
    }

    private fun tabStrip(pages: List<Page>): View {
        val card = MobileUi.card(this, 7)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        pages.forEachIndexed { index, page ->
            val button = MobileUi.button(
                this,
                page.title,
                false,
                MobileUi.BLUE,
            ) {
                pager.setCurrentItem(index, true)
            }
            tabButtons += button
            row.addView(
                button,
                LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                    marginStart = if (index == 0) 0 else dp(3)
                    marginEnd = if (index == pages.lastIndex) 0 else dp(3)
                },
            )
        }
        card.addView(row)
        return card
    }

    private fun updateTabs(selected: Int) {
        tabButtons.forEachIndexed { index, button ->
            val active = index == selected
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (active) MobileUi.BLUE else Color.WHITE,
            )
            button.setTextColor(if (active) Color.WHITE else MobileUi.NAVY)
            button.strokeColor = android.content.res.ColorStateList.valueOf(
                if (active) MobileUi.BLUE else MobileUi.BORDER,
            )
            button.strokeWidth = dp(1)
        }
    }

    private inner class MorePagerAdapter(
        private val pages: List<Page>,
    ) : RecyclerView.Adapter<PageHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val root = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(2), dp(2), dp(2), dp(10))
            }
            return PageHolder(root)
        }

        override fun getItemCount(): Int = pages.size

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.bind(pages[position])
        }
    }

    private inner class PageHolder(
        private val root: LinearLayout,
    ) : RecyclerView.ViewHolder(root) {
        fun bind(page: Page) {
            root.removeAllViews()
            root.addView(
                MobileUi.text(
                    this@MoreHubActivity,
                    page.subtitle,
                    11f,
                    MobileUi.MUTED,
                ).apply { gravity = Gravity.CENTER },
                MobileUi.match(dp(2)),
            )
            page.items.forEach { action ->
                root.addView(actionCard(action), MobileUi.match(dp(8)))
            }
        }
    }

    private fun actionCard(item: ActionItem): MaterialCardView {
        val card = MobileUi.card(this, 12).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { item.action() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                when (item.accent) {
                    MobileUi.PURPLE -> MobileUi.PURPLE_TINT
                    MobileUi.ORANGE -> MobileUi.ORANGE_TINT
                    MobileUi.PINK -> MobileUi.PINK_TINT
                    MobileUi.GREEN -> MobileUi.GREEN_TINT
                    else -> MobileUi.BLUE_TINT
                },
                dp(15).toFloat(),
            )
            addView(MobileUi.icon(this@MoreHubActivity, item.icon, item.accent, 25))
        }
        row.addView(
            iconBox,
            LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(11) },
        )
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(MobileUi.text(this, item.title, 15f, MobileUi.NAVY, true))
        copy.addView(
            MobileUi.text(this, item.subtitle, 10f, MobileUi.MUTED),
            MobileUi.match(dp(3)),
        )
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(
            MobileUi.text(this, "›", 26f, item.accent, true).apply { gravity = Gravity.CENTER },
        )
        card.addView(row)
        return card
    }
}
