package com.selfwallet.app.backup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.selfwallet.app.data.db.Claim
import com.selfwallet.app.ui.TxUi
import com.selfwallet.app.util.Currencies
import com.selfwallet.app.util.Receipts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds an A4 expense report: summary, itemised table, then one page per receipt photo.
 * Drawn with Android's built-in PdfDocument, so no library or network is needed.
 */
object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val M = 40f

    data class Info(
        val title: String,
        val owner: String,
        val scope: String,
        val period: String,
        val baseCurrency: String,
    )

    suspend fun write(context: Context, uri: Uri, info: Info, rows: List<TxUi>) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val items = rows.sortedBy { it.tx.timestamp }
        val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); color = Color.rgb(36, 32, 27) }
        val h2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(36, 32, 27) }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.rgb(36, 32, 27) }
        val muted = Paint(body).apply { color = Color.rgb(107, 98, 87) }
        val mono = Paint(body).apply { typeface = Typeface.MONOSPACE }
        val monoBold = Paint(mono).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        val rule = Paint().apply { color = Color.rgb(200, 192, 180); strokeWidth = 0.7f }
        val band = Paint().apply { color = Color.rgb(243, 238, 228) }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        lateinit var c: Canvas
        var y = 0f

        fun newPage() {
            page?.let { finish(doc, it, pageNo, muted) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page!!.canvas
            y = M
        }

        fun ensure(space: Float) { if (y + space > H - M - 20) newPage() }

        // ---- header ----
        newPage()
        c.drawText(info.title, M, y + 20, title); y += 34
        if (info.owner.isNotBlank()) { c.drawText(info.owner, M, y, h2); y += 16 }
        c.drawText("${info.scope} · ${info.period}", M, y, muted); y += 14
        c.drawText("Generated ${df.format(Date())} with Self Wallet", M, y, muted); y += 18
        c.drawLine(M, y, W - M, y, rule); y += 18

        // ---- summary ----
        val total = items.filter { it.isExpense }.sumOf { it.base }
        val claimable = items.filter { it.isExpense && it.isCorporate && Claim.of(it.tx) != Claim.REIMBURSED }.sumOf { it.base }
        c.drawText("Total expenses", M, y, h2)
        drawRight(c, Currencies.format(total, info.baseCurrency), W - M, y, monoBold); y += 16
        c.drawText("${items.size} transactions", M, y, muted)
        if (claimable > 0) drawRight(c, "To be reimbursed: ${Currencies.format(claimable, info.baseCurrency)}", W - M, y, mono)
        y += 22

        c.drawText("By category", M, y, h2); y += 14
        items.filter { it.isExpense }.groupBy { it.category?.name ?: "Uncategorised" }
            .map { it.key to it.value.sumOf { r -> r.base } }
            .sortedByDescending { it.second }
            .forEach { (name, sum) ->
                ensure(14f)
                c.drawText(name, M + 8, y, body)
                drawRight(c, Currencies.format(sum, info.baseCurrency), W - M, y, mono)
                y += 13
            }
        y += 12

        // ---- table ----
        val cols = floatArrayOf(M, M + 62, M + 210, M + 300, W - M - 92, W - M)
        fun header() {
            c.drawRect(M, y - 10, W - M, y + 5, band)
            c.drawText("Date", cols[0] + 2, y, h2.withSize(9f))
            c.drawText("Merchant", cols[1], y, h2.withSize(9f))
            c.drawText("Category", cols[2], y, h2.withSize(9f))
            c.drawText("Original", cols[3], y, h2.withSize(9f))
            drawRight(c, "In ${info.baseCurrency}", cols[5] - 30, y, h2.withSize(9f))
            drawRight(c, "Rcpt", cols[5] - 2, y, h2.withSize(9f))
            y += 16
        }
        ensure(40f)
        header()
        items.forEach { r ->
            if (y + 14 > H - M - 20) { newPage(); header() }
            val sign = if (r.isExpense) "" else "+"
            c.drawText(df.format(Date(r.tx.timestamp)), cols[0] + 2, y, body)
            c.drawText(ellipsize(r.tx.merchant.ifBlank { "—" }, body, cols[2] - cols[1] - 6), cols[1], y, body)
            c.drawText(ellipsize(r.category?.name ?: "—", body, cols[3] - cols[2] - 6), cols[2], y, body)
            c.drawText(sign + Currencies.format(r.tx.amount, r.tx.currency), cols[3], y, mono)
            drawRight(c, sign + String.format(Locale.US, "%,.2f", r.base), cols[5] - 30, y, mono)
            drawRight(c, if (Receipts.exists(r.tx.receiptPath)) "yes" else "", cols[5] - 2, y, muted)
            y += 6
            c.drawLine(M, y, W - M, y, rule)
            y += 10
        }

        // ---- receipts ----
        val withReceipts = items.filter { Receipts.exists(it.tx.receiptPath) }
        withReceipts.forEachIndexed { i, r ->
            val bmp = Receipts.load(r.tx.receiptPath!!, 1600) ?: return@forEachIndexed
            newPage()
            c.drawText("Receipt ${i + 1} of ${withReceipts.size}", M, y + 12, h2)
            y += 28
            c.drawText(
                "${df.format(Date(r.tx.timestamp))} · ${r.tx.merchant.ifBlank { "—" }} · ${Currencies.format(r.tx.amount, r.tx.currency)}",
                M, y, body,
            )
            y += 14
            val box = RectF(M, y, W - M, H - M - 24)
            val scale = minOf(box.width() / bmp.width, box.height() / bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            val dst = RectF(box.left, box.top, box.left + w, box.top + h)
            c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            bmp.recycle()
        }

        page?.let { finish(doc, it, pageNo, muted) }
        context.contentResolver.openOutputStream(uri, "wt")?.use { doc.writeTo(it) } ?: error("Cannot open file")
        doc.close()
    }

    private fun finish(doc: PdfDocument, page: PdfDocument.Page, n: Int, paint: Paint) {
        page.canvas.drawText("Page $n", W - M - paint.measureText("Page $n"), H - M + 8, paint)
        doc.finishPage(page)
    }

    private fun drawRight(c: Canvas, text: String, right: Float, y: Float, paint: Paint) {
        c.drawText(text, right - paint.measureText(text), y, paint)
    }

    private fun ellipsize(text: String, paint: Paint, max: Float): String {
        if (paint.measureText(text) <= max) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    private fun Paint.withSize(size: Float) = Paint(this).apply { textSize = size }
}
