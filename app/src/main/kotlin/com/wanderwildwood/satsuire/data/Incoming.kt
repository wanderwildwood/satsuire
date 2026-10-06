package com.wanderwildwood.satsuire.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import protect.card_locker.ImportURIHelper
import protect.card_locker.LoyaltyCard
import protect.card_locker.ParseResult
import protect.card_locker.ParseResultType
import protect.card_locker.Utils

/**
 * What another app handed over, read into cards: a picture or a PDF with a barcode in it, an
 * Apple Wallet pass, a Catima share link, or plain text taken as a card number.
 *
 * The reading itself is Catima's; this decides which of its readers a thing goes to.
 */
sealed interface Incoming {
    /** One or more cards found; the person chooses when there are several. */
    data class Found(val results: List<ParseResult>) : Incoming

    /** The file was read and holds no barcode. */
    data object NoBarcode : Incoming

    /** The file could not be read at all, or is a kind this app does not take. */
    data object Unreadable : Incoming

    companion object {
        private const val TAG = "Incoming"

        private val LINK = Regex("""https?://\S+""")

        val PASS_TYPES = setOf(
            "application/vnd.apple.pkpass",
            "application/vnd-com.apple.pkpass",
            "application/vnd.apple.pkpasses",
        )

        /** True for an intent this app was handed something by, as against opened from the launcher. */
        fun carries(intent: Intent?): Boolean = when (intent?.action) {
            Intent.ACTION_SEND -> true
            Intent.ACTION_VIEW -> intent.data != null
            else -> false
        }

        /** Reads [intent]; slow (it may decode a photograph or every page of a PDF), so off the main thread. */
        fun read(context: Context, intent: Intent): Incoming {
            if (intent.action == Intent.ACTION_SEND) {
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (stream != null) return file(context, stream, intent.type)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return Unreadable
                return text(context, text)
            }
            val uri = intent.data ?: return Unreadable
            return file(context, uri, intent.type)
        }

        /** A picture, a PDF or a pass file, told apart by its type or, failing that, its name. */
        fun file(context: Context, uri: Uri, givenType: String?): Incoming {
            val type = givenType?.lowercase() ?: context.contentResolver.getType(uri)?.lowercase()
            val name = displayName(context, uri)?.lowercase().orEmpty()
            return try {
                val results: List<ParseResult>? = when {
                    type in PASS_TYPES || name.endsWith(".pkpass") || name.endsWith(".pkpasses") -> pass(context, uri)
                    type == "application/pdf" || name.endsWith(".pdf") -> Utils.retrieveBarcodesFromPdf(context, uri)
                    type?.startsWith("image/") == true -> Utils.retrieveBarcodesFromImage(context, uri)
                    else -> null
                }
                when {
                    results == null -> Unreadable
                    results.isEmpty() -> NoBarcode
                    else -> Found(results)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read what was handed over", e)
                Unreadable
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "Out of memory reading what was handed over", e)
                Unreadable
            }
        }

        /** Catima's own order: try a single pass, and if that fails, a bundle of them. */
        private fun pass(context: Context, uri: Uri): List<ParseResult> = try {
            Utils.retrieveBarcodesFromPkPass(context, uri)
        } catch (e: Exception) {
            Utils.retrieveBarcodesFromPkPasses(context, uri)
        }

        /**
         * Shared text: a Catima share link becomes the card it describes; anything else is taken
         * as the number, whose barcode kind is then chosen by hand.
         */
        fun text(context: Context, text: String): Incoming {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return Unreadable
            // Catima shares a card as a sentence with its link at the end.
            val link = LINK.find(trimmed)?.value?.let { runCatching { Uri.parse(it) }.getOrNull() }
            if (link != null && link.host != null && link.path != null) {
                // Its parser refuses any link that is not one of Catima's, so a refusal here
                // only means the text is something else, and it falls through to a number.
                val card = runCatching { ImportURIHelper(context).parse(link) }.getOrNull()
                if (card != null) return Found(listOf(ParseResult(ParseResultType.FULL, card)))
            }
            val card = LoyaltyCard().apply { setCardId(trimmed) }
            return Found(listOf(ParseResult(ParseResultType.BARCODE_ONLY, card)))
        }

        private fun displayName(context: Context, uri: Uri): String? = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }
}
