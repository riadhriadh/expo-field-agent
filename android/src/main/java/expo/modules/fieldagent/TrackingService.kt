package expo.modules.fieldagent

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * The foreground service. Everything about it is about surviving.
 *
 * Why not expo-location's background task: that task runs on a JS engine the
 * system is free to tear down, cannot own a bounded on-disk outbox, cannot
 * declare foregroundServiceType=location for Android 14, and cannot bring itself
 * back after a memory kill. Acquisition itself stays the platform's job — this
 * service owns only the resilience around it.
 */
class TrackingService : Service() {

    companion object {
        const val ACTION_START = "expo.modules.fieldagent.START"
        const val ACTION_STOP = "expo.modules.fieldagent.STOP"
        const val ACTION_FLUSH = "expo.modules.fieldagent.FLUSH"
        const val ACTION_SET_INTERVAL = "expo.modules.fieldagent.SET_INTERVAL"
        const val EXTRA_INTERVAL = "interval"

        private const val RESUME_NOTIFICATION_ID = 0xFA03

        @Volatile
        private var instance: TrackingService? = null

        val isRunning: Boolean get() = instance != null

        /**
         * Returns false when the system refused the start. Android 12 forbids
         * starting a foreground service from the background outside a short list
         * of exemptions, and a watchdog alarm is not on it — so the failure has
         * to be visible rather than swallowed.
         */
        fun request(context: Context, reason: String): Boolean {
            val app = context.applicationContext
            val intent = Intent(app, TrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra("reason", reason)
            return try {
                ContextCompat.startForegroundService(app, intent)
                true
            } catch (error: Exception) {
                Bus.error("SERVICE_START", error.message ?: "demarrage refuse par le systeme")
                postResumeNotification(app)
                false
            }
        }

        fun send(context: Context, action: String, extras: Bundle? = null) {
            if (!isRunning) return
            val app = context.applicationContext
            val intent = Intent(app, TrackingService::class.java).setAction(action)
            extras?.let { intent.putExtras(it) }
            runCatching { ContextCompat.startForegroundService(app, intent) }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            Prefs.setDesiredRunning(app, false)
            Watchdog.disarm(app)
            runCatching {
                app.startService(Intent(app, TrackingService::class.java).setAction(ACTION_STOP))
            }.onFailure {
                // Nothing to stop, or the system refused a background start: the
                // persisted intention above is what matters for the next boot.
                app.stopService(Intent(app, TrackingService::class.java))
            }
        }

        /** Last resort when the system will not let the service restart on its own. */
        private fun postResumeNotification(context: Context) {
            if (!Alerts.hasNotificationPermission(context)) return
            runCatching {
                NotificationManagerCompat.from(context)
                    .notify(RESUME_NOTIFICATION_ID, Alerts.buildServiceNotification(context))
            }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val uploader = Executors.newSingleThreadExecutor()
    private val uploadScheduled = AtomicBoolean(false)

    private lateinit var fused: FusedLocationProviderClient

    private var lastAccepted: Geo.Fix? = null
    // Stamped by this process at ACCEPT time, not read off the fix: location.time
    // is provider-reported and can freeze/replay/rewind under mock-location
    // playback without lat/lng ever stopping. This clock is what lets judge()
    // self-heal after a real 120s no matter what the provider's own clock does.
    private var lastAcceptedAtMs: Long = 0L
    private var lastSent: Geo.Fix? = null
    private var lastLocation: Location? = null
    private var lastMovementAt = 0L
    private var idle = false
    private var intervalOverrideSeconds = 0
    private var started = false

    private var requestedIdle: Boolean? = null
    private var requestedInterval = 0
    private var heartbeat: Runnable? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { handleFix(it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        fused = LocationServices.getFusedLocationProviderClient(this)
        Alerts.recover(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // First thing, always: the system gives five seconds, and Android 14
        // crashes the process when the declared type and this one disagree.
        promoteToForeground()

        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }

            ACTION_FLUSH -> scheduleUpload()

            ACTION_SET_INTERVAL -> {
                val seconds = intent.getIntExtra(EXTRA_INTERVAL, 0)
                if (seconds > 0) {
                    intervalOverrideSeconds = seconds
                    // Hot: re-request instead of restarting, so the stream never gaps.
                    requestUpdates(force = true)
                }
            }

            else -> Unit
        }

        if (!started) startTracking()
        // Every wake-up is a chance to catch up: the Handler-driven heartbeat
        // below runs on uptimeMillis, which stops advancing in deep sleep, so a
        // motionless phone with the screen off would otherwise go quiet. This
        // call is self-guarding and does nothing when nothing is overdue.
        // ponytail: floor is the while-idle alarm interval (~15 min) — the only
        // way lower is an exact alarm, which Play refuses to non-alarm apps.
        emitHeartbeat()
        Watchdog.arm(this)
        Bubble.restoreIfWanted(this)

        // START_STICKY is what brings the service back after a memory kill.
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away is exactly the moment the user believes tracking
        // continues. Stopping here is the bug, not the feature.
        Watchdog.arm(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopUpdates()
        heartbeat?.let { main.removeCallbacks(it) }
        heartbeat = null
        uploader.shutdown()
        instance = null
        super.onDestroy()
    }

    // --- Lifecycle ---------------------------------------------------------

    private fun promoteToForeground() {
        runCatching {
            ServiceCompat.startForeground(
                this,
                Alerts.SERVICE_NOTIFICATION_ID,
                Alerts.buildServiceNotification(this),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            )
        }.onFailure { Bus.error("FOREGROUND", it.message ?: "startForeground refuse") }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Fine location can stay granted while background location gets revoked
     * out from under a running service — a manual Settings toggle, a restore,
     * Android's own auto-reset for an app left unused. Fused then simply stops
     * delivering fixes once the app is not in the foreground: no exception, no
     * failure callback, nothing `runCatching` around `requestLocationUpdates`
     * would ever see. This is the only place positioned to notice at all.
     */
    private fun hasBackgroundLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private var backgroundLocationWarned = false

    /** Checked at every (re)start and every heartbeat — cheap, and the only two
     *  moments the service does anything on its own regardless of who triggered it. */
    private fun checkBackgroundLocation() {
        if (hasBackgroundLocationPermission()) {
            backgroundLocationWarned = false
            return
        }
        if (backgroundLocationWarned) return
        backgroundLocationWarned = true
        Bus.error(
            "BACKGROUND_LOCATION_LOST",
            "ACCESS_BACKGROUND_LOCATION n'est plus accordee : le suivi ne captera plus rien ecran eteint."
        )
    }

    private fun startTracking() {
        if (!hasLocationPermission()) {
            Bus.error("PERMISSION", "ACCESS_FINE_LOCATION manquante : le suivi ne peut pas demarrer.")
            Prefs.setDesiredRunning(this, false)
            stopSelf()
            return
        }
        checkBackgroundLocation()
        started = true
        Prefs.setDesiredRunning(this, true)
        lastMovementAt = System.currentTimeMillis()
        requestUpdates(force = true)
        requestFirstFix()
        armHeartbeat()
        scheduleUpload()
    }

    private fun stopTracking() {
        Prefs.setDesiredRunning(this, false)
        Watchdog.disarm(this)
        stopUpdates()
        started = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Acquisition -------------------------------------------------------

    private fun intervalSeconds(): Int {
        val config = Config.get(this).tracking
        val active = if (intervalOverrideSeconds > 0) intervalOverrideSeconds else config.intervalSeconds
        return if (idle) maxOf(config.idleIntervalSeconds, active) else active
    }

    private fun buildRequest(): LocationRequest {
        val intervalMs = intervalSeconds() * 1000L
        return LocationRequest.Builder(
            if (idle) Priority.PRIORITY_BALANCED_POWER_ACCURACY else Priority.PRIORITY_HIGH_ACCURACY,
            intervalMs
        )
            .setMinUpdateIntervalMillis(intervalMs / 2)
            // Distance filtering happens in Geo, not on the chip: the chip filter
            // would also swallow the heartbeat, which exists precisely to fire
            // when nothing moved.
            .setMinUpdateDistanceMeters(0f)
            // At rest, batch fixes instead of waking the radio every cycle.
            .setMaxUpdateDelayMillis(if (idle) intervalMs * 3 else 0L)
            .setWaitForAccurateLocation(false)
            .build()
    }

    @Suppress("MissingPermission")
    private fun requestUpdates(force: Boolean) {
        if (!hasLocationPermission()) return
        val interval = intervalSeconds()
        if (!force && requestedIdle == idle && requestedInterval == interval) return
        requestedIdle = idle
        requestedInterval = interval

        runCatching {
            fused.removeLocationUpdates(callback)
            fused.requestLocationUpdates(buildRequest(), callback, Looper.getMainLooper())
        }.onFailure { Bus.error("LOCATION", it.message ?: "requestLocationUpdates a echoue") }
    }

    private fun stopUpdates() {
        runCatching { fused.removeLocationUpdates(callback) }
        requestedIdle = null
    }

    /**
     * With batching the first fix can land three minutes after the start, and the
     * user sees "on duty" while appearing nowhere. One high-accuracy shot at
     * startup fixes that for the cost of a single GPS wake.
     */
    @Suppress("MissingPermission")
    private fun requestFirstFix() {
        if (!hasLocationPermission()) return
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(30_000)
            .setMaxUpdateAgeMillis(60_000)
            .build()
        runCatching {
            fused.getCurrentLocation(request, null)
                .addOnSuccessListener { location -> location?.let { handleFix(it) } }
        }
    }

    private fun armHeartbeat() {
        heartbeat?.let { main.removeCallbacks(it) }
        val periodMs = Config.get(this).tracking.heartbeatSeconds * 1000L
        val runnable = object : Runnable {
            override fun run() {
                emitHeartbeat()
                checkBackgroundLocation()
                main.postDelayed(this, periodMs)
            }
        }
        heartbeat = runnable
        main.postDelayed(runnable, periodMs)
    }

    /**
     * "I am still here", stamped now, immune to the distance filter. Without it a
     * server that judges freshness declares a motionless agent missing.
     */
    private fun emitHeartbeat() {
        val location = lastLocation ?: return
        val periodMs = Config.get(this).tracking.heartbeatSeconds * 1000L
        val lastSentAt = Prefs.of(this).getLong(Prefs.LAST_SENT_AT, 0L)
        if (System.currentTimeMillis() - lastSentAt < periodMs) return
        enqueue(location, heartbeat = true, clientId = UUID.randomUUID().toString())
        scheduleUpload()
    }

    private fun handleFix(location: Location) {
        val fix = Geo.Fix(
            latitude = location.latitude,
            longitude = location.longitude,
            timeMs = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else -1.0
        )

        // A rejected fix is not an error the user can act on; the rejection is
        // the feature. Nothing is logged either: a position is personal data.
        val realElapsedMs = System.currentTimeMillis() - lastAcceptedAtMs
        if (Geo.judge(lastAccepted, fix, realElapsedMs) != Geo.Verdict.ACCEPT) return

        val moved = lastAccepted?.let { Geo.distanceMeters(it, fix) } ?: Double.MAX_VALUE
        lastAccepted = fix
        lastAcceptedAtMs = System.currentTimeMillis()
        lastLocation = location
        Prefs.putLong(this, Prefs.LAST_FIX_AT, fix.timeMs)

        val config = Config.get(this).tracking
        val clientId = UUID.randomUUID().toString()
        Bus.emit("position", positionBundle(location, fix, heartbeat = false, clientId = clientId))

        if (moved >= config.distanceFilterMeters) {
            lastMovementAt = System.currentTimeMillis()
            if (idle) {
                idle = false
                requestUpdates(force = false)
            }
        } else if (!idle && System.currentTimeMillis() - lastMovementAt > intervalSeconds() * 3_000L) {
            idle = true
            requestUpdates(force = false)
        }

        if (Geo.shouldSend(lastSent, fix, config.distanceFilterMeters, config.heartbeatSeconds * 1000L)) {
            lastSent = fix
            enqueue(location, heartbeat = false, clientId = clientId)
            scheduleUpload()
        }
    }

    private fun positionBundle(
        location: Location,
        fix: Geo.Fix,
        heartbeat: Boolean,
        clientId: String
    ): Bundle = Bundle().apply {
        putDouble("latitude", fix.latitude)
        putDouble("longitude", fix.longitude)
        putDouble("accuracy", fix.accuracyMeters)
        putDouble("altitude", if (location.hasAltitude()) location.altitude else 0.0)
        putDouble("speed", if (location.hasSpeed()) location.speed.toDouble() else -1.0)
        putDouble("heading", if (location.hasBearing()) location.bearing.toDouble() else -1.0)
        putDouble("timestamp", fix.timeMs.toDouble())
        putString("clientId", clientId)
        putBoolean("heartbeat", heartbeat)
    }

    // --- Outbox ------------------------------------------------------------

    private fun enqueue(location: Location, heartbeat: Boolean, clientId: String) {
        // The client id is what lets the server put a unique index on the row and
        // replay a batch without creating ghost positions.
        val payload = JSONObject().apply {
            put("client_id", clientId)
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("accuracy", if (location.hasAccuracy()) location.accuracy.toDouble() else JSONObject.NULL)
            put("speed", if (location.hasSpeed()) location.speed.toDouble() else JSONObject.NULL)
            put("heading", if (location.hasBearing()) location.bearing.toDouble() else JSONObject.NULL)
            put("altitude", if (location.hasAltitude()) location.altitude else JSONObject.NULL)
            put("recorded_at", if (heartbeat) System.currentTimeMillis() else location.time)
            put("heartbeat", heartbeat)
        }.toString()

        Outbox.add(this, Queue.Entry(clientId, payload))
    }

    private fun scheduleUpload() {
        if (!uploadScheduled.compareAndSet(false, true)) return
        runCatching {
            uploader.execute {
                try {
                    Outbox.flush(this)
                } finally {
                    uploadScheduled.set(false)
                }
            }
        }.onFailure { uploadScheduled.set(false) }
    }
}
