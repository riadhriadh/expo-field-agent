package expo.modules.fieldagent

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Config as RoboConfig

/**
 * An AlarmManager that says no, which no stock shadow does: the quota exists on
 * real devices and the watchdog's whole job is to survive being refused.
 */
@Implements(AlarmManager::class)
class ShadowRefusingAlarmManager {

    @Implementation
    fun setAndAllowWhileIdle(type: Int, triggerAtMillis: Long, operation: PendingIntent?) {
        throw SecurityException("trop d'alarmes programmees")
    }

    @Implementation
    fun setExactAndAllowWhileIdle(type: Int, triggerAtMillis: Long, operation: PendingIntent?) {
        throw SecurityException("trop d'alarmes programmees")
    }
}

@RunWith(RobolectricTestRunner::class)
@RoboConfig(manifest = RoboConfig.NONE)
class WatchdogTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val context: Context get() = application
    private val seen = mutableListOf<Pair<String, Bundle>>()

    @Before
    fun freshProcess() {
        // Bus keeps its Context in a static shared by the whole sandbox; left
        // attached, an error raised here would reach another test's database.
        Bus::class.java.getDeclaredField("appContext").apply { isAccessible = true }.set(null, null)
        Prefs.of(context).edit().clear().commit()
        Config.invalidate()
        shadowOf(application).clearStartedServices()
        Bus.listener = { name, payload -> seen += name to payload }
    }

    @After
    fun unplug() {
        Bus.listener = null
        Config.invalidate()
    }

    private fun errorCodes() = seen.filter { it.first == "error" }.mapNotNull { it.second.getString("code") }

    private fun providers(gps: Boolean, network: Boolean) {
        val manager = context.getSystemService(LocationManager::class.java)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, gps)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, network)
    }

    private fun providersChanged() =
        ProvidersChangedReceiver().onReceive(context, Intent(ProvidersChangedReceiver.ACTION))

    private fun watchdogTick() =
        WatchdogReceiver().onReceive(context, Intent(WatchdogReceiver.ACTION_CHECK))

    @Test
    fun `an exact alarm reads as unsupported until the host opts in`() {
        // Nothing to grant and nothing to ask for: the permission is not in the
        // manifest, so a settings screen would be a dead end.
        assertEquals("unsupported", Watchdog.exactAlarmState(context))
    }

    @Test
    @RoboConfig(manifest = RoboConfig.NONE, shadows = [ShadowRefusingAlarmManager::class])
    fun `a refused alarm is written down instead of thrown`() {
        Watchdog.arm(context)

        assertEquals(listOf("WATCHDOG"), errorCodes())
    }

    @Test
    fun `a provider change carries what is left enabled`() {
        providers(gps = true, network = false)

        providersChanged()

        assertEquals("providerChange", seen.single().first)
        assertTrue(seen[0].second.getBoolean("enabled"))
        assertTrue(seen[0].second.getBoolean("gps"))
        assertFalse(seen[0].second.getBoolean("network"))
    }

    @Test
    fun `location coming back does not drag the service up when nobody is on duty`() {
        // Off duty, the tile being switched back on is news for the host and
        // nothing more: starting here would put a driver back on the map hours
        // after their shift ended.
        Prefs.setDesiredRunning(context, false)
        providers(gps = true, network = true)

        providersChanged()

        assertEquals("providerChange", seen.single().first)
        assertNull(shadowOf(application).nextStartedService)
    }

    @Test
    fun `location coming back while on duty re-asks for the service`() {
        Prefs.setDesiredRunning(context, true)
        providers(gps = true, network = true)

        providersChanged()

        assertNotNull(shadowOf(application).nextStartedService)
    }

    @Test
    fun `a service that never had a fix is not silent, it is young`() {
        // The first fix can be a minute out. Raising NO_FIX on the first tick
        // would cry wolf on every single start.
        Prefs.setDesiredRunning(context, true)

        watchdogTick()

        assertTrue(errorCodes().isEmpty())
    }

    @Test
    fun `silence inside the bound is not worth an error`() {
        Prefs.setDesiredRunning(context, true)
        Prefs.putLong(context, Prefs.LAST_FIX_AT, System.currentTimeMillis() - Watchdog.INTERVAL_MS + 60_000L)

        watchdogTick()

        assertTrue(errorCodes().isEmpty())
    }

    @Test
    fun `silence past the bound is dated and raised`() {
        Prefs.setDesiredRunning(context, true)
        Prefs.putLong(context, Prefs.LAST_FIX_AT, System.currentTimeMillis() - Watchdog.INTERVAL_MS - 60_000L)

        watchdogTick()

        assertEquals(listOf("NO_FIX"), errorCodes())
    }

    /**
     * Caught on a real device, not in a test: a receiver runs in a process where
     * the module was never created, so nothing had attached the bus and every
     * line the boot resume wrote went nowhere. The three events the persistent
     * log exists for — boot, app update, watchdog tick — were the three it could
     * not record. Each receiver must attach for itself.
     */
    @Test
    fun `a receiver records even though no module ever attached the bus`() {
        // freshProcess() has already detached the bus: this is a cold receiver.
        // There is no manifest blob under Robolectric, so the level has to be
        // raised explicitly or an INFO line is filtered before it is written.
        Config.setOverrides(context, org.json.JSONObject().put("logLevel", "debug"))
        Prefs.setDesiredRunning(context, true)
        Log.clear(context)

        BootReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        val codes = Log.read(context).map { it.code }
        assertTrue("le redemarrage apres mise a jour doit laisser une trace", "BOOT" in codes)
    }
}
