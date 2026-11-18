package com.wanderwildwood.satsuire.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.checkbox.CheckboxMMD
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.satsuire.R
import com.wanderwildwood.satsuire.barcode.Modules
import com.wanderwildwood.satsuire.barcode.Suggest
import com.wanderwildwood.satsuire.data.Draft
import com.wanderwildwood.satsuire.data.Validity
import com.google.zxing.BarcodeFormat
import protect.card_locker.CatimaBarcode
import java.time.LocalDate

private enum class Picking { FORMAT, FROM, UNTIL, GROUPS }

/**
 * A card's details, typed or corrected: its name, its number, the kind of barcode, the days it
 * is good for, and a note. Saving refuses a number the chosen barcode cannot carry and says
 * why, rather than keeping a card whose barcode will not draw.
 *
 * Leaving with changes unsaved (Back, or the close button) asks first, in place: the first
 * press says the changes are not saved, and a second leaves without them.
 */
@Composable
fun EditScreen(
    draft: Draft,
    onChange: (Draft) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDelete: (() -> Unit)?,
    allGroups: List<String>,
    onNewGroup: (String) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<Picking?>(null) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    val cannotDraw = stringResource(R.string.edit_cannot_draw)
    val needsName = stringResource(R.string.edit_needs_name)
    val needsNumber = stringResource(R.string.edit_needs_number)
    val backwards = stringResource(R.string.edit_dates_backwards)
    // What the screen opened with, so leaving can tell whether anything was changed.
    val start by rememberSaveable(stateSaver = Draft.saver) { mutableStateOf<Draft?>(draft) }
    var leaving by rememberArmedFlag(disarmOnBack = false)
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current

    fun leave() {
        if (draft == start || leaving) onDiscard() else leaving = true
    }
    BackHandler { leave() }
    // Asked at the top of the form, so it is in view wherever the form was paged to.
    LaunchedEffect(leaving) { if (leaving) listState.scrollToItem(0) }

    // A picker opens with no field holding the focus, so the keyboard does not come back when it closes.
    fun pick(p: Picking) {
        focus.clearFocus()
        picking = p
    }

    fun save() {
        val format = draft.format?.let { BarcodeFormat.valueOf(it) }
        problem = when {
            draft.name.isBlank() -> needsName
            draft.number.isEmpty() -> needsNumber
            draft.validFrom != null && draft.expiry != null &&
                Validity.day(draft.expiry).isBefore(Validity.day(draft.validFrom)) -> backwards
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
                onBack = ::leave,
            ) {
                BarText(stringResource(R.string.save)) { save() }
            }
        },
    ) { padding ->
        PagedColumn(Modifier.padding(padding).fillMaxSize(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp), showCutRow = true) {
            if (leaving) {
                item(key = "leaving") {
                    TextMMD(
                        text = stringResource(R.string.edit_not_saved_back_again),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
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
                    onClick = { pick(Picking.FORMAT) },
                )
            }
            // A number typed with no barcode chosen that has the length and check digit of a
            // shop barcode: offered, not set, since a card number can pass the check by chance.
            val suggested = if (draft.format == null) Suggest.formatFor(draft.number) else null
            if (suggested != null) {
                item(key = "suggest") {
                    ChoiceRow(
                        text = stringResource(R.string.edit_use_format, CatimaBarcode.fromName(suggested).prettyName()),
                        note = stringResource(R.string.edit_use_format_note),
                    ) {
                        focus.clearFocus()
                        onChange(draft.copy(format = suggested))
                    }
                }
            }
            item(key = "from") {
                SettingRow(
                    title = stringResource(R.string.edit_valid_from),
                    value = draft.validFrom?.let { dayText(Validity.day(it)) } ?: stringResource(R.string.edit_no_date),
                    onClick = { pick(Picking.FROM) },
                )
            }
            item(key = "until") {
                SettingRow(
                    title = stringResource(R.string.edit_valid_until),
                    value = draft.expiry?.let { dayText(Validity.day(it)) } ?: stringResource(R.string.edit_no_date),
                    onClick = { pick(Picking.UNTIL) },
                )
            }
            item(key = "groups") {
                val chosen = draft.groups.orEmpty()
                SettingRow(
                    title = stringResource(R.string.edit_groups),
                    value = if (chosen.isEmpty()) stringResource(R.string.edit_no_groups) else chosen.joinToString(", "),
                    onClick = { pick(Picking.GROUPS) },
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
        Picking.GROUPS -> GroupsChoice(
            all = allGroups,
            chosen = draft.groups.orEmpty(),
            onToggle = { g ->
                val now = draft.groups.orEmpty()
                onChange(draft.copy(groups = if (g in now) now - g else now + g))
            },
            onNew = { name ->
                onNewGroup(name)
                onChange(draft.copy(groups = (draft.groups.orEmpty() + name.trim()).distinct()))
            },
            onDismiss = { picking = null },
        )
        null -> Unit
    }
}

/** The groups a card is in, ticked in place, and a new one typed in. */
@Composable
private fun GroupsChoice(
    all: List<String>,
    chosen: List<String>,
    onToggle: (String) -> Unit,
    onNew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val addTyped = {
        if (name.isNotBlank()) onNew(name)
        name = ""
    }
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = stringResource(R.string.edit_groups), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        if (all.isNotEmpty()) {
            // As tall as the groups when they fit. While a name is being typed the keyboard takes
            // the lower half of the screen and the dialog sits above it: the list then pages two
            // rows at a time, so the field and Done stay in sight instead of the dialog running
            // up under the status bar.
            val listState = rememberLazyListState()
            val height = rememberFittedHeight(listState, room = if (typing) 110.dp else 300.dp, tail = false)
            PagedColumn(
                Modifier.fillMaxWidth().heightIn(max = height),
                state = listState,
                showCutRow = true,
            ) {
                for (g in all) {
                    item(key = "g:$g") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onToggle(g) }.padding(vertical = 4.dp),
                        ) {
                            CheckboxMMD(checked = g in chosen, onCheckedChange = { onToggle(g) })
                            TextMMD(text = g, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        TextFieldMMD(
            value = name,
            onValueChange = { name = it },
            label = { TextMMD(text = stringResource(R.string.edit_new_group)) },
            singleLine = true,
            // Done on the keyboard (or Enter) adds the group typed and puts the keyboard away.
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addTyped(); focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).onFocusChanged { typing = it.isFocused },
        )
        Spacer(Modifier.height(14.dp))
        // The ticks take effect as they are made; this only closes, adding a group if one was typed.
        OutlinedButtonMMD(
            onClick = {
                addTyped()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.done), style = MaterialTheme.typography.bodySmall) }
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
        PagedColumn(Modifier.fillMaxWidth().height(460.dp), showCutRow = true) {
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
