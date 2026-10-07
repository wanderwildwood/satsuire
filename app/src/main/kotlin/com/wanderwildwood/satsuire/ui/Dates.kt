package com.wanderwildwood.satsuire.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.satsuire.R
import com.wanderwildwood.satsuire.data.Validity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A day as the phone's language writes it in full: "14 Oct 2026", "Oct 14, 2026". */
fun dayText(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/**
 * What a card's dates mean today, in a line. [short] is for the list, where a card valid
 * from a day long past and until none says nothing at all.
 */
@Composable
fun validityText(v: Validity, short: Boolean = false): String? = when (v) {
    Validity.Undated -> null
    is Validity.Expired -> stringResource(R.string.validity_expired, dayText(v.until))
    is Validity.NotYet -> stringResource(R.string.validity_not_yet, dayText(v.from))
    is Validity.Valid -> when {
        v.until != null && v.from == v.until -> stringResource(R.string.validity_on, dayText(v.until))
        v.until != null && v.from != null && !short -> stringResource(R.string.validity_from_until, dayText(v.from), dayText(v.until))
        v.until != null -> stringResource(R.string.validity_until, dayText(v.until))
        v.from != null && !short -> stringResource(R.string.validity_from, dayText(v.from))
        else -> null
    }
}
