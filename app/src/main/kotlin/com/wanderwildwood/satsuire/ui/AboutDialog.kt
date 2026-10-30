package com.wanderwildwood.satsuire.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.satsuire.BuildConfig
import com.wanderwildwood.satsuire.R

/**
 * About: the notes, the legal pages and the source, in a box of fixed height that turns by the page
 * (the dialog cannot scroll); Close stays under it.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val room = LocalConfiguration.current.screenHeightDp.dp * 0.62f
    var licences by rememberSaveable { mutableStateOf(false) }
    EInkDialog(onDismiss = onDismiss) {
        if (licences) {
            LicencesPage(onBack = { licences = false }, room = room)
            return@EInkDialog
        }
        PagedColumn(modifier = Modifier.fillMaxWidth().height(room)) {
            item(key = "title") {
                TextMMD(
                    text = stringResource(R.string.about_title, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            item(key = "privacy") { AboutPart(R.string.about_privacy_title, R.string.about_privacy) }
            item(key = "terms") { AboutPart(R.string.about_terms_title, R.string.about_terms) }
            item(key = "catima") {
                TextMMD(text = stringResource(R.string.about_catima), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 14.dp))
            }
            item(key = "licences") {
                TextMMD(
                    text = stringResource(R.string.about_licences),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { licences = true }.padding(top = 14.dp).heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
                )
            }
            item(key = "source") {
                TextMMD(
                    text = "github.com/wanderwildwood/satsuire",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.openLink("https://github.com/wanderwildwood/satsuire").padding(top = 14.dp),
                )
            }
            item(key = "llama") { Box(Modifier.padding(top = 4.dp)) { Llama() } }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.about_close), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun AboutPart(head: Int, body: Int) {
    Column(Modifier.padding(top = 14.dp)) {
        TextMMD(text = stringResource(head), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        TextMMD(text = stringResource(body), style = MaterialTheme.typography.labelSmall)
    }
}

/** A target of at least 48 dp that opens [url], or says there is no browser to do it. */
@Composable
private fun Modifier.openLink(url: String): Modifier {
    val context = LocalContext.current
    val shown = url.removePrefix("https://")
    return this
        .clickable { openLink(context, url, context.getString(R.string.about_no_browser_address, shown)) }
        .heightIn(min = 48.dp)
        .wrapContentHeight(Alignment.CenterVertically)
}

/**
 * A llama at the foot of the About, which opens the page a donation goes to. The site's
 * address sits at the start of the same line and opens the site; the llama and its words
 * open the page.
 *
 * Straight to the checkout: the Donate button on the site only leads there anyway. The short
 * square.link form, which is what the site itself links to, so a regenerated checkout follows
 * it. A phone with nothing that opens a web address says so rather than doing nothing.
 */
@Composable
private fun Llama() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // The site's address opens the site, the way the llama beside it opens its page.
        TextMMD(
            text = "wanderthe.dev",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.openLink("https://wanderthe.dev"),
        )
        Spacer(Modifier.width(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.openLink("https://square.link/u/AGu8oT10"),
        ) {
            Image(
                painter = painterResource(R.drawable.llama),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            TextMMD(text = stringResource(R.string.about_feed_the_llamas), style = MaterialTheme.typography.labelSmall)
        }
    }
}
