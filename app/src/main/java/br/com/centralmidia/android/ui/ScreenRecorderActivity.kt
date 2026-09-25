package br.com.centralmidia.android.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.dp
import br.com.centralmidia.android.recording.ScreenRecordService
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecorderActivity : BaseActivity() {
    private lateinit var master: SwitchMaterial
    private lateinit var mic: CheckBox
    private lateinit var hideAfterStart: CheckBox
    private lateinit var fps: Spinner
    private lateinit var quality: Spinner
    private lateinit var countdown: Spinner
    private lateinit var areaMode: Spinner
    private lateinit var duration: TextView
    private lateinit var sourceStat: TextView
    private lateinit var stateLabel: TextView
    private lateinit var pauseButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var startButton: MaterialButton
    private lateinit var recent: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var waitingOverlayPermission = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (!waitingOverlayPermission) return@registerForActivityResult
        waitingOverlayPermission = false

        if (Settings.canDrawOverlays(this)) {
            enableWidgetService()
        } else {
            setMasterCheckedWithoutCallback(false)
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            toast("É necessário permitir que a Central apareça sobre outros aplicativos para usar o widget.")
        }
        refreshState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = MobileScaffold.page(
            this,
            "Gravador de Tela",
            "",
            MobileScaffold.Tab.MORE,
            showAutomation = false,
        )
        root.addView(masterCard(), MobileUi.match(dp(8)))
        root.addView(stats(), MobileUi.match(dp(8)))
        root.addView(config(), MobileUi.match(dp(8)))
        root.addView(controls(), MobileUi.match(dp(8)))
        root.addView(recentCard(), MobileUi.match(dp(8)))

        refreshState()
        refreshRecent()
    }

    override fun onResume() {
        super.onResume()

        if (::areaMode.isInitialized) {
            areaMode.setSelection(
                prefs.getInt(
                    KEY_AREA_MODE,
                    0,
                ).coerceIn(
                    0,
                    1,
                ),
            )
        }

        refreshState()
        refreshRecent()

        if (prefs.getBoolean(KEY_ENABLED, false) && Settings.canDrawOverlays(this)) {
            enableWidgetService()
        }
    }

    override fun onPause() {
        saveSettings()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(stateTick)
        super.onDestroy()
    }

    private fun masterCard(): View {
        val card = MobileUi.card(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(MobileUi.PINK_TINT, dp(14).toFloat())
            addView(MobileUi.text(this@ScreenRecorderActivity, "●", 27f, MobileUi.PINK, true))
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(10) })

        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(MobileUi.text(this, "Gravador de Tela", 19f, MobileUi.NAVY, true))
        copy.addView(
            MobileUi.text(this, "Ligue para abrir o controle flutuante", 9.5f, MobileUi.MUTED),
            MobileUi.match(dp(3)),
        )
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        master = SwitchMaterial(this).apply {
            isChecked = prefs.getBoolean(KEY_ENABLED, false)
            text = if (isChecked) "Ligado" else "Desligado"
            textSize = 11.5f
            setTextColor(MobileUi.NAVY)
            setOnCheckedChangeListener { _, checked -> onMasterChanged(checked) }
        }
        row.addView(master)
        card.addView(row)
        return card
    }

    private fun onMasterChanged(checked: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, checked).apply()
        master.text = if (checked) "Ligado" else "Desligado"

        if (checked) {
            saveSettings()
            ensureOverlayAndEnable()
        } else {
            disableWidgetService()
        }
        refreshState()
    }

    private fun setMasterCheckedWithoutCallback(checked: Boolean) {
        if (!::master.isInitialized) return
        master.setOnCheckedChangeListener(null)
        master.isChecked = checked
        master.text = if (checked) "Ligado" else "Desligado"
        master.setOnCheckedChangeListener { _, value -> onMasterChanged(value) }
    }

    private fun stats(): View {
        val card = MobileUi.card(this)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        duration = stat("00:00:00", "Duração")
        sourceStat = stat("Tela", "Origem")

        row.addView(duration, LinearLayout.LayoutParams(0, dp(72), 1f).apply { marginEnd = dp(3) })
        row.addView(sourceStat, LinearLayout.LayoutParams(0, dp(72), 1f).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        })
        row.addView(stat("MP4", "Formato"), LinearLayout.LayoutParams(0, dp(72), 1f).apply { marginStart = dp(3) })
        card.addView(row)
        return card
    }

    private fun stat(value: String, caption: String): TextView =
        MobileUi.text(this, "$value\n$caption", 10.5f, MobileUi.NAVY, true).apply {
            gravity = Gravity.CENTER
            background = MobileUi.rounded(
                Color.rgb(248, 251, 255),
                dp(10).toFloat(),
                MobileUi.BORDER,
                dp(1),
            )
        }

    private fun config(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(MobileUi.text(this, "Configuração da gravação", 17f, MobileUi.NAVY, true))

        areaMode = spinner(
            listOf("Tela inteira", "Área personalizada"),
            prefs.getInt(KEY_AREA_MODE, 0).coerceIn(0, 1),
        )
        fps = spinner(
            listOf("24 FPS", "30 FPS", "60 FPS"),
            prefs.getInt(KEY_FPS_INDEX, 1).coerceIn(0, 2),
        )
        quality = spinner(
            listOf("Original", "720p", "1080p"),
            prefs.getInt(KEY_QUALITY_INDEX, 0).coerceIn(0, 2),
        )
        countdown = spinner(
            listOf("0 segundos", "3 segundos", "5 segundos", "10 segundos"),
            prefs.getInt(KEY_COUNTDOWN_INDEX, 0).coerceIn(0, 3),
        )

        box.addView(label("Área de captura"), MobileUi.match(dp(8)))
        box.addView(areaMode, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(4) })
        box.addView(label("Taxa de quadros"), MobileUi.match(dp(8)))
        box.addView(fps, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(4) })
        box.addView(label("Qualidade"), MobileUi.match(dp(8)))
        box.addView(quality, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(4) })
        box.addView(label("Contagem regressiva"), MobileUi.match(dp(8)))
        box.addView(countdown, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(4) })

        mic = CheckBox(this).apply {
            text = "Gravar microfone"
            isChecked = prefs.getBoolean(KEY_MIC, false)
            setTextColor(MobileUi.NAVY)
        }
        hideAfterStart = CheckBox(this).apply {
            text = "Ocultar a Central depois de iniciar"
            isChecked = prefs.getBoolean(KEY_HIDE, true)
            setTextColor(MobileUi.NAVY)
        }
        box.addView(mic, MobileUi.match(dp(8)))
        box.addView(hideAfterStart, MobileUi.match(dp(2)))

        box.addView(
            MobileUi.text(
                this,
                "No widget, toque em ÁREA para marcar ou redefinir a região que será gravada. Depois toque em REC. O widget ficará fora da área selecionada para continuar disponível sem aparecer no vídeo final.",
                9.5f,
                MobileUi.MUTED,
            ).apply {
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = MobileUi.rounded(MobileUi.BLUE_TINT, dp(9).toFloat())
            },
            MobileUi.match(dp(7)),
        )

        card.addView(box)
        return card
    }

    private fun controls(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        stateLabel = MobileUi.statusChip(this, "DESLIGADO", MobileUi.GREEN)
        box.addView(stateLabel)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        pauseButton = MobileUi.button(this, "Pausar", false, MobileUi.ORANGE) { togglePause() }
        stopButton = MobileUi.button(this, "Parar", false, MobileUi.PINK, R.drawable.ic_delete) { stopRecording() }
        row.addView(pauseButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
        row.addView(stopButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) })
        box.addView(row, MobileUi.match(dp(9)))

        startButton = MobileUi.button(this, "INICIAR GRAVAÇÃO", true, MobileUi.PINK) { beginFromCentral() }
        box.addView(startButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(7) })

        card.addView(box)
        return card
    }

    private fun recentCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            MobileUi.text(this, "Gravações recentes", 17f, MobileUi.NAVY, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        head.addView(MobileUi.button(this, "Atualizar") { refreshRecent() })
        box.addView(head)

        recent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(recent, MobileUi.match(dp(7)))
        card.addView(box)
        return card
    }

    private fun label(value: String) = MobileUi.text(this, value, 10f, MobileUi.NAVY, true)

    private fun spinner(values: List<String>, index: Int) = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@ScreenRecorderActivity,
            android.R.layout.simple_spinner_dropdown_item,
            values,
        )
        setSelection(index)
        background = MobileUi.rounded(Color.WHITE, dp(10).toFloat(), MobileUi.BORDER, dp(1))
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun beginFromCentral() {
        if (!master.isChecked) {
            master.isChecked = true
            return
        }

        saveSettings()

        runCatching {
            startService(
                Intent(
                    this,
                    ScreenRecordService::class.java,
                )
                    .setAction(
                        ScreenRecordService.ACTION_BEGIN_CAPTURE,
                    ),
            )
        }.onFailure {
            toast(
                it.message
                    ?: "Não foi possível iniciar a seleção da gravação.",
            )
        }
    }

    private fun ensureOverlayAndEnable() {
        if (!Settings.canDrawOverlays(this)) {
            waitingOverlayPermission = true
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        enableWidgetService()
    }

    private fun enableWidgetService() {
        saveSettings()
        runCatching {
            startService(
                Intent(this, ScreenRecordService::class.java)
                    .setAction(ScreenRecordService.ACTION_ENABLE_WIDGET),
            )
        }.onFailure {
            toast(it.message ?: "Não foi possível abrir o widget.")
        }
    }

    private fun disableWidgetService() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        runCatching {
            startService(
                Intent(this, ScreenRecordService::class.java)
                    .setAction(ScreenRecordService.ACTION_DISABLE_WIDGET),
            )
        }
    }

    private fun togglePause() {
        if (!ScreenRecordService.isRecording) return
        val action = if (ScreenRecordService.isPaused) {
            ScreenRecordService.ACTION_RESUME
        } else {
            ScreenRecordService.ACTION_PAUSE
        }
        startService(Intent(this, ScreenRecordService::class.java).setAction(action))
        handler.postDelayed({ refreshState() }, 250L)
    }

    private fun stopRecording() {
        if (!ScreenRecordService.isRecording) {
            refreshState()
            return
        }
        startService(
            Intent(this, ScreenRecordService::class.java)
                .setAction(ScreenRecordService.ACTION_STOP),
        )
        handler.postDelayed({ refreshState() }, 350L)
    }

    private fun refreshState() {
        if (!::stateLabel.isInitialized) return
        handler.removeCallbacks(stateTick)

        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val active = ScreenRecordService.isRecording
        val paused = ScreenRecordService.isPaused
        val processing = ScreenRecordService.isProcessing

        stateLabel.text = when {
            !enabled -> "DESLIGADO"
            processing -> "FINALIZANDO VÍDEO…"
            !active -> "LIGADO • WIDGET ATIVO"
            paused -> "PAUSADO"
            else -> "GRAVANDO"
        }

        startButton.isEnabled = enabled && !active && !processing
        pauseButton.isEnabled = active
        stopButton.isEnabled = active
        pauseButton.text = if (paused) "Retomar" else "Pausar"

        if (::master.isInitialized && master.isChecked != enabled) {
            setMasterCheckedWithoutCallback(enabled)
        }

        sourceStat.text = if (prefs.getInt(KEY_AREA_MODE, 0) == 1) "Área\nOrigem" else "Tela\nOrigem"
        updateDuration()

        if (active || processing) {
            handler.postDelayed(stateTick, 500L)
        } else {
            refreshRecent()
        }
    }

    private val stateTick = object : Runnable {
        override fun run() {
            refreshState()
        }
    }

    private fun updateDuration() {
        if (!::duration.isInitialized) return
        val seconds = ScreenRecordService.elapsedRecordingMs() / 1000L
        duration.text = "%02d:%02d:%02d\nDuração".format(
            seconds / 3600L,
            (seconds / 60L) % 60L,
            seconds % 60L,
        )
    }

    private fun saveSettings() {
        if (!::areaMode.isInitialized) return
        prefs.edit()
            .putInt(KEY_AREA_MODE, areaMode.selectedItemPosition)
            .putInt(KEY_FPS_INDEX, fps.selectedItemPosition)
            .putInt(KEY_QUALITY_INDEX, quality.selectedItemPosition)
            .putInt(KEY_COUNTDOWN_INDEX, countdown.selectedItemPosition)
            .putBoolean(KEY_MIC, mic.isChecked)
            .putBoolean(KEY_HIDE, hideAfterStart.isChecked)
            .apply()
    }

    private fun refreshRecent() {
        if (!::recent.isInitialized) return

        val rows = mutableListOf<Triple<Uri, String, Long>>()
        runCatching {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.RELATIVE_PATH,
                ),
                MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?",
                arrayOf("%Central Inteligente de Midia/Gravacoes%"),
                MediaStore.Video.Media.DATE_ADDED + " DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (cursor.moveToNext() && rows.size < 20) {
                    rows += Triple(
                        Uri.withAppendedPath(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(idColumn).toString(),
                        ),
                        cursor.getString(nameColumn).orEmpty(),
                        cursor.getLong(dateColumn) * 1000L,
                    )
                }
            }
        }

        recent.removeAllViews()
        if (rows.isEmpty()) {
            recent.addView(
                MobileUi.text(this, "Nenhuma gravação recente.", 11f, MobileUi.MUTED).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(24), dp(8), dp(24))
                },
            )
            return
        }

        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        rows.forEach { row ->
            val item = MobileUi.card(this, 10)
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            box.addView(MobileUi.text(this, row.second, 12f, MobileUi.NAVY, true))
            box.addView(MobileUi.text(this, fmt.format(Date(row.third)), 9.5f, MobileUi.MUTED), MobileUi.match(dp(3)))
            box.addView(
                MobileUi.button(this, "Abrir gravação", false, MobileUi.NAVY, R.drawable.ic_open) {
                    runCatching {
                        startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(row.first, "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                        )
                    }.onFailure { toast("Não foi possível abrir a gravação.") }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(7) },
            )
            item.addView(box)
            recent.addView(item, MobileUi.match(dp(6)))
        }
    }

    companion object {
        private const val PREFS = "screen_recorder_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AREA_MODE = "area_mode"
        private const val KEY_FPS_INDEX = "fps_index"
        private const val KEY_QUALITY_INDEX = "quality_index"
        private const val KEY_COUNTDOWN_INDEX = "countdown_index"
        private const val KEY_MIC = "mic"
        private const val KEY_HIDE = "hide_after_start"
    }
}
