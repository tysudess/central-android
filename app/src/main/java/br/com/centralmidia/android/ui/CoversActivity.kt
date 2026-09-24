package br.com.centralmidia.android.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import br.com.centralmidia.android.R

class CoversActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast("O módulo Capas requer Android 8.0 ou superior.")
            finish()
            return
        }
        startActivity(Intent(this, br.com.principaiscapas.ModernMainActivity::class.java))
        finish()
        overridePendingTransition(R.anim.central_slide_in_right, R.anim.central_slide_out_left)
    }
}
