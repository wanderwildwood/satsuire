package com.wanderwildwood.satsuire.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * A barcode as its modules: the smallest squares it is made of, one bit each, before any
 * scaling. A one-dimensional code is one row whose bars run the full height.
 *
 * Kept apart from drawing on purpose. Catima asked ZXing for a picture at the size of the
 * view and let the view stretch it; a stretch that is not a whole number gives bars of two
 * different widths and grey edges, which on sixteen greys a scanner reads as noise. Here the
 * modules are asked for once at one pixel each, and [Fit] decides a whole number of pixels
 * for every one of them.
 */
class Modules(
    val format: BarcodeFormat,
    /** Columns of modules, without the quiet zone. */
    val width: Int,
    /** Rows of modules, without the quiet zone. 1 for a one-dimensional code. */
    val height: Int,
    private val bits: BooleanArray,
) {
    /** True where a module is dark. */
    operator fun get(x: Int, y: Int): Boolean = bits[y * width + x]

    /** A bar code read along one line, as against a grid read as a whole. */
    val linear: Boolean get() = format in LINEAR

    /**
     * The white margin a scanner needs on every side, in modules. The figures are each
     * standard's own minimum, or a little over it where the standard asks for none.
     */
    val quiet: Int
        get() = when (format) {
            BarcodeFormat.QR_CODE -> 4
            BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E -> 11
            BarcodeFormat.PDF_417 -> 2
            BarcodeFormat.DATA_MATRIX -> 2
            BarcodeFormat.AZTEC -> 2
            else -> 10
        }

    companion object {
        val LINEAR = setOf(
            BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.CODE_128, BarcodeFormat.CODABAR,
            BarcodeFormat.EAN_8, BarcodeFormat.EAN_13, BarcodeFormat.ITF, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
        )

        private val TWO_D = setOf(BarcodeFormat.QR_CODE, BarcodeFormat.AZTEC, BarcodeFormat.PDF_417, BarcodeFormat.DATA_MATRIX)

        /**
         * The modules for [content] in [format], or an exception when the content cannot be that
         * format at all (letters in an EAN, a wrong check digit, too much for the symbol).
         *
         * The character set is passed to the encoder only when it is not ISO-8859-1, as Catima
         * does: a set named inside the symbol (an ECI) makes some scanners in the wild fail, and
         * cards without one have scanned for years. Text that ISO-8859-1 cannot hold is sent as
         * UTF-8 instead of being turned into question marks.
         */
        fun encode(content: String, format: BarcodeFormat, charset: Charset = StandardCharsets.ISO_8859_1): Modules {
            require(content.isNotEmpty()) { "Nothing to encode" }
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.MARGIN] = 0
            val set = when {
                charset.name() != StandardCharsets.ISO_8859_1.name() -> charset
                format in TWO_D && !StandardCharsets.ISO_8859_1.newEncoder().canEncode(content) -> StandardCharsets.UTF_8
                else -> null
            }
            if (set != null) hints[EncodeHintType.CHARACTER_SET] = set.name()

            val first = build(content, format, hints)
            if (format != BarcodeFormat.QR_CODE || SelfCheck.reads(first, content)) return first

            // About one QR code in a hundred is misread by ZXing's finder even when perfectly
            // drawn: a false alignment pattern among the data throws the grid off, the same at
            // any size, and scanners built on ZXing are common. The standard allows eight masks
            // for the same content; the encoder picks one by score, and any other is as valid.
            // Take the first one this phone's own copy of ZXing reads back.
            for (mask in 0 until 8) {
                hints[EncodeHintType.QR_MASK_PATTERN] = mask
                val other = runCatching { build(content, format, hints) }.getOrNull() ?: continue
                if (SelfCheck.reads(other, content)) return other
            }
            return first
        }

        private fun build(content: String, format: BarcodeFormat, hints: Map<EncodeHintType, Any>): Modules {
            val matrix = try {
                MultiFormatWriter().encode(content, format, 0, 0, hints)
            } catch (e: Exception) {
                // An encoder refuses bad content in several ways, some of them unchecked.
                throw IllegalArgumentException("Cannot encode as $format", e)
            }

            // A one-dimensional writer hands back one row; a two-dimensional one the whole grid,
            // PDF417's with each row already four modules tall, as its standard draws them.
            val w = matrix.width
            val h = if (format in LINEAR) 1 else matrix.height
            val bits = BooleanArray(w * h)
            for (y in 0 until h) for (x in 0 until w) bits[y * w + x] = matrix[x, y]
            return trim(Modules(format, w, h, bits))
        }

        /**
         * Without the white edge some writers leave whatever the margin asked for, so the
         * quiet zone is only ever the one [quiet] gives.
         */
        private fun trim(m: Modules): Modules {
            var left = 0
            var right = m.width - 1
            var top = 0
            var bottom = m.height - 1
            fun colEmpty(x: Int) = (0 until m.height).none { m[x, it] }
            fun rowEmpty(y: Int) = (0 until m.width).none { m[it, y] }
            while (left < right && colEmpty(left)) left++
            while (right > left && colEmpty(right)) right--
            while (top < bottom && rowEmpty(top)) top++
            while (bottom > top && rowEmpty(bottom)) bottom--
            if (left == 0 && top == 0 && right == m.width - 1 && bottom == m.height - 1) return m
            val w = right - left + 1
            val h = bottom - top + 1
            val bits = BooleanArray(w * h)
            for (y in 0 until h) for (x in 0 until w) bits[y * w + x] = m[x + left, y + top]
            return Modules(m.format, w, h, bits)
        }
    }
}
