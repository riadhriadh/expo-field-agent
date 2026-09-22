package expo.modules.fieldagent

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RoboConfig

class LocationSourceChoiceTest {

    @Test
    fun `play services present means the fused client`() {
        assertEquals("fused", LocationSource.choose(true))
    }

    @Test
    fun `play services absent falls back on the platform manager`() {
        assertEquals("manager", LocationSource.choose(false))
    }
}

@RunWith(RobolectricTestRunner::class)
@RoboConfig(manifest = RoboConfig.NONE)
class ManagerSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: LocationManager = context.getSystemService(LocationManager::class.java)
    private val shadow get() = shadowOf(manager)

    private lateinit var source: ManagerSource

    @Before
    fun bothProvidersOn() {
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        source = ManagerSource(context)
    }

    private fun request() =
        LocationSource.Request(intervalMs = 10_000L, highAccuracy = true, maxUpdateDelayMs = 0L)

    private fun fix(provider: String, latitude: Double, atMs: Long) = Location(provider).apply {
        this.latitude = latitude
        this.longitude = 10.0
        this.time = atMs
    }

    @Test
    fun `a gps fix reaches the callback`() {
        val seen = mutableListOf<Location>()
        source.start(request()) { seen += it }

        shadow.simulateLocation(fix(LocationManager.GPS_PROVIDER, 48.85, 1_000L))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, seen.size)
        assertEquals(48.85, seen[0].latitude, 0.0001)
    }

    @Test
    fun `a disabled provider is skipped instead of throwing`() {
        // Le cas reel : GPS coupe dans les reglages rapides, reseau encore la.
        // Un requestLocationUpdates sur un provider absent leve, et la levee
        // emporterait l'autre provider avec elle.
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)

        source.start(request()) { }

        assertEquals(1, shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)
        assertTrue(shadow.getLocationUpdateListeners(LocationManager.NETWORK_PROVIDER).isEmpty())
    }

    @Test
    fun `stop unregisters the listener`() {
        source.start(request()) { }
        assertTrue(shadow.getLocationUpdateListeners().isNotEmpty())

        source.stop()

        assertTrue(shadow.getLocationUpdateListeners().isEmpty())
    }

    @Test
    fun `the one-shot keeps the fresher of the two last known fixes`() {
        shadow.setLastKnownLocation(
            LocationManager.GPS_PROVIDER,
            fix(LocationManager.GPS_PROVIDER, 48.0, 1_000L)
        )
        shadow.setLastKnownLocation(
            LocationManager.NETWORK_PROVIDER,
            fix(LocationManager.NETWORK_PROVIDER, 49.0, 5_000L)
        )

        val seen = mutableListOf<Location>()
        source.currentFix { seen += it }

        assertEquals(1, seen.size)
        assertEquals(49.0, seen[0].latitude, 0.0001)
    }
}
