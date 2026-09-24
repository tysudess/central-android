package br.com.centralmidia.android.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import br.com.centralmidia.android.R

class VideoExtractorActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            toast("O Extrator de Vídeos integrado requer Android 10 ou superior.")
            finish()
            return
        }
        val next = Intent(this, br.com.extratorvideos.MainActivity::class.java)
        intent.getStringExtra("url")?.takeIf { it.isNotBlank() }?.let {
            next.putExtra("url", it)
        }
        startActivity(next)
        finish()
        overridePendingTransition(R.anim.central_slide_in_right, R.anim.central_slide_out_left)
    }
}
