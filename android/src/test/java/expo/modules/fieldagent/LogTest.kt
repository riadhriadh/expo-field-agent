package expo.modules.fieldagent

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RoboConfig

class LogPolicyTest {

    private fun entry(at: Long, message: String, code: String = "UPLINK") =
        Log.Entry(at, Log.Level.ERROR, code, message)

    @Test
    fun `every level name parses whatever the case`() {
        assertEquals(Log.Level.OFF, Log.Policy.parseLevel("off"))
        assertEquals(Log.Level.ERROR, Log.Policy.parseLevel("error"))
        assertEquals(Log.Level.WARN, Log.Policy.parseLevel("WARN"))
        assertEquals(Log.Level.INFO, Log.Policy.parseLevel("Info"))
        assertEquals(Log.Level.DEBUG, Log.Policy.parseLevel("dEbUg"))
    }

    @Test
    fun `an unknown or absent level falls back to error`() {
        assertEquals(Log.Level.ERROR, Log.Policy.parseLevel(null))
        assertEquals(Log.Level.ERROR, Log.Policy.parseLevel(""))
        assertEquals(Log.Level.ERROR, Log.Policy.parseLevel("verbose"))
    }

    @Test
    fun `off accepts nothing at all`() {
        Log.Level.values().forEach { assertFalse(Log.Policy.accepts(Log.Level.OFF, it)) }
    }

    @Test
    fun `off is never a candidate either`() {
        // Nothing calls write(OFF), but the ladder must not let it through if
        // something ever does: OFF is a switch, not a severity.
        Log.Level.values().forEach { assertFalse(Log.Policy.accepts(it, Log.Level.OFF)) }
    }

    @Test
    fun `the default level keeps errors and nothing else`() {
        assertTrue(Log.Policy.accepts(Log.Level.ERROR, Log.Level.ERROR))
        assertFalse(Log.Policy.accepts(Log.Level.ERROR, Log.Level.WARN))
        assertFalse(Log.Policy.accepts(Log.Level.ERROR, Log.Level.INFO))
        assertFalse(Log.Policy.accepts(Log.Level.ERROR, Log.Level.DEBUG))
    }

    @Test
    fun `debug accepts the whole ladder`() {
        assertTrue(Log.Policy.accepts(Log.Level.DEBUG, Log.Level.ERROR))
        assertTrue(Log.Policy.accepts(Log.Level.DEBUG, Log.Level.WARN))
        assertTrue(Log.Policy.accepts(Log.Level.DEBUG, Log.Level.INFO))
        assertTrue(Log.Policy.accepts(Log.Level.DEBUG, Log.Level.DEBUG))
    }

    @Test
    fun `the middle rungs accept everything more severe than themselves`() {
        assertTrue(Log.Policy.accepts(Log.Level.WARN, Log.Level.ERROR))
        assertTrue(Log.Policy.accepts(Log.Level.WARN, Log.Level.WARN))
        assertFalse(Log.Policy.accepts(Log.Level.WARN, Log.Level.INFO))

        assertTrue(Log.Policy.accepts(Log.Level.INFO, Log.Level.WARN))
        assertTrue(Log.Policy.accepts(Log.Level.INFO, Log.Level.INFO))
        assertFalse(Log.Policy.accepts(Log.Level.INFO, Log.Level.DEBUG))
    }

    @Test
    fun `the cutoff is the retention window behind now`() {
        assertEquals(1_000_000_000L - 7 * 86_400_000L, Log.Policy.cutoffMs(1_000_000_000L, 7))
        assertEquals(0L, Log.Policy.cutoffMs(30 * 86_400_000L, 30))
    }

    @Test
    fun `a single day of retention is honoured`() {
        assertEquals(86_400_000L, Log.Policy.cutoffMs(2 * 86_400_000L, 1))
    }

    @Test
    fun `a line is stamped in UTC down to the millisecond`() {
        assertEquals(
            "2020-09-13T12:26:40.123Z ERROR UPLINK envoi echoue",
            Log.Policy.format(entry(1_600_000_000_123L, "envoi echoue"))
        )
        assertEquals(
            "1970-01-01T00:00:00.000Z ERROR UPLINK epoque",
            Log.Policy.format(entry(0L, "epoque"))
        )
    }

    @Test
    fun `a message spanning several lines is flattened onto one`() {
        // A stack trace in the message would otherwise split into as many
        // entries as it has lines for whoever parses the exported file.
        val flattened = Log.Policy.format(entry(0L, "premier\nsecond\r\ntroisieme"))
        assertEquals("1970-01-01T00:00:00.000Z ERROR UPLINK premier second  troisieme", flattened)
        assertFalse(flattened.contains('\n'))
        assertFalse(flattened.contains('\r'))
    }
}

