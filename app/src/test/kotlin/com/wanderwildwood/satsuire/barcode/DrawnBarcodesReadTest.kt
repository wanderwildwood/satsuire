package com.wanderwildwood.satsuire.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Every kind of barcode, drawn the way the card screen draws it, read back by ZXing's reader
 * as a scanner would: the same text must come out, in the same format.
 *
 * Boxes are the Kompakt's card screen in pixels (480 wide, about 560 left under the bar and
 * the number), a smaller one, and a wide one, so the flat and the turned drawings are both
 * read. A reader that is handed nothing but a picture is not told where the code is.
 */
class DrawnBarcodesReadTest {

    private val samples = listOf(
        BarcodeFormat.AZTEC to "M1WHITLOCK/ADA        EABC123 BOSJFKB6 0617 278Y012A0042 100",
        BarcodeFormat.PDF_417 to "M1REYES/TOMAS         EXYZ789 SEAANCAS 0102 150M014C0007 148>5180  1150BAS 0000000000000",
        BarcodeFormat.QR_CODE to "FERRY-2026-10-18-ADULT-0042",
        BarcodeFormat.DATA_MATRIX to "TICKET 5550101001",
        BarcodeFormat.CODE_128 to "LIB-000123456",
        BarcodeFormat.CODE_39 to "MEMBER42",
        BarcodeFormat.CODE_93 to "CARD93X",
        BarcodeFormat.CODABAR to "12345678",
        BarcodeFormat.EAN_13 to "5901234123457",
        BarcodeFormat.EAN_8 to "96385074",
        BarcodeFormat.UPC_A to "036000291452",
        BarcodeFormat.UPC_E to "01234565",
        BarcodeFormat.ITF to "00012345678905",
        // The shape of a US public library card: fourteen digits, Codabar or Code 39.
        BarcodeFormat.CODABAR to "21234001234567",
        BarcodeFormat.CODE_39 to "21234001234567",
    )

    private val boxes = listOf(480 to 560, 360 to 300, 800 to 300)

    @Test
    fun everyFormatReadsBackAtEverySize() {
        for ((format, text) in samples) {
            for ((w, h) in boxes) {
                val m = Modules.encodeFor(text, format, Charsets.ISO_8859_1, w, h)
                val fit = Fit.of(m, w, h) ?: fail("$format did not fit $w x $h").let { return }
                assertTrue("$format overflows $w x $h", fit.width <= w && fit.height <= h)
                // A scanner reads a code whichever way it runs; this reader reads only across,
                // so a turned drawing is turned back as a hand would turn the phone.
                val pixels = render(m, fit)
                val read = if (fit.turned) {
                    readLikeAScanner(text, turnBack(pixels, fit.width, fit.height), fit.height, fit.width, format)
                } else {
                    readLikeAScanner(text, pixels, fit.width, fit.height, format)
                }
                assertEquals("$format at $w x $h (scale ${fit.scale}, turned ${fit.turned})", text, read)
            }
        }
    }

    @Test
    fun drawingIsOnlyBlackAndWhite() {
        for ((format, text) in samples) {
            val m = Modules.encode(text, format)
            val fit = Fit.of(m, 480, 560)!!
            val pixels = render(m, fit)
            assertTrue("$format has a grey pixel", pixels.all { it == BLACK || it == WHITE })
        }
    }

    /**
     * Every bar is a whole number of modules wide: along any row of a flat drawing, every run of
     * one colour is a multiple of the scale.
     */
    @Test
    fun everyRunIsAMultipleOfTheScale() {
        for ((format, text) in samples) {
            val m = Modules.encode(text, format)
            val fit = Fit.of(m, 800, 300)!!
            if (fit.turned) continue
            val pixels = render(m, fit)
            val y = fit.height / 2
            var run = 1
            for (x in 1 until fit.width) {
                if (pixels[y * fit.width + x] == pixels[y * fit.width + x - 1]) {
                    run++
                } else {
                    assertEquals("$format: a run of $run at scale ${fit.scale}", 0, run % fit.scale)
                    run = 1
                }
            }
        }
    }

    /**
     * A bar code is turned only when lying flat gives it under two pixels a module and turning
     * gives it more; a grid code whenever turning gives it more.
     */
    @Test
    fun turnedOnlyWhenItGains() {
        for (text in listOf("LIB-0001", "LIB-000123456-ABCDEF", "LIB-000123456-0000987654321-XYZ")) {
            val m = Modules.encode(text, BarcodeFormat.CODE_128)
            val along = m.width + 2 * m.quiet
            val flat = 480 / along
            val fit = Fit.of(m, 480, 600)!!
            assertEquals(text, 600 / along > flat && flat < 2, fit.turned)
        }
        val pdf = Modules.encode("M1REYES/TOMAS         EXYZ789 SEAANCAS 0102 150M014C0007 148", BarcodeFormat.PDF_417)
        val fit = Fit.of(pdf, 480, 600)!!
        val flat = minOf(480 / (pdf.width + 2 * pdf.quiet), 600 / (pdf.height + 2 * pdf.quiet))
        val side = minOf(600 / (pdf.width + 2 * pdf.quiet), 480 / (pdf.height + 2 * pdf.quiet))
        assertEquals(side > flat, fit.turned)
        assertEquals(maxOf(flat, side), fit.scale)
    }

