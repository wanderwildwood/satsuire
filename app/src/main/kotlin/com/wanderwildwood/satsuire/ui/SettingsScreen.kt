package com.wanderwildwood.satsuire.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.wanderwildwood.satsuire.R

/** The list's order, the groups, the lock-screen switch, and cards in and out as files. */
@Composable
fun SettingsScreen(
    onLockScreen: Boolean,
    glanceInstalled: Boolean,
    onLockScreenChange: (Boolean) -> Unit,
    order: String,
    onOrder: () -> Unit,
    groupCount: Int,
    onGroups: () -> Unit,
    onBringIn: () -> Unit,
    onSendOut: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { Bar(stringResource(R.string.settings_title), onBack = onBack) },
    ) { padding ->
        LazyColumnMMD(Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
            item(key = "order") {
                SettingRow(stringResource(R.string.settings_order), order, onOrder)
            }
            item(key = "groups") {
                SettingRow(
                    stringResource(R.string.settings_groups),
                    pluralStringResource(R.plurals.settings_groups_value, groupCount, groupCount),
                    onGroups,
                )
            }
            item(key = "lock") {
                SwitchRow(
                    title = stringResource(R.string.settings_lock_screen),
                    checked = onLockScreen,
                    // Whether Glance is there is a fact about the phone the switch cannot show.
                    note = if (glanceInstalled) null else stringResource(R.string.settings_lock_screen_no_glance),
                    onChange = onLockScreenChange,
                )
            }
            item(key = "in") {
                SettingRow(stringResource(R.string.settings_bring_in), stringResource(R.string.settings_bring_in_value), onBringIn)
            }
            item(key = "out") {
                SettingRow(stringResource(R.string.settings_send_out), stringResource(R.string.settings_send_out_value), onSendOut)
            }
        }
    }
}
