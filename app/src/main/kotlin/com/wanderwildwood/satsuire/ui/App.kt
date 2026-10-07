package com.wanderwildwood.satsuire.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.satsuire.R
import com.wanderwildwood.satsuire.barcode.shareIntent
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

/**
 * The whole app: a stack of screens over the list of cards, and whatever another app handed
 * over ([handed]) read into a new card.
 *
 * The stack and a card being edited are kept through the system closing the app, so a card
 * half typed in is still there when the phone comes back to it.
 */
@Composable
fun WalletApp(handed: Intent?, onHandled: () -> Unit) {
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
    val groups by produceState(emptyList<Pair<String, Int>>(), refresh) {
        value = withContext(Dispatchers.IO) { Wallet.groups(context) }
    }
    // A group deleted or renamed since it was chosen shows every card rather than none.
    val shownGroup = group?.takeIf { g -> groups.any { it.first == g } }

    fun changed() {
        refresh++
        GlanceProvider.changed(context)
    }

    fun push(s: String) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    fun startEditing(result: ParseResult) {
        val card = result.loyaltyCard
        if (result.parseResultType == ParseResultType.FULL) {
            base = card
            draft = Draft.of(card).copy(id = -1, groups = listOfNotNull(shownGroup))
        } else {
            base = null
            draft = Draft(number = card.cardId, format = card.barcodeType?.name(), groups = listOfNotNull(shownGroup))
        }
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

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
                    .getOrDefault(Transfer.Result.NotACardFile)
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
            }
        }
    }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) bringIn(uri, null)
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { Transfer.sendOut(context, uri) }
                    .onFailure { Log.w(TAG, "Saving cards out failed", it) }
                    .getOrDefault(false)
            }
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
                onOpen = { id ->
                    push(card(id))
                    scope.launch {
                        withContext(Dispatchers.IO) { Wallet.touch(context, id) }
                        if (order == Wallet.Order.LAST_USED) refresh++
                    }
                },
                onArchived = { push(ARCHIVED) },
                onAdd = { adding = true },
                onSettings = { push(SETTINGS) },
                onAbout = { about = true },
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
                    onSave = {
                        scope.launch {
                            val id = withContext(Dispatchers.IO) {
                                runCatching { Wallet.save(context, d, base) }
                                    .onFailure { Log.w(TAG, "Saving a card failed", it) }
                                    .getOrNull()
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
                            withContext(Dispatchers.IO) { Wallet.delete(context, d.id) }
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
                draft = Draft(number = text, format = format.name)
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
                onLockScreen = on,
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
                onBringIn = { importFile.launch(arrayOf("*/*")) },
                onSendOut = { exportFile.launch("wallet-${LocalDate.now()}.zip") },
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

    locked?.let { uri ->
        var password by remember { mutableStateOf("") }
        EInkDialog(onDismiss = { locked = null }) {
            TextMMD(text = stringResource(R.string.transfer_password), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            TextFieldMMD(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            DialogButtons(
                onCancel = { locked = null },
                onOk = {
                    locked = null
                    bringIn(uri, password.toCharArray())
                },
                okLabel = stringResource(R.string.transfer_open),
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
 * The card's days as an event in whatever calendar app answers Android's request to add one —
 * Calendar does. All-day, from the first day it is good to the last; a card with only one of
 * the two dates becomes a one-day event on that day. Returns what to say when nothing answers.
 */
private fun addToCalendar(context: Context, card: LoyaltyCard): String? {
    val from = card.validFrom?.time
    val until = card.expiry?.time
    val startDay = Validity.day(from ?: until ?: return null)
    val endDay = Validity.day(until ?: from!!)
    // Local midnights, as Etar sends an all-day event (Calendar reads both ways). The last day
    // is included, so the end is the midnight after it.
    val begin = Validity.millis(startDay)
    val end = Validity.millis(endDay.plusDays(1))
    val kind = card.barcodeType?.prettyName()
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, card.store)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        .putExtra(
            CalendarContract.Events.DESCRIPTION,
            listOfNotNull(card.cardId, kind, card.note.takeIf { it.isNotBlank() }).joinToString("\n"),
        )
    return try {
        context.startActivity(intent)
        null
    } catch (_: ActivityNotFoundException) {
        context.getString(R.string.no_calendar)
    }
}

private fun share(context: Context, card: LoyaltyCard): String? = try {
    context.startActivity(Intent.createChooser(shareIntent(context, card), null))
    null
} catch (e: Exception) {
    Log.w(TAG, "Sharing a card failed", e)
    context.getString(R.string.share_failed)
}
