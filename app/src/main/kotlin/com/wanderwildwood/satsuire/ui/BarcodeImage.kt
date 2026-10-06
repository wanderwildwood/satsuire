package com.wanderwildwood.satsuire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.satsuire.R
import com.wanderwildwood.satsuire.barcode.Fit
import com.wanderwildwood.satsuire.barcode.Modules
import com.wanderwildwood.satsuire.barcode.bitmap
import protect.card_locker.CatimaBarcode

/**
 * The barcode, as large as the space allows and drawn pixel for pixel: the drawing is made at
 * exactly the size it is shown, placed on a whole pixel, and copied without filtering, so
 * nothing between this and the panel can scale it and grey its edges.
 */
@Composable
fun BarcodeImage(modules: Modules, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val fit = remember(modules, w, h) { Fit.of(modules, w, h) }
        if (fit == null) {
            TextMMD(
                text = stringResource(R.string.card_too_long),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(20.dp),
            )
            return@BoxWithConstraints
        }
        val image = remember(modules, fit) { bitmap(modules, fit).asImageBitmap() }
        val label = stringResource(R.string.cd_barcode, CatimaBarcode.fromBarcode(modules.format).prettyName())
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = label }) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(fit.width, fit.height),
                dstOffset = IntOffset((w - fit.width) / 2, (h - fit.height) / 2),
                dstSize = IntSize(fit.width, fit.height),
                filterQuality = FilterQuality.None,
            )
        }
    }
}
