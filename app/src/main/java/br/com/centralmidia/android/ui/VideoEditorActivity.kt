package br.com.centralmidia.android.ui

import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.dp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class VideoEditorActivity : BaseActivity() {
    data class Clip(
        val uri: Uri,
        val name: String,
        val durationMs: Long,
        var startMs: Long = 0L,
        var endMs: Long = durationMs,
    )

    private val clips = mutableListOf<Clip>()
    private var selected = -1
    private lateinit var preview: VideoView
    private lateinit var selectedText: TextView
    private lateinit var timeline: LinearLayout
    private lateinit var startInput: android.widget.EditText
    private lateinit var endInput: android.widget.EditText
    private lateinit var resolution: Spinner
    private lateinit var codec: Spinner
    private lateinit var outputName: android.widget.EditText
    private lateinit var progress: ProgressBar
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
            clips += clipInfo(uri)
        }
        if (selected !in clips.indices && clips.isNotEmpty()) selected = 0
        refreshTimeline()
        loadSelected()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = MobileScaffold.page(
            this,
            "Editor de Vídeo",
            "Pré-visualização, timeline, cortes, ordem e exportação em MP4.",
            MobileScaffold.Tab.MORE,
            showAutomation = true,
        )
        root.addView(sourceCard(), MobileUi.match(dp(14)))
        root.addView(previewCard(), MobileUi.match(dp(10)))
        root.addView(trimCard(), MobileUi.match(dp(10)))
        root.addView(timelineCard(), MobileUi.match(dp(10)))
        root.addView(exportCard(), MobileUi.match(dp(10)))
        refreshTimeline()
        refreshTrim()
    }

    override fun onPause() {
        super.onPause()
        if (::preview.isInitialized && preview.isPlaying) preview.pause()
    }

    private fun sourceCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(MobileUi.icon(this, R.drawable.ic_video, MobileUi.PURPLE, 25))
        row.addView(
            MobileUi.text(this, "Projeto de vídeo", 18f, MobileUi.NAVY, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            },
        )
        row.addView(
            MobileUi.button(this, "Adicionar", true, MobileUi.BLUE, R.drawable.ic_add) {
                picker.launch(arrayOf("video/*"))
            },
        )
        box.addView(row)
        selectedText = MobileUi.text(this, "Nenhum vídeo selecionado", 10.5f, MobileUi.MUTED)
        box.addView(selectedText, MobileUi.match(dp(6)))
        card.addView(box)
        return card
    }

    private fun previewCard(): View {
        val card = MobileUi.card(this, 10)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        preview = VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        box.addView(
            preview,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)),
        )
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(
            MobileUi.button(this, "-5s") { seekBy(-5000) },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(3) },
        )
        actions.addView(
            MobileUi.button(this, "Reproduzir", true, MobileUi.BLUE, R.drawable.ic_video) {
                if (selected !in clips.indices) return@button
                if (preview.isPlaying) preview.pause() else preview.start()
            },
            LinearLayout.LayoutParams(0, dp(44), 2f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            },
        )
        actions.addView(
            MobileUi.button(this, "+5s") { seekBy(5000) },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(3) },
        )
        box.addView(actions, MobileUi.match(dp(8)))
        card.addView(box)
        return card
    }

    private fun trimCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(MobileUi.text(this, "Ajustar trecho", 17f, MobileUi.NAVY, true))

        val marks = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        marks.addView(
            MobileUi.button(this, "Início aqui") {
                val c = currentClip() ?: return@button
                c.startMs = preview.currentPosition.toLong().coerceAtMost(c.endMs)
                refreshTrim()
                refreshTimeline()
            },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) },
        )
        marks.addView(
            MobileUi.button(this, "Fim aqui") {
                val c = currentClip() ?: return@button
                c.endMs = preview.currentPosition.toLong().coerceAtLeast(c.startMs)
                refreshTrim()
                refreshTimeline()
            },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) },
        )
        box.addView(marks, MobileUi.match(dp(8)))

        startInput = MobileUi.input(this, "Início em segundos")
        endInput = MobileUi.input(this, "Fim em segundos")
        box.addView(startInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })
        box.addView(endInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) })
        box.addView(
            MobileUi.button(this, "Aplicar tempos", true, MobileUi.PURPLE) { applyTimes() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) },
        )
        card.addView(box)
        return card
    }

    private fun timelineCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            MobileUi.text(this, "Timeline", 18f, MobileUi.NAVY, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        head.addView(
            MobileUi.button(this, "Cortar no cursor", true, MobileUi.PURPLE) { splitSelected() },
        )
        box.addView(head)
        timeline = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(timeline, MobileUi.match(dp(8)))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(
            MobileUi.button(this, "Duplicar", false, MobileUi.NAVY, R.drawable.ic_copy) { duplicateSelected() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(3) },
        )
        actions.addView(
            MobileUi.button(this, "←") { moveSelected(-1) },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(3); marginEnd = dp(3) },
        )
        actions.addView(
            MobileUi.button(this, "→") { moveSelected(1) },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(3); marginEnd = dp(3) },
        )
        actions.addView(
            MobileUi.button(this, "Excluir", false, MobileUi.PINK, R.drawable.ic_delete) { deleteSelected() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(3) },
        )
        box.addView(actions, MobileUi.match(dp(8)))
        card.addView(box)
        return card
    }

    private fun exportCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(MobileUi.text(this, "Exportação", 18f, MobileUi.NAVY, true))
        outputName = MobileUi.input(this, "Nome").apply { setText("video_final.mp4") }
        box.addView(outputName, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })

        val opts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        resolution = spinner(listOf("Original", "1080p", "720p", "480p"))
        codec = spinner(listOf("H.264", "H.265 / HEVC"))
        opts.addView(resolution, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
        opts.addView(codec, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) })
        box.addView(opts, MobileUi.match(dp(8)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        box.addView(progress, MobileUi.match(dp(10)))
        status = MobileUi.text(this, "SISTEMA PRONTO • Adicione vídeos à timeline.", 10f, MobileUi.MUTED)
        box.addView(status, MobileUi.match(dp(5)))
        box.addView(
            MobileUi.button(this, "EXPORTAR", true, MobileUi.BLUE, R.drawable.ic_video) { exportProject() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(8) },
        )
        box.addView(
            MobileUi.button(this, "Abrir vídeos exportados", false, MobileUi.NAVY, R.drawable.ic_open) { openExports() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(7) },
        )
        card.addView(box)
        return card
    }

    private fun spinner(values: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@VideoEditorActivity, android.R.layout.simple_spinner_dropdown_item, values)
        background = MobileUi.rounded(Color.WHITE, dp(10).toFloat(), MobileUi.BORDER, dp(1))
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun clipInfo(uri: Uri): Clip {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(this, uri)
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1L
            Clip(uri, uri.lastPathSegment?.substringAfterLast('/') ?: "Vídeo", duration.coerceAtLeast(1L))
        } finally {
            mmr.release()
        }
    }

    private fun currentClip() = clips.getOrNull(selected)

    private fun loadSelected() {
        val c = currentClip()
        if (c == null) {
            selectedText.text = "Nenhum vídeo selecionado"
            if (::preview.isInitialized) preview.stopPlayback()
        } else {
            selectedText.text = "Selecionado: " + c.name
            preview.setVideoURI(c.uri)
            preview.seekTo(c.startMs.toInt())
        }
        refreshTrim()
    }

    private fun refreshTrim() {
        if (!::startInput.isInitialized) return
        val c = currentClip()
        startInput.setText(if (c == null) "0" else (c.startMs / 1000.0).toString())
        endInput.setText(if (c == null) "0" else (c.endMs / 1000.0).toString())
    }

    private fun refreshTimeline() {
        if (!::timeline.isInitialized) return
        timeline.removeAllViews()
        if (clips.isEmpty()) {
            timeline.addView(
                MobileUi.text(this, "Adicione vídeos para montar a timeline.", 11f, MobileUi.MUTED).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(26), dp(8), dp(26))
                },
            )
            return
        }
        clips.forEachIndexed { index, c ->
            val active = index == selected
            val item = MobileUi.card(this, 10).apply {
                strokeColor = if (active) MobileUi.BLUE else MobileUi.BORDER
                strokeWidth = if (active) dp(2) else dp(1)
                isClickable = true
                setOnClickListener {
                    selected = index
                    refreshTimeline()
                    loadSelected()
                }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(MobileUi.icon(this, R.drawable.ic_video, if (active) MobileUi.BLUE else MobileUi.PURPLE, 22))
            val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            copy.addView(MobileUi.text(this, (index + 1).toString() + ". " + c.name, 12f, MobileUi.NAVY, true))
            copy.addView(
                MobileUi.text(
                    this,
                    time(c.startMs) + " → " + time(c.endMs) + " • " + time(c.endMs - c.startMs),
                    9.5f,
                    MobileUi.MUTED,
                ),
                MobileUi.match(dp(3)),
            )
            row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(9) })
            item.addView(row)
            timeline.addView(item, MobileUi.match(dp(6)))
        }
    }

    private fun applyTimes() {
        val c = currentClip() ?: return
        val start = startInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull()?.times(1000.0)?.toLong()
        val end = endInput.text?.toString()?.replace(',', '.')?.toDoubleOrNull()?.times(1000.0)?.toLong()
        if (start == null || end == null || start < 0 || end <= start || end > c.durationMs) {
            toast("Tempos inválidos.")
            return
        }
        c.startMs = start
        c.endMs = end
        refreshTimeline()
        loadSelected()
    }

    private fun seekBy(delta: Int) {
        val c = currentClip() ?: return
        preview.seekTo((preview.currentPosition + delta).coerceIn(0, c.durationMs.toInt()))
    }

    private fun splitSelected() {
        val c = currentClip() ?: return
        val point = preview.currentPosition.toLong().coerceIn(c.startMs, c.endMs)
        if (point <= c.startMs + 250L || point >= c.endMs - 250L) {
            toast("Posicione o cursor dentro do trecho.")
            return
        }
        val right = c.copy(startMs = point)
        c.endMs = point
        clips.add(selected + 1, right)
        refreshTimeline()
    }

    private fun duplicateSelected() {
        val c = currentClip() ?: return
        clips.add(selected + 1, c.copy())
        selected += 1
        refreshTimeline()
        loadSelected()
    }

    private fun moveSelected(delta: Int) {
        if (selected !in clips.indices) return
        val target = selected + delta
        if (target !in clips.indices) return
        val c = clips.removeAt(selected)
        clips.add(target, c)
        selected = target
        refreshTimeline()
    }

    private fun deleteSelected() {
        if (selected !in clips.indices) return
        clips.removeAt(selected)
        selected = selected.coerceAtMost(clips.lastIndex)
        refreshTimeline()
        loadSelected()
    }

    private fun exportProject() {
        if (clips.isEmpty()) {
            toast("Adicione ao menos um vídeo.")
            return
        }
        val name0 = outputName.text?.toString().orEmpty().trim().ifBlank { "video_final.mp4" }
        val name = if (name0.endsWith(".mp4", true)) name0 else name0 + ".mp4"
        val res = resolution.selectedItem?.toString().orEmpty()
        val codecName = if (codec.selectedItemPosition == 1) "libx265" else "libx264"
        progress.isIndeterminate = true
        status.text = "Preparando timeline e exportação…"

        io(
            {
                val work = File(cacheDir, "editor-" + System.currentTimeMillis()).apply { mkdirs() }
                val prepared = mutableListOf<File>()
                clips.forEachIndexed { index, c ->
                    val input = copyToCache(c.uri, File(work, "in-" + index + ".bin"))
                    val out = File(work, "clip-" + index.toString().padStart(3, '0') + ".mp4")
                    val scale = when (res) {
                        "1080p" -> "-vf scale=-2:1080 "
                        "720p" -> "-vf scale=-2:720 "
                        "480p" -> "-vf scale=-2:480 "
                        else -> ""
                    }
                    val cmd =
                        "-y -ss " + (c.startMs / 1000.0) +
                        " -to " + (c.endMs / 1000.0) +
                        " -i " + q(input.absolutePath) + " " +
                        scale +
                        "-c:v " + codecName + " -crf 23 -pix_fmt yuv420p -c:a aac -b:a 128k " +
                        q(out.absolutePath)
                    val session = FFmpegKit.execute(cmd)
                    if (!ReturnCode.isSuccess(session.returnCode)) {
                        error("Falha no clipe " + (index + 1) + ": " + session.allLogsAsString.takeLast(400))
                    }
                    prepared += out
                }

                val finalFile = File(work, "final.mp4")
                if (prepared.size == 1) {
                    prepared.first().copyTo(finalFile, overwrite = true)
                } else {
                    val list = File(work, "concat.txt")
                    list.writeText(prepared.joinToString("\n") { "file '" + it.absolutePath.replace("'", "'\\''") + "'" } + "\n")
                    val session = FFmpegKit.execute(
                        "-y -f concat -safe 0 -i " + q(list.absolutePath) + " -c copy " + q(finalFile.absolutePath),
                    )
                    if (!ReturnCode.isSuccess(session.returnCode)) {
                        error("Falha ao juntar a timeline: " + session.allLogsAsString.takeLast(400))
                    }
                }
                publish(finalFile, name)
            },
            { uri ->
                progress.isIndeterminate = false
                progress.progress = 100
                status.text = "Exportação concluída: " + name
                CentralDb(this).addHistory("video_edit", name, clips.size.toString() + " clipe(s)", uri.toString())
                toast("Vídeo exportado.")
            },
            { error ->
                progress.isIndeterminate = false
                progress.progress = 0
                status.text = error.message ?: "Falha na exportação."
            },
        )
    }

    private fun copyToCache(uri: Uri, file: File): File {
        contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Não foi possível abrir o vídeo.")
        return file
    }

    private fun publish(file: File, name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Central Inteligente de Midia/Editados")
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Não foi possível criar o vídeo final.")
        contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        return uri
    }

    private fun openExports() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
        }.onFailure {
            toast("Abra o app Arquivos e acesse Filmes/Central Inteligente de Midia/Editados.")
        }
    }

    private fun q(path: String) = "'" + path.replace("'", "'\\''") + "'"

    private fun time(ms: Long): String {
        val s = ms.coerceAtLeast(0L) / 1000L
        return "%02d:%02d.%03d".format(s / 60L, s % 60L, ms.coerceAtLeast(0L) % 1000L)
    }
}
