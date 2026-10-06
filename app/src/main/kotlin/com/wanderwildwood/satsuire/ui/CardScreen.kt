package com.wanderwildwood.satsuire.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.satsuire.R
import com.wanderwildwood.satsuire.barcode.modules
import com.wanderwildwood.satsuire.data.Validity
import protect.card_locker.LoyaltyCard
import java.time.LocalDate

/** What the card screen's ⋮ can do. */
enum class CardAction { STAR, CALENDAR, SHARE, ARCHIVE }

/**
 * One card: its barcode filling what the screen has, the number written out under it for a
 * cashier to type when a scanner will not read, and when it can be used.
 *
 * The screen stays awake while it is open: a barcode on a phone that dims mid-queue has to be
 * woken and found again.
 */
@Composable
fun CardScreen(
    card: LoyaltyCard,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAction: (CardAction) -> Unit,
) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var menu by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf(false) }
    val modules = remember(card.id, card.cardId, card.barcodeId, card.barcodeType?.name()) { card.modules() }
    val validity = Validity.of(card.validFrom?.time, card.expiry?.time, LocalDate.now())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(card.store, onBack = onBack) {
                BarButton(Icons.Edit, stringResource(R.string.cd_edit), onEdit)
                BarButton(Icons.More, stringResource(R.string.cd_more), { menu = true })
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    modules != null -> BarcodeImage(modules, Modifier.fillMaxSize())
                    card.barcodeType != null -> TextMMD(
                        text = stringResource(R.string.card_cannot_draw, card.barcodeType!!.prettyName()),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(20.dp),
                    )
                    // A card with no barcode is shown by its number alone, below.
                    else -> Unit
                }
            }
            TextMMD(
                text = card.cardId,
                style = if (modules == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
            val dates = validityText(validity)
            if (dates != null) {
                TextMMD(
                    text = dates,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (validity is Validity.Expired || validity is Validity.NotYet) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (card.note.isNotBlank()) {
                TextMMD(
                    text = card.note,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    // A pass's note can run to a dozen lines; three show, and a press shows all.
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { note = true }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Box(Modifier.padding(bottom = 12.dp))
        }
    }

    if (note) MessageDialog(card.note) { note = false }

    if (menu) {
        EInkDialog(onDismiss = { menu = false }) {
            fun pick(a: CardAction) {
                menu = false
                onAction(a)
            }
            ChoiceRow(stringResource(if (card.starStatus != 0) R.string.action_unstar else R.string.action_star)) { pick(CardAction.STAR) }
            if (card.validFrom != null || card.expiry != null) {
                ChoiceRow(stringResource(R.string.action_calendar)) { pick(CardAction.CALENDAR) }
            }
            ChoiceRow(stringResource(R.string.action_share)) { pick(CardAction.SHARE) }
            ChoiceRow(stringResource(if (card.archiveStatus != 0) R.string.action_unarchive else R.string.action_archive)) {
                pick(CardAction.ARCHIVE)
            }
        }
    }
}
