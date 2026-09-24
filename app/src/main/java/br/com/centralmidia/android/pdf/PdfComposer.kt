package br.com.centralmidia.android.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlin.math.max

object PdfComposer {
    data class Item(
        val uri: Uri? = null,
        var rotation: Int = 0,
        var cropLeftPct: Int = 0,
        var cropTopPct: Int = 0,
        var cropRightPct: Int = 0,
        var cropBottomPct: Int = 0,
        var scalePercent: Int = 100,
        val blank: Boolean = false,
        val label: String = "",
    )

    fun compose(
        context: Context,
        items: List<Item>,
        cover: Uri?,
        out: java.io.OutputStream,
    ) = compose(context, items, cover, false, out)

    fun compose(
        context: Context,
        items: List<Item>,
        customCover: Uri?,
        includeDefaultCover: Boolean,
        out: java.io.OutputStream,
    ) {
        val doc = PdfDocument()
        var pageNo = 1
        try {
            if (includeDefaultCover) {
                runCatching {
                    context.assets.open("principais_capas_cover.png").use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()?.let { bmp ->
                    pageNo = addBitmap(doc, bmp, Item(label = "Capa padrão"), pageNo)
                    bmp.recycle()
                }
            }

            if (customCover != null) {
                decodeImage(context.contentResolver, customCover)?.let { bmp ->
                    pageNo = addBitmap(doc, bmp, Item(uri = customCover), pageNo)
                    bmp.recycle()
                }
            }

            items.forEach { item ->
                if (item.blank) {
                    pageNo = addBlank(doc, pageNo)
                    return@forEach
                }
                val uri = item.uri ?: return@forEach
                val mime = context.contentResolver.getType(uri).orEmpty()
                if (mime == "application/pdf" || uri.toString().lowercase().endsWith(".pdf")) {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            for (i in 0 until renderer.pageCount) {
                                renderer.openPage(i).use { page ->
                                    val ratio = minOf(
                                        1f,
                                        3000f / max(page.width, page.height).toFloat(),
                                    )
                                    val bmp = Bitmap.createBitmap(
                                        max(1, (page.width * ratio).toInt()),
                                        max(1, (page.height * ratio).toInt()),
                                        Bitmap.Config.ARGB_8888,
                                    )
                                    bmp.eraseColor(Color.WHITE)
                                    page.render(
                                        bmp,
                                        null,
                                        null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_PRINT,
                                    )
                                    pageNo = addBitmap(doc, bmp, item, pageNo)
                                    bmp.recycle()
                                }
                            }
                        }
                    }
                } else {
                    decodeImage(context.contentResolver, uri)?.let { bmp ->
                        pageNo = addBitmap(doc, bmp, item, pageNo)
                        bmp.recycle()
                    }
                }
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun decodeImage(cr: ContentResolver, uri: Uri): Bitmap? =
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }

    private fun addBlank(doc: PdfDocument, number: Int): Int {
        val info = PdfDocument.PageInfo.Builder(1240, 1754, number).create()
        val page = doc.startPage(info)
        page.canvas.drawColor(Color.WHITE)
        doc.finishPage(page)
        return number + 1
    }

    private fun addBitmap(
        doc: PdfDocument,
        source: Bitmap,
        item: Item,
        number: Int,
    ): Int {
        var bmp = source
        var owns = false

        val left = (source.width * item.cropLeftPct.coerceIn(0, 45) / 100f).toInt()
        val top = (source.height * item.cropTopPct.coerceIn(0, 45) / 100f).toInt()
        val right = (source.width * item.cropRightPct.coerceIn(0, 45) / 100f).toInt()
        val bottom = (source.height * item.cropBottomPct.coerceIn(0, 45) / 100f).toInt()
        val cropW = (source.width - left - right).coerceAtLeast(1)
        val cropH = (source.height - top - bottom).coerceAtLeast(1)

        if (left > 0 || top > 0 || right > 0 || bottom > 0) {
            bmp = Bitmap.createBitmap(source, left, top, cropW, cropH)
            owns = true
        }

        if (item.rotation % 360 != 0) {
            val rotated = Bitmap.createBitmap(
                bmp,
                0,
                0,
                bmp.width,
                bmp.height,
                Matrix().apply { postRotate(item.rotation.toFloat()) },
                true,
            )
            if (owns) bmp.recycle()
            bmp = rotated
            owns = true
        }

        val scale = item.scalePercent.coerceIn(25, 200) / 100f
        val edge = minOf(1f, 3000f / max(bmp.width, bmp.height).toFloat())
        val drawW = max(1, (bmp.width * edge * scale).toInt())
        val drawH = max(1, (bmp.height * edge * scale).toInt())
        val pageW = max(595, drawW)
        val pageH = max(842, drawH)

        val info = PdfDocument.PageInfo.Builder(pageW, pageH, number).create()
        val page = doc.startPage(info)
        page.canvas.drawColor(Color.WHITE)
        val x = (pageW - drawW) / 2
        val y = (pageH - drawH) / 2
        page.canvas.drawBitmap(
            bmp,
            null,
            Rect(x, y, x + drawW, y + drawH),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        doc.finishPage(page)
        if (owns) bmp.recycle()
        return number + 1
    }
}
