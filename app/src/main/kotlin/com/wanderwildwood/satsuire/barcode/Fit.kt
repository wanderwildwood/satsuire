package com.wanderwildwood.satsuire.barcode

/**
 * How a barcode sits in a box of pixels: how many pixels each module gets, always a whole
 * number, and whether it is turned on its side to get more of them.
 *
 * A scanner reads the width of a bar against the width of the narrowest one. Give every module
 * the same whole number of pixels and every bar is exactly a multiple of the narrowest; there is
 * nothing for the panel to round. A grid code (QR, Aztec, PDF417, Data Matrix) is read whole
 * by an imager in any direction, so it is turned whenever that buys it more. A bar code read
 * along a line is turned only when lying flat would leave it under two pixels a module.
 */
data class Fit(
    /** Pixels per module, at least 1. */
    val scale: Int,
    /** Drawn running down the panel rather than across it. */
    val turned: Boolean,
    /** The drawing, quiet zone included, as it lands on the panel. */
    val width: Int,
    val height: Int,
) {
    companion object {
        /**
         * The largest drawing of [m] that fits [boxWidth] by [boxHeight] pixels, or null when it
         * will not fit even at one pixel a module, which is when the panel cannot show it at all.
         *
         * A one-dimensional code has no height of its own; its bars are drawn half as tall as the
         * code is long, or the whole box where that is less, but never under [MIN_BAR] pixels.
         */
        fun of(m: Modules, boxWidth: Int, boxHeight: Int): Fit? {
            val along = m.width + 2 * m.quiet
            val across = if (m.linear) 0 else m.height + 2 * m.quiet

            fun scaleFor(w: Int, h: Int): Int {
                val byWidth = w / along
                if (m.linear) return if (h >= MIN_BAR) byWidth else 0
                return minOf(byWidth, h / across)
            }

            val flat = scaleFor(boxWidth, boxHeight)
            val side = scaleFor(boxHeight, boxWidth)
            // A bar code read along one line is turned only when lying flat would give it under
            // two pixels a module: a handheld laser reads along its own line, and a cashier
            // should not have to turn it. A grid code is read whole, whichever way it lies.
            val turned = side > flat && (!m.linear || flat < 2)
            val scale = if (turned) side else flat
            if (scale < 1) return null

            val length = along * scale
            val breadth = if (m.linear) {
                val room = if (turned) boxWidth else boxHeight
                minOf(room, maxOf(MIN_BAR, length / 2))
            } else {
                across * scale
            }
            return if (turned) Fit(scale, true, breadth, length) else Fit(scale, false, length, breadth)
        }

        /** The shortest bars worth drawing: about a centimetre on the Kompakt. */
        const val MIN_BAR = 80
    }
}

/**
 * The drawing itself: [fit.width] by [fit.height] pixels, each one pure black or pure white,
 * row after row. No pixel is anything between, so nothing on the way to the panel can soften
 * an edge.
 */
fun render(m: Modules, fit: Fit): IntArray {
    val out = IntArray(fit.width * fit.height) { WHITE }
    val q = m.quiet
    val s = fit.scale
    // Coordinates along the code (u) and across it (v), in pixels of the drawing as if flat.
    val flatWidth = if (fit.turned) fit.height else fit.width
    val flatHeight = if (fit.turned) fit.width else fit.height
    for (v in 0 until flatHeight) {
        for (u in 0 until flatWidth) {
            val mx = u / s - q
            val dark = if (m.linear) {
                mx in 0 until m.width && m[mx, 0]
            } else {
                val my = v / s - q
                mx in 0 until m.width && my in 0 until m.height && m[mx, my]
            }
            if (!dark) continue
            // Turned a quarter clockwise: what ran left to right runs top to bottom.
            val (x, y) = if (fit.turned) (flatHeight - 1 - v) to u else u to v
            out[y * fit.width + x] = BLACK
        }
    }
    return out
}

const val BLACK = 0xFF000000.toInt()
const val WHITE = 0xFFFFFFFF.toInt()
