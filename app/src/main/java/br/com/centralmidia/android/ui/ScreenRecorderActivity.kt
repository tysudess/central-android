package br.com.centralmidia.android.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
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
import androidx.core.content.ContextCompat
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
    private lateinit var floatingWidget: CheckBox
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
    private val prefs by lazy { getSharedPreferences("screen_recorder_settings", MODE_PRIVATE) }

    private var cropLeft = 0f
    private var cropTop = 0f
    private var cropRight = 1f
    private var cropBottom = 1f
    private var waitingOverlayPermission = false

    private val areaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) return@registerForActivityResult
        cropLeft = result.data?.getFloatExtra(CaptureAreaActivity.EXTRA_LEFT, 0f) ?: 0f
        cropTop = result.data?.getFloatExtra(CaptureAreaActivity.EXTRA_TOP, 0f) ?: 0f
        cropRight = result.data?.getFloatExtra(CaptureAreaActivity.EXTRA_RIGHT, 1f) ?: 1f
        cropBottom = result.data?.getFloatExtra(CaptureAreaActivity.EXTRA_BOTTOM, 1f) ?: 1f
        sourceStat.text = "Área\nOrigem"
        continueStart()
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!waitingOverlayPermission) return@registerForActivityResult
        waitingOverlayPermission = false
        if (Settings.canDrawOverlays(this)) requestCapture()
        else toast("Para usar o controle flutuante, permita que a Central apareça sobre outros aplicativos.")
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val micOk = !mic.isChecked || result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micOk) ensureOverlayThenCapture() else toast("Autorize o microfone ou desative o áudio.")
    }

    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val seconds = countdown.selectedItem?.toString()?.substringBefore(" ")?.toIntOrNull() ?: 0
            if (seconds > 0) {
                stateLabel.text = "INICIA EM ${seconds}s"
                handler.postDelayed({ startRecording(result) }, seconds * 1000L)
            } else startRecording(result)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = MobileScaffold.page(this, "Gravador de Tela", "", MobileScaffold.Tab.MORE, showAutomation = false)
        root.addView(masterCard(), MobileUi.match(dp(8)))
        root.addView(stats(), MobileUi.match(dp(8)))
        root.addView(config(), MobileUi.match(dp(8)))
        root.addView(controls(), MobileUi.match(dp(8)))
        root.addView(recentCard(), MobileUi.match(dp(8)))
        refreshState(); refreshRecent()
    }

    override fun onResume() { super.onResume(); refreshState(); refreshRecent() }
    override fun onDestroy() { handler.removeCallbacks(stateTick); super.onDestroy() }

    private fun masterCard(): View {
        val card = MobileUi.card(this)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(
            LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = MobileUi.rounded(MobileUi.PINK_TINT, dp(14).toFloat())
                addView(MobileUi.text(this@ScreenRecorderActivity, "●", 27f, MobileUi.PINK, true))
            },
            LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(10) },
        )
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(MobileUi.text(this, "Gravador de Tela", 19f, MobileUi.NAVY, true))
        copy.addView(MobileUi.text(this, "Controle flutuante • pausa real • seleção de área", 9.5f, MobileUi.MUTED), MobileUi.match(dp(3)))
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        master = SwitchMaterial(this).apply {
            isChecked = prefs.getBoolean("enabled", false)
            text = if (isChecked) "Ligado" else "Desligado"
            textSize = 11.5f; setTextColor(MobileUi.NAVY)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("enabled", checked).apply()
                text = if (checked) "Ligado" else "Desligado"
                if (!checked && ScreenRecordService.isRecording) stopRecording()
                refreshState()
            }
        }
        row.addView(master); card.addView(row); return card
    }

    private fun stats(): View {
        val card = MobileUi.card(this)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        duration = stat("00:00:00", "Duração"); sourceStat = stat("Tela", "Origem")
        row.addView(duration, LinearLayout.LayoutParams(0, dp(72), 1f).apply { marginEnd = dp(3) })
        row.addView(sourceStat, LinearLayout.LayoutParams(0, dp(72), 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
        row.addView(stat("MP4", "Formato"), LinearLayout.LayoutParams(0, dp(72), 1f).apply { marginStart = dp(3) })
        card.addView(row); return card
    }

    private fun stat(value: String, caption: String) = MobileUi.text(this, "$value\n$caption", 10.5f, MobileUi.NAVY, true).apply {
        gravity = Gravity.CENTER
        background = MobileUi.rounded(Color.rgb(248, 251, 255), dp(10).toFloat(), MobileUi.BORDER, dp(1))
    }

    private fun config(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(MobileUi.text(this, "Configuração da gravação", 17f, MobileUi.NAVY, true))
        areaMode = spinner(listOf("Tela inteira", "Área personalizada"), prefs.getInt("area_mode", 0).coerceIn(0, 1))
        fps = spinner(listOf("24 FPS", "30 FPS", "60 FPS"), prefs.getInt("fps_index", 1).coerceIn(0, 2))
        quality = spinner(listOf("Original", "720p", "1080p"), prefs.getInt("quality_index", 0).coerceIn(0, 2))
        countdown = spinner(listOf("0 segundos", "3 segundos", "5 segundos", "10 segundos"), prefs.getInt("countdown_index", 0).coerceIn(0, 3))
        addSpinner(box, "Área de captura", areaMode); addSpinner(box, "Taxa de quadros", fps)
        addSpinner(box, "Qualidade", quality); addSpinner(box, "Contagem regressiva", countdown)
        mic = check("Gravar microfone", prefs.getBoolean("mic", false))
        floatingWidget = check("Mostrar controle flutuante durante a gravação", prefs.getBoolean("floating_widget", true))
        hideAfterStart = check("Ocultar a Central depois de iniciar", prefs.getBoolean("hide_after_start", true))
        box.addView(mic, MobileUi.match(dp(8))); box.addView(floatingWidget, MobileUi.match(dp(2))); box.addView(hideAfterStart, MobileUi.match(dp(2)))
        box.addView(
            MobileUi.text(this, "Em Área personalizada, marque um retângulo. O arquivo final é salvo já recortado para essa região.", 9.5f, MobileUi.MUTED).apply {
                setPadding(dp(10), dp(8), dp(10), dp(8)); background = MobileUi.rounded(MobileUi.BLUE_TINT, dp(9).toFloat())
            },
            MobileUi.match(dp(7)),
        )
        card.addView(box); return card
    }

    private fun check(label: String, checked: Boolean) = CheckBox(this).apply { text = label; isChecked = checked; setTextColor(MobileUi.NAVY) }
    private fun addSpinner(box: LinearLayout, label: String, spinner: Spinner) {
        box.addView(MobileUi.text(this, label, 10f, MobileUi.NAVY, true), MobileUi.match(dp(8)))
        box.addView(spinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(4) })
    }
    private fun spinner(values: List<String>, index: Int) = Spinner(this).apply {
        adapter = ArrayAdapter(this@ScreenRecorderActivity, android.R.layout.simple_spinner_dropdown_item, values)
        setSelection(index); background = MobileUi.rounded(Color.WHITE, dp(10).toFloat(), MobileUi.BORDER, dp(1)); setPadding(dp(8), 0, dp(8), 0)
    }

    private fun controls(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stateLabel = MobileUi.statusChip(this, "DESLIGADO", MobileUi.GREEN); box.addView(stateLabel)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        pauseButton = MobileUi.button(this, "Pausar", false, MobileUi.ORANGE) { togglePause() }
        stopButton = MobileUi.button(this, "Parar", false, MobileUi.PINK, R.drawable.ic_delete) { stopRecording() }
        row.addView(pauseButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
        row.addView(stopButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) })
        box.addView(row, MobileUi.match(dp(9)))
        startButton = MobileUi.button(this, "INICIAR GRAVAÇÃO", true, MobileUi.PINK) { begin() }
        box.addView(startButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(7) })
        card.addView(box); return card
    }

    private fun recentCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(MobileUi.text(this, "Gravações recentes", 17f, MobileUi.NAVY, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(MobileUi.button(this, "Atualizar") { refreshRecent() }); box.addView(head)
        recent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; box.addView(recent, MobileUi.match(dp(7)))
        card.addView(box); return card
    }

    private fun begin() {
        if (!master.isChecked) { toast("Ligue o Gravador de Tela antes de iniciar."); return }
        if (ScreenRecordService.isRecording || ScreenRecordService.isProcessing) { toast("O gravador ainda está ocupado."); return }
        saveSettings()
        if (areaMode.selectedItemPosition == 1) {
            areaLauncher.launch(Intent(this, CaptureAreaActivity::class.java)); return
        }
        cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f; sourceStat.text = "Tela\nOrigem"
        continueStart()
    }

    private fun continueStart() {
        val needed = mutableListOf<String>()
        if (mic.isChecked && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.POST_NOTIFICATIONS
        if (needed.isEmpty()) ensureOverlayThenCapture() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun ensureOverlayThenCapture() {
        if (floatingWidget.isChecked && !Settings.canDrawOverlays(this)) {
            waitingOverlayPermission = true
            overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        requestCapture()
    }

    private fun requestCapture() {
        captureLauncher.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }

    private fun startRecording(result: androidx.activity.result.ActivityResult) {
        val fpsValue = fps.selectedItem?.toString()?.substringBefore(" ")?.toIntOrNull() ?: 30
        val qualityValue = when (quality.selectedItemPosition) { 1 -> "720"; 2 -> "1080"; else -> "original" }
        val startIntent = Intent(this, ScreenRecordService::class.java)
            .setAction(ScreenRecordService.ACTION_START)
            .putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
            .putExtra(ScreenRecordService.EXTRA_RESULT_DATA, result.data)
            .putExtra(ScreenRecordService.EXTRA_MIC, mic.isChecked)
            .putExtra(ScreenRecordService.EXTRA_FPS, fpsValue)
            .putExtra(ScreenRecordService.EXTRA_QUALITY, qualityValue)
            .putExtra(ScreenRecordService.EXTRA_SHOW_OVERLAY, floatingWidget.isChecked)
            .putExtra(ScreenRecordService.EXTRA_CROP_LEFT, cropLeft)
            .putExtra(ScreenRecordService.EXTRA_CROP_TOP, cropTop)
            .putExtra(ScreenRecordService.EXTRA_CROP_RIGHT, cropRight)
            .putExtra(ScreenRecordService.EXTRA_CROP_BOTTOM, cropBottom)
        val started = runCatching { ContextCompat.startForegroundService(this, startIntent) }
        if (started.isFailure) { stateLabel.text = "LIGADO • FALHA"; toast(started.exceptionOrNull()?.message ?: "Não foi possível iniciar o serviço de gravação."); return }
        stateLabel.text = "INICIANDO…"
        handler.postDelayed({ refreshState(); if (!ScreenRecordService.isRecording && ScreenRecordService.lastError.isNotBlank()) toast(ScreenRecordService.lastError) }, 900L)
        if (hideAfterStart.isChecked) handler.postDelayed({ if (ScreenRecordService.isRecording) moveTaskToBack(true) }, 1100L)
    }

    private fun togglePause() {
        if (!ScreenRecordService.isRecording) return
        val action = if (ScreenRecordService.isPaused) ScreenRecordService.ACTION_RESUME else ScreenRecordService.ACTION_PAUSE
        startService(Intent(this, ScreenRecordService::class.java).setAction(action)); handler.postDelayed({ refreshState() }, 250L)
    }

    private fun stopRecording() {
        if (!ScreenRecordService.isRecording) { refreshState(); return }
        startService(Intent(this, ScreenRecordService::class.java).setAction(ScreenRecordService.ACTION_STOP)); handler.postDelayed({ refreshState() }, 350L)
    }

    private val stateTick = object : Runnable { override fun run() = refreshState() }

    private fun refreshState() {
        if (!::stateLabel.isInitialized) return
        handler.removeCallbacks(stateTick)
        val enabled = if (::master.isInitialized) master.isChecked else prefs.getBoolean("enabled", false)
        val active = ScreenRecordService.isRecording; val paused = ScreenRecordService.isPaused; val processing = ScreenRecordService.isProcessing
        stateLabel.text = when { !enabled -> "DESLIGADO"; processing -> "FINALIZANDO VÍDEO…"; !active -> "LIGADO • PRONTO"; paused -> "PAUSADO"; else -> "GRAVANDO" }
        startButton.isEnabled = enabled && !active && !processing; pauseButton.isEnabled = active; stopButton.isEnabled = active
        pauseButton.text = if (paused) "Retomar" else "Pausar"; if (::master.isInitialized) master.text = if (enabled) "Ligado" else "Desligado"
        updateDuration()
        if (active || processing) handler.postDelayed(stateTick, 500L) else refreshRecent()
    }

    private fun updateDuration() {
        if (!::duration.isInitialized) return
        val seconds = ScreenRecordService.elapsedRecordingMs() / 1000L
        duration.text = "%02d:%02d:%02d\nDuração".format(seconds / 3600L, (seconds / 60L) % 60L, seconds % 60L)
    }

    private fun saveSettings() {
        prefs.edit().putInt("area_mode", areaMode.selectedItemPosition).putInt("fps_index", fps.selectedItemPosition)
            .putInt("quality_index", quality.selectedItemPosition).putInt("countdown_index", countdown.selectedItemPosition)
            .putBoolean("mic", mic.isChecked).putBoolean("floating_widget", floatingWidget.isChecked)
            .putBoolean("hide_after_start", hideAfterStart.isChecked).apply()
    }

    private fun refreshRecent() {
        if (!::recent.isInitialized) return
        val rows = mutableListOf<Triple<Uri, String, Long>>()
        runCatching {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.RELATIVE_PATH),
                MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?",
                arrayOf("%Central Inteligente de Midia/Gravacoes%"),
                MediaStore.Video.Media.DATE_ADDED + " DESC",
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID); val name = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME); val date = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (c.moveToNext() && rows.size < 20) rows += Triple(Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(id).toString()), c.getString(name).orEmpty(), c.getLong(date) * 1000L)
            }
        }
        recent.removeAllViews()
        if (rows.isEmpty()) {
            recent.addView(MobileUi.text(this, "Nenhuma gravação recente.", 11f, MobileUi.MUTED).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(24), dp(8), dp(24)) })
            return
        }
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        rows.forEach { row ->
            val item = MobileUi.card(this, 10); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            box.addView(MobileUi.text(this, row.second, 12f, MobileUi.NAVY, true)); box.addView(MobileUi.text(this, fmt.format(Date(row.third)), 9.5f, MobileUi.MUTED), MobileUi.match(dp(3)))
            box.addView(MobileUi.button(this, "Abrir gravação", false, MobileUi.NAVY, R.drawable.ic_open) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(row.first, "video/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }.onFailure { toast("Não foi possível abrir a gravação.") }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(7) })
            item.addView(box); recent.addView(item, MobileUi.match(dp(6)))
        }
    }
}
