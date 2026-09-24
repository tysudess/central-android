package br.com.centralmidia.android.ui

import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.dp
import br.com.centralmidia.android.pdf.PdfComposer
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PdfEditorActivity : BaseActivity() {
    private val items = mutableListOf<PdfComposer.Item>()
    private var cover: Uri? = null
    private var selected = -1
    private lateinit var includeDefaultCover: CheckBox
    private lateinit var list: LinearLayout
    private lateinit var count: TextView
    private lateinit var status: TextView

    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            items += PdfComposer.Item(
                uri = uri,
                label = uri.lastPathSegment ?: "Arquivo",
            )
        }
        if (selected !in items.indices && items.isNotEmpty()) selected = 0
        refresh()
    }

    private val coverPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            cover = uri
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = MobileScaffold.page(
            this,
            "Editor PDF",
            "Organize PDFs e imagens, corte, redimensione, ordene e exporte.",
            MobileScaffold.Tab.MORE,
            showAutomation = true,
        )
        root.addView(toolCard(), MobileUi.match(dp(14)))
        root.addView(documentCard(), MobileUi.match(dp(10)))
        root.addView(coverCard(), MobileUi.match(dp(10)))
        root.addView(exportCard(), MobileUi.match(dp(10)))
        refresh()
    }

    private fun toolCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(MobileUi.text(this, "Ferramentas PDF", 18f, MobileUi.NAVY, true))
        box.addView(
            MobileUi.text(
                this,
                "No Android, toque em Arquivos para adicionar PDFs e imagens e selecione uma linha para editar.",
                10f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(4)),
        )

        val a = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        a.addView(tool("Arquivos", R.drawable.ic_add, MobileUi.BLUE) { picker.launch(arrayOf("image/*", "application/pdf")) })
        a.addView(tool("Girar", R.drawable.ic_more, MobileUi.BLUE) { rotate() })
        a.addView(tool("Cortar", R.drawable.ic_more, MobileUi.PURPLE) { crop() })
        box.addView(a, MobileUi.match(dp(9)))

        val b = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        b.addView(tool("Redimensionar", R.drawable.ic_more, MobileUi.PINK) { resize() })
        b.addView(tool("Criar", R.drawable.ic_add, MobileUi.BLUE) { createBlank() })
        b.addView(tool("Excluir", R.drawable.ic_delete, MobileUi.PINK) { deleteSelected() })
        box.addView(b, MobileUi.match(dp(7)))

        val c = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        c.addView(tool("↑ Ordenar", R.drawable.ic_more, MobileUi.NAVY) { move(-1) })
        c.addView(tool("↓ Ordenar", R.drawable.ic_more, MobileUi.NAVY) { move(1) })
        c.addView(tool("Limpar", R.drawable.ic_delete, MobileUi.PINK) {
            items.clear()
            selected = -1
            refresh()
        })
        box.addView(c, MobileUi.match(dp(7)))
        card.addView(box)
        return card
    }

    private fun tool(
        label: String,
        icon: Int,
        accent: Int,
        action: () -> Unit,
    ): View = MobileUi.button(this, label, false, accent, icon, action).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginEnd = dp(4)
        }
    }

    private fun documentCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            MobileUi.text(this, "Visualização do documento", 18f, MobileUi.NAVY, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        count = MobileUi.text(this, "0 item(ns)", 10f, MobileUi.MUTED)
        head.addView(count)
        box.addView(head)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(list, MobileUi.match(dp(8)))
        card.addView(box)
        return card
    }

    private fun coverCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(MobileUi.text(this, "Capa e Exportação", 18f, MobileUi.NAVY, true))
        includeDefaultCover = CheckBox(this).apply {
            text = "Incluir capa padrão"
            setTextColor(MobileUi.NAVY)
            isChecked = true
            setOnCheckedChangeListener { _, _ -> refreshStatus() }
        }
        box.addView(includeDefaultCover, MobileUi.match(dp(7)))
        box.addView(
            MobileUi.button(this, "Trocar capa personalizada", false, MobileUi.NAVY, R.drawable.ic_open) {
                coverPicker.launch(arrayOf("image/*"))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(6) },
        )
        box.addView(
            MobileUi.button(this, "Remover capa personalizada", false, MobileUi.PINK, R.drawable.ic_delete) {
                cover = null
                refresh()
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(6) },
        )
        card.addView(box)
        return card
    }

    private fun exportCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = MobileUi.text(this, "Nenhum arquivo adicionado.", 10.5f, MobileUi.MUTED)
        box.addView(status)
        box.addView(
            MobileUi.button(this, "GERAR PDF", true, MobileUi.GREEN, R.drawable.ic_news) { generate() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(9) },
        )
        box.addView(
            MobileUi.button(this, "Abrir PDFs gerados", false, MobileUi.NAVY, R.drawable.ic_open) {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, MediaStore.Downloads.EXTERNAL_CONTENT_URI))
                }.onFailure {
                    toast("Abra Downloads/Central Inteligente de Midia/PDF no app Arquivos.")
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(7) },
        )
        card.addView(box)
        return card
    }

    private fun refresh() {
        if (!::list.isInitialized) return
        count.text = items.size.toString() + " item(ns)"
        list.removeAllViews()
        if (items.isEmpty()) {
            list.addView(
                MobileUi.text(
                    this,
                    "Adicione PDFs ou imagens para montar o documento.",
                    11f,
                    MobileUi.MUTED,
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(26), dp(8), dp(26))
                },
            )
        } else {
            items.forEachIndexed { index, item ->
                val active = index == selected
                val row = MobileUi.card(this, 10).apply {
                    strokeColor = if (active) MobileUi.BLUE else MobileUi.BORDER
                    strokeWidth = if (active) dp(2) else dp(1)
                    isClickable = true
                    setOnClickListener {
                        selected = index
                        refresh()
                    }
                }
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val label = item.label.ifBlank {
                    if (item.blank) "Página em branco"
                    else item.uri?.lastPathSegment ?: "Página"
                }
                box.addView(
                    MobileUi.text(this, (index + 1).toString() + ". " + label, 12f, MobileUi.NAVY, true),
                )
                box.addView(
                    MobileUi.text(
                        this,
                        "Rotação " + item.rotation + "° • Escala " + item.scalePercent +
                            "% • Corte L" + item.cropLeftPct + "/T" + item.cropTopPct +
                            "/R" + item.cropRightPct + "/B" + item.cropBottomPct + "%",
                        9.5f,
                        MobileUi.MUTED,
                    ),
                    MobileUi.match(dp(3)),
                )
                row.addView(box)
                list.addView(row, MobileUi.match(dp(6)))
            }
        }
        refreshStatus()
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        status.text =
            items.size.toString() + " item(ns) • capa padrão: " +
            (if (includeDefaultCover.isChecked) "sim" else "não") +
            " • capa personalizada: " + (if (cover != null) "sim" else "não")
    }

    private fun current() = items.getOrNull(selected)

    private fun rotate() {
        val item = current() ?: run {
            toast("Selecione uma página.")
            return
        }
        item.rotation = (item.rotation + 90) % 360
        refresh()
    }

    private fun crop() {
        val item = current() ?: run {
            toast("Selecione uma página.")
            return
        }
        if (item.blank) {
            toast("Página em branco não precisa de corte.")
            return
        }
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), 0)
        }
        val fields = listOf(
            MobileUi.input(this, "Esquerda %").apply { setText(item.cropLeftPct.toString()) },
            MobileUi.input(this, "Topo %").apply { setText(item.cropTopPct.toString()) },
            MobileUi.input(this, "Direita %").apply { setText(item.cropRightPct.toString()) },
            MobileUi.input(this, "Base %").apply { setText(item.cropBottomPct.toString()) },
        )
        fields.forEach {
            holder.addView(
                it,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) },
            )
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Cortar página")
            .setMessage("Informe o percentual removido de cada lado (0 a 45).")
            .setView(holder)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar") { _, _ ->
                item.cropLeftPct = fields[0].text?.toString()?.toIntOrNull()?.coerceIn(0, 45) ?: 0
                item.cropTopPct = fields[1].text?.toString()?.toIntOrNull()?.coerceIn(0, 45) ?: 0
                item.cropRightPct = fields[2].text?.toString()?.toIntOrNull()?.coerceIn(0, 45) ?: 0
                item.cropBottomPct = fields[3].text?.toString()?.toIntOrNull()?.coerceIn(0, 45) ?: 0
                refresh()
            }
            .show()
    }

    private fun resize() {
        val item = current() ?: run {
            toast("Selecione uma página.")
            return
        }
        val values = arrayOf("50%", "75%", "100%", "125%", "150%", "200%")
        MaterialAlertDialogBuilder(this)
            .setTitle("Redimensionar conteúdo")
            .setItems(values) { dialog, which ->
                item.scalePercent = values[which].removeSuffix("%").toInt()
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun createBlank() {
        items += PdfComposer.Item(blank = true, label = "Página em branco")
        selected = items.lastIndex
        refresh()
    }

    private fun deleteSelected() {
        if (selected !in items.indices) return
        items.removeAt(selected)
        selected = selected.coerceAtMost(items.lastIndex)
        refresh()
    }

    private fun move(delta: Int) {
        if (selected !in items.indices) return
        val target = selected + delta
        if (target !in items.indices) return
        val item = items.removeAt(selected)
        items.add(target, item)
        selected = target
        refresh()
    }

    private fun generate() {
        if (items.isEmpty() && cover == null && !includeDefaultCover.isChecked) {
            toast("Adicione páginas ou habilite uma capa.")
            return
        }
        status.text = "Gerando PDF…"
        io(
            {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "Central-PDF-" + System.currentTimeMillis() + ".pdf")
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/Central Inteligente de Midia/PDF")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Não foi possível criar o PDF.")
                contentResolver.openOutputStream(uri)?.use { out ->
                    PdfComposer.compose(
                        this,
                        items,
                        cover,
                        includeDefaultCover.isChecked,
                        out,
                    )
                } ?: error("Não foi possível abrir a saída.")
                uri
            },
            { uri ->
                status.text = "PDF gerado em Downloads/Central Inteligente de Midia/PDF."
                CentralDb(this).addHistory("pdf", "PDF gerado", items.size.toString() + " item(ns)", uri.toString())
                toast("PDF gerado com sucesso.")
            },
            { error ->
                status.text = error.message ?: "Falha ao gerar PDF."
            },
        )
    }
}
