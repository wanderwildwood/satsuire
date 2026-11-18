package com.wanderwildwood.satsuire.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.activity.compose.BackHandler
import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import com.mudita.mmd.components.checkbox.CheckboxMMD
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.satsuire.R
import com.wanderwildwood.satsuire.barcode.shareIntent
import com.wanderwildwood.satsuire.data.CalendarEvent
import com.wanderwildwood.satsuire.data.CardLink
import com.wanderwildwood.satsuire.data.Datebook
import com.wanderwildwood.satsuire.data.Draft
import com.wanderwildwood.satsuire.data.Incoming
import com.wanderwildwood.satsuire.data.Prefs
import com.wanderwildwood.satsuire.data.Transfer
import com.wanderwildwood.satsuire.data.Validity
import com.wanderwildwood.satsuire.data.Wallet
import com.wanderwildwood.satsuire.glance.GlanceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import protect.card_locker.LoyaltyCard
import protect.card_locker.ParseResult
import protect.card_locker.ParseResultType
import java.time.LocalDate
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction

/**
 * The whole app: a stack of screens over the list of cards, and whatever another app handed
 * over ([handed]) read into a new card.
 *
 * The stack and a card being edited are kept through the system closing the app, so a card
 * half typed in is still there when the phone comes back to it.
 */
