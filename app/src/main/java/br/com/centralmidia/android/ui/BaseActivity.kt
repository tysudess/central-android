package br.com.centralmidia.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.centralmidia.android.core.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class BaseActivity : AppCompatActivity() {
    protected val auth by lazy { AuthManager.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    protected fun toast(message:String) = Toast.makeText(this,message,Toast.LENGTH_LONG).show()

    protected fun openUrl(url:String) {
        startActivity(Intent(this, BrowserActivity::class.java).putExtra("url",url))
    }

    protected fun externalUrl(url:String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    protected fun <T> io(block:()->T, success:(T)->Unit, failure:(Throwable)->Unit={toast(it.message ?: "Erro")}) {
        lifecycleScope.launch {
            try {
                val value=withContext(Dispatchers.IO){ block() }
                success(value)
            } catch(t:Throwable) { failure(t) }
        }
    }
}
