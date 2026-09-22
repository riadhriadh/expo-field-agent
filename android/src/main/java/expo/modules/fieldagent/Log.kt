package expo.modules.fieldagent

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The log that survives the shift.
 *
 * On disk rather than in logcat: the failures worth reading happen on a phone
 * in a van, hours before anyone plugs it into adb, and logcat is a ring buffer
 * the system recycles in minutes. A row here is the only evidence left when the
 * agent finally reports "it stopped working this morning".
 *
 * Positions never go in. The export leaves the device the moment someone taps
 * the button, and a position is personal data.
 */
object Log {

    /** Ordinal order is the severity ladder; OFF first because it is the floor, not a severity. */
    enum class Level { OFF, ERROR, WARN, INFO, DEBUG }

    data class Entry(val at: Long, val level: Level, val code: String, val message: String)

    /**
     * The decisions, with no android.* import, so the ladder, the retention
     * arithmetic and the line format are exercised on a plain JVM instead of
     * behind a Context nobody wants to stand up.
     */
    object Policy {
        private const val DAY_MS = 86_400_000L
        private const val STAMP = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

        /** A typo in app.json must not silently turn the log to DEBUG, so unknown falls back down. */
        fun parseLevel(raw: String?): Level {
            val wanted = raw?.trim()?.uppercase(Locale.ROOT) ?: return Level.ERROR
            return Level.values().firstOrNull { it.name == wanted } ?: Level.ERROR
        }

        /** OFF in either position accepts nothing: it is a switch, not a rung. */
        fun accepts(configured: Level, candidate: Level): Boolean =
            configured != Level.OFF && candidate != Level.OFF && candidate.ordinal <= configured.ordinal

        fun cutoffMs(nowMs: Long, maxDays: Int): Long = nowMs - maxDays * DAY_MS

        /**
         * One line, UTC, fixed locale. The export is read by whoever receives
         * the file and not by the phone that wrote it: a device left in Arabic
         * must not hand out Eastern-Arabic digits, and a device in Tunis must
         * not stamp +01 next to a server timeline in UTC.
         */
        fun format(entry: Entry): String {
            val stamp = SimpleDateFormat(STAMP, Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(entry.at))
            // A stack trace in the message would otherwise split into as many
            // entries as it has lines for whoever parses the file.
            return "$stamp ${entry.level.name} ${entry.code} ${entry.message}"
                .replace('\n', ' ')
                .replace('\r', ' ')
        }
    }

    /** Disk budget, not a preference: an unbounded log on a 16 GB phone is a support ticket. */
    const val MAX_ROWS = 10_000

    /**
     * Where the configured level comes from. A seam rather than a direct
     * `Config.get(context)` call so the log keeps working — at the ERROR
     * default — from a process that has not parsed the manifest yet, and so
     * this file needs no Context to be tested.
     */
    @Volatile
    internal var levelProvider: ((Context) -> Pair<Level, Int>)? = null

    fun write(context: Context, level: Level, code: String, message: String) {
        val (configured, maxDays) = settings(context)
        if (!Policy.accepts(configured, level)) return

        val now = System.currentTimeMillis()
        withDb(context) { db ->
            val id = db.insert(
                TABLE,
                null,
                ContentValues().apply {
                    put("at", now)
                    put("level", level.name)
                    put("code", code)
                    put("message", message)
                }
            )
            rotate(db, id, Policy.cutoffMs(now, maxDays))
        }
    }

    /** Newest first, which is the order a support screen reads them in. */
    fun read(context: Context, limit: Int = 500, sinceMs: Long = 0L): List<Entry> =
        withDb(context) { db ->
            // By id and not by at: two rows written inside the same millisecond
            // still have to come back in the order they happened.
            db.query(TABLE, COLUMNS, "at >= ?", arrayOf(sinceMs.toString()), null, null, "id DESC", limit.toString())
                .use { cursor ->
                    val entries = ArrayList<Entry>(cursor.count)
                    while (cursor.moveToNext()) entries.add(cursor.toEntry())
                    entries
                }
        } ?: emptyList()

    fun clear(context: Context) {
        withDb(context) { db -> db.delete(TABLE, null, null) }
    }

    /**
     * Oldest first: a file is read top to bottom, and the newest-first order of
     * [read] is a screen concern that would only confuse whoever opens this in
     * a text editor. Returns null when nothing could be written.
     */
    fun export(context: Context): File? = withDb(context) { db ->
        val target = File(File(context.cacheDir, "field-agent"), "log-export.txt")
        target.parentFile?.mkdirs()
        db.query(TABLE, COLUMNS, null, null, null, null, "id ASC").use { cursor ->
            target.bufferedWriter().use { writer ->
                while (cursor.moveToNext()) {
                    writer.append(Policy.format(cursor.toEntry()))
                    writer.append('\n')
                }
            }
        }
        target
    }

    fun size(context: Context): Int = withDb(context) { db ->
        db.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    } ?: 0

    private const val DB_NAME = "field-agent-log.db"
    private const val TABLE = "log"
    private val COLUMNS = arrayOf("at", "level", "code", "message")

    private fun settings(context: Context): Pair<Level, Int> =
        runCatching { levelProvider?.invoke(context) }.getOrNull() ?: (Level.ERROR to 7)

    private fun Cursor.toEntry(): Entry =
        Entry(getLong(0), Policy.parseLevel(getString(1)), getString(2), getString(3))

    /**
     * Both deletes hit an index because this runs on every single write: the
     * age cutoff rides the `at` index, the cap rides the primary key. Ids are
     * AUTOINCREMENT and never reused, so "drop everything at or below lastId -
     * MAX_ROWS" keeps the newest rows and at most MAX_ROWS of them without
     * counting anything first.
     */
    private fun rotate(db: SQLiteDatabase, lastId: Long, cutoffMs: Long) {
        db.delete(TABLE, "at < ?", arrayOf(cutoffMs.toString()))
        if (lastId > MAX_ROWS) db.delete(TABLE, "id <= ?", arrayOf((lastId - MAX_ROWS).toString()))
    }

    /**
     * Opened and closed around each call, and every failure swallowed: a disk
     * full, a corrupt file or storage still locked after a reboot must never
     * turn the logger into the crash it exists to record.
     *
     * ponytail: one file open per write. Cache the helper only if a host ever
     * runs at DEBUG with a sub-second interval.
     */
    private fun <T> withDb(context: Context, block: (SQLiteDatabase) -> T): T? {
        val helper = runCatching { Helper(context.applicationContext) }.getOrNull() ?: return null
        return try {
            runCatching { block(helper.writableDatabase) }.getOrNull()
        } finally {
            runCatching { helper.close() }
        }
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE (id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, " +
                    "level TEXT NOT NULL, code TEXT NOT NULL, message TEXT NOT NULL)"
            )
            db.execSQL("CREATE INDEX ${TABLE}_at ON $TABLE (at)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Diagnostics from before an upgrade describe a build that no longer
            // exists; migrating them would cost more than they are worth.
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            onCreate(db)
        }
    }
}