@Composable
fun WalletApp(handed: Intent?, onHandled: () -> Unit, asked: Int? = null, onAsked: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stack = rememberSaveable(saver = stackSaver()) { mutableStateListOf(LIST) }
    var draft by rememberSaveable(stateSaver = Draft.saver) { mutableStateOf<Draft?>(null) }
    // A pass file brings more than the draft holds (a balance, a colour); kept beside it.
    var base by remember { mutableStateOf<LoyaltyCard?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    var choices by remember { mutableStateOf<List<ParseResult>?>(null) }
    var locked by remember { mutableStateOf<Uri?>(null) }
    var group by remember { mutableStateOf(Prefs.group(context)) }
    var order by remember { mutableStateOf(Prefs.order(context)) }
    var ordering by rememberSaveable { mutableStateOf(false) }
    // The card just deleted, kept so the list can offer to put it back.
    var removed by remember { mutableStateOf<Wallet.Removed?>(null) }
    val groups by produceState(emptyList<Pair<String, Int>>(), refresh) {
        value = withContext(Dispatchers.IO) { Wallet.groups(context) }
    }
    // A group deleted or renamed since it was chosen shows every card rather than none.
    val shownGroup = group?.takeIf { g -> groups.any { it.first == g } }

    fun changed() {
        refresh++
        GlanceProvider.changed(context)
    }

    var saving by remember { mutableStateOf(false) }

    fun push(s: String) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    // A card handed in while another is being edited, held until the person says whether to
    // leave the one in the editor; nothing replaces an unsaved draft without asking.
    var waiting by remember { mutableStateOf<ParseResult?>(null) }

    fun startEditing(result: ParseResult, leave: Boolean = false) {
        if (!leave && stack.last() == EDIT) {
            waiting = result
            return
        }
        val card = result.loyaltyCard
        if (result.parseResultType == ParseResultType.FULL) {
            base = card
            draft = Draft.of(card).copy(id = -1, groups = listOfNotNull(shownGroup))
        } else {
            base = null
            draft = Draft(number = card.cardId, format = card.barcodeType?.name(), groups = listOfNotNull(shownGroup))
        }
        // One editor at a time: a card handed in while another was being edited, once the person
        // has chosen to leave that one, takes its place.
        if (stack.last() == EDIT) pop()
        push(EDIT)
    }

    fun take(found: Incoming) {
        when (found) {
            is Incoming.Found -> if (found.results.size == 1) startEditing(found.results[0]) else choices = found.results
            Incoming.NoBarcode -> message = context.getString(R.string.incoming_no_barcode)
            Incoming.Unreadable -> message = context.getString(R.string.incoming_unreadable)
        }
    }

    // Something handed over by another app: a picture, a pass, a link or a number.
    // Taken off the activity at once, so it is read once; read in its own effect, which clearing
    // the activity's copy does not cancel.
    var reading by remember { mutableStateOf<Intent?>(null) }
    LaunchedEffect(handed) {
        if (handed != null) {
            reading = handed
            onHandled()
        }
    }
    LaunchedEffect(reading) {
        val intent = reading ?: return@LaunchedEffect
        busy = context.getString(R.string.incoming_reading)
        val found = withContext(Dispatchers.IO) { Incoming.read(context, intent) }
        busy = null
        take(found)
        reading = null
    }

    // A card asked for by the calendar event made from it; said plainly when it has gone since.
    // Taken off the activity at once and looked up in its own effect, as with [handed]: clearing
    // the activity's copy changes the key above, which would cancel a lookup made there.
    var opening by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(asked) {
        if (asked != null) {
            opening = asked
            onAsked()
        }
    }
    LaunchedEffect(opening) {
        val id = opening ?: return@LaunchedEffect
        val there = withContext(Dispatchers.IO) { Wallet.card(context, id) } != null
        if (there) {
            if (stack.last() != card(id)) push(card(id))
        } else {
            message = context.getString(R.string.card_gone)
        }
        opening = null
    }

    val pickFile = rememberLauncherForActivityResult(FilesOpenDocument(writable = false)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = context.getString(R.string.incoming_reading)
            val found = withContext(Dispatchers.IO) { Incoming.file(context, uri, null) }
            busy = null
            take(found)
        }
    }

    fun bringIn(uri: Uri, password: CharArray?) {
        scope.launch {
            busy = context.getString(R.string.transfer_reading)
            val result = withContext(Dispatchers.IO) {
                runCatching { Transfer.bringIn(context, uri, password) }
                    .onFailure { Log.w(TAG, "Bringing cards in failed", it) }
                    .getOrDefault(Transfer.Result.Unreadable)
            }
            busy = null
            when (result) {
                is Transfer.Result.Brought -> {
                    message = if (result.added == 0) {
                        context.getString(R.string.transfer_nothing_new)
                    } else {
                        context.resources.getQuantityString(R.plurals.transfer_brought, result.added, result.added)
                    }
                    changed()
                }
                Transfer.Result.NeedsPassword -> {
                    if (password != null) message = context.getString(R.string.transfer_wrong_password)
                    locked = uri
                }
                Transfer.Result.NotACardFile -> message = context.getString(R.string.transfer_not_cards)
                Transfer.Result.Unreadable -> message = context.getString(R.string.transfer_unreadable)
            }
        }
    }

    val importFile = rememberLauncherForActivityResult(FilesOpenDocument(writable = false)) { uri ->
        if (uri != null) bringIn(uri, null)
    }
    // Where to save, asked first; then whether to lock it, asked over the list.
    var exporting by remember { mutableStateOf<Uri?>(null) }
    // Whether the picked file was new and empty. A file picked to be written over is never deleted.
    var exportIsNew by remember { mutableStateOf(false) }
    val exportFile = rememberLauncherForActivityResult(FilesCreateDocument("application/zip")) { uri ->
        if (uri != null) {
            exportIsNew = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                    c.moveToFirst() && !c.isNull(0) && c.getLong(0) == 0L
                }
            }.getOrNull() ?: false
            exporting = uri
        }
    }
    // The file picker has already made an empty file by the time the password is asked: if nothing
    // is written to it, it must not be left behind. One that was there before is left alone.
    fun dropExport(uri: Uri) {
        if (!exportIsNew) return
        runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }
    fun sendOut(uri: Uri, password: CharArray?) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { Transfer.sendOut(context, uri, password) }
                    .onFailure { Log.w(TAG, "Saving cards out failed", it) }
                    .getOrDefault(false)
            }
            if (!ok) dropExport(uri)
            message = context.getString(if (ok) R.string.transfer_saved else R.string.transfer_not_saved)
        }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    val top = stack.last()
    when {
        top == LIST || top == ARCHIVED -> {
            val archived = top == ARCHIVED
            val rows by produceState<List<Wallet.Row>?>(null, refresh, archived, shownGroup, order) {
                value = withContext(Dispatchers.IO) { Wallet.rows(context, archived, if (archived) null else shownGroup, order) }
            }
            val archivedCount by produceState(0, refresh) {
                value = withContext(Dispatchers.IO) { Wallet.archivedCount(context) }
            }
            ListScreen(
                rows = rows,
                archived = archived,
                archivedCount = if (archived) 0 else archivedCount,
                group = shownGroup,
                groups = groups,
                onGroup = {
                    group = it
                    Prefs.setGroup(context, it)
                },
                removed = if (archived) null else removed,
                onUndo = {
                    val r = removed
                    removed = null
                    if (r != null) scope.launch {
                        withContext(Dispatchers.IO) { Wallet.restore(context, r) }
                        changed()
                    }
                },
                onOpen = { id ->
                    removed = null
                    push(card(id))
                    scope.launch {
                        withContext(Dispatchers.IO) { Wallet.touch(context, id) }
                        if (order == Wallet.Order.LAST_USED) refresh++
                    }
                },
                onArchived = { push(ARCHIVED) },
                onAdd = { removed = null; adding = true },
                onSettings = { removed = null; push(SETTINGS) },
                onBack = ::pop,
            )
        }
        top.startsWith(CARD) -> {
            val id = top.removePrefix(CARD).toInt()
            val card by produceState<LoyaltyCard?>(null, id, refresh) {
                value = withContext(Dispatchers.IO) { Wallet.card(context, id) }
            }
            val c = card
            if (c != null) {
                CardScreen(
                    card = c,
                    datebook = { Datebook.takes(Datebook.answering(context.packageManager)) },
                    onBack = ::pop,
                    onEdit = {
                        scope.launch {
                            val inGroups = withContext(Dispatchers.IO) { Wallet.cardGroups(context, c.id) }
                            base = null
                            draft = Draft.of(c).copy(groups = inGroups)
                            push(EDIT)
                        }
                    },
                    onAction = { action ->
                        when (action) {
                            CardAction.STAR -> scope.launch {
                                withContext(Dispatchers.IO) { Wallet.setStarred(context, c.id, c.starStatus == 0) }
                                changed()
                            }
                            CardAction.ARCHIVE -> scope.launch {
                                val putAway = c.archiveStatus == 0
                                withContext(Dispatchers.IO) { Wallet.setArchived(context, c.id, putAway) }
                                changed()
                                // Put away, a card leaves the list it was opened from.
                                if (putAway) pop()
                            }
                            CardAction.CALENDAR -> addToCalendar(context, c)?.let { message = it }
                            CardAction.SHARE -> share(context, c)?.let { message = it }
                            CardAction.DELETE -> scope.launch {
                                removed = withContext(Dispatchers.IO) { Wallet.remove(context, c.id) }
                                changed()
                                pop()
                            }
                        }
                    },
                )
            }
        }
        top == EDIT -> {
            val d = draft
            if (d == null) {
                // Nothing being edited (the screen outlived what it was editing): step back.
                LaunchedEffect(Unit) { pop() }
            } else {
                EditScreen(
                    draft = d,
                    onChange = { draft = it },
                    onSave = save@{
                        // A second press while the first is saving would add a new card twice.
                        if (saving) return@save
                        saving = true
                        scope.launch {
                            val id = try {
                                withContext(Dispatchers.IO) {
                                    runCatching { Wallet.save(context, d, base) }
                                        .onFailure { Log.w(TAG, "Saving a card failed", it) }
                                        .getOrNull()
                                }
                            } finally {
                                saving = false
                            }
                            if (id == null) {
                                message = context.getString(R.string.edit_not_saved)
                                return@launch
                            }
                            draft = null
                            base = null
                            changed()
                            pop()
                            // A new card opens, so it can be checked at once; a changed one is
                            // already the screen underneath.
                            if (d.isNew) push(card(id))
                        }
                    },
                    onDiscard = {
                        draft = null
                        base = null
                        pop()
                    },
                    onDelete = if (d.isNew) null else ({
                        scope.launch {
                            removed = withContext(Dispatchers.IO) { Wallet.remove(context, d.id) }
                            draft = null
                            changed()
                            // Out of the editor and off the deleted card's own screen.
                            pop()
                            if (stack.last() == card(d.id)) pop()
                        }
                    }),
                    allGroups = groups.map { it.first },
                    onNewGroup = { name ->
                        scope.launch {
                            withContext(Dispatchers.IO) { Wallet.addGroup(context, name) }
                            refresh++
                        }
                    },
                )
            }
        }
        top == SCAN -> ScanScreen(
            onRead = { text, format ->
                pop()
                base = null
                draft = Draft(number = text, format = format.name, groups = listOfNotNull(shownGroup))
                push(EDIT)
            },
            onType = {
                pop()
                base = null
                draft = Draft(format = null, groups = listOfNotNull(shownGroup))
                push(EDIT)
            },
            onBack = ::pop,
        )
        top == GROUPS -> GroupsScreen(
            groups = groups,
            onAdd = { name -> scope.launch { withContext(Dispatchers.IO) { Wallet.addGroup(context, name) }; refresh++ } },
            onRename = { from, to ->
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Wallet.renameGroup(context, from, to) }
                    if (!ok) message = context.getString(R.string.groups_not_renamed)
                    if (ok && group == from) {
                        group = to.trim()
                        Prefs.setGroup(context, group)
                    }
                    refresh++
                }
            },
            onDelete = { name -> scope.launch { withContext(Dispatchers.IO) { Wallet.deleteGroup(context, name) }; refresh++ } },
            onBack = ::pop,
        )
        top == SETTINGS -> {
            var on by remember { mutableStateOf(Prefs.onLockScreen(context)) }
            val glance = remember {
                context.packageManager.getLaunchIntentForPackage(GlanceProvider.READER) != null
            }
            SettingsScreen(
                onLockScreen = on && glance,
                glanceInstalled = glance,
                onLockScreenChange = {
                    on = it
                    Prefs.setOnLockScreen(context, it)
                    GlanceProvider.changed(context)
                },
                order = orderName(order),
                onOrder = { ordering = true },
                groupCount = groups.size,
                onGroups = { push(GROUPS) },
                onBringIn = {
                    runCatching { importFile.launch(arrayOf("*/*")) }
                        .onFailure { message = context.getString(R.string.no_file_picker) }
                },
                onSendOut = {
                    runCatching { exportFile.launch("wallet-${LocalDate.now()}.zip") }
                        .onFailure { message = context.getString(R.string.no_file_picker) }
                },
                onAbout = { about = true },
                onBack = ::pop,
            )
        }
    }

    if (ordering) {
        EInkDialog(onDismiss = { ordering = false }) {
            TextMMD(text = stringResource(R.string.settings_order), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            for (o in Wallet.Order.entries) {
                ChoiceRow(orderName(o), bold = o == order) {
                    order = o
                    Prefs.setOrder(context, o)
                    ordering = false
                }
            }
        }
    }

    if (adding) {
        EInkDialog(onDismiss = { adding = false }) {
            TextMMD(text = stringResource(R.string.add_title), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            ChoiceRow(stringResource(R.string.add_scan)) {
                adding = false
                push(SCAN)
            }
            ChoiceRow(stringResource(R.string.add_type)) {
                adding = false
                base = null
                draft = Draft(groups = listOfNotNull(shownGroup))
                push(EDIT)
            }
            ChoiceRow(stringResource(R.string.add_file), stringResource(R.string.add_file_note)) {
                adding = false
                runCatching { pickFile.launch(arrayOf("image/*", "application/pdf", "application/vnd.apple.pkpass", "application/octet-stream")) }
                    .onFailure { message = context.getString(R.string.no_file_picker) }
            }
        }
    }

    choices?.let { list ->
        EInkDialog(onDismiss = { choices = null }) {
            TextMMD(text = stringResource(R.string.incoming_several), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            for (result in list.take(8)) {
                val card = result.loyaltyCard
                val kind = card.barcodeType?.prettyName() ?: stringResource(R.string.format_none)
                ChoiceRow(
                    text = card.store.ifBlank { card.cardId },
                    note = listOfNotNull(result.note, kind, card.cardId.takeIf { card.store.isNotBlank() }).joinToString(" · "),
                ) {
                    choices = null
                    startEditing(result)
                }
            }
        }
    }

    exporting?.let { uri ->
        // A password that cannot be recovered is typed twice, or read back in the clear: a typo
        // would make a backup nobody can open.
        var password by remember { mutableStateOf("") }
        var again by remember { mutableStateOf("") }
        var shown by remember { mutableStateOf(false) }
        var differ by remember { mutableStateOf(false) }
        val hide = if (shown) VisualTransformation.None else PasswordVisualTransformation()
        // A password keyboard either way: shown or not, it learns and suggests nothing.
        val passwordKeys = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
        val exportField = remember { FocusRequester() }
        val save = {
            if (!shown && password.isNotEmpty() && again != password) {
                differ = true
            } else {
                exporting = null
                sendOut(uri, password.toCharArray())
            }
        }
        val asksAgain = !shown && password.isNotEmpty()
        // Read from the screen beneath: the dialog's own window is moved, not resized, by the keyboard.
        val room = rememberRoomAboveKeyboard()
        EInkDialog(onDismiss = { dropExport(uri); exporting = null }) {
            // The field is what the dialog is for: it takes the focus as it opens and the keyboard
            // comes up. Asked from inside the dialog, a frame after it is drawn: the field lives in
            // the dialog's own window, which a request from the screen beneath does not reach.
            val keyboardNow = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { exportField.requestFocus() }
                keyboardNow?.show()
            }
            // With both fields and the keyboard up the dialog is taller than the room above the
            // keyboard, and a dialog is measured against the whole screen, so it ran on under the
            // keys. Cancel and Save keep their place at the foot: what is above them gets the
            // room that is left and scrolls, and the field being typed in is kept in sight.
            Column(Modifier.heightIn(max = (room - EXPORT_DIALOG_FOOT - if (differ) 32.dp else 0.dp).coerceAtLeast(96.dp)).verticalScroll(rememberScrollState())) {
            TextMMD(text = stringResource(R.string.export_password), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            TextFieldMMD(
                value = password,
                onValueChange = { password = it; differ = false },
                label = { TextMMD(text = stringResource(R.string.export_password_label)) },
                singleLine = true,
                visualTransformation = hide,
                // With the keyboard up it covers Save: its own key goes on to the second field,
                // or saves when there is none.
                keyboardOptions = passwordKeys.copy(imeAction = if (asksAgain) ImeAction.Next else ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(exportField),
            )
            if (asksAgain) {
                TextFieldMMD(
                    value = again,
                    onValueChange = { again = it; differ = false },
                    label = { TextMMD(text = stringResource(R.string.export_password_repeat)) },
                    singleLine = true,
                    visualTransformation = hide,
                    keyboardOptions = passwordKeys.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable { shown = !shown; differ = false },
            ) {
                CheckboxMMD(checked = shown, onCheckedChange = { shown = it; differ = false })
                TextMMD(text = stringResource(R.string.export_password_show), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(6.dp))
            TextMMD(text = stringResource(R.string.export_password_note), style = MaterialTheme.typography.bodySmall)
            }
            // Said beside the buttons, where it is seen whatever the part above has scrolled to.
            if (differ) {
                Spacer(Modifier.height(6.dp))
                TextMMD(text = stringResource(R.string.export_password_mismatch), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            DialogButtons(
                onCancel = { dropExport(uri); exporting = null },
                onOk = { save() },
                okLabel = stringResource(R.string.save),
            )
        }
    }

    locked?.let { uri ->
        var password by remember { mutableStateOf("") }
        val lockedField = remember { FocusRequester() }
        val openLocked = {
            locked = null
            bringIn(uri, password.toCharArray())
        }
        EInkDialog(onDismiss = { locked = null }) {
            // The field is what the dialog is for: it takes the focus as it opens and the keyboard
            // comes up. Asked from inside the dialog, a frame after it is drawn: the field lives in
            // the dialog's own window, which a request from the screen beneath does not reach.
            val keyboardNow = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { lockedField.requestFocus() }
                keyboardNow?.show()
            }
            TextMMD(text = stringResource(R.string.transfer_password), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            TextFieldMMD(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                // Done on the keyboard opens the file.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { openLocked() }),
                modifier = Modifier.fillMaxWidth().focusRequester(lockedField),
            )
            Spacer(Modifier.height(18.dp))
            DialogButtons(
                onCancel = { locked = null },
                onOk = { openLocked() },
                okLabel = stringResource(R.string.transfer_open),
            )
        }
    }

    waiting?.let { result ->
        EInkDialog(onDismiss = { waiting = null }) {
            TextMMD(text = stringResource(R.string.edit_leave_title), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            TextMMD(text = stringResource(R.string.edit_leave_text), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(18.dp))
            DialogButtons(
                cancel = stringResource(R.string.edit_leave_keep),
                confirm = stringResource(R.string.edit_leave_go),
                onCancel = { waiting = null },
                onConfirm = {
                    waiting = null
                    startEditing(result, leave = true)
                },
            )
        }
    }

    busy?.let { text ->
        EInkDialog(onDismiss = {}) { TextMMD(text = text, style = MaterialTheme.typography.bodyMedium) }
    }
    message?.let { MessageDialog(it) { message = null } }
    if (about) AboutDialog(onDismiss = { about = false })
}

private const val TAG = "Wallet"
private const val LIST = "list"
private const val ARCHIVED = "archived"
private const val CARD = "card:"
private const val EDIT = "edit"
private const val SCAN = "scan"
private const val SETTINGS = "settings"
private const val GROUPS = "groups"

@Composable
private fun orderName(o: Wallet.Order): String = stringResource(
    when (o) {
        Wallet.Order.NAME -> R.string.order_name
        Wallet.Order.LAST_USED -> R.string.order_last_used
        Wallet.Order.LAST_ADDED -> R.string.order_last_added
        Wallet.Order.EXPIRY -> R.string.order_expiry
        Wallet.Order.VALID_FROM -> R.string.order_valid_from
    },
)

private fun card(id: Int) = CARD + id

private fun stackSaver() = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { mutableStateListOf(*it.toTypedArray()) },
)

/**
 * The card as a new event in Datebook ([Datebook]), and in no other calendar.
 *
 * A complete, ordinary request, as Android's own documentation writes it: a title that is never
 * empty, a note, an empty place, an explicit "not all day", and a start and an end that are
 * always set, the half-hour stretched from the first day to the last ([CalendarEvent]), with
 * the link back to the card ([CardLink]). Addressed to Datebook by package, so it cannot reach
 * a calendar it was not made for, and started in a task of its own. The row is only offered
 * while Datebook answers it. Returns what to say when Datebook does not open.
 */
private fun addToCalendar(context: Context, card: LoyaltyCard): String? {
    val times = CalendarEvent.times(card.validFrom?.time, card.expiry?.time) ?: return null
    val title = card.store.trim().ifEmpty { context.getString(R.string.calendar_title_card) }
    val note = listOfNotNull(
        card.cardId.takeIf { it.isNotBlank() },
        card.barcodeType?.prettyName(),
        card.note.takeIf { it.isNotBlank() },
        context.getString(R.string.calendar_note_wallet, title),
    ).joinToString("\n")
    val intent = Datebook.request()
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.Events.DESCRIPTION, note)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, "")
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, times.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, times.end)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
    CardLink.put(context, intent, card.id)
    return try {
        context.startActivity(intent)
        null
    } catch (e: Exception) {
        Log.w(TAG, "Datebook did not open", e)
        context.getString(R.string.datebook_failed)
    }
}

private fun share(context: Context, card: LoyaltyCard): String? = try {
    context.startActivity(Intent.createChooser(shareIntent(context, card), null))
    null
} catch (e: Exception) {
    Log.w(TAG, "Sharing a card failed", e)
    context.getString(R.string.share_failed)
}

/** What the export dialog needs besides its scrolling part: its padding and rim, the gap, the buttons, a margin. */
private val EXPORT_DIALOG_FOOT = 36.dp + 4.dp + 18.dp + 48.dp + 10.dp

/**
 * The height of the part of the screen this window can show: below the status bar and, while the
 * keyboard is up, above it. Read from the window, since a dialog's own constraints are the whole
 * screen's whatever the keyboard covers.
 */
@Composable
private fun rememberRoomAboveKeyboard(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    var px by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val frame = Rect()
        val read = ViewTreeObserver.OnGlobalLayoutListener {
            view.getWindowVisibleDisplayFrame(frame)
            px = frame.height()
        }
        read.onGlobalLayout()
        view.viewTreeObserver.addOnGlobalLayoutListener(read)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(read) }
    }
    return if (px <= 0) Dp.Infinity else with(density) { px.toDp() }
}
