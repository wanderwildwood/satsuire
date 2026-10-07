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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.satsuire.R

/**
 * Catima's groups: added, renamed, deleted. Deleting a group leaves its cards where they are.
 * Cards are put in groups from their own editor.
 */
@Composable
fun GroupsScreen(
    groups: List<Pair<String, Int>>,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(stringResource(R.string.settings_groups), onBack = onBack) {
                BarButton(Icons.Add, stringResource(R.string.cd_add_group)) { adding = true }
            }
        },
    ) { padding ->
        if (groups.isEmpty()) {
            TextMMD(
                text = stringResource(R.string.groups_none),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(padding).padding(20.dp),
            )
            return@Scaffold
        }
        LazyColumnMMD(Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
            for ((name, count) in groups) {
                item(key = "g:$name") {
                    SettingRow(name, pluralStringResource(R.plurals.group_count, count, count)) { open = name }
                }
            }
        }
    }

    if (adding) {
        NameDialog(stringResource(R.string.groups_new), "", onDone = { onAdd(it); adding = false }, onDismiss = { adding = false })
    }
    open?.let { name ->
        EInkDialog(onDismiss = { open = null }) {
            var text by rememberSaveable(name) { mutableStateOf(name) }
            TextFieldMMD(
                value = text,
                onValueChange = { text = it },
                label = { TextMMD(text = stringResource(R.string.groups_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            ArmedRow(stringResource(R.string.groups_delete), stringResource(R.string.groups_delete_armed)) {
                onDelete(name)
                open = null
            }
            Spacer(Modifier.height(8.dp))
            DialogButtons(onCancel = { open = null }, onOk = {
                if (text.isNotBlank() && text.trim() != name) onRename(name, text)
                open = null
            }, okLabel = stringResource(R.string.save))
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    EInkDialog(onDismiss = onDismiss) {
        TextFieldMMD(
            value = text,
            onValueChange = { text = it },
            label = { TextMMD(text = title) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        DialogButtons(onCancel = onDismiss, onOk = { if (text.isNotBlank()) onDone(text) else onDismiss() })
    }
}
