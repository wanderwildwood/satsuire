package com.wanderwildwood.satsuire.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.satsuire.R
import kotlinx.coroutines.delay

/** An icon in a top bar, with a target a thumb can find. */
@Composable
fun BarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A word in a top bar that does what it says: "Save". */
@Composable
fun BarText(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.height(48.dp).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The bar every screen has: a title, a way back where there is one, and its own actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(
    title: String,
    back: ImageVector? = Icons.Back,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBarMMD(
        title = { TextMMD(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null && back != null) {
                BarButton(back, stringResource(if (back == Icons.Close) R.string.cd_close else R.string.cd_back), onBack)
            }
        },
        actions = { actions() },
    )
}

/** A row that says what it is and what it is set to, and does something when pressed. */
@Composable
fun SettingRow(title: String, value: String?, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 14.dp),
    ) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
        if (value != null) TextMMD(text = value, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
            if (note != null) TextMMD(text = note, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(12.dp))
        SwitchMMD(checked = checked, onCheckedChange = null)
    }
}

/**
 * A row that asks once, in its own face: the first press arms it and changes what it says,
 * the second does it. It disarms itself after four seconds, so a stray press does not leave
 * a live trigger for whoever picks the phone up next.
 */
@Composable
fun ArmedRow(label: String, armedLabel: String, onConfirmed: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (!armed) return@LaunchedEffect
        delay(4000)
        armed = false
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (armed) {
                    armed = false
                    onConfirmed()
                } else {
                    armed = true
                }
            }
            .padding(vertical = 14.dp),
    ) {
        TextMMD(
            text = if (armed) armedLabel else label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** One choice in a dialog's list: the whole width presses it. */
@Composable
fun ChoiceRow(text: String, note: String? = null, bold: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        TextMMD(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
        if (note != null) TextMMD(text = note, style = MaterialTheme.typography.labelSmall)
    }
}

/** A sentence and a Close, for what has to be said and needs nothing more. */
@Composable
fun MessageDialog(message: String, onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(18.dp))
        OutlinedButtonMMD(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            TextMMD(text = stringResource(R.string.close), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun DialogButtons(onCancel: () -> Unit, onOk: () -> Unit, okLabel: String = stringResource(R.string.ok)) {
    Row(Modifier.fillMaxWidth()) {
        OutlinedButtonMMD(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp)) {
            TextMMD(text = stringResource(R.string.cancel), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        ButtonMMD(onClick = onOk, modifier = Modifier.weight(1f).height(48.dp)) {
            TextMMD(text = okLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
}
