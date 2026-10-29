package br.com.centralmidia.android.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import br.com.principaiscapas.ModernMainActivity
import br.com.centralmidia.android.core.dp
import java.lang.reflect.Field

/**
 * Tela de Capas integrada ao dock da Central.
 *
 * Esta classe mantém os mecanismos atuais de busca, seleção, inserção manual e
 * exportação PDF em ModernMainActivity/MainActivity. O trabalho aqui é visual:
 * paleta naval azul-marinho e dourado, cartões claros em grade adaptável e a
 * navegação inferior compartilhada permanece como parte do shell da Central.
 */
class CoversActivity : ModernMainActivity() {

    private var layoutRefreshToken = 0
    private var compactInsetsFixInstalled = false
    private var packingGrid = false
    private var currentGridRow: LinearLayout? = null
    private var activeList: LinearLayout? = null
    private var summaryRow: LinearLayout? = null
    private var gridColumns = 2

    private val gridRowTag = "central-covers-grid-row-v1"
    private val summaryTag = "central-covers-summary-v1"

    private val navy = Color.rgb(8, 39, 70)          // #082746
    private val navyDeep = Color.rgb(4, 24, 44)      // #04182C
    private val navyBlue = Color.rgb(22, 67, 105)    // #164369
    private val gold = Color.rgb(198, 161, 91)       // #C6A15B
    private val goldDeep = Color.rgb(151, 111, 43)   // #976F2B
    private val goldLight = Color.rgb(250, 243, 224)
    private val pageBg = Color.rgb(239, 244, 249)
    private val surface = Color.WHITE
    private val surfaceSoft = Color.rgb(247, 249, 252)
    private val textMain = Color.rgb(16, 43, 73)
    private val textMuted = Color.rgb(93, 113, 137)
    private val border = Color.rgb(211, 221, 232)
    private val green = Color.rgb(20, 117, 82)
    private val greenLight = Color.rgb(228, 245, 237)
    private val red = Color.rgb(179, 49, 59)
    private val redLight = Color.rgb(252, 235, 237)
    private val amber = Color.rgb(151, 98, 12)
    private val amberLight = Color.rgb(255, 245, 222)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleNavalLayout()
    }

    override fun onResume() {
        super.onResume()
        // ModernMainActivity reaplica o estilo genérico ao voltar à tela. A fila
        // em duas etapas garante que nosso tema seja aplicado depois desse ajuste.
        scheduleNavalLayout()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scheduleNavalLayout()
    }

    private fun scheduleNavalLayout() {
        val token = ++layoutRefreshToken
        window.decorView.post {
            window.decorView.postOnAnimation {
                retryNavalLayout(token, 0)
            }
        }
    }

    private fun retryNavalLayout(token: Int, attempt: Int) {
        if (token != layoutRefreshToken || isFinishing) return
        if (applyNavalLayout()) {
            // O scaffold base instala insets e estilos após a primeira passagem em
            // alguns aparelhos. Uma aplicação final, já com as dimensões medidas,
            // fixa o tema e o item Capas visível na barra inferior.
            window.decorView.postDelayed({
                if (token == layoutRefreshToken && !isFinishing) applyNavalLayout()
            }, 160L)
            return
        }
        if (attempt < 6) {
            window.decorView.postDelayed({ retryNavalLayout(token, attempt + 1) }, 70L)
        }
    }

    private fun applyNavalLayout(): Boolean {
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return false
        val shell = content.getChildAt(0) as? ViewGroup ?: return false
        val moduleRoot = shell.getChildAt(0) as? LinearLayout ?: return false
        val scroll = findFirstScrollView(moduleRoot) ?: return false
        val list = scroll.getChildAt(0) as? LinearLayout ?: return false

        activeList = list
        gridColumns = if (resources.configuration.screenWidthDp >= 520) 3 else 2

        applySystemChrome()
        moduleRoot.setBackgroundColor(pageBg)
        // Menos altura antes da primeira linha de jornais, para mostrar mais capas
        // sem reduzir a área de toque dos controles principais.
        moduleRoot.setPadding(dp(8), dp(2), dp(8), dp(5))
        installCompactBottomInsetsFix(moduleRoot)
        scroll.setBackgroundColor(Color.TRANSPARENT)
        scroll.setPadding(0, 0, 0, 0)
        scroll.clipToPadding = false
        list.setBackgroundColor(Color.TRANSPARENT)
        list.setPadding(0, dp(1), 0, dp(5))

        styleHeader(moduleRoot)
        styleDateAndActions(moduleRoot)
        ensureSummaryRow(moduleRoot)
        styleSectionHeader(moduleRoot)
        installOrRefreshGrid(list)

        // A tela base já expande o primeiro jornal. Não expandimos todos de uma vez:
        // cartões pendentes ficam compactos e a ação de inserir manual aparece ao abrir
        // somente o jornal desejado.
        restyleVisibleCards(list)
        updateSummaryCounts()
        styleSelectedDockItem(shell)
        return true
    }


    /**
     * O scaffold compartilhado reserva espaço no conteúdo para o dock inferior.
     * Como Capas já tem a própria rolagem acima desse dock, mantemos um padding
     * interno compacto para não desperdiçar uma faixa grande no final da lista.
     */
    private fun installCompactBottomInsetsFix(moduleRoot: LinearLayout) {
        if (!compactInsetsFixInstalled) {
            ViewCompat.setOnApplyWindowInsetsListener(moduleRoot) { view, insets ->
                val compactBottom = dp(8)
                if (view.paddingBottom != compactBottom) {
                    view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, compactBottom)
                }
                insets
            }
            compactInsetsFixInstalled = true
        }
        ViewCompat.requestApplyInsets(moduleRoot)
    }

    @Suppress("DEPRECATION")
    private fun applySystemChrome() {
        window.statusBarColor = navyDeep
        window.navigationBarColor = navyDeep
        var flags = window.decorView.systemUiVisibility
        flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        window.decorView.systemUiVisibility = flags
        // A barra compartilhada usa WindowInsetsControllerCompat. Definimos também
        // o estado moderno para evitar que o primeiro frame reative ícones escuros.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun styleHeader(root: LinearLayout) {
        val toolbar = root.getChildAt(0) as? LinearLayout ?: return
        toolbar.layoutParams = toolbar.layoutParams.apply { height = dp(54) }
        toolbar.setPadding(dp(8), dp(5), dp(6), dp(5))
        toolbar.background = gradient(navy, navyDeep, 17, gold, 1)
        toolbar.elevation = dp(3).toFloat()

        val brandBox = toolbar.getChildAt(1) as? LinearLayout
        val brandTitle = brandBox?.getChildAt(0) as? TextView
        brandTitle?.apply {
            text = "Central Inteligente de Mídia"
            setTextColor(Color.WHITE)
            setTextSize(13.2f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val brandSubtitle = brandBox?.getChildAt(1) as? TextView
        brandSubtitle?.apply {
            text = "Monitoramento, análise e produção de informação"
            setTextColor(Color.rgb(218, 227, 238))
            setTextSize(8.4f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        forEachView(toolbar) { view ->
            if (view is ImageView) view.contentDescription = "Logo da Central Inteligente de Mídia"
            if (view is Button) {
                val label = view.text?.toString().orEmpty().uppercase()
                when {
                    label.contains("PDFS") -> styleButton(view, surface, gold, navy)
                    label.contains("CONFIG") || label.contains("⚙") -> styleButton(view, surface, gold, navy)
                }
            }
        }
    }

    private fun styleDateAndActions(root: LinearLayout) {
        val dateCard = root.getChildAt(1) as? LinearLayout
        dateCard?.apply {
            background = rounded(surface, 14, border, 1)
            setPadding(dp(9), dp(3), dp(9), dp(3))
            elevation = dp(1).toFloat()
            layoutParams = layoutParams.apply {
                height = dp(52)
                if (this is LinearLayout.LayoutParams) {
                    topMargin = dp(4)
                    bottomMargin = dp(3)
                }
            }
        }

        dateCard?.let { date ->
            findTextViewContaining(date, "DATA")?.apply {
                setTextColor(goldDeep)
                setTextSize(10.5f)
                typeface = Typeface.DEFAULT_BOLD
            }
            findAllViews(date).filterIsInstance<TextView>().forEach { tv ->
                if (tv !is Button && tv.text?.toString()?.contains("DATA", true) != true) {
                    tv.setTextColor(if (tv.text?.toString().orEmpty().contains("Toque para alterar", true)) textMuted else textMain)
                }
            }
            findAllViews(date).filterIsInstance<Button>().forEach { b ->
                b.setTextColor(textMain)
                b.setTextSize(14.8f)
                b.typeface = Typeface.DEFAULT_BOLD
                b.background = rounded(Color.TRANSPARENT, 8, Color.TRANSPARENT, 0)
                b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
        }

        val actions = root.getChildAt(2) as? LinearLayout
        actions?.apply {
            setPadding(0, dp(1), 0, 0)
            layoutParams = layoutParams.apply { height = dp(40) }
            for (i in 0 until childCount) {
                val button = getChildAt(i) as? Button ?: continue
                val label = button.text?.toString().orEmpty().uppercase()
                button.setAllCaps(false)
                button.setTypeface(Typeface.DEFAULT_BOLD)
                button.setTextSize(11f)
                button.setPadding(dp(8), 0, dp(8), 0)
                button.layoutParams = button.layoutParams.apply { height = dp(36) }
                if (label.contains("GERAR PDF")) {
                    styleButton(button, gold, goldDeep, navyDeep)
                } else {
                    styleButton(button, navy, gold, Color.WHITE)
                }
            }
        }

        val progress = root.getChildAt(3) as? ProgressBar
        progress?.let {
            it.progressTintList = ColorStateList.valueOf(gold)
            it.indeterminateTintList = ColorStateList.valueOf(gold)
        }
    }

    private fun ensureSummaryRow(root: LinearLayout) {
        val existing = root.findViewWithTag<LinearLayout>(summaryTag)
        if (existing != null) {
            summaryRow = existing
            styleSummaryBoxes(existing)
            return
        }

        val row = LinearLayout(this).apply {
            tag = summaryTag
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(1), 0, dp(2))
            clipChildren = false
            clipToPadding = false
        }

        val specs = listOf(
            Triple("total", "Jornais", navyBlue),
            Triple("available", "Com capa", green),
            Triple("pending", "Aguardando", amber),
            Triple("selected", "Selecionadas", goldDeep),
        )
        specs.forEachIndexed { index, spec ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(4), dp(2), dp(4))
                background = rounded(surface, 11, border, 1)
                elevation = dp(1).toFloat()
                tag = "covers-summary-box:${spec.first}"
            }
            box.addView(
                TextView(this).apply {
                    tag = "covers-summary-value:${spec.first}"
                    text = "—"
                    setTextColor(spec.third)
                    setTextSize(14f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                },
            )
            box.addView(
                TextView(this).apply {
                    text = spec.second
                    setTextColor(textMuted)
                    setTextSize(if (gridColumns == 3) 8f else 8.8f)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
            row.addView(
                box,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = dp(4)
                },
            )
        }

        root.addView(
            row,
            4.coerceAtMost(root.childCount),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        summaryRow = row
        styleSummaryBoxes(row)
    }

    private fun styleSummaryBoxes(row: LinearLayout) {
        row.background = rounded(Color.TRANSPARENT, 1, Color.TRANSPARENT, 0)
        for (i in 0 until row.childCount) {
            val box = row.getChildAt(i) as? LinearLayout ?: continue
            box.background = rounded(surface, 11, border, 1)
            box.elevation = dp(1).toFloat()
            if (box.childCount > 0) {
                (box.getChildAt(0) as? TextView)?.apply {
                    setTextSize(14f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
            }
            if (box.childCount > 1) {
                (box.getChildAt(1) as? TextView)?.apply {
                    setTextColor(textMuted)
                    setTextSize(if (gridColumns == 3) 8f else 8.8f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                }
            }
        }
    }

    private fun styleSectionHeader(root: LinearLayout) {
        val section = root.childrenDepthFirst().firstOrNull { v ->
            v is LinearLayout && findTextViewContaining(v, "JORNAIS") != null &&
                findTextViewContaining(v, "SELEÇÃO") != null
        } as? LinearLayout ?: return

        section.layoutParams = section.layoutParams.apply { height = dp(34) }
        section.setPadding(dp(1), dp(1), dp(1), dp(1))
        findTextViewContaining(section, "JORNAIS")?.apply {
            text = "CAPAS DE JORNAIS"
            setTextColor(navy)
            setTextSize(11f)
            typeface = Typeface.DEFAULT_BOLD
        }
        findTextViewContaining(section, "selecionados")?.apply {
            setTextColor(goldDeep)
            setTextSize(9f)
        }
        findAllViews(section).filterIsInstance<Button>().forEach {
            styleButton(it, surface, gold, navy)
            it.setTextSize(9f)
        }
    }

    private fun installOrRefreshGrid(list: LinearLayout) {
        // O estilo-base é reaplicado ao voltar à tela; achata a grade anterior e
        // a recria antes de reinstalar nosso listener, sem tocar na lógica dos cards.
        list.setOnHierarchyChangeListener(null)
        val flattened = mutableListOf<View>()
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            if (child.tag == gridRowTag && child is ViewGroup) {
                val rowChildren = (0 until child.childCount).map { child.getChildAt(it) }
                rowChildren.forEach { child.removeView(it); flattened.add(it) }
            } else {
                flattened.add(child)
            }
        }

        packingGrid = true
        list.removeAllViews()
        currentGridRow = null
        flattened.forEach { child ->
            (child.parent as? ViewGroup)?.removeView(child)
            placeInGrid(list, child)
        }
        packingGrid = false

        activeList = list
        list.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                val container = activeList ?: return
                if (packingGrid || parent !== container || child == null) return
                packingGrid = true
                container.removeView(child)
                placeInGrid(container, child)
                packingGrid = false
                updateSummaryCounts()
            }

            override fun onChildViewRemoved(parent: View?, child: View?) {
                if (child === currentGridRow || child?.tag == gridRowTag) currentGridRow = null
            }
        })
    }

    private fun placeInGrid(list: LinearLayout, child: View) {
        if (!isCoverCard(child)) {
            list.addView(
                child,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            return
        }

        styleCoverCard(child as LinearLayout)
        var row = currentGridRow
        if (row == null || row.childCount >= gridColumns) {
            row = LinearLayout(this).apply {
                tag = gridRowTag
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                clipChildren = false
                clipToPadding = false
            }
            list.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            currentGridRow = row
        }

        row.addView(
            child,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(5))
            },
        )
    }

    private fun restyleVisibleCards(list: LinearLayout) {
        forEachView(list) { view ->
            if (view is LinearLayout && isCoverCard(view)) styleCoverCard(view)
        }
    }

    private fun isCoverCard(view: View): Boolean {
        val card = view as? ViewGroup ?: return false
        if (card.tag == gridRowTag || card.childCount == 0) return false
        val header = card.getChildAt(0) as? ViewGroup ?: return false
        return (0 until header.childCount).any { header.getChildAt(it) is CheckBox }
    }

    private fun styleCoverCard(card: LinearLayout) {
        card.background = rounded(surface, 15, border, 1)
        card.setPadding(dp(7), dp(7), dp(7), dp(8))
        card.elevation = dp(2).toFloat()
        card.clipToPadding = false

        val header = card.getChildAt(0) as? LinearLayout
        if (header != null) {
            header.gravity = Gravity.CENTER_VERTICAL
            header.setPadding(0, 0, 0, dp(3))
            header.background = rounded(surfaceSoft, 10, Color.TRANSPARENT, 0)

            // Remove apenas o número ordinal (não é necessário na grade compacta).
            for (i in header.childCount - 1 downTo 0) {
                val item = header.getChildAt(i)
                if (item is TextView && item !is CheckBox && item.text?.toString()?.matches(Regex("\\d+")) == true) {
                    header.removeViewAt(i)
                }
            }

            // Quando a capa está disponível, mostre uma prévia útil também no cartão
            // recolhido. Assim a grade lembra o painel aprovado, sem expandir todos os
            // jornais e exibir oito botões de inserção manual quando ainda estão pendentes.
            var hasLargePreview = (1 until card.childCount).any { card.getChildAt(it) is ImageView }
            if (!hasLargePreview) {
                val thumbnail = (0 until header.childCount)
                    .map { header.getChildAt(it) }
                    .filterIsInstance<ImageView>()
                    .firstOrNull { it.drawable != null }
                if (thumbnail != null) {
                    header.removeView(thumbnail)
                    thumbnail.setBackground(rounded(surface, 8, border, 1))
                    thumbnail.setPadding(dp(3), dp(3), dp(3), dp(3))
                    thumbnail.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    thumbnail.adjustViewBounds = true
                    card.addView(
                        thumbnail,
                        1.coerceAtMost(card.childCount),
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(if (gridColumns == 3) 108 else 126),
                        ).apply {
                            topMargin = dp(4)
                            bottomMargin = dp(3)
                        },
                    )
                    hasLargePreview = true
                }
            }
            for (i in header.childCount - 1 downTo 0) {
                val child = header.getChildAt(i)
                if (child is ImageView && hasLargePreview) {
                    header.removeViewAt(i)
                } else if (child is ImageView) {
                    child.setBackground(rounded(surface, 6, border, 1))
                    child.setPadding(dp(2), dp(2), dp(2), dp(2))
                    child.scaleType = ImageView.ScaleType.CENTER_CROP
                    child.layoutParams = child.layoutParams.apply {
                        width = dp(if (gridColumns == 3) 34 else 42)
                        height = dp(if (gridColumns == 3) 48 else 56)
                    }
                }
            }

            for (i in 0 until header.childCount) {
                val child = header.getChildAt(i)
                when (child) {
                    is CheckBox -> {
                        child.setButtonTintList(ColorStateList(
                            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                            intArrayOf(navyBlue, border),
                        ))
                        // Preserva o OnCheckedChangeListener da lógica original e atualiza
                        // apenas o resumo visual quando o usuário toca no checkbox.
                        child.setOnClickListener { updateSummaryCounts() }
                        child.setPadding(0, 0, 0, 0)
                        child.layoutParams = child.layoutParams.apply {
                            width = dp(if (gridColumns == 3) 27 else 30)
                            height = dp(36)
                        }
                    }
                    is Button -> {
                        val label = child.text?.toString().orEmpty()
                        if (label == "▲" || label == "▼") {
                            child.setTextColor(navy)
                            child.setTextSize(10.5f)
                            child.setPadding(0, 0, 0, 0)
                            child.background = rounded(surface, 8, border, 1)
                            child.layoutParams = child.layoutParams.apply {
                                width = dp(27)
                                height = dp(32)
                            }
                        }
                    }
                    is LinearLayout -> {
                        child.setPadding(dp(2), 0, dp(2), 0)
                        val name = child.getChildAt(0) as? TextView
                        name?.apply {
                            setTextColor(textMain)
                            setTextSize(if (gridColumns == 3) 9.5f else 11f)
                            typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            maxLines = 2
                            ellipsize = TextUtils.TruncateAt.END
                        }
                        val status = if (child.childCount > 1) child.getChildAt(1) as? TextView else null
                        status?.let { styleStatus(it, card) }
                    }
                }
            }
        }

        for (i in 1 until card.childCount) {
            val child = card.getChildAt(i)
            when (child) {
                is ViewGroup -> {
                    if (child is LinearLayout && child.childCount == 0) continue
                    if (child is LinearLayout && child.tag != gridRowTag) {
                        // Child containers inside each cover card stay transparent.
                        if (child.childCount > 0 && child.getChildAt(0) !is TextView) {
                            child.setBackgroundColor(Color.TRANSPARENT)
                        }
                    }
                }
                is View -> {
                    if (child !is ImageView && child !is TextView && child !is Button) {
                        child.setBackgroundColor(gold.copyAlpha(0.25f))
                    }
                }
            }

            if (child is ImageView) {
                child.setBackground(rounded(surface, 8, border, 1))
                child.setPadding(dp(2), dp(2), dp(2), dp(2))
                child.scaleType = ImageView.ScaleType.CENTER_INSIDE
                child.adjustViewBounds = true
                val lp = child.layoutParams
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = dp(if (gridColumns == 3) 112 else 132)
                child.layoutParams = lp
            } else if (child is Button) {
                val label = child.text?.toString().orEmpty().uppercase()
                child.setAllCaps(false)
                child.typeface = Typeface.DEFAULT_BOLD
                child.setTextSize(if (gridColumns == 3) 9f else 10.5f)
                child.setPadding(dp(4), 0, dp(4), 0)
                child.layoutParams = child.layoutParams.apply {
                    height = dp(if (label.contains("VOLTAR PARA CAPA AUTOMÁTICA")) 32 else 35)
                    if (this is LinearLayout.LayoutParams) {
                        topMargin = dp(3)
                        bottomMargin = dp(2)
                    }
                }
                when {
                    label.contains("REVISAR CAPA") || label.contains("VISUALIZAR") -> {
                        child.text = "Visualizar"
                        styleButton(child, navy, navyBlue, Color.WHITE)
                    }
                    label.contains("INSERIR CAPA") || label.contains("TROCAR CAPA") -> {
                        child.text = "Inserir capa manual"
                        styleButton(child, goldLight, gold, navyDeep)
                    }
                    label.contains("VOLTAR PARA CAPA AUTOMÁTICA") -> {
                        child.text = "Restaurar capa automática"
                        styleButton(child, greenLight, green, green)
                    }
                    else -> styleButton(child, surface, border, textMain)
                }
            } else if (child is TextView) {
                val value = child.text?.toString().orEmpty()
                if (value.contains("Este jornal", true) || value.contains("incluído no PDF", true) || value.contains("fora do PDF", true) || value.contains("aguardando uma capa", true)) {
                    val hasCover = cardContainsPreview(card)
                    child.setTextColor(if (hasCover) green else textMuted)
                    child.setTextSize(if (gridColumns == 3) 8.5f else 9.5f)
                    child.setPadding(dp(7), dp(5), dp(7), dp(5))
                    child.background = rounded(if (hasCover) greenLight else surfaceSoft, 8, if (hasCover) green else border, 1)
                    child.maxLines = 2
                } else {
                    child.setTextColor(textMain)
                }
            }
        }
    }

    private fun styleStatus(status: TextView, card: ViewGroup) {
        val value = status.text?.toString().orEmpty()
        val upper = value.uppercase()
        val hasCover = cardContainsPreview(card)
        when {
            upper.startsWith("FALHA") || upper.contains("ERRO") || upper.contains("NENHUMA IMAGEM") -> {
                status.setTextColor(red)
                status.background = rounded(redLight, 6, Color.TRANSPARENT, 0)
            }
            hasCover || upper.contains("ENCONTR") || upper.contains("CONCLU") || upper.contains("SELECIONADA MANUALMENTE") || upper.contains("CAPA INSERIDA MANUALMENTE") -> {
                status.setTextColor(green)
                status.background = rounded(greenLight, 6, Color.TRANSPARENT, 0)
            }
            else -> {
                val color = if (upper.contains("AGUARDANDO") || upper.contains("BUSCANDO") || upper.contains("LOCALIZANDO") || upper.contains("GMAIL") || upper.contains("SEM EDIÇÃO")) amber else textMuted
                status.setTextColor(color)
                status.background = rounded(if (color == amber) amberLight else surfaceSoft, 6, Color.TRANSPARENT, 0)
            }
        }
        status.setTextSize(if (gridColumns == 3) 8f else 9f)
        status.setPadding(dp(4), dp(2), dp(4), dp(2))
        status.maxLines = 2
        status.ellipsize = TextUtils.TruncateAt.END
    }

    private fun styleButton(button: Button, fill: Int, stroke: Int, foreground: Int) {
        button.setAllCaps(false)
        button.setTextColor(foreground)
        button.setTypeface(Typeface.DEFAULT_BOLD)
        button.background = rounded(fill, 10, stroke, if (stroke == Color.TRANSPARENT) 0 else 1)
        button.minHeight = 0
        button.minWidth = 0
        button.stateListAnimator = null
        if (android.os.Build.VERSION.SDK_INT >= 21) button.elevation = 0f
    }

    private fun updateSummaryCounts() {
        val row = summaryRow ?: return
        val entries = privateField("entries")?.let { field ->
            try { field.get(this) as? List<*> } catch (_: Exception) { null }
        }?.filterNotNull().orEmpty()

        var available = 0
        var selected = 0
        var unavailable = 0
        entries.forEach { entry ->
            val status = readField(entry, "status")?.toString().orEmpty().uppercase()
            val hasCover = readField(entry, "coverBitmap") != null
            if (readField(entry, "selected") == true) selected++
            if (hasCover) available++
            else if (status.startsWith("FALHA") || status.contains("NÃO VEIO") || status.contains("NÃO ENCONTRADO") || status.contains("NENHUMA") || status.contains("SEM EDIÇÃO") || status.contains("NÃO PÔDE")) unavailable++
        }
        val pending = (entries.size - available - unavailable).coerceAtLeast(0)
        mapOf(
            "total" to entries.size,
            "available" to available,
            "pending" to pending,
            "selected" to selected,
        ).forEach { (key, value) ->
            row.findViewWithTag<TextView>("covers-summary-value:$key")?.text = value.toString()
        }
    }

    private fun styleSelectedDockItem(shell: ViewGroup) {
        val navScroll = findByDescription(shell, "Navegação das áreas da Central") as? ViewGroup ?: return
        val row = navScroll.getChildAt(0) as? ViewGroup ?: return
        val unselectedLabel = Color.rgb(218, 229, 241)
        val unselectedIcon = Color.rgb(220, 231, 243)

        // ModernMainActivity estiliza genericamente todo TextView após abrir/voltar
        // da atividade. Reforçamos contraste em TODOS os itens do dock, não apenas
        // no selecionado, para que os rótulos não fiquem azul-escuros sobre o navy.
        var selectedItem: LinearLayout? = null
        for (i in 0 until row.childCount) {
            val item = row.getChildAt(i) as? LinearLayout ?: continue
            val description = item.contentDescription?.toString().orEmpty()
            if (description.isBlank()) continue // ignora os espaços laterais do scroll
            val selected = description.contains("selecionada", true)
            if (description.contains("Capas", true) && selected) selectedItem = item

            item.background = if (selected) {
                rounded(Color.argb(42, Color.red(gold), Color.green(gold), Color.blue(gold)), 16, gold, 1)
            } else {
                rounded(Color.TRANSPARENT, 16, Color.TRANSPARENT, 0)
            }
            item.elevation = if (selected) dp(1).toFloat() else 0f

            val iconBox = item.getChildAt(0) as? LinearLayout
            if (iconBox != null) {
                iconBox.background = if (selected) {
                    GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(gold, goldDeep),
                    ).apply {
                        cornerRadius = dp(13).toFloat()
                        setStroke(dp(1), Color.argb(100, 255, 255, 255))
                    }
                } else {
                    rounded(Color.argb(24, 255, 255, 255), 13, Color.argb(42, 255, 255, 255), 1)
                }
                for (j in 0 until iconBox.childCount) {
                    val icon = iconBox.getChildAt(j)
                    if (icon is ImageView) icon.setColorFilter(if (selected) navyDeep else unselectedIcon)
                }
            }

            for (j in 1 until item.childCount) {
                val child = item.getChildAt(j)
                if (child is TextView) {
                    child.setTextColor(if (selected) gold else unselectedLabel)
                    child.typeface = Typeface.create("sans-serif", if (selected) Typeface.BOLD else Typeface.NORMAL)
                } else if (selected && j == item.childCount - 1) {
                    child.background = rounded(gold, 3, Color.TRANSPARENT, 0)
                }
            }
        }

        // O dock real é o pai do HorizontalScrollView. O item Capas é centralizado
        // após a medida do layout, corrigindo a primeira abertura quando a largura
        // ainda era zero no primeiro post do scaffold.
        val dock = navScroll.parent as? LinearLayout
        dock?.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(navyBlue, navyDeep),
        ).apply {
            cornerRadius = dp(19).toFloat()
            setStroke(dp(1), Color.rgb(47, 73, 99))
        }

        selectedItem?.let { active ->
            navScroll.post {
                navScroll.postOnAnimation {
                    if (navScroll.width > 0 && active.width > 0) {
                        val target = (active.left + active.width / 2 - navScroll.width / 2)
                            .coerceAtLeast(0)
                        navScroll.scrollTo(target, 0)
                    }
                }
            }
        }
    }

    private fun privateField(name: String): Field? {
        var type: Class<*>? = javaClass
        while (type != null) {
            try {
                return type.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        return null
    }

    private fun readField(instance: Any, name: String): Any? {
        return try {
            val field = findField(instance.javaClass, name) ?: return null
            field.isAccessible = true
            field.get(instance)
        } catch (_: Exception) { null }
    }

    private fun findField(type0: Class<*>, name: String): Field? {
        var type: Class<*>? = type0
        while (type != null) {
            try { return type.getDeclaredField(name) } catch (_: NoSuchFieldException) { type = type.superclass }
        }
        return null
    }

    private fun findFirstScrollView(view: View): ScrollView? {
        if (view is ScrollView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findFirstScrollView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun findTextViewContaining(view: View, phrase: String): TextView? {
        if (view is TextView && view.text?.toString()?.contains(phrase, true) == true) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findTextViewContaining(view.getChildAt(i), phrase)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findAllViews(root: View): List<View> {
        val result = mutableListOf<View>()
        forEachView(root) { result.add(it) }
        return result
    }

    private fun forEachView(root: View, action: (View) -> Unit) {
        action(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) forEachView(root.getChildAt(i), action)
    }

    private fun findByDescription(root: View, description: String): View? {
        if (root.contentDescription?.toString() == description) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findByDescription(root.getChildAt(i), description)
                if (found != null) return found
            }
        }
        return null
    }

    private fun ViewGroup.childrenDepthFirst(): Sequence<View> = sequence {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            yield(child)
            if (child is ViewGroup) yieldAll(child.childrenDepthFirst())
        }
    }

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int, strokeWidthDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidthDp > 0) setStroke(dp(strokeWidthDp), stroke)
        }

    private fun gradient(start: Int, end: Int, radiusDp: Int, stroke: Int, strokeWidthDp: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidthDp > 0) setStroke(dp(strokeWidthDp), stroke)
        }

    private fun cardContainsPreview(card: ViewGroup): Boolean =
        findAllViews(card).any { it is ImageView && it.drawable != null }

    private fun Int.copyAlpha(factor: Float): Int = Color.argb(
        (Color.alpha(this) * factor).toInt().coerceIn(0, 255),
        Color.red(this), Color.green(this), Color.blue(this),
    )
}
