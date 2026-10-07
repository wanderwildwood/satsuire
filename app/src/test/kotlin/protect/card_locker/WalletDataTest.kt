package protect.card_locker

import android.app.Activity
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.wanderwildwood.satsuire.data.Draft
import com.wanderwildwood.satsuire.data.Incoming
import com.wanderwildwood.satsuire.data.Transfer
import com.wanderwildwood.satsuire.data.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import protect.card_locker.importexport.DataFormat
import java.io.File

/**
 * The app's own layer over Catima's: guessing which app an export came from, reading shared
 * text, and saving and listing cards.
 */
@RunWith(RobolectricTestRunner::class)
class WalletDataTest {
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        TestHelpers.getEmptyDb(activity)
    }

    private fun resource(name: String): Uri {
        val file = File(activity.cacheDir, name)
        javaClass.getResourceAsStream(name)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(file)
    }

    @Test
    fun fidmeExportIsRecognised() {
        val result = Transfer.bringIn(activity, resource("fidme.zip"))
        assertEquals(Transfer.Result.Brought(3, DataFormat.Fidme), result)
    }

    /** Catima's and FidMe's importers are tried first and must leave nothing behind. */
    @Test
    fun voucherVaultExportIsRecognised() {
        val result = Transfer.bringIn(activity, resource("vouchervault.json"))
        assertEquals(Transfer.Result.Brought(2, DataFormat.VoucherVault), result)
        assertEquals(2, DBHelper.getLoyaltyCardCount(Wallet.db(activity)))
    }

    @Test
    fun ownExportComesBack() {
        Wallet.save(activity, Draft(name = "Ada Whitlock library", number = "LIB-000123456", format = "CODE_128"))
        Wallet.save(activity, Draft(name = "Ferry", number = "FERRY-0042", format = "AZTEC", note = "Deck B"))
        val file = File(activity.cacheDir, "out.zip")
        assertTrue(Transfer.sendOut(activity, Uri.fromFile(file)))
        TestHelpers.getEmptyDb(activity)
        assertEquals(Transfer.Result.Brought(2, DataFormat.Catima), Transfer.bringIn(activity, Uri.fromFile(file)))
        val rows = Wallet.rows(activity, archived = false)
        assertEquals(listOf("Ada Whitlock library", "Ferry"), rows.map { it.name })
    }

    /**
     * Catima's importer takes any text as an old-style export and finds no cards in it, so a
     * stray text file reads as "nothing came in" rather than "not a card file", which is why
     * the app says it the first way. A file no importer opens at all is refused.
     */
    /** A locked export refuses without its password, says so, and opens with it. */
    @Test
    fun lockedExportComesBackWithItsPassword() {
        Wallet.save(activity, Draft(name = "Ferry", number = "FERRY-0042", format = "AZTEC"))
        val file = File(activity.cacheDir, "locked.zip")
        assertTrue(Transfer.sendOut(activity, Uri.fromFile(file), "correct horse".toCharArray()))
        TestHelpers.getEmptyDb(activity)
        assertEquals(Transfer.Result.NeedsPassword, Transfer.bringIn(activity, Uri.fromFile(file)))
        assertEquals(Transfer.Result.NeedsPassword, Transfer.bringIn(activity, Uri.fromFile(file), "wrong".toCharArray()))
        assertEquals(0, DBHelper.getLoyaltyCardCount(Wallet.db(activity)))
        assertEquals(Transfer.Result.Brought(1, DataFormat.Catima), Transfer.bringIn(activity, Uri.fromFile(file), "correct horse".toCharArray()))
        assertEquals("Ferry", Wallet.rows(activity, archived = false).single().name)
        // An empty password is no password: a plain zip, as before.
        val plain = File(activity.cacheDir, "plain.zip")
        assertTrue(Transfer.sendOut(activity, Uri.fromFile(plain), CharArray(0)))
        TestHelpers.getEmptyDb(activity)
        assertEquals(Transfer.Result.Brought(1, DataFormat.Catima), Transfer.bringIn(activity, Uri.fromFile(plain)))
    }

    @Test
    fun somethingElseBringsNothing() {
        val text = File(activity.cacheDir, "notes.txt").apply { writeText("Buy milk\n") }
        assertEquals(Transfer.Result.Brought(0, DataFormat.Catima), Transfer.bringIn(activity, Uri.fromFile(text)))
        assertEquals(0, DBHelper.getLoyaltyCardCount(Wallet.db(activity)))

        val missing = Uri.fromFile(File(activity.cacheDir, "not-there.zip"))
        assertEquals(Transfer.Result.NotACardFile, Transfer.bringIn(activity, missing))
    }

    @Test
    fun sharedNumberBecomesACardToFinish() {
        val found = Incoming.text(activity, "  5550101001 \n") as Incoming.Found
        val r = found.results.single()
        assertEquals(ParseResultType.BARCODE_ONLY, r.parseResultType)
        assertEquals("5550101001", r.loyaltyCard.cardId)
    }

    @Test
    fun catimaShareLinkBecomesTheCard() {
        val card = LoyaltyCard().apply {
            setStore("Tomas Reyes gym")
            setCardId("GYM-7781")
            setBarcodeType(CatimaBarcode.fromBarcode(BarcodeFormat.QR_CODE))
        }
        val link = ImportURIHelper(activity).toUri(card)
        val found = Incoming.text(activity, "I want to share a card with you\n$link") as Incoming.Found
        val r = found.results.single()
        assertEquals(ParseResultType.FULL, r.parseResultType)
        assertEquals("Tomas Reyes gym", r.loyaltyCard.store)
        assertEquals("GYM-7781", r.loyaltyCard.cardId)
        assertEquals(BarcodeFormat.QR_CODE, r.loyaltyCard.barcodeType!!.format())
    }

    @Test
    fun starredFirstThenByName() {
        val b = Wallet.save(activity, Draft(name = "beta", number = "2"))
        Wallet.save(activity, Draft(name = "Alpha", number = "1"))
        val z = Wallet.save(activity, Draft(name = "zulu", number = "3"))
        Wallet.setStarred(activity, z, true)
        Wallet.setArchived(activity, b, true)
        assertEquals(listOf("zulu", "Alpha"), Wallet.rows(activity, archived = false).map { it.name })
        assertEquals(listOf("beta"), Wallet.rows(activity, archived = true).map { it.name })
    }

    /** Changing a card keeps what the editor does not show: its star and Catima's colour. */
    @Test
    fun editingKeepsWhatIsNotEdited() {
        val id = Wallet.save(activity, Draft(name = "Library", number = "1234"))
        Wallet.setStarred(activity, id, true)
        val before = Wallet.card(activity, id)!!
        Wallet.save(activity, Draft.of(before).copy(name = "Town library", note = "Back door"))
        val after = Wallet.card(activity, id)!!
        assertEquals("Town library", after.store)
        assertEquals("Back door", after.note)
        assertEquals(1, after.starStatus)
        assertEquals(before.headerColor, after.headerColor)
        assertEquals(1, DBHelper.getLoyaltyCardCount(Wallet.db(activity)))
    }

    @Test
    fun groupsFilterAndSurviveEditing() {
        Wallet.addGroup(activity, "Travel")
        Wallet.addGroup(activity, "Shops")
        Wallet.addGroup(activity, "Travel ")
        val ferry = Wallet.save(activity, Draft(name = "Ferry", number = "F1", groups = listOf("Travel")))
        Wallet.save(activity, Draft(name = "Co-op", number = "C1", groups = listOf("Shops")))
        Wallet.save(activity, Draft(name = "Library", number = "L1"))
        assertEquals(listOf("Shops" to 1, "Travel" to 1), Wallet.groups(activity).sortedBy { it.first })
        assertEquals(listOf("Ferry"), Wallet.rows(activity, false, "Travel").map { it.name })
        // An edit that never read the groups leaves them alone.
        Wallet.save(activity, Draft.of(Wallet.card(activity, ferry)!!).copy(note = "Deck B"))
        assertEquals(listOf("Travel"), Wallet.cardGroups(activity, ferry))
        assertTrue(Wallet.renameGroup(activity, "Travel", "Trips"))
        assertEquals(listOf("Ferry"), Wallet.rows(activity, false, "Trips").map { it.name })
        Wallet.deleteGroup(activity, "Trips")
        assertEquals(3, Wallet.rows(activity, false).size)
    }

    @Test
    fun orders() {
        val a = Wallet.save(activity, Draft(name = "A", number = "1", expiry = 3_000_000_000_000))
        val b = Wallet.save(activity, Draft(name = "B", number = "2", expiry = 2_000_000_000_000))
        Wallet.save(activity, Draft(name = "C", number = "3"))
        assertEquals(listOf("B", "A", "C"), Wallet.rows(activity, false, order = Wallet.Order.EXPIRY).map { it.name })
        assertEquals(listOf("C", "B", "A"), Wallet.rows(activity, false, order = Wallet.Order.LAST_ADDED).map { it.name })
        assertTrue(a > 0 && b > 0)
    }
}
