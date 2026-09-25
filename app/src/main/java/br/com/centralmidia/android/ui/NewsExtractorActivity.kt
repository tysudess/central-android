package br.com.centralmidia.android.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.GoogleNewsUrlResolver
import br.com.centralmidia.android.core.dp
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.net.URI
import java.util.concurrent.TimeUnit

class NewsExtractorActivity : BaseActivity() {
    private data class ExtractedArticle(
        val title: String,
        val source: String,
        val date: String,
        val author: String,
        val subtitle: String,
        val text: String,
        val url: String,
    )

    private lateinit var web: WebView
    private lateinit var urlInput: EditText
    private lateinit var output: EditText
    private lateinit var status: TextView
    private lateinit var counter: TextView

    private val meta = linkedMapOf<String, TextView>()
    private val resolver = GoogleNewsUrlResolver()

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var webFallbackRunning = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = MobileScaffold.page(
            this,
            "Extrator de Notícias",
            "",
            MobileScaffold.Tab.MORE,
            showAutomation = false,
        )

        root.addView(inputCard(), MobileUi.match(dp(8)))
        root.addView(statusCard(), MobileUi.match(dp(10)))
        root.addView(metadataCard(), MobileUi.match(dp(10)))
        root.addView(textCard(), MobileUi.match(dp(10)))

