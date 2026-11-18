package com.wanderwildwood.satsuire.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import protect.card_locker.DBHelper
import protect.card_locker.importexport.DataFormat
import protect.card_locker.importexport.ImportExportResultType
import protect.card_locker.importexport.MultiFormatExporter
import protect.card_locker.importexport.MultiFormatImporter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Cards in and out as files, in Catima's formats and through Catima's own importers.
 *
 * A file is offered to each importer in turn — Catima, then FidMe, then Voucher Vault — so
 * nobody has to know which app made it. Each importer runs inside a transaction that is rolled
 * back when it fails, so a wrong guess writes nothing.
 */
object Transfer {

    sealed interface Result {
        /** [added] is how many cards the file brought that were not here before. */
        data class Brought(val added: Int, val format: DataFormat) : Result

        /** The file is a locked zip, and the password was missing or wrong. */
        data object NeedsPassword : Result

        /** No importer could read it. */
        data object NotACardFile : Result

        /** The file could not be opened or read at all (it moved, or access was refused). */
        data object Unreadable : Result
    }

    private val ORDER = listOf(DataFormat.Catima, DataFormat.Fidme, DataFormat.VoucherVault)

    fun bringIn(context: Context, uri: Uri, password: CharArray? = null): Result {
        val db = Wallet.db(context)
        val before = DBHelper.getLoyaltyCardCount(db)
        var locked = false
        for (format in ORDER) {
            val input = try {
                context.contentResolver.openInputStream(uri)
            } catch (_: java.io.IOException) {
                null
            } catch (_: SecurityException) {
                null
            } ?: return Result.Unreadable
            val result = input.use { MultiFormatImporter.importData(context, db, it, format, password) }
            when (result.resultType()) {
                ImportExportResultType.Success -> return Result.Brought(DBHelper.getLoyaltyCardCount(db) - before, format)
                // A locked zip refuses every importer the same way until the password opens it,
                // and FidMe's own exports are locked, so the rest are still tried.
                ImportExportResultType.BadPassword -> locked = true
                else -> Unit
            }
        }
        return if (locked) Result.NeedsPassword else Result.NotACardFile
    }

    /**
     * Every card, in Catima's own zip, which Catima and this app both read back. With a
     * [password] the zip is locked the way Catima locks its own (AES inside the zip), so
     * Catima opens it with the same password; without one it is a plain zip.
     *
     * The zip is made in a temporary file first and copied over only once it is whole, so a
     * backup being written over is still there, untouched, when the export fails.
     */
    fun sendOut(context: Context, uri: Uri, password: CharArray? = null): Boolean {
        val key = password?.takeIf { it.isNotEmpty() }
        val temp = File.createTempFile("export", ".zip", context.cacheDir)
        try {
            val result = FileOutputStream(temp).use {
                MultiFormatExporter.exportData(context, Wallet.db(context), it, DataFormat.Catima, key)
            }
            if (result.resultType() != ImportExportResultType.Success) return false
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: return false
            out.use { o -> FileInputStream(temp).use { it.copyTo(o) } }
            return true
        } finally {
            temp.delete()
        }
    }

    /**
     * True when the document holds nothing: the file picker has just made it, or it was empty
     * anyway, so deleting it loses nothing. A provider that gives no size is asked for the first
     * byte instead; anything that cannot be read counts as not empty and is left alone.
     */
    fun isEmpty(context: Context, uri: Uri): Boolean {
        val size = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
        }.getOrNull()
        if (size != null) return size == 0L
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.read() == -1 }
        }.getOrNull() ?: false
    }
}
