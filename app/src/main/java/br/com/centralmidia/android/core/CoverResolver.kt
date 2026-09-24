package br.com.centralmidia.android.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

class CoverResolver(private val context: Context) {
    data class Candidate(
        val file: File,
        val sourceUrl: String,
        val label: String,
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun resolve(name: String, urls: List<String>, date: String): List<Candidate> {
        val out = mutableListOf<Candidate>()
        urls.distinct().take(5).forEachIndexed { index, raw ->
            val url = raw.replace("{date}", date).replace("{yyyy-mm-dd}", date)
            runCatching { resolveOne(name, url, index) }.getOrNull()?.let { candidate ->
                if (out.none { it.file.length() == candidate.file.length() }) out += candidate
            }
        }
        return out
    }

    fun imageBytes(
        name: String,
        bytes: ByteArray,
        label: String,
        sourceUrl: String,
    ): Candidate? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val result = save(name, bitmap, label, sourceUrl)
        bitmap.recycle()
        return result
    }

    fun pdfBytes(name: String, bytes: ByteArray, label: String): Candidate? {
        val dir = folder(name)
        val pdf = File(dir, "source-" + System.nanoTime() + ".pdf")
        pdf.writeBytes(bytes)
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val factor = minOf(
                        1f,
                        2200f / maxOf(page.width, page.height).toFloat(),
                    )
                    val bitmap = Bitmap.createBitmap(
                        (page.width * factor).toInt().coerceAtLeast(1),
                        (page.height * factor).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                    )
                    val result = save(name, bitmap, label, "Apps Script / PDF")
                    bitmap.recycle()
                    return result
                }
            }
        }
    }

    private fun resolveOne(name: String, url: String, index: Int): Candidate? {
        val response = request(url)
        val type = response.first.lowercase()
        val bytes = response.second
        if (
            type.startsWith("image/") ||
            url.lowercase().matches(Regex(".*\\.(?:jpg|jpeg|png|webp)(?:\\?.*)?$"))
        ) {
            return imageBytes(name, bytes, "Fonte " + (index + 1), url)
        }
        if (type.contains("pdf") || url.lowercase().contains(".pdf")) {
            return pdfBytes(name, bytes, "PDF • Fonte " + (index + 1))
        }

        val html = bytes.toString(Charsets.UTF_8)
        val image = imageUrl(html, url) ?: return null
        val img = request(image)
        return imageBytes(name, img.second, "Página • Fonte " + (index + 1), image)
    }

    private fun request(url: String): Pair<String, ByteArray> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) CentralInteligenteMidia/1.0")
            .build()
        return http.newCall(req).execute().use { response ->
            if (!response.isSuccessful) error("HTTP " + response.code)
            val body = response.body ?: error("Resposta vazia")
            response.header("Content-Type").orEmpty() to body.bytes()
        }
    }

    private fun imageUrl(html: String, base: String): String? {
        val patterns = listOf(
            Regex("(?is)<meta[^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"'][^>]+content=[\"']([^\"']+)[\"']"),
            Regex("(?is)<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"']"),
            Regex("(?is)<img[^>]+(?:class|alt|title)=[\"'][^\"']*(?:capa|cover|front)[^\"']*[\"'][^>]+src=[\"']([^\"']+)[\"']"),
        )
        val raw = patterns.firstNotNullOfOrNull {
            it.find(html)?.groupValues?.getOrNull(1)
        }?.replace("&amp;", "&")?.trim() ?: return null
        return runCatching { URI(base).resolve(raw).toString() }.getOrDefault(raw)
    }

    private fun save(
        name: String,
        bitmap: Bitmap,
        label: String,
        sourceUrl: String,
    ): Candidate {
        val file = File(folder(name), "cover-" + System.nanoTime() + ".jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
        }
        return Candidate(file, sourceUrl, label)
    }

    private fun folder(name: String): File {
        val safe = name.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').ifBlank { "cover" }
        return File(context.cacheDir, "covers/" + safe).apply { mkdirs() }
    }
}
