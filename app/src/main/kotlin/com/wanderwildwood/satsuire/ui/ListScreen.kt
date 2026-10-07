package com.wanderwildwood.satsuire.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.satsuire.R
import com.wanderwildwood.satsuire.data.Validity
import com.wanderwildwood.satsuire.data.Wallet
import java.time.LocalDate

/**
 * The cards, favourites first, in the order chosen in settings, all of them or one group's. A card's line under its name says
 * only what changes whether it can be used: not valid yet, valid until a day, expired.
 *
 * [archived] lists the cards put away instead, reached from the last row of the main list.
 */
@Composable
fun ListScreen(
    rows: List<Wallet.Row>?,
    archived: Boolean,
    archivedCount: Int,
    group: String?,
    groups: List<Pair<String, Int>>,
    onGroup: (String?) -> Unit,
    onOpen: (Int) -> Unit,
    onArchived: () -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (archived) {
                Bar(stringResource(R.string.put_away_title), onBack = onBack)
            } else {
                Bar(stringResource(R.string.app_name), onBack = null) {
                    BarButton(Icons.Add, stringResource(R.string.cd_add), onAdd)
                    BarButton(Icons.Settings, stringResource(R.string.cd_settings), onSettings)
                    BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout)
                }
            }
        },
    ) { padding ->
        val today = LocalDate.now()
        var choosing by rememberSaveable { mutableStateOf(false) }
        if (rows == null) return@Scaffold
        if (choosing) {
            GroupFilterDialog(group, groups, onPick = { choosing = false; onGroup(it) }, onDismiss = { choosing = false })
        }
        val showFilter = !archived && groups.isNotEmpty()
        if (rows.isEmpty() && archivedCount == 0 && !showFilter) {
            TextMMD(
                text = stringResource(if (archived) R.string.put_away_none else R.string.list_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(padding).padding(20.dp),
            )
            return@Scaffold
        }
        LazyColumnMMD(Modifier.padding(padding).fillMaxSize()) {
            if (showFilter) {
                item(key = "filter") {
                    TextMMD(
                        text = stringResource(R.string.list_showing, group ?: stringResource(R.string.group_all)),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { choosing = true }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    HorizontalDividerMMD()
                }
            }
            for (row in rows) {
                item(key = "c:${row.id}") {
                    CardRow(row, today) { onOpen(row.id) }
                    HorizontalDividerMMD()
                }
            }
            if (!archived && archivedCount > 0) {
                item(key = "archived") {
                    TextMMD(
                        text = pluralStringResource(R.plurals.put_away_row, archivedCount, archivedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onArchived)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardRow(row: Wallet.Row, today: LocalDate, onClick: () -> Unit) {
    val validity = Validity.of(row.validFrom?.time, row.expiry?.time, today)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.starred) {
                Icon(
                    imageVector = Icons.Star,
                    contentDescription = stringResource(R.string.cd_favourite),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 6.dp).size(18.dp),
                )
            }
            TextMMD(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        validityText(validity, short = true)?.let {
            TextMMD(text = it, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Every card, or one group's: Catima's groups as a filter on the list. */
@Composable
private fun GroupFilterDialog(current: String?, groups: List<Pair<String, Int>>, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        LazyColumnMMD(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            item(key = "all") { ChoiceRow(stringResource(R.string.group_all), bold = current == null) { onPick(null) } }
            for ((name, count) in groups) {
                item(key = "g:$name") {
                    ChoiceRow(name, pluralStringResource(R.plurals.group_count, count, count), bold = current == name) { onPick(name) }
                }
            }
        }
    }
}