    @Test
    fun textBeyondLatinIsKeptWhole() {
        val text = "Ada Whitlock — Kraków Ö 東京"
        val m = Modules.encode(text, BarcodeFormat.QR_CODE)
        val fit = Fit.of(m, 480, 560)!!
        assertEquals(text, readLikeAScanner(text, render(m, fit), fit.width, fit.height, BarcodeFormat.QR_CODE))
    }

    /**
     * "FERRY-2026-10-18-ADULT-0042" is one of the QR codes ZXing's finder misreads in the mask
     * its encoder prefers. Drawn here, another mask is chosen, and it reads with no help.
     */
    @Test
    fun qrCodeIsChosenToReadUnaided() {
        val text = "FERRY-2026-10-18-ADULT-0042"
        val m = Modules.encode(text, BarcodeFormat.QR_CODE)
        for ((w, h) in boxes) {
            val fit = Fit.of(m, w, h)!!
            assertEquals(text, read(render(m, fit), fit.width, fit.height, BarcodeFormat.QR_CODE)?.first)
        }
        // The control: ZXing's own drawing of it, in the mask it prefers, is not read.
        val own = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300)
        val ownPixels = IntArray(own.width * own.height) { i -> if (own[i % own.width, i / own.width]) BLACK else WHITE }
        assertNull(read(ownPixels, own.width, own.height, BarcodeFormat.QR_CODE))
    }

    /** A boarding pass's PDF417, shaped for the card screen, gets at least two pixels a module. */
    @Test
    fun boardingPassIsShapedForThePanel() {
        val text = samples.first { it.first == BarcodeFormat.PDF_417 }.second
        val flat = Fit.of(Modules.encode(text, BarcodeFormat.PDF_417), 480, 560)!!
        val shaped = Fit.of(Modules.encodeFor(text, BarcodeFormat.PDF_417, Charsets.ISO_8859_1, 480, 560), 480, 560)!!
        assertTrue("shaped ${shaped.scale}, default ${flat.scale}", shaped.scale >= 2 && shaped.scale > flat.scale)
    }

    @Test(expected = IllegalArgumentException::class)
    fun lettersAreNotAnEan() {
        Modules.encode("ABC", BarcodeFormat.EAN_13)
    }

    /** The reader used above can fail: a blank drawing and a damaged one are not read. */
    @Test
    fun readerCanFail() {
        val blank = IntArray(300 * 300) { WHITE }
        assertNull(read(blank, 300, 300, BarcodeFormat.QR_CODE))

        val m = Modules.encode("FERRY-2026-10-18-ADULT-0042", BarcodeFormat.QR_CODE)
        val fit = Fit.of(m, 480, 560)!!
        val damaged = render(m, fit)
        // Wipe the middle third of the code.
        for (y in fit.height / 3 until 2 * fit.height / 3) for (x in 0 until fit.width) damaged[y * fit.width + x] = WHITE
        assertNull(read(damaged, fit.width, fit.height, BarcodeFormat.QR_CODE))
    }

    companion object {
        /** A quarter turn anticlockwise: undoes the card screen's quarter turn clockwise. */
        fun turnBack(p: IntArray, w: Int, h: Int): IntArray {
            val out = IntArray(w * h)
            // Out is h wide and w tall: out(x', y') = in(w - 1 - y', x').
            for (y2 in 0 until w) for (x2 in 0 until h) out[y2 * h + x2] = p[x2 * w + (w - 1 - y2)]
            return out
        }

        /**
         * What a reader makes of a drawing, found the way a scanner finds it, in a picture with
         * no hint of where the code is.
         *
         * ZXing's finder misplaces about one QR code in a hundred even in its own perfect
         * drawings — "FERRY-2026-10-18-ADULT-0042" is one — through a false alignment pattern,
         * whatever the size. That is the content's, not the drawing's: such a code is accepted
         * here only if ZXing's own drawing of the same content fails the same way, and the
         * drawing still reads module for module when the reader is told it holds nothing else.
         */
        fun readLikeAScanner(text: String, pixels: IntArray, w: Int, h: Int, format: BarcodeFormat): String? {
            read(pixels, w, h, format)?.let { return it.first }
            val own = MultiFormatWriter().encode(text, format, w, h)
            val ownPixels = IntArray(own.width * own.height) { i -> if (own[i % own.width, i / own.width]) BLACK else WHITE }
            assertNull("$format: ZXing reads its own drawing of this, but not ours", read(ownPixels, own.width, own.height, format))
            return read(pixels, w, h, format, pure = true)?.first
        }

        fun read(pixels: IntArray, w: Int, h: Int, format: BarcodeFormat, pure: Boolean = false): Pair<String, BarcodeFormat>? {
            val source = RGBLuminanceSource(w, h, pixels)
            val hints = mutableMapOf<DecodeHintType, Any>(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(format),
            )
            if (pure) hints[DecodeHintType.PURE_BARCODE] = true
            return try {
                val r = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints)
                r.text to r.barcodeFormat
            } catch (_: NotFoundException) {
                null
            } catch (_: com.google.zxing.FormatException) {
                null
            } catch (_: com.google.zxing.ChecksumException) {
                null
            }
        }
    }
}
