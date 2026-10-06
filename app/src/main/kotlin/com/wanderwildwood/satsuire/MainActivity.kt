package com.wanderwildwood.satsuire

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.satsuire.data.Incoming
import com.wanderwildwood.satsuire.ui.WalletApp
import com.wanderwildwood.satsuire.ui.monochrome

/**
 * The one screen, opened from the launcher or handed something by another app: a picture or a
 * PDF with a barcode in it, a pass file, a Catima link, or a number as text.
 */
class MainActivity : ComponentActivity() {

    /** What another app handed over and has not been read yet. */
    private val handed = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Read once: after the system recreates the activity the same intent comes back, and
        // the card it made is already on screen or saved.
        if (savedInstanceState == null && Incoming.carries(intent)) handed.value = intent
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                WalletApp(handed = handed.value, onHandled = { handed.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (Incoming.carries(intent)) handed.value = intent
    }
}
