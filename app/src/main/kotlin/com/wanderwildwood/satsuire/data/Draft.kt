package com.wanderwildwood.satsuire.data

import androidx.compose.runtime.saveable.Saver
import protect.card_locker.LoyaltyCard

/**
 * A card being added or changed: what the edit screen holds until it is saved. Plain values
 * only, so it survives the app being closed by the system while the keyboard is up.
 *
 * [format] is a ZXing format name, or null for a card with no barcode at all, whose number
 * is read out rather than scanned. [barcodeValue] is Catima's "barcode differs from the
 * number" field: kept, and shown only when a card already has one.
 */
data class Draft(
    val id: Int = -1,
    val name: String = "",
    val number: String = "",
    val format: String? = null,
    val barcodeValue: String? = null,
    val validFrom: Long? = null,
    val expiry: Long? = null,
    val note: String = "",
) {
    val isNew: Boolean get() = id < 0

    /** A card needs a name and something to show; the rest is optional. */
    val savable: Boolean get() = name.isNotBlank() && number.isNotEmpty()

    companion object {
        fun of(card: LoyaltyCard) = Draft(
            id = card.id,
            name = card.store,
            number = card.cardId,
            format = card.barcodeType?.name(),
            barcodeValue = card.barcodeId,
            validFrom = card.validFrom?.time,
            expiry = card.expiry?.time,
            note = card.note,
        )

        val saver: Saver<Draft?, Any> = Saver(
            save = { d ->
                if (d == null) {
                    arrayListOf<Any?>()
                } else {
                    arrayListOf<Any?>(d.id, d.name, d.number, d.format, d.barcodeValue, d.validFrom, d.expiry, d.note)
                }
            },
            restore = { v ->
                val l = v as List<*>
                if (l.isEmpty()) {
                    null
                } else {
                    Draft(
                        id = l[0] as Int,
                        name = l[1] as String,
                        number = l[2] as String,
                        format = l[3] as String?,
                        barcodeValue = l[4] as String?,
                        validFrom = l[5] as Long?,
                        expiry = l[6] as Long?,
                        note = l[7] as String,
                    )
                }
            },
        )
    }
}
