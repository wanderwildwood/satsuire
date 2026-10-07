package com.wanderwildwood.satsuire.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.satsuire.R
import protect.card_locker.CatimaBarcode

/**
 * The camera, reading for any barcode Catima knows. The first one read ends the screen.
 *
 * The camera's picture is drawn as it comes, which on this panel is slow and smeared, so the
 * picture is for aiming only; the reading is done on the camera's own frames, not the panel's.
 * Without the camera permission it says so and offers typing the number instead.
 */
@Composable
fun ScanScreen(onRead: (String, BarcodeFormat) -> Unit, onType: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var refused by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        refused = !granted
    }
    LaunchedEffect(Unit) { if (!allowed && !refused) ask.launch(Manifest.permission.CAMERA) }
    // Allowed later in the phone's settings, the camera comes on when the app is back in front.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            ) {
                allowed = true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { Bar(stringResource(R.string.scan_title), back = Icons.Close, onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (allowed) {
                Box(Modifier.weight(1f).fillMaxWidth()) { Camera(onRead) }
                TextMMD(
                    text = stringResource(R.string.scan_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp),
                )
            } else if (refused) {
                Column(Modifier.padding(20.dp)) {
                    TextMMD(text = stringResource(R.string.scan_no_camera), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(18.dp))
                    OutlinedButtonMMD(onClick = onType, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        TextMMD(text = stringResource(R.string.add_type), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Camera(onRead: (String, BarcodeFormat) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var done by remember { mutableStateOf(false) }
    val view = remember { mutableStateOf<BarcodeView?>(null) }
    AndroidView(
        factory = { ctx ->
            BarcodeView(ctx).apply {
                decoderFactory = DefaultDecoderFactory(CatimaBarcode.barcodeFormats)
                decodeContinuous { result ->
                    if (done) return@decodeContinuous
                    val format = result.barcodeFormat
                    if (format !in CatimaBarcode.barcodeFormats) return@decodeContinuous
                    done = true
                    pause()
                    onRead(result.text, format)
                }
                view.value = this
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
    DisposableEffect(lifecycle, view.value) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!done) view.value?.resume()
                Lifecycle.Event.ON_PAUSE -> view.value?.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.value?.resume()
        onDispose {
            lifecycle.removeObserver(observer)
            view.value?.pause()
        }
    }
}
