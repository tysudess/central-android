package br.com.centralmidia.android.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.CheckBox
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import br.com.centralmidia.android.core.Ui
import br.com.centralmidia.android.recording.ScreenRecordService

class ScreenRecorderActivity : BaseActivity() {
    private lateinit var mic: CheckBox

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val micOk = !mic.isChecked || result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micOk) launchCapture() else toast("Autorize o microfone ou desative a opção de áudio.")
    }

    private val capture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) {
            val i = Intent(this, ScreenRecordService::class.java)
                .setAction(ScreenRecordService.ACTION_START)
                .putExtra("resultCode", r.resultCode)
                .putExtra("data", r.data)
                .putExtra("mic", mic.isChecked)
            ContextCompat.startForegroundService(this, i)
            toast("Gravação iniciada.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.page(this, "Gravador de Tela", "MediaProjection nativo do Android")
        mic = CheckBox(this).apply {
            text = "Gravar microfone junto"
            isChecked = true
        }
        root.addView(mic, Ui.lp())
        root.addView(Ui.button(this, "Iniciar gravação") { requestAndStart() }, Ui.lp())
        root.addView(Ui.button(this, "Parar gravação") {
            startService(Intent(this, ScreenRecordService::class.java).setAction(ScreenRecordService.ACTION_STOP))
            toast("Gravação encerrada e salva em Filmes/Central Inteligente de Midia/Gravacoes.")
        }, Ui.lp())
        root.addView(
            Ui.label(
                this,
                "O Android sempre exibe a autorização oficial de captura de tela. Áudio interno de outros aplicativos depende das regras de cada aplicativo/versão Android; a opção acima grava o microfone.",
                12f,
            ),
            Ui.lp(),
        )
    }

    private fun requestAndStart() {
        val needed = mutableListOf<String>()
        if (mic.isChecked && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) permissions.launch(needed.toTypedArray()) else launchCapture()
    }

    private fun launchCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        capture.launch(manager.createScreenCaptureIntent())
    }
}
