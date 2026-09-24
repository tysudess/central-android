package br.com.centralmidia.android.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.dp
import org.json.JSONObject
import org.json.JSONTokener

class NewsExtractorActivity : BaseActivity() {
    private lateinit var web: WebView
    private lateinit var urlInput: EditText
    private lateinit var output: EditText
    private lateinit var status: TextView
    private lateinit var counter: TextView
    private val meta = linkedMapOf<String, TextView>()
    private var pendingExtract = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = MobileScaffold.page(
            this,
            "Extrator de Notícias",
            "Extraia o texto principal e os dados da matéria sem sair do aplicativo.",
            MobileScaffold.Tab.MORE,
            showAutomation = true,
        )

        root.addView(inputCard(), MobileUi.match(dp(14)))
        root.addView(statusCard(), MobileUi.match(dp(10)))
        root.addView(metadataCard(), MobileUi.match(dp(10)))
        root.addView(textCard(), MobileUi.match(dp(10)))

        web = WebView(this).apply {
            visibility = View.GONE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (pendingExtract) {
                        pendingExtract = false
                        evaluateExtraction()
                    }
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(1, 1))

        val incoming = intent.getStringExtra("url").orEmpty().trim()
        if (incoming.isNotBlank()) {
            urlInput.setText(incoming)
            status.text = "Link recebido da aba Notícias. Toque em Extrair matéria."
        }
    }

    private fun inputCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        box.addView(
            MobileUi.text(
                this,
                "NOVA EXTRAÇÃO",
                9.5f,
                MobileUi.BLUE,
                true,
            ),
        )
        box.addView(
            MobileUi.text(
                this,
                "Cole o link da matéria",
                18f,
                MobileUi.NAVY,
                true,
            ),
            MobileUi.match(dp(4)),
        )

        urlInput = MobileUi.input(
            this,
            "https://veiculo.com.br/noticia/materia-completa",
        )
        box.addView(
            urlInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50),
            ).apply {
                topMargin = dp(9)
            },
        )

        box.addView(
            MobileUi.button(
                this,
                "Extrair matéria",
                true,
                MobileUi.BLUE,
                R.drawable.ic_news,
            ) {
                startExtraction()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50),
            ).apply {
                topMargin = dp(8)
            },
        )

        box.addView(
            MobileUi.text(
                this,
                "O endereço recebido de Notícias é o mesmo usado em Abrir matéria e Copiar link.",
                9.5f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(6)),
        )

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        actions.addView(
            MobileUi.button(
                this,
                "Limpar",
                false,
                MobileUi.PINK,
                R.drawable.ic_delete,
            ) {
                clearAll()
            },
            actionLp(),
        )

        actions.addView(
            MobileUi.button(
                this,
                "Abrir matéria",
                false,
                MobileUi.NAVY,
                R.drawable.ic_open,
            ) {
                val u = urlInput.text?.toString().orEmpty().trim()
                if (u.isNotBlank()) {
                    openUrl(u)
                }
            },
            actionLp(),
        )

        actions.addView(
            MobileUi.button(
                this,
                "Copiar texto",
                false,
                MobileUi.NAVY,
                R.drawable.ic_copy,
            ) {
                copyText()
            },
            actionLp(),
        )

        actions.addView(
            MobileUi.button(
                this,
                "Salvar TXT",
                false,
                MobileUi.BLUE,
                R.drawable.ic_news,
            ) {
                saveText()
            },
            actionLp(),
        )

        actions.addView(
            MobileUi.button(
                this,
                "Compartilhar",
                false,
                MobileUi.GREEN,
                R.drawable.ic_share,
            ) {
                shareText()
            },
            actionLp(),
        )

        scroll.addView(actions)
        box.addView(
            scroll,
            MobileUi.match(dp(9)),
        )

        card.addView(box)
        return card
    }

    private fun statusCard(): View {
        val card = MobileUi.card(
            this,
            11,
        ).apply {
            setCardBackgroundColor(MobileUi.GREEN_TINT)
            strokeColor = Color.rgb(190, 233, 211)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(
            MobileUi.text(
                this,
                "●",
                16f,
                MobileUi.GREEN,
                true,
            ),
        )

        status = MobileUi.text(
            this,
            "Pronto para receber um link da aba Notícias.",
            10.5f,
            Color.rgb(4, 120, 78),
            true,
        )

        row.addView(
            status,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart = dp(8)
            },
        )

        card.addView(row)
        return card
    }

    private fun metadataCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        box.addView(
            MobileUi.text(
                this,
                "Dados identificados",
                17f,
                MobileUi.NAVY,
                true,
            ),
        )

        listOf(
            "title" to "TÍTULO",
            "source" to "VEÍCULO",
            "date" to "DATA",
            "author" to "AUTOR",
            "subtitle" to "SUBTÍTULO",
        ).forEach { (key, label) ->
            box.addView(
                MobileUi.text(
                    this,
                    label,
                    8.5f,
                    MobileUi.BLUE,
                    true,
                ),
                MobileUi.match(dp(9)),
            )
            val value = MobileUi.text(
                this,
                "—",
                11.5f,
                MobileUi.NAVY,
                false,
            )
            value.setTextIsSelectable(true)
            meta[key] = value
            box.addView(
                value,
                MobileUi.match(dp(2)),
            )
        }

        card.addView(box)
        return card
    }

    private fun textCard(): View {
        val card = MobileUi.card(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            MobileUi.text(
                this,
                "Texto da matéria",
                17f,
                MobileUi.NAVY,
                true,
            ),
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        counter = MobileUi.text(
            this,
            "Nenhuma matéria extraída",
            9.5f,
            MobileUi.MUTED,
        )
        header.addView(counter)
        box.addView(header)

        output = EditText(this).apply {
            hint = "O conteúdo extraído aparecerá aqui."
            textSize = 12.5f
            setTextColor(MobileUi.NAVY)
            setHintTextColor(MobileUi.MUTED)
            gravity = Gravity.TOP or Gravity.START
            minLines = 16
            isSingleLine = false
            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12),
            )
            background = MobileUi.rounded(
                Color.rgb(248, 251, 255),
                dp(11).toFloat(),
                MobileUi.BORDER,
                dp(1),
            )
        }

        box.addView(
            output,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(360),
            ).apply {
                topMargin = dp(8)
            },
        )

        card.addView(box)
        return card
    }

    private fun actionLp() =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(42),
        ).apply {
            marginEnd = dp(6)
        }

    private fun startExtraction() {
        val raw = urlInput.text?.toString().orEmpty().trim()

        if (
            !raw.startsWith("http://") &&
            !raw.startsWith("https://")
        ) {
            toast("Informe um link http:// ou https:// válido.")
            return
        }

        status.text = "Abrindo a matéria e preparando a extração…"
        pendingExtract = true
        web.loadUrl(raw)
    }

    private fun evaluateExtraction() {
        val js = """
            (function(){
              function m(prop, name){
                var e = prop ? document.querySelector('meta[property="'+prop+'"]') : document.querySelector('meta[name="'+name+'"]');
                return e ? (e.content || '').trim() : '';
              }
              function text(sel){ var e=document.querySelector(sel); return e ? (e.innerText || e.textContent || '').trim() : ''; }
              var article = document.querySelector('article') || document.querySelector('main') || document.body;
              var body = article ? (article.innerText || article.textContent || '') : '';
              var author = m('', 'author') || text('[rel=author]') || text('.author') || text('[class*=author]');
              var date = m('article:published_time','') || m('', 'date') || m('', 'pubdate') || text('time');
              var source = m('og:site_name','') || location.hostname.replace(/^www./,'');
              var subtitle = m('og:description','') || m('', 'description') || text('article h2') || text('main h2');
              return JSON.stringify({
                title:(m('og:title','') || document.title || text('h1')).trim(),
                source:source,
                date:date,
                author:author,
                subtitle:subtitle,
                text:(body || '').replace(/
{3,}/g,'

').trim(),
                url:location.href
              });
            })()
        """.trimIndent()

        web.evaluateJavascript(js) { raw ->
            runCatching {
                val inner = JSONTokener(raw).nextValue() as String
                val obj = JSONObject(inner)
                applyResult(obj)
            }.onFailure {
                status.text = "Não foi possível interpretar o conteúdo desta página."
                toast("Falha ao extrair a matéria.")
            }
        }
    }

    private fun applyResult(obj: JSONObject) {
        val values = mapOf(
            "title" to obj.optString("title"),
            "source" to obj.optString("source"),
            "date" to obj.optString("date"),
            "author" to obj.optString("author"),
            "subtitle" to obj.optString("subtitle"),
        )

        values.forEach { (key, value) ->
            meta[key]?.text = value.ifBlank { "—" }
        }

        val text = obj.optString("text").trim()
        output.setText(text)

        val words = text
            .split(Regex("\s+"))
            .count { it.isNotBlank() }

        counter.text =
            "$words palavra(s) • ${text.length} caractere(s)"

        val directUrl = obj.optString("url").ifBlank {
            urlInput.text?.toString().orEmpty()
        }

        urlInput.setText(directUrl)

        status.text =
            if (text.isBlank()) {
                "A página abriu, mas não foi possível identificar texto principal."
            } else {
                "Matéria extraída com sucesso."
            }

        CentralDb(this).addHistory(
            "extract_news",
            values["title"].orEmpty().ifBlank {
                "Matéria extraída"
            },
            values["source"].orEmpty(),
            directUrl,
        )
    }

    private fun clearAll() {
        pendingExtract = false
        urlInput.setText("")
        output.setText("")
        meta.values.forEach {
            it.text = "—"
        }
        counter.text = "Nenhuma matéria extraída"
        status.text = "Pronto para receber um link da aba Notícias."
        web.loadUrl("about:blank")
    }

    private fun copyText() {
        val text = output.text?.toString().orEmpty()
        if (text.isBlank()) {
            toast("Nenhum texto extraído.")
            return
        }

        val clipboard =
            getSystemService(
                Context.CLIPBOARD_SERVICE,
            ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "Matéria",
                text,
            ),
        )

        toast("Texto copiado.")
    }

    private fun saveText() {
        val text = output.text?.toString().orEmpty()
        if (text.isBlank()) {
            toast("Nada para salvar.")
            return
        }

        val values = ContentValues().apply {
            put(
                MediaStore.Downloads.DISPLAY_NAME,
                "materia-${System.currentTimeMillis()}.txt",
            )
            put(
                MediaStore.Downloads.MIME_TYPE,
                "text/plain",
            )
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "Download/Central Inteligente de Midia/Noticias",
            )
        }

        val uri = contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        ) ?: run {
            toast("Não foi possível criar o arquivo.")
            return
        }

        contentResolver.openOutputStream(uri)?.use {
            it.write(
                text.toByteArray(),
            )
        }

        toast("Texto salvo em Downloads.")
    }

    private fun shareText() {
        val text = output.text?.toString().orEmpty()
        if (text.isBlank()) {
            toast("Nenhum texto extraído.")
            return
        }

        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        text,
                    )
                },
                "Compartilhar matéria",
            ),
        )
    }
}
