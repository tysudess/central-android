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

                    // Aguarda a hidratação do DOM, como o motor persistente Windows.
                    view?.postDelayed({
                        if (webFallbackRunning) evaluateWebExtraction()
                    }, WEB_EXTRACTION_SETTLE_MS)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incoming = intent.getStringExtra("url").orEmpty().trim()
        if (incoming.isNotBlank() && ::urlInput.isInitialized) {
            urlInput.setText(incoming)
            status.text = if (resolver.isGoogleNews(incoming)) {
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
        val linkRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        linkRow.addView(urlInput, LinearLayout.LayoutParams(0, dp(50), 1f).apply {
            topMargin = dp(9)
            marginEnd = dp(7)
        })
        linkRow.addView(
            MobileUi.button(this, "Colar link", false, MobileUi.NAVY, R.drawable.ic_copy) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val pasted = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
                if (pasted.isBlank()) toast("A área de transferência está vazia.")
                else {
                    urlInput.setText(pasted)
                    urlInput.setSelection(urlInput.text.length)
                    status.text = "Link colado. Toque em Extrair matéria."
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(50)).apply { topMargin = dp(9) },
        )
        box.addView(linkRow, MobileUi.match())

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

        val actionGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun actionRow(first: View, second: View? = null) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(first, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                if (second != null) marginEnd = dp(6)
            })
            if (second != null) row.addView(second, LinearLayout.LayoutParams(0, dp(42), 1f))
            actionGrid.addView(row, MobileUi.match(if (actionGrid.childCount > 0) dp(6) else 0))
        }
        actionRow(
            MobileUi.button(this, "Limpar", false, MobileUi.PINK, R.drawable.ic_delete) { clearAll() },
            MobileUi.button(this, "Abrir matéria", false, MobileUi.NAVY, R.drawable.ic_open) { openCurrentArticle() },
        )
        actionRow(
            MobileUi.button(this, "Copiar matéria", false, MobileUi.NAVY, R.drawable.ic_copy) { copyText() },
            MobileUi.button(this, "Salvar TXT", false, MobileUi.BLUE, R.drawable.ic_news) { saveText() },
        )
        actionRow(MobileUi.button(this, "Compartilhar", false, MobileUi.GREEN, R.drawable.ic_share) { shareText() })
        box.addView(actionGrid, MobileUi.match(dp(9)))

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

                val bodyIsLikelyComplete =
                    if (isValorUrl(direct)) {
                        !valorBodyLooksShort(result.text)
                    } else {
                        !genericBodyLooksShort(result.text)
                    }

                if (
                    result.text.length >= MIN_DIRECT_TEXT &&
                    bodyIsLikelyComplete
                ) {
                    applyResult(
                        result,
                    )
                } else {
                    // O HTTP pode ter recebido apenas resumo ou um DOM parcial.
                    // O WebView interno ganha uma chance de hidratar o corpo completo.
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

    /**
     * Replica no Android a estratégia específica usada pelo motor Windows para o Valor:
     * tenta a URL publicada e a rota /google/amp/, compara os corpos e, quando
     * o corpo parece curto, examina também o HTML entregue publicamente pelo site.
     */
    private fun extractWithHttp(
        url: String,
    ): ExtractedArticle {
        if (!isValorUrl(url)) {
            return extractWithHttpGeneric(url)
        }

        val routes = linkedSetOf(url.trim())
        valorAlternativeUrl(url)?.let { routes.add(it) }
        var best: ExtractedArticle? = null
        val failures = mutableListOf<String>()

        for (route in routes) {
            var routeArticle: ExtractedArticle? = null

            try {
                routeArticle = extractWithHttpSingle(route).copy(url = url)
                best = chooseBetterValorArticle(best, routeArticle)
            } catch (error: Exception) {
                failures += "$route: ${error.message.orEmpty()}"
            }

            if (best == null || valorBodyLooksShort(best.text)) {
                try {
                    val html = downloadValorHtml(route)
                    val candidate = extractValorArticleFromHtml(
                        html = html,
                        finalUrl = route,
                        originalUrl = url,
                        reference = routeArticle ?: best,
                    )
                    best = chooseBetterValorArticle(best, candidate)
                } catch (error: Exception) {
                    failures += "$route [HTML]: ${error.message.orEmpty()}"
                }
            }
        }

        val result = best?.takeIf { it.text.isNotBlank() }
            ?: throw IllegalStateException(
                "Não foi possível extrair a matéria do Valor pelas rotas públicas disponíveis." +
                    if (failures.isEmpty()) "" else " ${failures.joinToString(" | ")}",
            )

        return normalizeArticle(result.copy(url = url))
    }

    /**
     * Fallback geral para portais especializados e sites com CMS diferentes.
     * Primeiro lê a URL original; se o corpo parecer um resumo, tenta apenas
     * rotas AMP comuns e conserva o maior texto realmente encontrado.
     */
    private fun extractWithHttpGeneric(
        url: String,
    ): ExtractedArticle {
        val original = url.trim()
        val routes = linkedSetOf(original)
        genericAlternativeArticleUrls(original).forEach { routes.add(it) }
        var best: ExtractedArticle? = null
        val failures = mutableListOf<String>()

        for (route in routes) {
            if (best != null && !genericBodyLooksShort(best.text)) break
            try {
                val candidate = extractWithHttpSingle(route).copy(url = original)
                if (best == null || candidate.text.trim().length > best.text.trim().length) {
                    best = candidate
                }
            } catch (error: Exception) {
                failures += "$route: ${error.message.orEmpty()}"
            }
        }

        return best?.takeIf { it.text.isNotBlank() }?.let {
            normalizeArticle(it.copy(url = original))
        } ?: throw IllegalStateException(
            "Não foi possível identificar o corpo da matéria no HTML recebido." +
                if (failures.isEmpty()) "" else " ${failures.joinToString(" | ")}",
        )
    }

    /** Gera alternativas comuns sem alterar host, caminho original ou parâmetros existentes. */
    private fun genericAlternativeArticleUrls(url: String): List<String> {
        val output = linkedSetOf<String>()
        val withoutFragment = url.substringBefore('#')
        val uri = runCatching { URI(withoutFragment) }.getOrNull() ?: return emptyList()
        val path = uri.rawPath.orEmpty()
        val lowerPath = path.lowercase()

        if (
            path.isNotBlank() &&
            !lowerPath.endsWith("/amp") &&
            !lowerPath.endsWith("/amp/") &&
            !lowerPath.endsWith(".amp")
        ) {
            val pathIndex = withoutFragment.indexOf(path)
            if (pathIndex >= 0) {
                val alternativePath = path.trimEnd('/') + "/amp/"
                output += withoutFragment.substring(0, pathIndex) +
                    alternativePath +
                    withoutFragment.substring(pathIndex + path.length)
            }
        }

        if (!Regex("(?:[?&])amp(?:=|&|$)", RegexOption.IGNORE_CASE).containsMatchIn(withoutFragment)) {
            val separator = if ('?' in withoutFragment) "&" else "?"
            output += "$withoutFragment${separator}amp=1"
        }

        return output.filter { it.isNotBlank() && it != withoutFragment }
    }

    private fun genericBodyLooksShort(text: String): Boolean {
        val cleaned = text.trim()
        val blocks = cleaned.split(Regex("\\n\\s*\\n+")).count { it.isNotBlank() }
        return cleaned.length < GENERIC_MIN_BODY_CHARS || blocks < GENERIC_MIN_BODY_BLOCKS
    }

    private fun isValorUrl(url: String): Boolean =
        runCatching {
            val host = URI(url).host.orEmpty().lowercase()
            host == "valor.globo.com" || host.endsWith(".valor.globo.com")
        }.getOrDefault(false)

    /** Cria a rota alternativa idêntica à do motor Windows: /google/amp/. */
    private fun valorAlternativeUrl(url: String): String? = runCatching {
        val uri = URI(url)
        val host = uri.host.orEmpty().lowercase()
        if (host != "valor.globo.com" && !host.endsWith(".valor.globo.com")) {
            return@runCatching null
        }

        val rawPath = uri.rawPath.orEmpty().ifBlank { "/" }
        val alternatePath = if (rawPath.startsWith("/google/amp/")) {
            rawPath.removePrefix("/google/amp")
        } else {
            "/google/amp" + if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        }
        val pathIndex = url.indexOf(rawPath)
        if (pathIndex < 0) return@runCatching null
        url.substring(0, pathIndex) + alternatePath + url.substring(pathIndex + rawPath.length)
    }.getOrNull()?.takeIf { it.isNotBlank() && it != url }

    private fun valorBodyLooksShort(text: String): Boolean {
        val clean = text.trim()
        val blocks = clean.split(Regex("""\n\s*\n+""")).filter { it.isNotBlank() }
        return clean.length < VALOR_MIN_BODY_CHARS || blocks.size < VALOR_MIN_BODY_BLOCKS
    }

    private fun chooseBetterValorArticle(
        current: ExtractedArticle?,
        candidate: ExtractedArticle?,
    ): ExtractedArticle? {
        if (current == null) return candidate
        if (candidate == null) return current
        return if (candidate.text.trim().length > current.text.trim().length) candidate else current
    }

    /** Download do HTML público: mesmo User-Agent, idioma e fallback de duas tentativas do Windows. */
    private fun downloadValorHtml(url: String): String {
        var lastError: Exception? = null
        for (attempt in 1..2) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/149.0.0.0 Safari/537.36",
                    )
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Connection", "close")
                    .build()

                return http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code} ao acessar o Valor.")
                    }
                    response.body?.string().orEmpty().also {
                        if (it.isBlank()) error("O Valor retornou HTML vazio.")
                    }
                }
            } catch (error: Exception) {
                lastError = error
                if (attempt == 2) break
            }
        }
        throw lastError ?: IllegalStateException("Não foi possível baixar o HTML público do Valor.")
    }

    /** Extrai o maior corpo visível do HTML, com os mesmos filtros de conteúdo do motor Windows. */
    private fun extractValorArticleFromHtml(
        html: String,
        finalUrl: String,
        originalUrl: String,
        reference: ExtractedArticle?,
    ): ExtractedArticle? {
        val doc = Jsoup.parse(html, finalUrl)
        val canonicalUrl = doc.selectFirst("link[rel=canonical]")
            ?.absUrl("href")
            .orEmpty()
            .trim()
            .ifBlank { finalUrl }
            .substringBefore("#")
        val structured = extractStructuredArticle(doc, canonicalUrl)

        fun metaValue(selector: String): String =
            doc.selectFirst(selector)?.attr("content").orEmpty().trim()

        val title = reference?.title.orEmpty().ifBlank { structured?.title.orEmpty() }
            .ifBlank { metaValue("meta[property=og:title]") }
            .ifBlank { doc.selectFirst("h1")?.text().orEmpty() }
            .ifBlank { doc.title() }
        val source = reference?.source.orEmpty().ifBlank { structured?.source.orEmpty() }
            .ifBlank { metaValue("meta[property=og:site_name]") }
            .ifBlank { "Valor Econômico" }
        val date = reference?.date.orEmpty().ifBlank { structured?.date.orEmpty() }
            .ifBlank { metaValue("meta[property=article:published_time]") }
            .ifBlank { metaValue("meta[name=date]") }
            .ifBlank { doc.selectFirst("time[datetime]")?.attr("datetime").orEmpty() }
        val author = reference?.author.orEmpty().ifBlank { structured?.author.orEmpty() }
            .ifBlank { metaValue("meta[name=author]") }
            .ifBlank { metaValue("meta[property=article:author]") }
        val subtitle = reference?.subtitle.orEmpty().ifBlank { structured?.subtitle.orEmpty() }
            .ifBlank { metaValue("meta[property=og:description]") }
            .ifBlank { metaValue("meta[name=description]") }

        val metadata = ExtractedArticle(
            title = title,
            source = source,
            date = date,
            author = author,
            subtitle = subtitle,
            text = "",
            url = originalUrl,
        )
        val visibleBody = extractVisibleValorBody(doc, metadata)
        val structuredBody = structured?.text.orEmpty().trim()
        val existingBody = reference?.text.orEmpty().trim()
        val body = listOf(visibleBody, structuredBody, existingBody)
            .filter { it.isNotBlank() }
            .maxByOrNull { it.length }
            .orEmpty()

        if (body.isBlank()) return null
        return normalizeArticle(metadata.copy(text = body, url = originalUrl))
    }

    private fun extractVisibleValorBody(
        doc: org.jsoup.nodes.Document,
        metadata: ExtractedArticle,
    ): String {
        val selectors = listOf(
            "[itemprop=articleBody]",
            ".mc-article-body",
            ".content-text",
            "[class*=article-body]",
            "[class*=article-content]",
            "article",
            "main",
        )
        val candidates = linkedSetOf<org.jsoup.nodes.Element>()
        selectors.forEach { selector ->
            doc.select(selector).forEach { element ->
                if (!isValorHiddenOrRestricted(element)) candidates.add(element)
            }
        }

        val container = candidates.maxByOrNull { valorContainerScore(it) } ?: return ""
        val excluded = setOf(
            metadata.title,
            metadata.subtitle,
            metadata.author,
            metadata.date,
        ).filter { it.isNotBlank() }.map { it.trim().lowercase() }.toSet()
        val output = mutableListOf<String>()

        container.select("p, h2, h3, blockquote").forEach { element ->
            if (isValorHiddenOrRestricted(element)) return@forEach
            val text = normalizeText(element.text()).trim()
            if (text.isBlank() || text.lowercase() in excluded || isValorJunk(text)) return@forEach

            val tag = element.normalName()
            if (valorLinkDensity(element) > 0.70 && text.length < 340) return@forEach
            if ((tag == "p" || tag == "blockquote") && text.length < 45) return@forEach
            if ((tag == "h2" || tag == "h3") && text.length < 10) return@forEach
            if (output.lastOrNull() != text) output += text
        }
        return output.joinToString("\n\n").trim()
    }

    private fun valorContainerScore(element: org.jsoup.nodes.Element): Int {
        var score = 0
        element.select("p, blockquote").forEach { paragraph ->
            if (isValorHiddenOrRestricted(paragraph)) return@forEach
            val textLength = paragraph.text().trim().length
            if (textLength >= 60) score += textLength.coerceAtMost(1500)
        }
        return score
    }

    private fun isValorHiddenOrRestricted(element: org.jsoup.nodes.Element): Boolean {
        var current: org.jsoup.nodes.Element? = element
        val restricted = Regex(
            """(?:^|[\s_-])(paywall|signwall|subscription|subscriber|subscribe|premium-wall|login-wall|content-blocked|assinatura|assine)(?:[\s_-]|$)""",
            RegexOption.IGNORE_CASE,
        )
        while (current != null) {
            if (current.hasAttr("hidden") || current.attr("aria-hidden").equals("true", ignoreCase = true)) {
                return true
            }
            val style = current.attr("style").lowercase().replace(Regex("""\s+"""), "")
            if ("display:none" in style || "visibility:hidden" in style) return true
            val marker = "${current.id()} ${current.classNames().joinToString(" ")}".lowercase()
            if (restricted.containsMatchIn(marker)) return true
            current = current.parent()
        }
        return false
    }

    private fun isValorJunk(value: String): Boolean {
        val text = value.trim()
        return text.matches(Regex("^(publicidade|anúncio|anuncio|compartilhar|salvar|ouvir|mais lidas|mais lidos|só para assinantes|matéria exclusiva para assinantes)$", RegexOption.IGNORE_CASE)) ||
            text.matches(Regex("""^(leia|veja|confira)\s+(tamb[eé]m|mais)\b.*""", RegexOption.IGNORE_CASE)) ||
            text.matches(Regex("""^(assine|j[aá] é assinante|fa[cç]a login)\b.*""", RegexOption.IGNORE_CASE))
    }

    private fun valorLinkDensity(element: org.jsoup.nodes.Element): Double {
        val total = element.text().trim().length
        if (total == 0) return 0.0
        val linked = element.select("a").sumOf { it.text().trim().length }
        return linked.toDouble() / total.toDouble()
    }

    private fun extractWithHttpSingle(
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

                val canonicalUrl =
                    doc.selectFirst(
                        "link[rel=canonical]",
                    )
                        ?.absUrl(
                            "href",
                        )
                        .orEmpty()
                        .trim()
                        .ifBlank {
                            finalUrl
                        }
                        .substringBefore(
                            "#",
                        )

                // O extrator desktop devolve campos estruturados (título, veículo,
                // data, autor, subtítulo e corpo formatado). No Android tentamos
                // primeiro o JSON-LD NewsArticle/Article da própria página, que é
                // normalmente a fonte mais fiel para esses mesmos campos.
                val structured =
                    extractStructuredArticle(
                        doc,
                        canonicalUrl,
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
                                    canonicalUrl,
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

                val editorialMetadata = ExtractedArticle(
                    title = title,
                    source = source,
                    date = date,
                    author = author,
                    subtitle = subtitle,
                    text = "",
                    url = canonicalUrl,
                )
                val visibleText = extractBestEditorialBody(
                    doc = doc,
                    metadata = editorialMetadata,
                )
                val structuredText = structured?.text.orEmpty().trim()
                val text = listOf(visibleText, structuredText)
                    .filter { it.isNotBlank() }
                    .maxByOrNull { it.length }
                    .orEmpty()

                return normalizeArticle(
                    editorialMetadata.copy(
                        text = normalizeText(text),
                        url = canonicalUrl,
                    ),
                )
            }
    }

    /**
     * Descobre o corpo do artigo por pontuação, em vez de escolher o primeiro
     * seletor que existir. Isso cobre WordPress, Elementor e temas de portais
     * regionais/especializados que não usam somente <article> ou main.
     */
    private fun extractBestEditorialBody(
        doc: org.jsoup.nodes.Document,
        metadata: ExtractedArticle,
    ): String {
        val selectors = listOf(
            "[itemprop=articleBody]",
            "[data-testid*=article-body]",
            "[data-testid*=article-content]",
            ".entry-content",
            ".td-post-content",
            ".wp-block-post-content",
            ".elementor-widget-theme-post-content",
            ".post-content",
            ".post-body",
            ".post__content",
            ".article-content",
            ".article-body",
            ".article__content",
            ".article__body",
            ".article-text",
            ".article__text",
            ".story-content",
            ".story-body",
            ".content-single",
            ".single-content",
            ".the-content",
            ".content-text",
            ".texto-materia",
            ".materia-conteudo",
            ".materia__texto",
            ".conteudo-materia",
            ".conteudo-noticia",
            "[class*=entry-content]",
            "[class*=td-post-content]",
            "[class*=post-content]",
            "[class*=post-body]",
            "[class*=article-body]",
            "[class*=article-content]",
            "[class*=content-text]",
            "[id*=article-body]",
            "[id*=article-content]",
            "article",
            "main",
        )
        val candidates = linkedSetOf<org.jsoup.nodes.Element>()
        for (selector in selectors) {
            runCatching { doc.select(selector).forEach { candidates.add(it) } }
        }

        val excludedMetadata = setOf(
            metadata.title,
            metadata.subtitle,
            metadata.author,
            metadata.date,
        ).filter { it.isNotBlank() }.map { comparableArticleText(it) }.toSet()

        fun bodyFor(container: org.jsoup.nodes.Element): String {
            if (isEditorialJunkElement(container)) return ""
            val paragraphs = mutableListOf<String>()
            val seen = hashSetOf<String>()
            container.select("p, blockquote, h2, h3, li").forEach { element ->
                if (isEditorialJunkElement(element)) return@forEach
                val text = normalizeText(element.text()).trim()
                if (text.isBlank()) return@forEach
                val tag = element.normalName()
                val minimum = if (tag == "li") 12 else if (tag == "h2" || tag == "h3") 10 else 25
                if (text.length < minimum) return@forEach
                val comparable = comparableArticleText(text)
                if (comparable in excludedMetadata || !seen.add(comparable)) return@forEach
                val linked = element.select("a").sumOf { it.text().trim().length }
                if (text.isNotEmpty() && linked.toDouble() / text.length.toDouble() > 0.72 && text.length < 340) {
                    return@forEach
                }
                if (isEditorialJunkText(text)) return@forEach
                paragraphs += text
            }
            return paragraphs.joinToString("\n\n").trim()
        }

        var bestBody = ""
        var bestScore = Int.MIN_VALUE
        for (container in candidates) {
            val body = bodyFor(container)
            if (body.isBlank()) continue
            val marker = "${container.id()} ${container.classNames().joinToString(" ")}".lowercase()
            val specificity = when {
                "articlebody" in marker || "article-body" in marker || "entry-content" in marker ||
                    "td-post-content" in marker || "post-content" in marker || "article-content" in marker -> 2_400
                container.normalName() == "article" -> 1_600
                container.normalName() == "main" -> 800
                else -> 0
            }
            val score = body.length + specificity
            if (score > bestScore) {
                bestScore = score
                bestBody = body
            }
        }

        if (bestBody.isNotBlank()) return bestBody
        // Último recurso: parágrafos do body, já sem navegação, anúncios laterais e rodapé.
        val body = doc.body() ?: return ""
        return bodyFor(body)
    }

    private fun isEditorialJunkElement(element: org.jsoup.nodes.Element): Boolean {
        val junkElementNames = setOf("nav", "footer", "aside", "form", "header")
        val junkMarker = Regex(
            """(?:related|recommend|most[-_ ]?read|mais[-_ ]?lidas|newsletter|comment|social|share|breadcrumb|advert|advertisement|publicidade|sidebar|menu-principal|post-navigation|related-posts|recommended)""",
            RegexOption.IGNORE_CASE,
        )
        var current: org.jsoup.nodes.Element? = element
        while (current != null) {
            if (current.normalName() in junkElementNames) return true
            val marker = "${current.id()} ${current.classNames().joinToString(" ")}".lowercase()
            if (junkMarker.containsMatchIn(marker)) return true
            current = current.parent()
        }
        return false
    }

    private fun isEditorialJunkText(value: String): Boolean {
        val text = value.trim()
        return text.matches(
            Regex(
                """^(publicidade|an[uú]ncio|continua (ap[oó]s|depois da) publicidade|leia tamb[eé]m|veja tamb[eé]m|mais lidas|mais not[ií]cias|compartilhar|compartilhe|s[oó] para assinantes|assine|receba nossas not[ií]cias|siga-nos|acompanhe nossas redes sociais)\b.*$""",
                RegexOption.IGNORE_CASE,
            ),
        )
    }

    private fun comparableArticleText(value: String): String =
        value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

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

              // O Valor e outros portais podem montar o corpo em um contêiner
              // próprio, sem usar <article> como elemento principal.
              // Motor Android inspirado no pipeline Windows: procura primeiro
              // os contêineres editoriais específicos e compara a quantidade real
              // de parágrafos úteis para não confundir resumo com matéria completa.
              var roots = document.querySelectorAll([
                '[itemprop="articleBody"]',
                '[data-testid*="article-body" i]',
                '[data-testid*="article-content" i]',
                '[class*="entry-content" i]',
                '[class*="td-post-content" i]',
                '[class*="wp-block-post-content" i]',
                '[class*="elementor-widget-theme-post-content" i]',
                '[class*="post-content" i]',
                '[class*="post-body" i]',
                '[class*="article-body" i]',
                '[class*="article-content" i]',
                '[class*="article__content" i]',
                '[class*="story-content" i]',
                '[class*="story-body" i]',
                '[class*="content-single" i]',
                '[class*="single-content" i]',
                '[class*="content-text" i]',
                '[class*="texto-materia" i]',
                '[class*="materia-conteudo" i]',
                '[class*="materia-corpo" i]',
                '[class*="conteudo-materia" i]',
                '[class*="conteudo-noticia" i]',
                '[id*="article-body" i]',
                '[id*="article-content" i]',
                'article', 'main'
              ].join(','));
              var root = null;
              var bestRootText = '';
              for (var r = 0; r < roots.length; r++) {
                var candidateText = (roots[r].innerText || roots[r].textContent || '').trim();
                if (candidateText.length > bestRootText.length && candidateText.length < 60000) {
                  root = roots[r];
                  bestRootText = candidateText;
                }
              }
              if (!root) root = document.querySelector('article') || document.querySelector('main') || document.body;

              function isJunk(el, value) {
                var t = String(value || '').replace(/\s+/g, ' ').trim();
                if (!t || /^(publicidade|continua após a publicidade|leia também|veja também|mais lidas|compartilhe|só para assinantes|assine valor)$/i.test(t)) return true;
                if (el.closest('nav,footer,aside,form,[class*="related" i],[id*="related" i],[class*="recommend" i],[class*="most-read" i],[class*="mais-lidas" i],[class*="share" i],[class*="social" i],[class*="newsletter" i],[class*="comment" i],[class*="breadcrumb" i],[class*="advert" i],[class*="publicidade" i],[class*="sidebar" i]')) return true;
                return false;
              }
              function collectParagraphs(container) {
                var out = [];
                if (!container) return out;
                var nodes = container.querySelectorAll('p, blockquote, h2, h3, li, [itemprop="articleBody"] p');
                for (var i = 0; i < nodes.length; i++) {
                  var el = nodes[i];
                  var value = (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
                  var minimum = /^(H2|H3)$/.test(el.tagName) ? 10 : (el.tagName === 'LI' ? 12 : 25);
                  if (value.length >= minimum && !isJunk(el, value) && out.indexOf(value) < 0) out.push(value);
                }
                return out;
              }
              var paragraphs = collectParagraphs(root);
              for (var rr = 0; rr < roots.length; rr++) {
                var alternative = collectParagraphs(roots[rr]);
                if (alternative.join('\n\n').length > paragraphs.join('\n\n').length && alternative.join('\n\n').length < 50000) paragraphs = alternative;
              }
              var body = paragraphs.join('\n\n');
              if (body.length < 120 && root) body = bestRootText;
              body = body.replace(/\n{3,}/g, '\n\n').trim();
              if (structured && structured.articleBody) {
                var structuredBody = String(structured.articleBody || '').replace(/\s+/g, ' ').trim();
                if (structuredBody.length > body.length) body = structuredBody;
              }
              var subscriberOnly = /só para assinantes|conteúdo exclusivo para assinantes|assine o valor/i.test(document.body ? document.body.innerText : '');

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

              var canonicalNode = document.querySelector('link[rel="canonical"]');
              var canonicalUrl =
                (canonicalNode && canonicalNode.href ? canonicalNode.href : location.href)
                  .split('#')[0];

              return JSON.stringify({
                title: title.trim(),
                source: source.trim(),
                date: date.trim(),
                author: author.trim(),
                subtitle: subtitle.trim(),
                text: body,
                url: canonicalUrl,
                subscriberOnly: subscriberOnly
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

                if (extracted.text.isBlank() || extracted.text.length < MIN_DIRECT_TEXT) {
                    val restricted = obj.optBoolean("subscriberOnly", false)
                    error(
                        if (restricted) {
                            "O Valor entregou apenas a prévia e sinalizou conteúdo para assinantes. Abra a matéria com sua sessão autorizada e tente novamente."
                        } else {
                            "O texto principal ainda parece incompleto. Abra a matéria no navegador e tente extrair novamente."
                        }
                    )
                }
                webFallbackRunning = false
                applyResult(extracted)
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
                .substringBefore(
                    "#",
                )

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
                directUrl,
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
        articleUrl: String,
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

        fun withoutLinks(
            value: String,
        ): String {
            var cleaned =
                value
                    // Markdown: mantém o texto visível e elimina somente o destino.
                    .replace(
                        Regex(
                            "\\[([^\\]]+)]\\(https?://[^)]+\\)",
                            RegexOption.IGNORE_CASE,
                        ),
                    ) { match ->
                        match.groupValues[
                            1
                        ]
                    }
                    // O motor desktop entrega texto jornalístico limpo; URLs soltas
                    // de 'leia também', publicidade ou cards não entram no corpo.
                    .replace(
                        Regex(
                            "https?://[^\\s<>()]+",
                            RegexOption.IGNORE_CASE,
                        ),
                        "",
                    )
                    .replace(
                        Regex(
                            "www\\.[^\\s<>()]+",
                            RegexOption.IGNORE_CASE,
                        ),
                        "",
                    )

            if (
                articleUrl.isNotBlank()
            ) {
                cleaned =
                    cleaned.replace(
                        articleUrl,
                        "",
                        ignoreCase = true,
                    )
            }

            return cleaned
                .replace(
                    Regex(
                        "[ \\t]{2,}",
                    ),
                    " ",
                )
                .trim()
                .trim(
                    '|',
                    '•',
                    '-',
                )
                .trim()
        }

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
                comparable(
                    articleUrl,
                ),
            )
                .filter {
                    it.isNotBlank()
                }
                .toSet()

        val junkPrefixes =
            listOf(
                "publicidade",
                "continua depois da publicidade",
                "leia também",
                "leia tambem",
                "veja também",
                "veja tambem",
                "saiba mais",
                "conteúdo recomendado",
                "conteudo recomendado",
                "mais lidas",
                "mais notícias",
                "mais noticias",
                "assine",
                "compartilhe",
                "siga o",
                "clique aqui",
            )

        return normalized
            .split(
                Regex(
                    "\\n\\s*\\n",
                ),
            )
            .map {
                withoutLinks(
                    it.trim(),
                )
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

                    cmp !in remove &&
                        junkPrefixes.none {
                            prefix,
                        ->
                            cmp == comparable(prefix) ||
                                cmp.startsWith(
                                    comparable(prefix) + " ",
                                )
                        } &&
                        // Elimina cards/linhas que eram essencialmente um link.
                        !Regex(
                            "^(https?://|www\\.)",
                            RegexOption.IGNORE_CASE,
                        ).containsMatchIn(
                            paragraph,
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
        // Tempo para a página concluir a hidratação do conteúdo antes da extração.
        private const val WEB_EXTRACTION_SETTLE_MS =
            1_600L

        private const val MIN_DIRECT_TEXT =
            120

        private const val MIN_PARAGRAPH =
            30

        private const val GENERIC_MIN_BODY_CHARS =
            850

        private const val GENERIC_MIN_BODY_BLOCKS =
            3

        private const val VALOR_MIN_BODY_CHARS =
            1_200

        private const val VALOR_MIN_BODY_BLOCKS =
            3

        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/125.0 Safari/537.36"
    }
}
