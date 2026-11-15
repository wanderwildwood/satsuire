package com.wanderwildwood.satsuire.barcode

/**
 * The barcode a typed number most likely came from, when it can be told from the number alone:
 * all digits, the length of an EAN 13, UPC A or EAN 8, and its last digit the check digit those
 * barcodes end with. Only offered, never set by itself: a card number can pass the check by chance.
 */
object Suggest {
    /** A ZXing format name ("EAN_13", "UPC_A", "EAN_8"), or null when the number fits none. */
    fun formatFor(number: String): String? {
        val n = number.trim()
        if (n.isEmpty() || !n.all { it in '0'..'9' } || !checks(n)) return null
        return when (n.length) {
            13 -> "EAN_13"
            12 -> "UPC_A"
            8 -> "EAN_8"
            else -> null
        }
    }

    /**
     * The GS1 check: from the right, the digits before the check digit are weighed 3, 1, 3, ...,
     * and the check digit brings the sum up to a multiple of ten.
     */
    internal fun checks(digits: String): Boolean {
        if (digits.length < 2) return false
        val body = digits.dropLast(1)
        var sum = 0
        for ((i, c) in body.reversed().withIndex()) sum += (c - '0') * (if (i % 2 == 0) 3 else 1)
        return (10 - sum % 10) % 10 == digits.last() - '0'
    }
}
