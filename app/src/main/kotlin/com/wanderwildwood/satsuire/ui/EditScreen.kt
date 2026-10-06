package com.wanderwildwood.satsuire.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.satsuire.R
import com.wanderwildwood.satsuire.barcode.Modules
import com.wanderwildwood.satsuire.data.Draft
import com.wanderwildwood.satsuire.data.Validity
import com.google.zxing.BarcodeFormat
import protect.card_locker.CatimaBarcode
import java.time.LocalDate

private enum class Picking { FORMAT, FROM, UNTIL }

/**
 * A card's details, typed or corrected: its name, its number, the kind of barcode, the days it
 * is good for, and a note. Saving refuses a number the chosen barcode cannot carry and says
 * why, rather than keeping a card whose barcode will not draw.
 */
@Composable
fun EditScreen(
    draft: Draft,
    onChange: (Draft) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var picking by rememberSaveable { mutableStateOf<Picking?>(null) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    val cannotDraw = stringResource(R.string.edit_cannot_draw)
    val needsName = stringResource(R.string.edit_needs_name)
    val needsNumber = stringResource(R.string.edit_needs_number)
    val backwards = stringResource(R.string.edit_dates_backwards)

    fun save() {
        val format = draft.format?.let { BarcodeFormat.valueOf(it) }
        problem = when {
            draft.name.isBlank() -> needsName
            draft.number.isEmpty() -> needsNumber
            draft.validFrom != null && draft.expiry != null && draft.expiry < draft.validFrom -> backwards
            format != null && runCatching {
                Modules.encode(draft.barcodeValue?.takeIf { it.isNotEmpty() } ?: draft.number, format)
            }.isFailure -> cannotDraw.format(CatimaBarcode.fromBarcode(format).prettyName())
            else -> null
        }
        if (problem == null) onSave()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = stringResource(if (draft.isNew) R.string.edit_new else R.string.edit_title),
                back = Icons.Close,
                onBack = onDiscard,
            ) {
                BarText(stringResource(R.string.save)) { save() }
            }
        },
    ) { padding ->
        LazyColumnMMD(Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
            item(key = "name") {
                Spacer(Modifier.height(8.dp))
                TextFieldMMD(
                    value = draft.name,
                    onValueChange = { onChange(draft.copy(name = it)) },
                    label = { TextMMD(text = stringResource(R.string.edit_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().textActions(),
                )
            }
            item(key = "number") {
                TextFieldMMD(
                    value = draft.number,
                    onValueChange = { onChange(draft.copy(number = it.trim('\n'))) },
                    label = { TextMMD(text = stringResource(R.string.edit_number)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (draft.format in NUMERIC) KeyboardType.Number else KeyboardType.Text,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).textActions(),
                )
            }
            if (!draft.barcodeValue.isNullOrEmpty()) {
                // Catima lets a card's barcode carry something other than the number shown;
                // a card that came with one keeps it, and it can be read and corrected here.
                item(key = "value") {
                    TextFieldMMD(
                        value = draft.barcodeValue,
                        onValueChange = { onChange(draft.copy(barcodeValue = it)) },
                        label = { TextMMD(text = stringResource(R.string.edit_barcode_value)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).textActions(),
                    )
                }
            }
            item(key = "format") {
                SettingRow(
                    title = stringResource(R.string.edit_format),
                    value = draft.format?.let { CatimaBarcode.fromName(it).prettyName() } ?: stringResource(R.string.format_none),
                    onClick = { picking = Picking.FORMAT },
                )
            }
            item(key = "from") {
                SettingRow(
                    title = stringResource(R.string.edit_valid_from),
                    value = draft.validFrom?.let { dayText(Validity.day(it)) } ?: stringResource(R.string.edit_no_date),
                    onClick = { picking = Picking.FROM },
                )
            }
            item(key = "until") {
                SettingRow(
                    title = stringResource(R.string.edit_valid_until),
                    value = draft.expiry?.let { dayText(Validity.day(it)) } ?: stringResource(R.string.edit_no_date),
                    onClick = { picking = Picking.UNTIL },
                )
            }
            item(key = "note") {
                TextFieldMMD(
                    value = draft.note,
                    onValueChange = { onChange(draft.copy(note = it)) },
                    label = { TextMMD(text = stringResource(R.string.edit_note)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).textActions(),
                )
            }
            if (onDelete != null) {
                item(key = "delete") {
                    Spacer(Modifier.height(12.dp))
                    ArmedRow(stringResource(R.string.edit_delete), stringResource(R.string.edit_delete_armed), onDelete)
                }
            }
            item(key = "foot") { Spacer(Modifier.height(24.dp)) }
        }
    }

    problem?.let { MessageDialog(it) { problem = null } }

    when (picking) {
        Picking.FORMAT -> FormatDialog(
            current = draft.format,
            onPick = { onChange(draft.copy(format = it)); picking = null },
            onDismiss = { picking = null },
        )
        Picking.FROM -> DateChoice(
            title = stringResource(R.string.edit_valid_from),
            current = draft.validFrom,
            onPick = { onChange(draft.copy(validFrom = it)); picking = null },
            onDismiss = { picking = null },
        )
        Picking.UNTIL -> DateChoice(
            title = stringResource(R.string.edit_valid_until),
            current = draft.expiry,
            onPick = { onChange(draft.copy(expiry = it)); picking = null },
            onDismiss = { picking = null },
        )
        null -> Unit
    }
}

/** Formats whose content is digits only, for which the number pad is the right keyboard. */
private val NUMERIC = setOf("EAN_8", "EAN_13", "UPC_A", "UPC_E", "ITF")

/**
 * The barcode kinds Catima draws, by the names printed on them, and "No barcode" for a card
 * that is only ever read out.
 */
@Composable
fun FormatDialog(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        LazyColumnMMD(Modifier.fillMaxWidth().height(460.dp)) {
            item(key = "none") {
                ChoiceRow(stringResource(R.string.format_none), bold = current == null) { onPick(null) }
            }
            for (format in CatimaBarcode.barcodeFormats) {
                item(key = format.name) {
                    val note = when (format) {
                        BarcodeFormat.AZTEC, BarcodeFormat.PDF_417 -> stringResource(R.string.format_note_boarding)
                        BarcodeFormat.QR_CODE -> stringResource(R.string.format_note_qr)
                        BarcodeFormat.EAN_13, BarcodeFormat.UPC_A -> stringResource(R.string.format_note_shops)
                        else -> null
                    }
                    ChoiceRow(CatimaBarcode.fromBarcode(format).prettyName(), note, bold = current == format.name) { onPick(format.name) }
                }
            }
        }
    }
}

/** A day, or none: a date picker, with a way to clear the date as well as to set it. */
@Composable
private fun DateChoice(title: String, current: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    var calendar by rememberSaveable { mutableStateOf(current == null) }
    if (calendar) {
        DateDialog(
            initial = current?.let { Validity.day(it) } ?: LocalDate.now(),
            onPick = { onPick(Validity.millis(it)) },
            onDismiss = onDismiss,
        )
    } else {
        EInkDialog(onDismiss = onDismiss) {
            TextMMD(text = title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            ChoiceRow(stringResource(R.string.edit_change_date)) { calendar = true }
            ChoiceRow(stringResource(R.string.edit_clear_date)) { onPick(null) }
        }
    }
}