        web = WebView(this).apply {
            visibility = View.INVISIBLE

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

            webChromeClient = WebChromeClient()

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(
                    view: WebView?,
                    url: String?,
                ) {
                    super.onPageFinished(view, url)

                    if (!webFallbackRunning) {
                        return
                    }

                    val current =
                        url.orEmpty()
                            .trim()

                    if (
                        current.isBlank() ||
                        current == "about:blank"
                    ) {
                        return
                    }

                    if (
                        resolver.isGoogleNews(
                            current,
                        )
                    ) {
                        status.text =
                            "O Google News ainda está redirecionando para o veículo…"
                        return
                    }

                    webFallbackRunning = false
                    evaluateWebExtraction()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    super.onReceivedError(
                        view,
                        request,
                        error,
                    )

                    if (
                        request?.isForMainFrame == true &&
                        webFallbackRunning
                    ) {
                        webFallbackRunning = false
                        status.text =
                            "Não foi possível abrir a matéria: " +
                                error?.description?.toString().orEmpty()
                    }
                }
            }
        }

        root.addView(
            web,
            LinearLayout.LayoutParams(
                1,
                1,
            ),
        )

        val incoming =
            intent.getStringExtra(
                "url",
            )
                .orEmpty()
                .trim()

        if (incoming.isNotBlank()) {
            urlInput.setText(incoming)

            status.text =
                if (
                    resolver.isGoogleNews(
                        incoming,
                    )
                ) {
                    "Link recebido. O endereço direto do veículo será resolvido ao extrair."
                } else {
                    "Link recebido da aba Notícias. Toque em Extrair matéria."
                }
        }
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            runCatching {
                web.stopLoading()
                web.loadUrl("about:blank")
                web.destroy()
            }
        }

        super.onDestroy()
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
                "O Android tenta primeiro a extração direta. Se o site exigir navegador, a Central usa um WebView interno automaticamente.",
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
                openCurrentArticle()
            },
            actionLp(),
        )

        actions.addView(
            MobileUi.button(
                this,
                "Copiar matéria",
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
            setCardBackgroundColor(
                MobileUi.GREEN_TINT,
            )

            strokeColor =
                Color.rgb(
                    190,
                    233,
                    211,
                )
        }

        val row = LinearLayout(this).apply {
            orientation =
                LinearLayout.HORIZONTAL

            gravity =
                Gravity.CENTER_VERTICAL
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
            Color.rgb(
                4,
                120,
                78,
            ),
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
            orientation =
                LinearLayout.VERTICAL
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

            value.setTextIsSelectable(
                true,
            )

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
            orientation =
                LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation =
                LinearLayout.HORIZONTAL

            gravity =
                Gravity.CENTER_VERTICAL
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

        header.addView(
            counter,
        )

        box.addView(
            header,
        )

        box.addView(
            MobileUi.text(
                this,
                "Deslize dentro do quadro para ler a matéria inteira. A saída segue o padrão Windows/Ubuntu.",
                9.2f,
                MobileUi.MUTED,
            ),
            MobileUi.match(dp(4)),
        )

        output = EditText(this).apply {
            hint =
                "O conteúdo extraído aparecerá aqui."

            textSize = 12.5f

            setTextColor(
                MobileUi.NAVY,
            )

            setHintTextColor(
                MobileUi.MUTED,
            )

            gravity =
                Gravity.TOP or
                    Gravity.START

            minLines = 16
            isSingleLine = false
            setHorizontallyScrolling(false)
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
            overScrollMode = View.OVER_SCROLL_ALWAYS
            movementMethod = ScrollingMovementMethod.getInstance()

            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE,
                    -> view.parent?.requestDisallowInterceptTouchEvent(true)

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                    -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }

            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12),
            )

            background =
                MobileUi.rounded(
                    Color.rgb(
                        248,
                        251,
                        255,
                    ),
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
        val raw =
            urlInput.text
                ?.toString()
                .orEmpty()
                .trim()

        if (
            !raw.startsWith(
                "http://",
            ) &&
            !raw.startsWith(
                "https://",
            )
        ) {
            toast(
                "Informe um link http:// ou https:// válido.",
            )
            return
        }

        webFallbackRunning = false

        status.text =
            if (
                resolver.isGoogleNews(
                    raw,
                )
            ) {
                "Resolvendo o link direto do veículo…"
            } else {
                "Extraindo a matéria…"
            }

        io(
            {
                val direct =
                    resolver.resolve(
                        raw,
                    )
                        .ifBlank {
                            raw
                        }

                try {
                    extractWithHttp(
                        direct,
                    )
                } catch (
                    error: Exception
                ) {
                    ExtractedArticle(
                        title = "",
                        source = "",
                        date = "",
                        author = "",
                        subtitle = "",
                        text = "",
                        url = direct,
                    )
                }
            },
            { result ->
                val direct =
                    result.url.ifBlank {
                        raw
                    }

                urlInput.setText(
                    direct,
                )

                if (
                    result.text.length >=
                    MIN_DIRECT_TEXT
                ) {
                    applyResult(
                        result,
                    )
                } else {
                    startWebFallback(
                        direct,
                    )
                }
            },
            {
                startWebFallback(
                    raw,
                )
            },
        )
    }

    private fun extractWithHttp(
        url: String,
    ): ExtractedArticle {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                DESKTOP_UA,
            )
            .header(
                "Accept-Language",
                "pt-BR,pt;q=0.9,en;q=0.7",
            )
            .build()

        http.newCall(
            request,
        )
            .execute()
            .use { response ->
                if (
                    !response.isSuccessful
                ) {
                    error(
                        "HTTP ${response.code}",
                    )
                }

                val html =
                    response.body
                        ?.string()
                        .orEmpty()

                if (
                    html.isBlank()
                ) {
                    error(
                        "Página vazia.",
                    )
                }

                val finalUrl =
                    response.request
                        .url
                        .toString()

                val doc =
                    Jsoup.parse(
                        html,
                        finalUrl,
                    )

                // O extrator desktop devolve campos estruturados (título, veículo,
                // data, autor, subtítulo e corpo formatado). No Android tentamos
                // primeiro o JSON-LD NewsArticle/Article da própria página, que é
                // normalmente a fonte mais fiel para esses mesmos campos.
                val structured =
                    extractStructuredArticle(
                        doc,
                        finalUrl,
                    )

                doc.select(
                    "script,style,noscript,svg,nav,footer,header,aside,form",
                )
                    .remove()

                fun metaValue(
                    css: String,
                ): String =
                    doc.selectFirst(
                        css,
                    )
                        ?.attr(
                            "content",
                        )
                        .orEmpty()
                        .trim()

                val title =
                    structured?.title.orEmpty()
                        .ifBlank {
                            metaValue(
                                "meta[property=og:title]",
                            )
                        }
                        .ifBlank {
                            doc.selectFirst(
                                "h1",
                            )
                                ?.text()
                                .orEmpty()
                        }
                        .ifBlank {
                            doc.title()
                        }
                        .trim()

                val source =
                    structured?.source.orEmpty()
                        .ifBlank {
                            metaValue(
                                "meta[property=og:site_name]",
                            )
                        }
                        .ifBlank {
                            runCatching {
                                URI(
                                    finalUrl,
                                )
                                    .host
                                    .orEmpty()
                                    .removePrefix(
                                        "www.",
                                    )
                            }
                                .getOrDefault(
                                    "",
                                )
                        }

                val date =
                    structured?.date.orEmpty()
                        .ifBlank {
                            metaValue(
                                "meta[property=article:published_time]",
                            )
                        }
                        .ifBlank {
                            metaValue(
                                "meta[name=date]",
                            )
                        }
                        .ifBlank {
                            metaValue(
                                "meta[itemprop=datePublished]",
                            )
                        }
                        .ifBlank {
                            metaValue(
                                "meta[name=parsely-pub-date]",
                            )
                        }
                        .ifBlank {
                            doc.selectFirst(
                                "time[datetime]",
                            )
                                ?.attr(
                                    "datetime",
                                )
                                .orEmpty()
                        }
                        .ifBlank {
                            doc.selectFirst(
                                "time",
                            )
                                ?.text()
                                .orEmpty()
                        }

                val author =
                    structured?.author.orEmpty()
                        .ifBlank {
                            metaValue(
                                "meta[name=author]",
                            )
                        }
                        .ifBlank {
                            metaValue(
                                "meta[property=article:author]",
                            )
                        }
                        .ifBlank {
                            metaValue(
                                "meta[name=parsely-author]",
                            )
                        }
                        .ifBlank {
                            metaValue(
                                "meta[name=byl]",
                            )
                        }
                        .ifBlank {
                            doc.selectFirst(
                                "[rel=author], .author, [class*=author]",
                            )
                                ?.text()
                                .orEmpty()
                        }

                val subtitle =
                    structured?.subtitle.orEmpty()
                        .ifBlank {
                            metaValue(
                                "meta[property=og:description]",
                            )
                        }
                        .ifBlank {
                            metaValue(
                                "meta[name=description]",
                            )
                        }
                        .ifBlank {
                            doc.selectFirst(
                                "article h2, main h2",
                            )
                                ?.text()
                                .orEmpty()
                        }

                val article =
                    doc.selectFirst(
                        "article",
                    )
                        ?: doc.selectFirst(
                            "main",
                        )
                        ?: doc.body()

                val paragraphs =
                    article
                        ?.select(
                            "p",
                        )
                        ?.map {
                            it.text()
                                .trim()
                        }
                        ?.filter {
                            it.length >=
                                MIN_PARAGRAPH
                        }
                        ?.distinct()
                        .orEmpty()

                val paragraphText =
                    paragraphs.joinToString(
                        "\n\n",
                    )

                val text =
                    when {
                        paragraphText.length >=
                            MIN_DIRECT_TEXT &&
                            paragraphs.size >=
                            2 ->
                            paragraphText

                        structured?.text.orEmpty().length >=
                            MIN_DIRECT_TEXT ->
                            structured?.text.orEmpty()

                        else ->
                            article
                                ?.text()
                                .orEmpty()
                                .trim()
                    }

                return normalizeArticle(
                    ExtractedArticle(
                        title = title,
                        source = source,
                        date = date,
                        author = author,
                        subtitle = subtitle,
                        text = normalizeText(
                            text,
                        ),
                        url = finalUrl,
                    ),
                )
            }
    }

    private fun extractStructuredArticle(
        doc: org.jsoup.nodes.Document,
        finalUrl: String,
    ): ExtractedArticle? {
        val objects = mutableListOf<JSONObject>()

        doc.select("script[type=application/ld+json]")
            .forEach { script ->
                val raw = script.data()
                    .ifBlank { script.html() }
                    .trim()

                if (raw.isBlank()) {
                    return@forEach
                }

                runCatching {
                    JSONTokener(raw).nextValue()
                }
                    .getOrNull()
                    ?.let { value ->
                        collectJsonObjects(
                            value,
                            objects,
                        )
                    }
            }

        if (objects.isEmpty()) {
            return null
        }

        val article = objects.firstOrNull { obj ->
            jsonTypes(obj).any { type ->
                type.equals("NewsArticle", true) ||
                    type.equals("Article", true) ||
                    type.equals("ReportageNewsArticle", true) ||
                    type.equals("AnalysisNewsArticle", true)
            }
        } ?: objects.firstOrNull { obj ->
            obj.optString("articleBody").isNotBlank()
        } ?: return null

        val publisher =
            jsonName(
                article.opt("publisher"),
            )

        val author =
            jsonName(
                article.opt("author"),
            )

        val title =
            article.optString("headline")
                .ifBlank {
                    article.optString("name")
                }

        val subtitle =
            article.optString("description")

        val date =
            article.optString("datePublished")
                .ifBlank {
                    article.optString("dateModified")
                }

        val body =
            normalizeText(
                article.optString("articleBody"),
            )

        return normalizeArticle(
            ExtractedArticle(
                title = title.trim(),
                source = publisher.trim(),
                date = date.trim(),
                author = author.trim(),
                subtitle = subtitle.trim(),
                text = body,
                url = finalUrl,
            ),
        )
    }

    private fun collectJsonObjects(
        value: Any?,
        out: MutableList<JSONObject>,
    ) {
        when (value) {
            is JSONObject -> {
                out += value

                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    collectJsonObjects(
                        value.opt(key),
                        out,
                    )
                }
            }

            is JSONArray -> {
                for (index in 0 until value.length()) {
                    collectJsonObjects(
                        value.opt(index),
                        out,
                    )
                }
            }
        }
    }

    private fun jsonTypes(
        obj: JSONObject,
    ): List<String> {
        val raw = obj.opt("@type")

        return when (raw) {
            is JSONArray ->
                buildList {
                    for (index in 0 until raw.length()) {
                        raw.optString(index)
                            .trim()
                            .takeIf { it.isNotBlank() }
                            ?.let(::add)
                    }
                }

            is String ->
                listOf(raw.trim())
                    .filter { it.isNotBlank() }

            else ->
                emptyList()
        }
    }

    private fun jsonName(
        value: Any?,
    ): String =
        when (value) {
            is JSONObject ->
                value.optString("name")
                    .ifBlank {
                        value.optString("headline")
                    }

            is JSONArray ->
                buildList {
                    for (index in 0 until value.length()) {
                        val name = jsonName(
                            value.opt(index),
                        )
                            .trim()

                        if (name.isNotBlank()) {
                            add(name)
                        }
                    }
                }
                    .distinct()
                    .joinToString(", ")

            is String ->
                value

            else ->
                ""
        }

    private fun startWebFallback(
        input: String,
    ) {
        if (
            !::web.isInitialized
        ) {
            status.text =
                "O navegador interno não está disponível."
            return
        }

        status.text =
            "O site exige navegador. Tentando pelo WebView interno…"

        webFallbackRunning = true

        runCatching {
            web.stopLoading()
            web.clearHistory()
            web.loadUrl(
                input,
            )
        }.onFailure {
            webFallbackRunning = false
            status.text =
                "Não foi possível abrir a matéria no navegador interno."
        }
    }

    private fun evaluateWebExtraction() {
        val js = """
            (function() {
              function meta(selector) {
                var el = document.querySelector(selector);
                return el ? (el.content || '').trim() : '';
              }

              function firstText(selector) {
                var el = document.querySelector(selector);
                return el ? (el.innerText || el.textContent || '').trim() : '';
              }

              function nameOf(value) {
                if (!value) return '';
                if (typeof value === 'string') return value.trim();
                if (Array.isArray(value)) {
                  return value.map(nameOf).filter(Boolean).filter(function(v, i, a) {
                    return a.indexOf(v) === i;
                  }).join(', ');
                }
                if (typeof value === 'object') {
                  return String(value.name || value.headline || '').trim();
                }
                return '';
              }

              function findArticle(value) {
                if (!value) return null;
                if (Array.isArray(value)) {
                  for (var i = 0; i < value.length; i++) {
                    var nested = findArticle(value[i]);
                    if (nested) return nested;
                  }
                  return null;
                }
                if (typeof value !== 'object') return null;

                var type = value['@type'];
                var types = Array.isArray(type) ? type : [type];
                for (var t = 0; t < types.length; t++) {
                  var current = String(types[t] || '').toLowerCase();
                  if (
                    current === 'newsarticle' ||
                    current === 'article' ||
                    current === 'reportagenewsarticle' ||
                    current === 'analysisnewsarticle'
                  ) {
                    return value;
                  }
                }

                if (value.articleBody) return value;

                for (var key in value) {
                  if (!Object.prototype.hasOwnProperty.call(value, key)) continue;
                  var child = findArticle(value[key]);
                  if (child) return child;
                }
                return null;
              }

              function structuredArticle() {
                var scripts = document.querySelectorAll('script[type="application/ld+json"]');
                for (var i = 0; i < scripts.length; i++) {
                  try {
                    var parsed = JSON.parse(scripts[i].textContent || scripts[i].innerText || '');
                    var found = findArticle(parsed);
                    if (found) return found;
                  } catch (e) {}
                }
                return null;
              }

              var structured = structuredArticle();

              var root =
                document.querySelector('article') ||
                document.querySelector('main') ||
                document.body;

              var paragraphs = [];
              if (root) {
                var nodes = root.querySelectorAll('p');
                for (var i = 0; i < nodes.length; i++) {
                  var value =
                    (nodes[i].innerText || nodes[i].textContent || '').trim();
                  if (value.length >= 30 && paragraphs.indexOf(value) < 0) {
                    paragraphs.push(value);
                  }
                }
              }

              var body = paragraphs.join('\n\n');

              if (body.length < 120 && root) {
                body = (root.innerText || root.textContent || '').trim();
              }

              body = body.replace(/\n{3,}/g, '\n\n').trim();

              if (body.length < 120 && structured && structured.articleBody) {
                body = String(structured.articleBody || '').trim();
              }

              var author =
                (structured ? nameOf(structured.author) : '') ||
                meta('meta[name="author"]') ||
                meta('meta[name="parsely-author"]') ||
                meta('meta[property="article:author"]') ||
                firstText('[rel="author"]') ||
                firstText('.author') ||
                firstText('[class*="author"]');

              var date =
                (structured ? String(structured.datePublished || structured.dateModified || '') : '') ||
                meta('meta[property="article:published_time"]') ||
                meta('meta[name="date"]') ||
                meta('meta[name="parsely-pub-date"]') ||
                firstText('time');

              var source =
                (structured ? nameOf(structured.publisher) : '') ||
                meta('meta[property="og:site_name"]') ||
                location.hostname.replace(/^www\./, '');

              var subtitle =
                (structured ? String(structured.description || '') : '') ||
                meta('meta[property="og:description"]') ||
                meta('meta[name="description"]') ||
                firstText('article h2') ||
                firstText('main h2');

              var title =
                (structured ? String(structured.headline || structured.name || '') : '') ||
                meta('meta[property="og:title"]') ||
                firstText('h1') ||
                document.title ||
                '';

              return JSON.stringify({
                title: title.trim(),
                source: source.trim(),
                date: date.trim(),
                author: author.trim(),
                subtitle: subtitle.trim(),
                text: body,
                url: location.href
              });
            })()
        """.trimIndent()

        web.evaluateJavascript(
            js,
        ) { raw ->
            runCatching {
                val value =
                    JSONTokener(
                        raw,
                    )
                        .nextValue()

                val inner =
                    value as? String
                        ?: error(
                            "Resposta JavaScript inválida.",
                        )

                val obj =
                    JSONObject(
                        inner,
                    )

                val extracted =
                    ExtractedArticle(
                        title =
                            obj.optString(
                                "title",
                            ),
                        source =
                            obj.optString(
                                "source",
                            ),
                        date =
                            obj.optString(
                                "date",
                            ),
                        author =
                            obj.optString(
                                "author",
                            ),
                        subtitle =
                            obj.optString(
                                "subtitle",
                            ),
                        text =
                            normalizeText(
                                obj.optString(
                                    "text",
                                ),
                            ),
                        url =
                            obj.optString(
                                "url",
                            )
                                .ifBlank {
                                    urlInput.text
                                        ?.toString()
                                        .orEmpty()
                                },
                    )

                if (
                    extracted.text.isBlank()
                ) {
                    error(
                        "Texto principal não identificado.",
                    )
                }

                applyResult(
                    extracted,
                )
            }.onFailure {
                status.text =
                    "A página abriu, mas o texto principal não pôde ser identificado."
                toast(
                    "Não foi possível extrair esta matéria.",
                )
            }
        }
    }

    private fun applyResult(
        rawResult: ExtractedArticle,
    ) {
        val result =
            normalizeArticle(
                rawResult,
            )

        val values =
            mapOf(
                "title" to
                    result.title,
                "source" to
                    result.source,
                "date" to
                    result.date,
                "author" to
                    result.author,
                "subtitle" to
                    result.subtitle,
            )

        values.forEach {
                (key, value),
            ->
            meta[key]?.text =
                value.ifBlank {
                    "—"
                }
        }

        val formatted =
            formatDesktopOutput(
                result,
            )

        output.setText(
            formatted,
        )

        output.setSelection(
            0,
        )

        val words =
            result.text
                .split(
                    Regex(
                        "\\s+",
                    ),
                )
                .count {
                    it.isNotBlank()
                }

        counter.text =
            "$words palavra(s) • ${result.text.length} caractere(s)"

        urlInput.setText(
            result.url,
        )

        status.text =
            "✓ Matéria extraída no padrão Windows/Ubuntu."

        CentralDb(
            this,
        )
            .addHistory(
                "extract_news",
                result.title.ifBlank {
                    "Matéria extraída"
                },
                result.source,
                result.url,
            )
    }

    /**
     * Padroniza a saída do Android para o mesmo formato textual entregue pelo
     * motor Windows/Ubuntu:
     *
     * URL
     *
     * VEÍCULO
     *
     * *TÍTULO*
     *
     * _SUBTÍTULO_
     *
     * AUTOR
     * DATA
     *
     * CORPO EM PARÁGRAFOS
     */
    private fun formatDesktopOutput(
        article: ExtractedArticle,
    ): String {
        val blocks =
            mutableListOf<String>()

        blocks +=
            article.url.trim()

        blocks +=
            article.source.ifBlank {
                "—"
            }

        blocks +=
            if (
                article.title.isBlank()
            ) {
                "*—*"
            } else {
                "*${article.title.trim()}*"
            }

        if (
            article.subtitle.isNotBlank()
        ) {
            blocks +=
                "_${article.subtitle.trim()}_"
        }

        blocks +=
            buildString {
                append(
                    article.author.ifBlank {
                        "—"
                    },
                )

                append(
                    "\n",
                )

                append(
                    article.date.ifBlank {
                        "—"
                    },
                )
            }

        if (
            article.text.isNotBlank()
        ) {
            blocks +=
                article.text.trim()
        }

        return blocks
            .filter {
                it.isNotBlank()
            }
            .joinToString(
                "\n\n",
            )
            .trim()
    }

    private fun normalizeArticle(
        input: ExtractedArticle,
    ): ExtractedArticle {
        val directUrl =
            input.url
                .trim()

        val source =
            canonicalSource(
                input.source,
                directUrl,
            )

        val title =
            cleanInline(
                input.title,
            )

        val subtitle =
            cleanInline(
                input.subtitle,
            )

        val author =
            cleanAuthor(
                input.author,
            )

        val date =
            formatArticleDate(
                input.date,
            )

        val body =
            cleanArticleBody(
                input.text,
                title,
                subtitle,
                author,
                date,
            )

        return input.copy(
            title =
                title,
            source =
                source,
            date =
                date,
            author =
                author,
            subtitle =
                subtitle,
            text =
                body,
            url =
                directUrl,
        )
    }

    private fun cleanInline(
        value: String,
    ): String =
        value
            .replace(
                Regex(
                    "\\s+",
                ),
                " ",
            )
            .trim()
            .trim(
                '|',
                '•',
            )
            .trim()

    private fun cleanAuthor(
        value: String,
    ): String =
        cleanInline(
            value,
        )
            .replace(
                Regex(
                    "^(por|by)\\s+",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(
                Regex(
                    "\\s*[|•]\\s*$",
                ),
                "",
            )
            .trim()

    private fun formatArticleDate(
        value: String,
    ): String {
        val raw =
            cleanInline(
                value,
            )

        if (
            raw.isBlank()
        ) {
            return ""
        }

        Regex(
            "(\\d{4})-(\\d{2})-(\\d{2})",
        )
            .find(
                raw,
            )
            ?.let {
                match,
            ->
                val year =
                    match.groupValues[
                        1
                    ]

                val month =
                    match.groupValues[
                        2
                    ]

                val day =
                    match.groupValues[
                        3
                    ]

                return "$day/$month/$year"
            }

        Regex(
            "(\\d{2})[/-](\\d{2})[/-](\\d{4})",
        )
            .find(
                raw,
            )
            ?.let {
                return "${it.groupValues[1]}/${it.groupValues[2]}/${it.groupValues[3]}"
            }

        return raw
            .substringBefore(
                "T",
            )
            .substringBefore(
                " | ",
            )
            .trim()
    }

    private fun canonicalSource(
        source: String,
        url: String,
    ): String {
        val clean =
            cleanInline(
                source,
            )

        if (
            clean.isNotBlank() &&
            !clean.contains(
                ".",
            )
        ) {
            return clean
        }

        val host =
            runCatching {
                URI(
                    url,
                )
                    .host
                    .orEmpty()
                    .lowercase()
                    .removePrefix(
                        "www.",
                    )
            }
                .getOrDefault(
                    "",
                )

        return when {
            "oglobo.globo.com" in
                host ->
                "O Globo"

            host ==
                "g1.globo.com" ||
                host.endsWith(
                    ".g1.globo.com",
                ) ->
                "g1"

            "folha.uol.com.br" in
                host ->
                "Folha de S.Paulo"

            "estadao.com.br" in
                host ->
                "Estadão"

            "cnnbrasil.com.br" in
                host ->
                "CNN Brasil"

            clean.isNotBlank() ->
                clean

            else ->
                host
        }
    }

    private fun cleanArticleBody(
        body: String,
        title: String,
        subtitle: String,
        author: String,
        date: String,
    ): String {
        val normalized =
            normalizeText(
                body,
            )

        if (
            normalized.isBlank()
        ) {
            return ""
        }

        fun comparable(
            value: String,
        ): String =
            value
                .lowercase()
                .replace(
                    Regex(
                        "[^\\p{L}\\p{N}]+",
                    ),
                    " ",
                )
                .replace(
                    Regex(
                        "\\s+",
                    ),
                    " ",
                )
                .trim()

        val remove =
            setOf(
                comparable(
                    title,
                ),
                comparable(
                    subtitle,
                ),
                comparable(
                    author,
                ),
                comparable(
                    date,
                ),
            )
                .filter {
                    it.isNotBlank()
                }
                .toSet()

        return normalized
            .split(
                Regex(
                    "\\n\\s*\\n",
                ),
            )
            .map {
                it.trim()
            }
            .filter {
                paragraph,
            ->
                if (
                    paragraph.isBlank()
                ) {
                    false
                } else {
                    val cmp =
                        comparable(
                            paragraph,
                        )

                    cmp !in
                        remove &&
                        !cmp.equals(
                            "publicidade",
                            true,
                        ) &&
                        !cmp.equals(
                            "continua depois da publicidade",
                            true,
                        )
                }
            }
            .distinct()
            .joinToString(
                "\n\n",
            )
            .trim()
    }

    private fun normalizeText(
        value: String,
    ): String =
        value
            .replace(
                "\r\n",
                "\n",
            )
            .replace(
                "\r",
                "\n",
            )
            .replace(
                Regex(
                    "[ \\t]+\\n",
                ),
                "\n",
            )
            .replace(
                Regex(
                    "\\n{3,}",
                ),
                "\n\n",
            )
            .trim()

    private fun openCurrentArticle() {
        val raw =
            urlInput.text
                ?.toString()
                .orEmpty()
                .trim()

        if (
            raw.isBlank()
        ) {
            toast(
                "Nenhum link informado.",
            )
            return
        }

        status.text =
            "Preparando link da matéria…"

        io(
            {
                resolver.resolve(
                    raw,
                )
                    .ifBlank {
                        raw
                    }
            },
            { direct ->
                urlInput.setText(
                    direct,
                )

                openUrl(
                    direct,
                )

                status.text =
                    "Matéria aberta no navegador."
            },
            {
                openUrl(
                    raw,
                )
            },
        )
    }

    private fun clearAll() {
        webFallbackRunning = false

        urlInput.setText(
            "",
        )

        output.setText(
            "",
        )

        meta.values.forEach {
            it.text = "—"
        }

        counter.text =
            "Nenhuma matéria extraída"

        status.text =
            "Pronto para receber um link da aba Notícias."

        if (
            ::web.isInitialized
        ) {
            web.stopLoading()
            web.loadUrl(
                "about:blank",
            )
        }
    }

    private fun copyText() {
        val text =
            output.text
                ?.toString()
                .orEmpty()

        if (
            text.isBlank()
        ) {
            toast(
                "Nenhum texto extraído.",
            )
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

        toast(
            "Texto copiado.",
        )
    }

    private fun saveText() {
        val text =
            output.text
                ?.toString()
                .orEmpty()

        if (
            text.isBlank()
        ) {
            toast(
                "Nada para salvar.",
            )
            return
        }

        val values =
            ContentValues().apply {
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

        val uri =
            contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values,
            )
                ?: run {
                    toast(
                        "Não foi possível criar o arquivo.",
                    )
                    return
                }

        contentResolver
            .openOutputStream(
                uri,
            )
            ?.use {
                it.write(
                    text.toByteArray(),
                )
            }

        toast(
            "Texto salvo em Downloads.",
        )
    }

    private fun shareText() {
        val text =
            output.text
                ?.toString()
                .orEmpty()

        if (
            text.isBlank()
        ) {
            toast(
                "Nenhum texto extraído.",
            )
            return
        }

        startActivity(
            Intent.createChooser(
                Intent(
                    Intent.ACTION_SEND,
                ).apply {
                    type =
                        "text/plain"

                    putExtra(
                        Intent.EXTRA_TEXT,
                        text,
                    )
                },
                "Compartilhar matéria",
            ),
        )
    }

    companion object {
        private const val MIN_DIRECT_TEXT =
            120

        private const val MIN_PARAGRAPH =
            30

        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/125.0 Safari/537.36"
    }
}
