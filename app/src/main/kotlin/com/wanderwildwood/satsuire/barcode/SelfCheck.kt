package com.wanderwildwood.satsuire.barcode

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/** Whether ZXing, finding a code the way a scanner does, reads [m] back as [content]. */
internal object SelfCheck {
    fun reads(m: Modules, content: String): Boolean {
        val along = m.width + 2 * m.quiet
        val across = if (m.linear) Fit.MIN_BAR else m.height + 2 * m.quiet
        val scale = 4
        val fit = Fit(scale, false, along * scale, if (m.linear) across else across * scale)
        val source = RGBLuminanceSource(fit.width, fit.height, render(m, fit))
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(m.format),
        )
        return try {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text == content
        } catch (_: ReaderException) {
            false
        }
    }
}
