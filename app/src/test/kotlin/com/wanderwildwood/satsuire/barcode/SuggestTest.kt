package com.wanderwildwood.satsuire.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestTest {
    @Test fun aValidEan13IsSuggested() {
        assertEquals("EAN_13", Suggest.formatFor("4006381333931"))
        assertEquals("EAN_13", Suggest.formatFor(" 5901234123457 "))
    }

    @Test fun aValidUpcAIsSuggested() {
        assertEquals("UPC_A", Suggest.formatFor("036000291452"))
    }

    @Test fun aValidEan8IsSuggested() {
        assertEquals("EAN_8", Suggest.formatFor("96385074"))
    }

    @Test fun aWrongCheckDigitIsNotSuggested() {
        assertNull(Suggest.formatFor("4006381333932"))
        assertNull(Suggest.formatFor("036000291453"))
        assertNull(Suggest.formatFor("96385075"))
    }

    @Test fun otherLengthsAndLettersAreNotSuggested() {
        assertNull(Suggest.formatFor(""))
        assertNull(Suggest.formatFor("12345"))
        assertNull(Suggest.formatFor("400638133393A"))
        assertNull(Suggest.formatFor("40063813339310"))
    }

    @Test fun whatIsSuggestedAlsoDraws() {
        for (n in listOf("4006381333931", "036000291452", "96385074")) {
            val f = com.google.zxing.BarcodeFormat.valueOf(Suggest.formatFor(n)!!)
            Modules.encode(n, f)
        }
    }
}
