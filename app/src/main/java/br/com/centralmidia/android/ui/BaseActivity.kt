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

    /*
     * V1.0.1
     *
     * PasswordDialogs é um object auxiliar do mesmo módulo, não uma subclasse
     * de BaseActivity. Por isso membros "protected" não podem ser acessados
     * por ele.
     *
     * "internal" mantém esses recursos restritos ao módulo app e permite que
     * os helpers de UI compartilhem a mesma sessão/auth e os utilitários de
     * coroutine/toast.
     */
    internal val auth by lazy {
        AuthManager.get(this)
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )
    }

    internal fun toast(
        message: String
    ) = Toast.makeText(
        this,
        message,
        Toast.LENGTH_LONG
    ).show()

    protected fun openUrl(
        url: String
    ) {
        startActivity(
            Intent(
                this,
                BrowserActivity::class.java
            ).putExtra(
                "url",
                url
            )
        )
    }

    protected fun externalUrl(
        url: String
    ) {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )
        )
    }

    internal fun <T> io(
        block: () -> T,
        success: (T) -> Unit,
        failure: (Throwable) -> Unit = {
            toast(
                it.message
                    ?: "Erro"
            )
        },
    ) {
        lifecycleScope.launch {
            try {
                val value = withContext(
                    Dispatchers.IO
                ) {
                    block()
                }

                success(
                    value
                )

            } catch (
                t: Throwable
            ) {
                failure(
                    t
                )
            }
        }
    }
}
