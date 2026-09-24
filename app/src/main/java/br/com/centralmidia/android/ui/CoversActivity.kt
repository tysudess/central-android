package br.com.centralmidia.android.ui

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CatalogRepository
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.CoverResolver
import br.com.centralmidia.android.core.CoversClient
import br.com.centralmidia.android.core.Newspaper
import br.com.centralmidia.android.core.dp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CoversActivity : BaseActivity() {
    data class PaperState(
        val paper: Newspaper,
        var selected: Boolean = true,
        var status: String = "Aguardando",
        var candidates: MutableList<CoverResolver.Candidate> = mutableListOf(),
        var chosenIndex: Int = -1,
        var automaticIndex: Int = -1,
        var manualUri: Uri? = null,
    )

    private val client by lazy { CoversClient(this) }
    private val resolver by lazy { CoverResolver(this) }
    private val states = mutableListOf<PaperState>()
    private val matters = mutableMapOf<String, List<String>>()
    private var selectedPaper = 0
    private var previewCandidate = 0
    private var lastPdf: Uri? = null
    private var date = Calendar.getInstance()

    private lateinit var dateText: TextView
    private lateinit var status: TextView
    private lateinit var papersList: LinearLayout
    private lateinit var title: TextView
    private lateinit var paperStatus: TextView
    private lateinit var preview: ImageView
    private lateinit var candidateLabel: TextView
    private lateinit var useButton: com.google.android.material.button.MaterialButton

    private val manualPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val state = states.getOrNull(selectedPaper) ?: return@registerForActivityResult
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            state.manualUri = uri
            state.status = "Capa inserida manualmente"
            refreshPapers()
            refreshPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CatalogRepository.newspapers(this).forEach { states += PaperState(it) }

        val root = MobileScaffold.page(
            this,
            "Principais Capas",
            "Busca automática, revisão manual e PDF otimizado.",
            MobileScaffold.Tab.MORE,
            showAutomation = true,
        )
        root.addView(headerCard(), MobileUi.match(dp(14)))
        root.addView(actionsCard(), MobileUi.match(dp(10)))
        root.addView(papersCard(), MobileUi.match(dp(10)))
        root.addView(previewCard(), MobileUi.match(dp(10)))
        root.addView(statusCard(), MobileUi.match(dp(10)))
        refreshDate()
        refreshPapers()
        refreshPreview()
    }

    private fun headerCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(MobileUi.BLUE_TINT, dp(13).toFloat())
            addView(MobileUi.icon(this@CoversActivity, R.drawable.ic_news, MobileUi.BLUE, 24))
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(10) })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(MobileUi.text(this@CoversActivity, "PRINCIPAIS CAPAS", 17f, MobileUi.NAVY, true))
        copy.addView(
            MobileUi.text(
                this@CoversActivity,
                "Android • busca automática • revisão manual • PDF",
                9.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(3)),
        )
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dateText = MobileUi.text(this, "", 10.5f, MobileUi.NAVY, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = MobileUi.rounded(Color.WHITE, dp(10).toFloat(), MobileUi.BORDER, dp(1))
            isClickable = true
            setOnClickListener { chooseDate() }
        }
        row.addView(dateText)
        box.addView(row)

        box.addView(
            MobileUi.button(this, "Apps Script / Gmail", false, MobileUi.BLUE, R.drawable.ic_open) {
                fetchBridge()
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                topMargin = dp(8)
            },
        )
        card.addView(box)
        return card
    }

    private fun actionsCard(): View {
        val card = MobileUi.card(this, 10)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val a = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        a.addView(
            MobileUi.button(this, "ATUALIZAR CAPAS", true, MobileUi.BLUE, R.drawable.ic_search) {
                updateCovers()
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) },
        )
        a.addView(
            MobileUi.button(this, "GERAR PDF", true, MobileUi.BLUE, R.drawable.ic_news) {
                generatePdf()
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) },
        )
        box.addView(a)

        val b = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        b.addView(
            MobileUi.button(this, "Abrir PDF", false, MobileUi.NAVY, R.drawable.ic_open) {
                openPdf()
            },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(3) },
        )
        b.addView(
            MobileUi.button(this, "Marcar todas") {
                states.forEach { it.selected = true }
                refreshPapers()
            },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            },
        )
        b.addView(
            MobileUi.button(this, "Desmarcar", false, MobileUi.PINK) {
                states.forEach { it.selected = false }
                refreshPapers()
            },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(3) },
        )
        box.addView(b, MobileUi.match(dp(7)))
        card.addView(box)
        return card
    }

    private fun papersCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(
            MobileUi.text(
                this,
                "JORNAIS (" + states.size + ")",
                15f,
                MobileUi.NAVY,
                true,
            ),
        )
        papersList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(papersList, MobileUi.match(dp(7)))
        card.addView(box)
        return card
    }

    private fun previewCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        title = MobileUi.text(this, "", 17f, MobileUi.NAVY, true)
        paperStatus = MobileUi.text(this, "Aguardando", 10f, MobileUi.MUTED)
        box.addView(title)
        box.addView(paperStatus, MobileUi.match(dp(3)))

        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(248, 251, 255))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        box.addView(
            preview,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(430)).apply {
                topMargin = dp(8)
            },
        )

        candidateLabel = MobileUi.text(this, "0 de 0", 10.5f, MobileUi.NAVY, true).apply {
            gravity = Gravity.CENTER
        }
        box.addView(candidateLabel, MobileUi.match(dp(7)))

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(
            MobileUi.button(this, "◀ Anterior") { changeCandidate(-1) },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) },
        )
        nav.addView(
            MobileUi.button(this, "Próxima ▶") { changeCandidate(1) },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) },
        )
        box.addView(nav, MobileUi.match(dp(7)))

        useButton = MobileUi.button(this, "USAR ESTA PÁGINA", true, MobileUi.BLUE) {
            chooseCandidate()
        }
        box.addView(
            useButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(8)
            },
        )
        box.addView(
            MobileUi.button(this, "INSERIR CAPA MANUALMENTE", true, MobileUi.BLUE, R.drawable.ic_add) {
                manualPicker.launch(arrayOf("image/*"))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(7)
            },
        )
        box.addView(
            MobileUi.button(this, "VOLTAR PARA AUTOMÁTICO", false, MobileUi.NAVY) {
                restoreAutomatic()
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                topMargin = dp(7)
            },
        )
        box.addView(
            MobileUi.button(this, "Abrir fonte atual", false, MobileUi.NAVY, R.drawable.ic_open) {
                openSource()
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                topMargin = dp(7)
            },
        )
        card.addView(box)
        return card
    }

    private fun statusCard(): View {
        val card = MobileUi.card(this, 11).apply {
            setCardBackgroundColor(MobileUi.GREEN_TINT)
            strokeColor = Color.rgb(188, 233, 210)
        }
        status = MobileUi.text(
            this,
            "Pronto para atualizar as capas.",
            10.5f,
            MobileUi.GREEN,
            true,
        )
        card.addView(status)
        return card
    }

    private fun chooseDate() {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                date.set(year, month, day, 0, 0, 0)
                refreshDate()
            },
            date.get(Calendar.YEAR),
            date.get(Calendar.MONTH),
            date.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun refreshDate() {
        if (::dateText.isInitialized) {
            dateText.text = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(date.time)
        }
    }

    private fun isoDate() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date.time)

    private fun fetchBridge() {
        status.text = "Consultando Apps Script / Gmail…"
        io(
            { client.matters(isoDate()) },
            { json ->
                parseMatters(json)
                val total = matters.values.sumOf { it.size }
                status.text = "Apps Script consultado: " + total + " link(s). Toque em Atualizar Capas."
            },
            { error ->
                status.text = error.message ?: "Falha ao consultar Apps Script."
            },
        )
    }

    private fun parseMatters(root: JSONObject) {
        matters.clear()
        val arr = root.optJSONArray("matters") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").uppercase()
            val urls = mutableListOf<String>()
            obj.optString("matterUrl").takeIf { it.isNotBlank() }?.let { urls += it }
            val many = obj.optJSONArray("urls")
            if (many != null) {
                for (j in 0 until many.length()) {
                    many.optString(j).takeIf { it.isNotBlank() }?.let { urls += it }
                }
            }
            if (urls.isNotEmpty()) matters[name] = urls.distinct()
        }
    }

    private fun updateCovers() {
        status.text = "Atualizando capas…"
        states.forEach {
            if (it.manualUri == null) it.status = "Buscando…"
        }
        refreshPapers()

        io(
            {
                states.map { state ->
                    if (state.manualUri != null) return@map state.candidates
                    val extra = matters[state.paper.name.uppercase()].orEmpty()
                    val urls = (extra + state.paper.urls).distinct()
                    if (state.paper.name.contains("VALOR", true)) {
                        val valor = runCatching {
                            resolver.pdfBytes(
                                state.paper.name,
                                client.valorPdf(isoDate(), 1),
                                "Valor Econômico • Página 1",
                            )
                        }.getOrNull()
                        buildList {
                            if (valor != null) add(valor)
                            addAll(resolver.resolve(state.paper.name, urls, isoDate()))
                        }
                    } else {
                        resolver.resolve(state.paper.name, urls, isoDate())
                    }
                }
            },
            { all ->
                all.forEachIndexed { index, found ->
                    val state = states[index]
                    if (state.manualUri == null) {
                        state.candidates = found.toMutableList()
                        state.chosenIndex = if (found.isNotEmpty()) 0 else -1
                        state.automaticIndex = state.chosenIndex
                        state.status =
                            if (found.isNotEmpty()) "Capa encontrada"
                            else "Nenhuma candidata encontrada"
                    }
                }
                status.text = "Atualização concluída. Revise as capas e gere o PDF."
                previewCandidate = 0
                refreshPapers()
                refreshPreview()
            },
            { error ->
                status.text = error.message ?: "Falha ao atualizar capas."
                states.forEach {
                    if (it.status == "Buscando…") it.status = "Falha"
                }
                refreshPapers()
            },
        )
    }

    private fun refreshPapers() {
        if (!::papersList.isInitialized) return
        papersList.removeAllViews()
        states.forEachIndexed { index, state ->
            val active = index == selectedPaper
            val item = MobileUi.card(this, 9).apply {
                strokeColor = if (active) MobileUi.BLUE else MobileUi.BORDER
                strokeWidth = if (active) dp(2) else dp(1)
                isClickable = true
                setOnClickListener {
                    selectedPaper = index
                    previewCandidate = state.chosenIndex.coerceAtLeast(0)
                    refreshPapers()
                    refreshPreview()
                }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val check = CheckBox(this).apply {
                isChecked = state.selected
                setOnCheckedChangeListener { _, checked -> state.selected = checked }
            }
            row.addView(check)
            row.addView(
                MobileUi.text(this, (index + 1).toString(), 12f, MobileUi.BLUE, true),
                LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            copy.addView(MobileUi.text(this, state.paper.name, 12.5f, MobileUi.NAVY, true))
            copy.addView(MobileUi.text(this, state.status, 9.5f, MobileUi.MUTED), MobileUi.match(dp(3)))
            row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            item.addView(row)
            papersList.addView(item, MobileUi.match(dp(5)))
        }
    }

    private fun refreshPreview() {
        if (!::preview.isInitialized || states.isEmpty()) return
        selectedPaper = selectedPaper.coerceIn(0, states.lastIndex)
        val state = states[selectedPaper]
        title.text = state.paper.name
        paperStatus.text = state.status

        if (state.manualUri != null) {
            preview.setImageURI(state.manualUri)
            candidateLabel.text = "Capa manual"
            useButton.isEnabled = false
            return
        }

        if (state.candidates.isEmpty()) {
            preview.setImageDrawable(null)
            candidateLabel.text = "0 de 0"
            useButton.isEnabled = false
            return
        }

        previewCandidate = previewCandidate.coerceIn(0, state.candidates.lastIndex)
        val candidate = state.candidates[previewCandidate]
        preview.setImageBitmap(BitmapFactory.decodeFile(candidate.file.absolutePath))
        candidateLabel.text =
            (previewCandidate + 1).toString() + " de " + state.candidates.size + " • " + candidate.label
        useButton.isEnabled = true
    }

    private fun changeCandidate(delta: Int) {
        val state = states.getOrNull(selectedPaper) ?: return
        if (state.manualUri != null || state.candidates.isEmpty()) return
        previewCandidate = (previewCandidate + delta).coerceIn(0, state.candidates.lastIndex)
        refreshPreview()
    }

    private fun chooseCandidate() {
        val state = states.getOrNull(selectedPaper) ?: return
        if (previewCandidate !in state.candidates.indices) return
        state.manualUri = null
        state.chosenIndex = previewCandidate
        state.status = "Página selecionada manualmente"
        refreshPapers()
        refreshPreview()
    }

    private fun restoreAutomatic() {
        val state = states.getOrNull(selectedPaper) ?: return
        state.manualUri = null
        state.chosenIndex = state.automaticIndex
        state.status =
            if (state.chosenIndex in state.candidates.indices) "Seleção automática restaurada"
            else "Aguardando"
        previewCandidate = state.chosenIndex.coerceAtLeast(0)
        refreshPapers()
        refreshPreview()
    }

    private fun openSource() {
        val state = states.getOrNull(selectedPaper) ?: return
        val source =
            state.candidates.getOrNull(previewCandidate)?.sourceUrl
                ?: state.paper.urls.firstOrNull()
        if (!source.isNullOrBlank() && source.startsWith("http")) {
            openUrl(source)
        } else {
            toast("Fonte não disponível.")
        }
    }

    private fun generatePdf() {
        val chosen = states.filter { it.selected }.mapNotNull { state ->
            when {
                state.manualUri != null ->
                    state.paper.name to state.manualUri
                state.chosenIndex in state.candidates.indices ->
                    state.paper.name to Uri.fromFile(state.candidates[state.chosenIndex].file)
                else -> null
            }
        }
        if (chosen.isEmpty()) {
            toast("Nenhuma capa selecionada e disponível.")
            return
        }

        status.text = "Gerando PDF das capas…"
        io(
            {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, pdfName())
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/Central Inteligente de Midia/Capas")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Não foi possível criar o PDF.")
                contentResolver.openOutputStream(uri)?.use { out ->
                    val doc = PdfDocument()
                    try {
                        var pageNo = 1
                        runCatching {
                            assets.open("principais_capas_cover.png").use {
                                BitmapFactory.decodeStream(it)
                            }
                        }.getOrNull()?.let { bitmap ->
                            pageNo = addPage(doc, bitmap, pageNo)
                            bitmap.recycle()
                        }

                        chosen.forEach { pair ->
                            val itemUri = pair.second ?: return@forEach
                            val bitmap =
                                if (itemUri.scheme == "file") {
                                    BitmapFactory.decodeFile(itemUri.path)
                                } else {
                                    contentResolver.openInputStream(itemUri)?.use {
                                        BitmapFactory.decodeStream(it)
                                    }
                                }
                            if (bitmap != null) {
                                pageNo = addPage(doc, bitmap, pageNo)
                                bitmap.recycle()
                            }
                        }
                        doc.writeTo(out)
                    } finally {
                        doc.close()
                    }
                } ?: error("Não foi possível abrir a saída.")
                uri
            },
            { uri ->
                lastPdf = uri
                status.text = "PDF gerado com " + chosen.size + " capa(s)."
                CentralDb(this).addHistory(
                    "cover",
                    "Principais Capas • " + isoDate(),
                    chosen.size.toString() + " capa(s)",
                    uri.toString(),
                )
                toast("PDF salvo em Downloads/Central Inteligente de Midia/Capas.")
            },
            { error ->
                status.text = error.message ?: "Falha ao gerar PDF."
            },
        )
    }

    private fun addPage(
        doc: PdfDocument,
        bitmap: android.graphics.Bitmap,
        number: Int,
    ): Int {
        val width = 1600
        val height =
            (bitmap.height * width.toFloat() / bitmap.width.toFloat()).toInt().coerceAtLeast(1)
        val info = PdfDocument.PageInfo.Builder(width, height, number).create()
        val page = doc.startPage(info)
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawBitmap(
            bitmap,
            null,
            Rect(0, 0, width, height),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        doc.finishPage(page)
        return number + 1
    }

    private fun pdfName(): String {
        val months = arrayOf(
            "JAN", "FEV", "MAR", "ABR", "MAI", "JUN",
            "JUL", "AGO", "SET", "OUT", "NOV", "DEZ",
        )
        val day = date.get(Calendar.DAY_OF_MONTH)
        val month = date.get(Calendar.MONTH)
        return "%02d%s - PRINCIPAIS CAPAS.pdf".format(day, months[month])
    }

    private fun openPdf() {
        val uri = lastPdf ?: run {
            toast("Gere um PDF primeiro.")
            return
        }
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }.onFailure {
            toast("Não há visualizador de PDF disponível.")
        }
    }
}
