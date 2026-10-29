package br.com.centralmidia.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.method.ArrowKeyMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.centralmidia.android.R
import br.com.centralmidia.android.automation.CentralBackgroundService
import br.com.centralmidia.android.core.AuthManager
import br.com.centralmidia.android.core.dp
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

        // Mantém um serviço em primeiro plano enquanto a Central está aberta.
        // Isso permite que as tarefas já iniciadas continuem enquanto a tela
        // fica apagada. A notificação oferece uma ação explícita para parar.
        when (javaClass.simpleName) {
            "MainActivity" -> CentralBackgroundService.start(this)
            "LoginActivity" -> CentralBackgroundService.stop(this)
        }

        // A tela raiz é criada pela Activity concreta depois deste método.
        // O post permite aplicar a identidade naval quando a árvore já existir.
        window.decorView.post {
            NavalTheme.install(this)
        }
    }

    override fun onResume() {
        super.onResume()

        // O texto extraído deve manter a edição por toque longo e a seleção
        // nativa do Android, sem afetar os campos das demais telas.
        if (javaClass.simpleName == NEWS_EXTRACTOR_ACTIVITY) {
            window.decorView.post {
                enableNewsArticleTextSelection()
            }
        }

        // Reaplica o tema ao voltar de diálogos e de telas secundárias.
        window.decorView.post {
            NavalTheme.install(this)
        }
    }

    override fun onDestroy() {
        NavalTheme.clear(this)
        super.onDestroy()
    }

    private fun enableNewsArticleTextSelection() {
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        val editor = findArticleEditor(content) ?: return

        // EditText precisa do movimento padrão de edição para exibir as alças
        // de seleção, o menu Copiar/Recortar e aceitar Backspace.
        editor.movementMethod = ArrowKeyMovementMethod.getInstance()
        editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        editor.isFocusable = true
        editor.isFocusableInTouchMode = true
        editor.isLongClickable = true

        val parent = editor.parent as? LinearLayout ?: return
        if (parent.findViewWithTag<View>(NEWS_SELECTION_DELETE_TAG) != null) return

        val deleteSelectionButton = MobileUi.button(
            this,
            "Apagar seleção",
            false,
            MobileUi.PINK,
            R.drawable.ic_delete,
        ) {
            val start = minOf(editor.selectionStart, editor.selectionEnd)
            val end = maxOf(editor.selectionStart, editor.selectionEnd)

            if (start < 0 || end <= start) {
                toast("Toque e segure o texto para selecionar o trecho que deseja apagar.")
                return@button
            }

            editor.text?.delete(start, end)
            editor.setSelection(start.coerceAtMost(editor.length()))
            editor.requestFocus()
            toast("Trecho selecionado apagado.")
        }.apply {
            tag = NEWS_SELECTION_DELETE_TAG
            contentDescription = "Apagar somente o trecho selecionado da matéria"
        }

        val editorIndex = parent.indexOfChild(editor)
        parent.addView(
            deleteSelectionButton,
            editorIndex + 1,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42),
            ).apply {
                topMargin = dp(8)
            },
        )

        findInstructionText(content)?.text =
            "Toque e segure o texto para selecionar um trecho. Arraste as alças e use Apagar seleção ou a tecla Backspace."
    }

    private fun findArticleEditor(view: View): EditText? {
        if (view is EditText) {
            val hint = view.hint?.toString().orEmpty()
            if (hint.contains("conteúdo extraído aparecerá aqui", ignoreCase = true)) {
                return view
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findArticleEditor(view.getChildAt(index))
                if (found != null) return found
            }
        }

        return null
    }

    private fun findInstructionText(view: View): TextView? {
        if (view is TextView && view.text?.toString().orEmpty().startsWith("Deslize dentro do quadro")) {
            return view
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findInstructionText(view.getChildAt(index))
                if (found != null) return found
            }
        }

        return null
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

    private companion object {
        const val NEWS_EXTRACTOR_ACTIVITY = "NewsExtractorActivity"
        const val NEWS_SELECTION_DELETE_TAG = "central-news-selection-delete"
    }
}
