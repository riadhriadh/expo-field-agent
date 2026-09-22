package expo.modules.fieldagent

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RoboConfig

@RunWith(RobolectricTestRunner::class)
@RoboConfig(manifest = RoboConfig.NONE)
class BusTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val seen = mutableListOf<Pair<String, Bundle>>()

    @Before
    fun freshProcess() {
        detach()
        Config.invalidate()
        Prefs.of(context).edit().clear().commit()
        // A fresh file rather than DELETE FROM, so a row left by another test
        // cannot pass for one this test wrote.
        context.getDatabasePath("field-agent-log.db").let { file ->
            file.delete()
            listOf("-journal", "-wal", "-shm").forEach { File(file.path + it).delete() }
        }
        Bus.listener = { name, payload -> seen += name to payload }
    }

    @After
    fun unplug() {
        Bus.listener = null
        Log.levelProvider = null
        detach()
        Config.invalidate()
    }

    /**
     * Bus holds its Context in a static and offers no way back, while the whole
     * class shares one sandbox: without this, whichever test attached first
     * would decide whether the degraded path is still reachable below.
     */
    private fun detach() {
        Bus::class.java.getDeclaredField("appContext").apply { isAccessible = true }.set(null, null)
    }

    private fun logEverything() {
        Config.setOverrides(context, JSONObject().put("logLevel", "debug"))
    }

    private fun lastError(): String? = Prefs.of(context).getString(Prefs.LAST_ERROR, null)

    @Test
    fun `an error is written down where getState will find it`() {
        Bus.attach(context)
        val before = System.currentTimeMillis()

        Bus.error("UPLINK", "envoi echoue")

        assertEquals("UPLINK: envoi echoue", lastError())
        assertTrue(Prefs.of(context).getLong(Prefs.LAST_ERROR_AT, 0L) >= before)
        assertEquals(listOf("UPLINK"), Log.read(context).map { it.code })
    }

    @Test
    fun `an error reaches the listener as well as the disk`() {
        Bus.attach(context)

        Bus.error("UPLINK", "envoi echoue")

        assertEquals(listOf("error"), seen.map { it.first })
        assertEquals("UPLINK", seen[0].second.getString("code"))
        assertEquals("envoi echoue", seen[0].second.getString("message"))
    }

    @Test
    fun `a warning is logged without erasing the last error`() {
        // The slot getState() reads holds one string. A warning overwriting it
        // would wipe the outage the host is still trying to date.
        logEverything()
        Bus.attach(context)
        Bus.error("UPLINK", "envoi echoue")
        val raisedAt = Prefs.of(context).getLong(Prefs.LAST_ERROR_AT, 0L)
        seen.clear()

        Bus.warn("STALE", "position trop vieille")
        Bus.info("PROVIDER", "repli sur LocationManager")

        assertEquals("UPLINK: envoi echoue", lastError())
        assertEquals(raisedAt, Prefs.of(context).getLong(Prefs.LAST_ERROR_AT, 0L))
        assertTrue(seen.isEmpty())
        assertEquals(listOf("PROVIDER", "STALE", "UPLINK"), Log.read(context).map { it.code })
    }

    @Test
    fun `an error raised before attach still reaches the listener`() {
        // The service process between onCreate and the first attach, or a crash
        // during init: dropping the event there would hide the fault twice.
        Bus.error("SERVICE_START", "demarrage refuse par le systeme")

        assertEquals(listOf("error"), seen.map { it.first })
        assertEquals("SERVICE_START", seen[0].second.getString("code"))
        assertNull(lastError())
    }

    @Test
    fun `attach points the log threshold at the configuration`() {
        logEverything()

        Bus.attach(context)
        Bus.info("PROVIDER", "Google Play Services absent")

        assertEquals(listOf("PROVIDER"), Log.read(context).map { it.code })
    }

    @Test
    fun `the default threshold keeps everything below an error out`() {
        Bus.attach(context)

        Bus.info("PROVIDER", "Google Play Services absent")
        Bus.warn("STALE", "position trop vieille")

        assertTrue(Log.read(context).isEmpty())
    }
}
