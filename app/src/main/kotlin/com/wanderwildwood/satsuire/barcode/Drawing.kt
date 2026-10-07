package com.wanderwildwood.satsuire.barcode

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import protect.card_locker.LoyaltyCard
import java.io.File
import java.nio.charset.StandardCharsets

/** The text a card's barcode carries: Catima's separate barcode value when it has one, else the number. */
fun LoyaltyCard.encoded(): String = barcodeId?.takeIf { it.isNotEmpty() } ?: cardId

/** The card's modules, or null when it has no barcode or its number cannot be that kind of barcode. */
fun LoyaltyCard.modules(): Modules? {
    val format: BarcodeFormat = barcodeType?.format() ?: return null
    return runCatching { Modules.encode(encoded(), format, barcodeEncoding ?: StandardCharsets.ISO_8859_1) }.getOrNull()
}

/** As [modules], with the shape chosen for a box of [w] by [h] pixels where the kind allows a choice. */
fun LoyaltyCard.modulesFor(w: Int, h: Int): Modules? {
    val format: BarcodeFormat = barcodeType?.format() ?: return null
    return runCatching { Modules.encodeFor(encoded(), format, barcodeEncoding ?: StandardCharsets.ISO_8859_1, w, h) }.getOrNull()
}

fun bitmap(m: Modules, fit: Fit): Bitmap =
    Bitmap.createBitmap(render(m, fit), fit.width, fit.height, Bitmap.Config.ARGB_8888)

/**
 * The barcode as a picture another phone can scan or read back in: drawn flat, at a whole
 * number of pixels a module, a little over a thousand pixels across at most.
 *
 * Written to the app's cache and handed over through a content link that only the receiving
 * app is let read, with the card's name and number as the message beside it.
 */
fun shareIntent(context: Context, card: LoyaltyCard): Intent {
    val text = card.store + "\n" + card.cardId
    val m = card.modules()
    val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text)
    if (m == null) return intent.setType("text/plain")

    val along = m.width + 2 * m.quiet
    val scale = maxOf(1, 1200 / along)
    val length = along * scale
    val fit = if (m.linear) {
        Fit(scale, false, length, maxOf(Fit.MIN_BAR, length / 3))
    } else {
        Fit(scale, false, length, (m.height + 2 * m.quiet) * scale)
    }
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "barcode.png")
    file.outputStream().use { bitmap(m, fit).compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    return intent
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .also { it.clipData = ClipData.newRawUri(null, uri) }
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