@RunWith(RobolectricTestRunner::class)
@RoboConfig(manifest = RoboConfig.NONE)
class LogTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun openTheTap() {
        Log.levelProvider = { Log.Level.DEBUG to 7 }
        // A fresh file rather than DELETE FROM: the AUTOINCREMENT counter is
        // what the hard cap trims against, and a clear does not reset it.
        databaseFile().let { file ->
            file.delete()
            listOf("-journal", "-wal", "-shm").forEach { File(file.path + it).delete() }
        }
    }

    @After
    fun releaseTheSeam() {
        Log.levelProvider = null
    }

    private fun databaseFile(): File = context.getDatabasePath("field-agent-log.db")

    /** Rows written behind Log's back, so a test can date them or make 10 000 of them cheaply. */
    private fun seed(count: Int, atMs: Long, code: String, message: (Int) -> String) {
        Log.size(context) // opens the helper once so the schema exists
        val db = SQLiteDatabase.openOrCreateDatabase(databaseFile(), null)
        db.beginTransaction()
        try {
            val insert = db.compileStatement("INSERT INTO log (at, level, code, message) VALUES (?, ?, ?, ?)")
            repeat(count) { index ->
                insert.bindLong(1, atMs)
                insert.bindString(2, "INFO")
                insert.bindString(3, code)
                insert.bindString(4, message(index))
                insert.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    @Test
    fun `a written line comes back out intact`() {
        Log.write(context, Log.Level.WARN, "STALE", "position trop vieille")

        val entries = Log.read(context)
        assertEquals(1, entries.size)
        assertEquals(Log.Level.WARN, entries[0].level)
        assertEquals("STALE", entries[0].code)
        assertEquals("position trop vieille", entries[0].message)
        assertTrue(entries[0].at > 0L)
    }

    @Test
    fun `a level below the configured one is not even stored`() {
        Log.levelProvider = { Log.Level.ERROR to 7 }

        Log.write(context, Log.Level.DEBUG, "FIX", "point accepte")
        assertEquals(0, Log.size(context))

        Log.write(context, Log.Level.ERROR, "UPLINK", "envoi echoue")
        assertEquals(1, Log.size(context))
    }

    @Test
    fun `read hands the newest back first`() {
        Log.write(context, Log.Level.INFO, "UN", "premier")
        Log.write(context, Log.Level.INFO, "DEUX", "second")
        Log.write(context, Log.Level.INFO, "TROIS", "troisieme")

        assertEquals(listOf("TROIS", "DEUX", "UN"), Log.read(context).map { it.code })
    }

    @Test
    fun `read honours the limit and the since bound`() {
        val before = System.currentTimeMillis()
        Log.write(context, Log.Level.INFO, "UN", "premier")
        Log.write(context, Log.Level.INFO, "DEUX", "second")
        Log.write(context, Log.Level.INFO, "TROIS", "troisieme")

        assertEquals(listOf("TROIS", "DEUX"), Log.read(context, limit = 2).map { it.code })
        assertEquals(3, Log.read(context, sinceMs = before).size)
        assertTrue(Log.read(context, sinceMs = System.currentTimeMillis() + 60_000L).isEmpty())
    }

    @Test
    fun `rotation drops what fell out of the retention window`() {
        seed(count = 1, atMs = System.currentTimeMillis() - 8 * 86_400_000L, code = "VIEUX") { "oublie" }
        assertEquals(1, Log.size(context))

        Log.write(context, Log.Level.ERROR, "NEUF", "toujours la")

        assertEquals(listOf("NEUF"), Log.read(context).map { it.code })
    }

    @Test
    fun `the hard cap trims the oldest rows and never the newest`() {
        val now = System.currentTimeMillis()
        seed(count = Log.MAX_ROWS, atMs = now, code = "SEED") { "seed-$it" }

        Log.write(context, Log.Level.ERROR, "NEUF", "le dernier")

        assertEquals(Log.MAX_ROWS, Log.size(context))
        val entries = Log.read(context, limit = Log.MAX_ROWS)
        assertEquals("NEUF", entries.first().code)
        assertEquals("seed-1", entries.last().message)
    }

    @Test
    fun `clear empties the log`() {
        Log.write(context, Log.Level.ERROR, "UPLINK", "envoi echoue")
        assertEquals(1, Log.size(context))

        Log.clear(context)

        assertEquals(0, Log.size(context))
        assertTrue(Log.read(context).isEmpty())
    }

    @Test
    fun `the export file reads oldest first`() {
        Log.write(context, Log.Level.ERROR, "UN", "premier")
        Log.write(context, Log.Level.WARN, "DEUX", "second")

        val exported = Log.export(context)

        assertNotNull(exported)
        val lines = exported!!.readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].endsWith("ERROR UN premier"))
        assertTrue(lines[1].endsWith("WARN DEUX second"))
    }

    @Test
    fun `a database that refuses to open never takes the service down`() {
        // Disk full, corrupt file, encrypted storage locked after a reboot: the
        // logger is the last thing allowed to crash the process it observes.
        val hostile = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this

            override fun getDatabasePath(name: String): File = File("/dev/null/nowhere/$name")

            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?
            ): SQLiteDatabase = throw SQLiteException("disque plein")

            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?
            ): SQLiteDatabase = throw SQLiteException("disque plein")
        }

        Log.write(hostile, Log.Level.ERROR, "UPLINK", "rien ne doit exploser")

        assertEquals(0, Log.size(hostile))
        assertTrue(Log.read(hostile).isEmpty())
        assertNull(Log.export(hostile))
        Log.clear(hostile)
    }
}
