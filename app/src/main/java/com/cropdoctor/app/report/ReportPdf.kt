package com.cropdoctor.app.report

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.cropdoctor.app.knowledge.Info
import com.cropdoctor.app.knowledge.Lang
import java.io.File
import java.text.DateFormat
import java.util.Date

/** One-page A4 diagnosis report the farmer can send to a KVK officer or family on WhatsApp. */
object ReportPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 36f

    fun build(
        context: Context, info: Info, confidencePct: Int, certaintyNote: String?, photo: Bitmap?,
        fieldName: String?, timestamp: Long, lang: Lang, labels: Labels,
    ): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val c = page.canvas
        var y = M

        c.drawRect(0f, 0f, W.toFloat(), 64f, Paint().apply { color = Color.parseColor("#15803D") })
        c.drawText("CropDoctor", M, 42f, TextPaint().apply { color = Color.WHITE; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true })
        y = 90f

        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
        y = text(c, "${info.cropName.get(lang)}: ${info.name.get(lang)}", y, 20f, true, "#14532D")
        val meta = listOfNotNull(fieldName, date, "${labels.confidence}: $confidencePct%").joinToString("   |   ")
        y = text(c, meta, y + 2, 11f, false, "#475569")
        if (certaintyNote != null) y = text(c, certaintyNote, y + 4, 11f, true, "#B45309")

        if (photo != null) {
            val maxH = 170f
            val s = minOf(maxH / photo.height, 240f / photo.width)
            val dst = android.graphics.RectF(M, y + 10, M + photo.width * s, y + 10 + photo.height * s)
            c.drawBitmap(photo, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            y = dst.bottom
        }
        y += 10

        fun section(title: String, body: List<String>) {
            if (body.isEmpty() || y > H - 90) return
            y = text(c, title, y + 8, 13f, true, "#15803D")
            body.forEach { y = text(c, "•  $it", y + 2, 11.5f, false, "#0F172A", indent = 8f) }
        }
        info.cause.get(lang).takeIf { it.isNotBlank() }?.let { section(labels.why, listOf(it)) }
        section(labels.doNow, info.actions.get(lang))
        section(labels.organic, info.organic.get(lang))
        section(labels.chemical, info.chemical.get(lang))
        section(labels.prevent, info.prevent.get(lang))

        text(c, labels.disclaimer, (H - 56).toFloat(), 9.5f, false, "#64748B")
        doc.finishPage(page)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 86_400_000) it.delete() }
        val out = File(dir, "CropDoctor_${info.classId.replace("___", "_")}_${timestamp}.pdf")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, null,
        )
    }

    private fun text(c: Canvas, s: String, top: Float, size: Float, bold: Boolean, color: String, indent: Float = 0f): Float {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = Color.parseColor(color)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val width = (W - 2 * M - indent).toInt()
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        c.save(); c.translate(M + indent, top); layout.draw(c); c.restore()
        return top + layout.height
    }

    data class Labels(
        val confidence: String, val why: String, val doNow: String, val organic: String,
        val chemical: String, val prevent: String, val disclaimer: String,
    )
}
