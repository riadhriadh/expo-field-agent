package expo.modules.fieldagent

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * The one seam between the service and whatever actually produces fixes.
 *
 * Fused was the only path until now, which meant a device without Google Play
 * Services — a Huawei sold after 2019, a de-googled ROM, a Play Services the
 * user disabled to save battery — got a service that started, stayed in the
 * notification shade and never emitted a single position. LocationManager is
 * less accurate and cannot batch, but it is on every Android ever shipped.
 */
sealed interface LocationSource {

    val name: String

    fun start(request: Request, onFix: (Location) -> Unit)

    fun stop()

    /** One-shot, best effort: never guaranteed to call back at all. */
    fun currentFix(onFix: (Location) -> Unit)

    data class Request(val intervalMs: Long, val highAccuracy: Boolean, val maxUpdateDelayMs: Long)

    companion object {
        const val FUSED = "fused"
        const val MANAGER = "manager"

        /** Pure so the arbitration can be tested without a device and without Robolectric. */
        fun choose(playServicesAvailable: Boolean): String = if (playServicesAvailable) FUSED else MANAGER

        fun resolve(context: Context): LocationSource {
            // The class itself can be missing from the APK on a stripped build,
            // so this is a NoClassDefFoundError as much as a return code.
            val available = runCatching {
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
                    ConnectionResult.SUCCESS
            }.getOrDefault(false)

            return if (choose(available) == FUSED) FusedSource(context) else ManagerSource(context)
        }
    }
}

/** Fused: batching, arbitration between chip and network, and the only source with maxUpdateDelay. */
class FusedSource(context: Context) : LocationSource {

    override val name = LocationSource.FUSED

    private val fused = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @Volatile
    private var sink: ((Location) -> Unit)? = null

    // A single callback instance for the life of the source: removeLocationUpdates
    // matches on identity, so a new object per request would leak the old
    // registration and double every fix after the first interval change.
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val current = sink ?: return
            result.locations.forEach(current)
        }
    }

    @Suppress("MissingPermission")
    override fun start(request: LocationSource.Request, onFix: (Location) -> Unit) {
        sink = onFix
        runCatching {
            fused.removeLocationUpdates(callback)
            fused.requestLocationUpdates(build(request), callback, Looper.getMainLooper())
                .addOnFailureListener { report(it, "requestLocationUpdates a echoue") }
        }.onFailure { report(it, "requestLocationUpdates a echoue") }
    }

    override fun stop() {
        sink = null
        runCatching { fused.removeLocationUpdates(callback) }
    }

    @Suppress("MissingPermission")
    override fun currentFix(onFix: (Location) -> Unit) {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(30_000)
            .setMaxUpdateAgeMillis(60_000)
            .build()
        runCatching {
            fused.getCurrentLocation(request, null)
                .addOnSuccessListener { location -> location?.let(onFix) }
                .addOnFailureListener { report(it, "getCurrentLocation a echoue") }
        }.onFailure { report(it, "getCurrentLocation a echoue") }
    }

    private fun build(request: LocationSource.Request): LocationRequest = LocationRequest.Builder(
        if (request.highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        request.intervalMs
    )
        .setMinUpdateIntervalMillis(request.intervalMs / 2)
        // Distance filtering happens in Geo, not on the chip: the chip filter
        // would also swallow the heartbeat, which exists precisely to fire
        // when nothing moved.
        .setMinUpdateDistanceMeters(0f)
        .setMaxUpdateDelayMillis(request.maxUpdateDelayMs)
        .setWaitForAccurateLocation(false)
        .build()

    /**
     * A Task failing is not an exception on this thread: the old `runCatching`
     * around the call only ever caught the synchronous dispatch, so a Play
     * Services that was disabled, out of date or out of quota returned a Task
     * that failed in silence and the service waited forever for fixes that were
     * never coming. This is the listener that had to exist.
     */
    private fun report(error: Throwable, fallback: String) {
        Bus.error("LOCATION", error.message ?: fallback)
    }
}

/** The platform provider, straight. No batching, no arbitration — but no Google either. */
class ManagerSource(context: Context) : LocationSource {

    override val name = LocationSource.MANAGER

    private val manager: LocationManager? =
        context.applicationContext.getSystemService(LocationManager::class.java)

    private var listener: LocationListener? = null

    @Suppress("MissingPermission")
    override fun start(request: LocationSource.Request, onFix: (Location) -> Unit) {
        stop()
        val manager = manager ?: return
        val current = object : LocationListener {
            override fun onLocationChanged(location: Location) = onFix(location)
        }
        listener = current

        // Both providers at once, and Geo sorts out the duplicates: network
        // fixes arrive indoors and in tunnels where GPS gives nothing at all,
        // and the reverse in the open. Each registration is guarded on its own
        // because a provider missing from the ROM makes the call throw, and the
        // throw would take the other provider down with it.
        PROVIDERS.forEach { provider ->
            runCatching {
                if (!manager.isProviderEnabled(provider)) return@forEach
                manager.requestLocationUpdates(
                    provider,
                    request.intervalMs,
                    0f,
                    current,
                    Looper.getMainLooper()
                )
            }.onFailure { Bus.error("LOCATION", it.message ?: "requestLocationUpdates a echoue") }
        }
    }

    override fun stop() {
        val current = listener ?: return
        listener = null
        runCatching { manager?.removeUpdates(current) }
    }

    @Suppress("MissingPermission")
    override fun currentFix(onFix: (Location) -> Unit) {
        val manager = manager ?: return
        // No getCurrentLocation below API 30, so the cache is all there is. The
        // fresher of the two, not the more accurate: a stale GPS point is worse
        // than a coarse one taken now.
        PROVIDERS
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let(onFix)
    }

    private companion object {
        val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    }
}
