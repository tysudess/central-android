package br.com.centralmidia.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import br.com.centralmidia.android.core.dp
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * Ícones de veículos para as listas de fontes e cartões de notícias.
 *
 * - Usa a URL oficial publicada no RSS quando disponível.
 * - Para links do Google Notícias, usa um catálogo de domínios conhecidos.
 * - Baixa favicons fora da thread de UI e limita tamanho/concorrência.
 * - Mantém as iniciais como fallback se a rede falhar.
 */
object VehicleIconView {
    private const val MAX_FAVICON_BYTES = 256 * 1024
    private const val MAX_CACHE_KB = 2 * 1024
    private const val FAVICON_BASE = "https://www.google.com/s2/favicons"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val loading = mutableSetOf<String>()
    private val attempted = mutableSetOf<String>()
    private val pendingTargets = mutableMapOf<String, MutableList<Target>>()
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 3
            maxRequestsPerHost = 3
        })
        .build()

    @Volatile
    private var fallbackSiteMap: Map<String, String>? = null

    private data class Target(
        val image: WeakReference<ImageView>,
        val fallback: WeakReference<View>,
    )

    /** Creates an adaptive logo badge; its initials remain visible until a favicon loads. */
    fun create(
        context: Context,
        sourceName: String,
        sourceUrl: String = "",
        articleUrl: String = "",
        widthDp: Int = 48,
        heightDp: Int = 48,
    ): View {
        val appContext = context.applicationContext
        val root = FrameLayout(context).apply {
            background = MobileUi.rounded(
                Color.WHITE,
                context.dp(9).toFloat(),
                MobileUi.BORDER,
                context.dp(1),
            )
            clipToPadding = true
        }

        val initials = initials(sourceName)
        val fallback = TextView(context).apply {
            text = initials
            textSize = 11f
            setTextColor(MobileUi.BLUE_DEEP)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = MobileUi.rounded(
                MobileUi.BLUE_TINT,
                context.dp(8).toFloat(),
            )
        }
        root.addView(
            fallback,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val logo = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(context.dp(5), context.dp(5), context.dp(5), context.dp(5))
            background = MobileUi.rounded(
                Color.WHITE,
                context.dp(8).toFloat(),
            )
            visibility = View.GONE
        }
        root.addView(
            logo,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.layoutParams = android.widget.LinearLayout.LayoutParams(
            context.dp(widthDp),
            context.dp(heightDp),
        )

        val site = resolveSite(
            appContext,
            sourceName,
            sourceUrl,
            articleUrl,
        )
        if (site.isNotBlank()) {
            load(site, logo, fallback)
        }
        return root
    }

    /** Pure domain normalization is intentionally conservative: only public HTTP(S) sites. */
    fun normalizeSiteUrl(value: String): String {
        val uri = runCatching { android.net.Uri.parse(value.trim()) }.getOrNull() ?: return ""
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.').orEmpty()
        if (scheme !in setOf("http", "https") || host.isBlank()) return ""
        if (uri.encodedAuthority?.contains('@') == true) return ""
        if (host == "localhost" || host.endsWith(".local") || !host.contains('.')) return ""
        if (host in GOOGLE_WRAPPER_HOSTS || host.endsWith(".google.com") || host.endsWith(".googleusercontent.com")) return ""
        if (host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))) return ""
        return "$scheme://$host/"
    }

    private fun resolveSite(
        context: Context,
        sourceName: String,
        sourceUrl: String,
        articleUrl: String,
    ): String {
        normalizeSiteUrl(sourceUrl).takeIf { it.isNotBlank() }?.let { return it }
        normalizeSiteUrl(articleUrl).takeIf { it.isNotBlank() }?.let { return it }

        val wanted = normalizeKey(sourceName)
        if (wanted.isBlank()) return ""
        val sites = fallbackSites(context)
        sites[wanted]?.let { return normalizeSiteUrl(it) }

        // The desktop catalogue contains a few historic aliases. Match only a
        // unique normalized name to avoid assigning another outlet's logo.
        val matches = sites.filterKeys { key ->
            key.startsWith(wanted) || wanted.startsWith(key)
        }.values.distinct()
        return if (matches.size == 1) normalizeSiteUrl(matches.first()) else ""
    }

    private fun fallbackSites(context: Context): Map<String, String> {
        fallbackSiteMap?.let { return it }
        synchronized(this) {
            fallbackSiteMap?.let { return it }
            val loaded = runCatching {
                context.assets.open("source_site_fallbacks.json").bufferedReader().use { reader ->
                    val json = JSONObject(reader.readText())
                    buildMap {
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val name = keys.next()
                            val site = normalizeSiteUrl(json.optString(name))
                            val normalized = normalizeKey(name)
                            if (normalized.isNotBlank() && site.isNotBlank()) put(normalized, site)
                        }
                    }
                }
            }.getOrDefault(emptyMap())
            fallbackSiteMap = loaded
            return loaded
        }
    }

    private fun load(
        site: String,
        image: ImageView,
        fallback: View,
    ) {
        val domain = android.net.Uri.parse(site).host?.lowercase(Locale.ROOT).orEmpty()
        if (domain.isBlank()) return
        image.tag = domain
        val cached = synchronized(lock) { cache.get(domain) }
        if (cached != null) {
            showBitmap(image, fallback, domain, cached)
            return
        }

        var startRequest = false
        synchronized(lock) {
            if (domain in attempted) return
            pendingTargets.getOrPut(domain) { mutableListOf() }.add(
                Target(WeakReference(image), WeakReference(fallback)),
            )
            if (loading.add(domain)) startRequest = true
        }
        if (!startRequest) return

        val encoded = URLEncoder.encode(site, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("$FAVICON_BASE?domain_url=$encoded&sz=64")
            .header("User-Agent", "Mozilla/5.0 CentralInteligenteDeMidiaAndroid/1.0")
            .header("Accept", "image/*")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                finish(domain, null)
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                val bitmap = response.use responseUse@ { res ->
                    if (!res.isSuccessful) return@responseUse null
                    val mime = res.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                    if (!mime.startsWith("image/")) return@responseUse null
                    val body = res.body ?: return@responseUse null
                    val declaredLength = body.contentLength()
                    if (declaredLength > MAX_FAVICON_BYTES) return@responseUse null
                    val bytes = body.byteStream().use streamUse@ { stream ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_FAVICON_BYTES) return@streamUse null
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: return@responseUse null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                finish(domain, bitmap)
            }
        })
    }

    private fun finish(domain: String, bitmap: Bitmap?) {
        val targets = synchronized(lock) {
            loading.remove(domain)
            attempted.add(domain)
            if (bitmap != null && !bitmap.isRecycled) cache.put(domain, bitmap)
            pendingTargets.remove(domain).orEmpty()
        }
        if (bitmap == null || bitmap.isRecycled) return
        mainHandler.post {
            targets.forEach { target ->
                val image = target.image.get() ?: return@forEach
                val fallback = target.fallback.get() ?: return@forEach
                showBitmap(image, fallback, domain, bitmap)
            }
        }
    }

    private fun showBitmap(image: ImageView, fallback: View, domain: String, bitmap: Bitmap) {
        if (image.tag != domain) return
        image.setImageBitmap(bitmap)
        image.visibility = View.VISIBLE
        fallback.visibility = View.GONE
    }

    private fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .take(2).joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "F" }.take(2)

    private fun normalizeKey(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]"), "")
    }

    private val GOOGLE_WRAPPER_HOSTS = setOf(
        "google.com",
        "www.google.com",
        "news.google.com",
        "g.co",
        "googleusercontent.com",
    )
}
