package expo.modules.fieldagent

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RoboConfig

@RunWith(RobolectricTestRunner::class)
@RoboConfig(manifest = RoboConfig.NONE)
class ConfigTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun blankHost() {
        Prefs.of(context).edit().clear().commit()
        manifest("{}")
    }

    @After
    fun forgetTheCache() {
        Prefs.of(context).edit().clear().commit()
        Config.invalidate()
    }

    /** The blob the config plugin bakes into the manifest at build time. */
    private fun manifest(json: String) {
        shadowOf(context.packageManager)
            .getInternalMutablePackageInfo(context.packageName)
            .applicationInfo!!
            .metaData = Bundle().apply { putString("expo.modules.fieldagent.CONFIG", json) }
        Config.invalidate()
    }

    /** What `start(options)` persists before the service reads it back. */
    private fun startOptions(vararg pairs: Pair<String, Any>) {
        Config.setOverrides(context, JSONObject().apply { pairs.forEach { put(it.first, it.second) } })
    }

    private fun tracking() = Config.get(context).tracking

    @Test
    fun `the point quality keys come off the manifest`() {
        manifest("""{"tracking":{"maxAccuracyMeters":40,"maxSpeedMps":25.5,"rejectMock":true,"exactAlarms":true}}""")

        assertEquals(40.0, tracking().maxAccuracyMeters, 0.0001)
        assertEquals(25.5, tracking().maxSpeedMps, 0.0001)
        assertTrue(tracking().rejectMock)
        assertTrue(tracking().exactAlarms)
    }

    @Test
    fun `a host that said nothing gets the shipped defaults`() {
        assertEquals(100.0, tracking().maxAccuracyMeters, 0.0001)
        assertEquals(60.0, tracking().maxSpeedMps, 0.0001)
        assertFalse(tracking().rejectMock)
        assertFalse(tracking().exactAlarms)
    }

    @Test
    fun `a ceiling of zero or less is raised to one metre and one metre per second`() {
        // Zero would reject every fix ever taken and freeze the whole fleet, so
        // a fat finger in app.json has to land on a usable value instead.
        manifest("""{"tracking":{"maxAccuracyMeters":0,"maxSpeedMps":-5}}""")

        assertEquals(1.0, tracking().maxAccuracyMeters, 0.0001)
        assertEquals(1.0, tracking().maxSpeedMps, 0.0001)
    }

    @Test
    fun `the log level parses whatever the case`() {
        manifest("""{"logLevel":"DeBuG","logMaxDays":30}""")

        assertEquals(Log.Level.DEBUG, Config.get(context).logLevel)
        assertEquals(30, Config.get(context).logMaxDays)
    }

    @Test
    fun `garbage falls back to errors only and to one day of retention`() {
        manifest("""{"logLevel":"loud","logMaxDays":0}""")

        assertEquals(Log.Level.ERROR, Config.get(context).logLevel)
        assertEquals(1, Config.get(context).logMaxDays)
    }

    @Test
    fun `the resume notification has strings of its own`() {
        assertEquals("Suivi interrompu", Config.get(context).notification.resumeTitle)
        assertEquals(
            "Android a refuse de relancer le suivi. Ouvre l'application pour reprendre.",
            Config.get(context).notification.resumeBody
        )

        manifest("""{"notification":{"resumeTitle":"Suivi a reprendre"}}""")

        assertEquals("Suivi a reprendre", Config.get(context).notification.resumeTitle)
        assertEquals(
            "Android a refuse de relancer le suivi. Ouvre l'application pour reprendre.",
            Config.get(context).notification.resumeBody
        )
    }

    @Test
    fun `a start option beats the manifest`() {
        manifest("""{"tracking":{"intervalSeconds":15,"maxSpeedMps":60}}""")

        startOptions("intervalSeconds" to 5, "maxSpeedMps" to 70.0)

        assertEquals(5, tracking().intervalSeconds)
        assertEquals(70.0, tracking().maxSpeedMps, 0.0001)
    }

    @Test
    fun `exact alarms are not something a start option can switch on`() {
        // SCHEDULE_EXACT_ALARM is declared at build time or not at all. Saying
        // yes at runtime would only promise an alarm the manifest never asked
        // for, and the watchdog would silently keep the inexact one.
        manifest("""{"tracking":{"exactAlarms":false}}""")

        startOptions("exactAlarms" to true)

        assertFalse(tracking().exactAlarms)
    }
}
