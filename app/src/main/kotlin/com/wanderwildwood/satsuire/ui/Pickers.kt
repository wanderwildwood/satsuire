package com.wanderwildwood.satsuire.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.mudita.mmd.components.time.DatePickerFormatterMMD
import com.mudita.mmd.components.time.DatePickerMMD
import com.mudita.mmd.components.time.rememberDatePickerMMDState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Mudita's own date picker, as Calendar shows it. It steps a month at a time rather than
 * sliding, which is why it is used here rather than a hand-drawn grid.
 *
 * It is shown on the whole screen, not in the house dialog: it is Material's picker
 * underneath and wants 360dp of width, the Kompakt is 366dp wide, and squeezed inside a
 * dialog's rim its last column clipped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerMMDState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
                DatePickerMMD(
                    state = state,
                    dateFormatter = UtcDateFormatter,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(horizontal = 20.dp)) {
                    DialogButtons(
                        onCancel = onDismiss,
                        onOk = {
                            state.selectedDateMillis?.let {
                                onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                            } ?: onDismiss()
                        },
                    )
                }
            }
        }
    }
}

/**
 * MMD's picker hands its formatter the first of the month as UTC milliseconds, and its default
 * formatter reads them in the phone's zone. West of Greenwich that is the last evening of the
 * month before, so the header named the wrong month. This reads them in UTC, as Calendar does.
 */
@OptIn(ExperimentalMaterial3Api::class)
private object UtcDateFormatter : DatePickerFormatterMMD {
    override fun formatMonthYear(monthMillis: Long?, locale: Locale): String? =
        monthMillis?.let {
            YearMonth.from(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC))
                .format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        }

    override fun formatDate(dateMillis: Long?, locale: Locale, forContentDescription: Boolean): String? =
        dateMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
        }
}
