package com.wanderwildwood.satsuire.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import protect.card_locker.CatimaBarcode
import protect.card_locker.DBHelper
import protect.card_locker.LoyaltyCard
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.Date

/**
 * The cards, kept in Catima's own database through Catima's own helper, so that what Catima
 * exports this reads, and what this exports Catima reads, with nothing translated between.
 *
 * Every call here touches the disk; call it off the main thread.
 */
object Wallet {

    private var helper: DBHelper? = null
    private var owner: Context? = null

    /** One open database for the life of the process (a new one only for a new application, as in tests). */
    @Synchronized
    fun db(context: Context): SQLiteDatabase {
        val app = context.applicationContext
        val h = helper?.takeIf { owner === app } ?: DBHelper(app).also {
            helper = it
            owner = app
        }
        return h.writableDatabase
    }

    /** One row of the list: enough to name a card and say whether it can be used today. */
    data class Row(
        val id: Int,
        val name: String,
        val starred: Boolean,
        val validFrom: Date?,
        val expiry: Date?,
    )

    /**
     * How the list is ordered, after the favourites, which always come first. Catima's own five
     * orders, each in the direction Catima defaults to: names A to Z, the most recently used or
     * added first, the soonest to start or to end first.
     */
    enum class Order(val db: DBHelper.LoyaltyCardOrder) {
        NAME(DBHelper.LoyaltyCardOrder.Alpha),
        LAST_USED(DBHelper.LoyaltyCardOrder.LastUsed),
        LAST_ADDED(DBHelper.LoyaltyCardOrder.LastAdded),
        EXPIRY(DBHelper.LoyaltyCardOrder.Expiry),
        VALID_FROM(DBHelper.LoyaltyCardOrder.ValidFrom),
    }

    /**
     * The cards, favourites first and then in [order]. [archived] picks the cards put away
     * rather than the ones in use; [group] keeps only that group's cards.
     */
    fun rows(context: Context, archived: Boolean, group: String? = null, order: Order = Order.NAME): List<Row> {
        val filter = if (archived) DBHelper.LoyaltyCardArchiveFilter.Archived else DBHelper.LoyaltyCardArchiveFilter.Unarchived
        val db = db(context)
        val g = group?.let { DBHelper.getGroup(db, it) }
        val out = ArrayList<Row>()
        DBHelper.getLoyaltyCardCursor(db, "", g, order.db, DBHelper.LoyaltyCardOrderDirection.Ascending, filter).use { c ->
            while (c.moveToNext()) {
                val card = LoyaltyCard.fromCursor(context, c)
                out += Row(card.id, card.store, card.starStatus != 0, card.validFrom, card.expiry)
            }
        }
        return out
    }

    /** The groups, in Catima's order, each with how many cards it holds. */
    fun groups(context: Context): List<Pair<String, Int>> {
        val db = db(context)
        return DBHelper.getGroups(db).map { it._id to DBHelper.getGroupCardCount(db, it._id) }
    }

    fun cardGroups(context: Context, id: Int): List<String> = DBHelper.getLoyaltyCardGroups(db(context), id).map { it._id }

    /** Adds a group unless one of that name is already there. */
    fun addGroup(context: Context, name: String) {
        val db = db(context)
        val n = name.trim()
        if (n.isNotEmpty() && DBHelper.getGroup(db, n) == null) DBHelper.insertGroup(db, n)
    }

    fun renameGroup(context: Context, from: String, to: String): Boolean {
        val db = db(context)
        val n = to.trim()
        if (n.isEmpty() || n == from || DBHelper.getGroup(db, n) != null) return false
        return DBHelper.updateGroup(db, from, n)
    }

    /** Deletes the group only; its cards stay. */
    fun deleteGroup(context: Context, name: String): Boolean = DBHelper.deleteGroup(db(context), name)

    /** Marks a card as just used, for the "last used" order. */
    fun touch(context: Context, id: Int) {
        DBHelper.updateLoyaltyCardLastUsed(db(context), id)
    }

    fun archivedCount(context: Context): Int = DBHelper.getArchivedCardsCount(db(context))

    fun card(context: Context, id: Int): LoyaltyCard? = DBHelper.getLoyaltyCard(context, db(context), id)

    /**
     * Writes [draft] as a card and returns its id. A new card starts from [base] when there is
     * one (a pass file carries a balance and a colour this app does not edit but keeps); an
     * existing card keeps everything the draft does not hold.
     */
    fun save(context: Context, draft: Draft, base: LoyaltyCard? = null): Int {
        val db = db(context)
        val existing = if (draft.id >= 0) card(context, draft.id) else null
        val from = existing ?: base ?: LoyaltyCard()
        val format = draft.format?.let { CatimaBarcode.fromName(it) }
        val barcodeValue = draft.barcodeValue?.takeIf { it.isNotEmpty() && it != draft.number }
        val encoding = from.barcodeEncoding ?: StandardCharsets.ISO_8859_1
        val id = if (existing != null) {
            DBHelper.updateLoyaltyCard(
                db, existing.id, draft.name.trim(), draft.note.trim(),
                draft.validFrom?.let(::Date), draft.expiry?.let(::Date),
                from.balance ?: BigDecimal.ZERO, from.balanceType,
                draft.number, barcodeValue, format, encoding, from.headerColor,
                from.starStatus, from.lastUsed, from.archiveStatus,
            )
            existing.id
        } else {
            DBHelper.insertLoyaltyCard(
                db, draft.name.trim(), draft.note.trim(),
                draft.validFrom?.let(::Date), draft.expiry?.let(::Date),
                from.balance ?: BigDecimal.ZERO, from.balanceType,
                draft.number, barcodeValue, format, encoding, from.headerColor,
                0, null, 0,
            ).toInt()
        }
        if (draft.groups != null) {
            val chosen = draft.groups.mapNotNull { DBHelper.getGroup(db, it) }
            DBHelper.setLoyaltyCardGroups(db, id, chosen)
        }
        return id
    }

    fun delete(context: Context, id: Int): Boolean = DBHelper.deleteLoyaltyCard(db(context), context, id)

    fun setStarred(context: Context, id: Int, starred: Boolean): Boolean =
        DBHelper.updateLoyaltyCardStarStatus(db(context), id, if (starred) 1 else 0)

    fun setArchived(context: Context, id: Int, archived: Boolean): Boolean =
        DBHelper.updateLoyaltyCardArchiveStatus(db(context), id, if (archived) 1 else 0)
}
