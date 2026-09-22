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

        @Volatile
        private var instance: TrackingService? = null

        val isRunning: Boolean get() = instance != null

        /**
         * What is actually acquiring, not what was configured: "fused",
         * "manager", or "none" when stopped. On a device without Google Play
         * Services the two were never the same thing, and nothing said so.
         */
        val activeProvider: String get() = instance?.sourceName ?: "none"

        /**
         * Returns false when the system refused the start. Android 12 forbids
         * starting a foreground service from the background outside a short list
         * of exemptions, and a watchdog alarm is not on it — so the failure has
         * to be visible rather than swallowed.
         */
        fun request(context: Context, reason: String): Boolean {
            val app = context.applicationContext
            // Before the first log line: this can run from a receiver, long
            // before any module exists to have attached the bus.
            Bus.attach(app)
            val intent = Intent(app, TrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra("reason", reason)
            return try {
                ContextCompat.startForegroundService(app, intent)
                Bus.info("SERVICE", "demarrage demande ($reason)")
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

        /**
         * Last resort when the system will not let the service restart on its own.
         *
         * It used to post "on duty, your position is being shared" — the
         * service's own silent, permanent notification — at the exact moment the
         * start had just been refused. The rider read the opposite of the truth,
         * and nothing ever took that message away. This one says tracking is
         * stopped, is visible, and clears itself as soon as the service is back.
         */
        private fun postResumeNotification(context: Context) {
            if (!Alerts.hasNotificationPermission(context)) return
            runCatching {
                NotificationManagerCompat.from(context)
                    .notify(Alerts.RESUME_NOTIFICATION_ID, Alerts.buildResumeNotification(context))
            }
        }

        private fun clearResumeNotification(context: Context) {
            runCatching { NotificationManagerCompat.from(context).cancel(Alerts.RESUME_NOTIFICATION_ID) }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val uploader = Executors.newSingleThreadExecutor()
    private val uploadScheduled = AtomicBoolean(false)

    private var source: LocationSource? = null

    private var lastAccepted: Geo.Fix? = null
    // Stamped by this process at ACCEPT time, not read off the fix: location.time
    // is provider-reported and can freeze/replay/rewind under mock-location
    // playback without lat/lng ever stopping. This clock is what lets judge()
    // self-heal after a real 120s no matter what the provider's own clock does.
    private var lastAcceptedAtMs: Long = 0L
    private var lastSent: Geo.Fix? = null
    private var lastLocation: Location? = null
    /** When this process accepted lastLocation. Bounds the heartbeat's re-send. */
    private var lastLocationAtMs: Long = 0L
    private var lastMovementAt = 0L
    private var idle = false
    private var intervalOverrideSeconds = 0
    private var started = false

    /** "fused" or "manager", read back through TrackingService.activeProvider. */
    @Volatile
    private var sourceName: String = "none"

    private var requestedIdle: Boolean? = null
    private var requestedInterval = 0
    private var heartbeat: Runnable? = null

    /** Raised once per run: mock-location fraud has to be visible, not a log flood. */
    private var mockWarned = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        // This process may be the only one: restarted by the system, with no JS
        // anywhere. Without attaching here, everything that breaks is lost.
        Bus.attach(this)
        // Play Services is not a given: recent Huawei devices ship without it,
        // and the Fused client then never delivers anything — silently. Picking
        // the source here, once, is what turns that into a documented fallback
        // instead of a tracker that simply says nothing.
        val resolved = LocationSource.resolve(this)
        source = resolved
        sourceName = resolved.name
        if (resolved.name == LocationSource.FUSED) {
            Bus.info("PROVIDER", "Fournisseur : FusedLocationProvider.")
        } else {
            Bus.info("PROVIDER", "Google Play Services absent : repli sur LocationManager.")
        }
        // The cadence the host chose only lived in process memory: the first
        // kill put it back to the default, with nobody around to ask again.
        intervalOverrideSeconds = Prefs.of(this).getInt(Prefs.INTERVAL_OVERRIDE, 0)
        Alerts.recover(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // First thing, always: the system gives five seconds, and Android 14
        // crashes the process when the declared type and this one disagree.
        if (!promoteToForeground()) {
            postResumeNotification(this)
            stopSelf()
            return START_NOT_STICKY
        }

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
                    Prefs.of(this).edit().putInt(Prefs.INTERVAL_OVERRIDE, seconds).apply()
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
        // Sans cette ligne, un service tue ne laisse aucune trace : c'est l'ecart
        // entre le dernier demarrage et elle qui date la panne.
        Bus.info("SERVICE", "service detruit")
        stopUpdates()
        heartbeat?.let { main.removeCallbacks(it) }
        heartbeat = null
        uploader.shutdown()
        instance = null
        super.onDestroy()
    }

    // --- Lifecycle ---------------------------------------------------------

    /**
     * False when the system refused the promotion.
     *
     * The failure used to be swallowed: onStartCommand still returned
     * START_STICKY, and Android answered that notification-less service with a
     * ForegroundServiceDidNotStartInTime — a crash, not a clean stop. Better to
     * stop on our own terms and leave the trace behind.
     */
    private fun promoteToForeground(): Boolean = runCatching {
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
        // Running: the "tracking interrupted" message has no reason to stand.
        clearResumeNotification(this)
        true
    }.getOrElse {
        Bus.error("FOREGROUND", it.message ?: "startForeground refuse")
        false
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
        Bus.info("TRACKING", "suivi demarre (provider=$sourceName, cadence=${intervalSeconds()}s)")
        lastMovementAt = System.currentTimeMillis()
        requestUpdates(force = true)
        requestFirstFix()
        armHeartbeat()
        scheduleUpload()
    }

    private fun stopTracking() {
        Bus.info("TRACKING", "suivi arrete sur demande")
        Prefs.setDesiredRunning(this, false)
        Watchdog.disarm(this)
        stopUpdates()
        started = false
        sourceName = "none"
        // A requested stop is not an interruption: the resume message would
        // outlive stopForeground, which only removes the service's own
        // notification.
        clearResumeNotification(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Acquisition -------------------------------------------------------

    private fun intervalSeconds(): Int {
        val config = Config.get(this).tracking
        val active = if (intervalOverrideSeconds > 0) intervalOverrideSeconds else config.intervalSeconds
        return if (idle) maxOf(config.idleIntervalSeconds, active) else active
    }

    private fun buildRequest(): LocationSource.Request {
        val intervalMs = intervalSeconds() * 1000L
        return LocationSource.Request(
            intervalMs = intervalMs,
            highAccuracy = !idle,
            // At rest, batch fixes instead of waking the radio every cycle.
            maxUpdateDelayMs = if (idle) intervalMs * 3 else 0L
        )
    }

    private fun requestUpdates(force: Boolean) {
        if (!hasLocationPermission()) return
        val active = source ?: return
        val interval = intervalSeconds()
        if (!force && requestedIdle == idle && requestedInterval == interval) return
        requestedIdle = idle
        requestedInterval = interval
        active.start(buildRequest()) { handleFix(it) }
    }

    private fun stopUpdates() {
        source?.stop()
        requestedIdle = null
    }

    /**
     * With batching the first fix can land three minutes after the start, and the
     * user sees "on duty" while appearing nowhere. One high-accuracy shot at
     * startup fixes that for the cost of a single GPS wake.
     */
    private fun requestFirstFix() {
        if (!hasLocationPermission()) return
        source?.currentFix { handleFix(it) }
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
     *
     * Bounded, though. It re-sends the last known position under a fresh
     * timestamp, and with no age limit a phone that lost GPS forty minutes ago
     * in a car park kept publishing where it used to be as where it is. That is
     * not a stale point, it is a fabricated one — and a dispatcher routing on it
     * sends a rider to a place nobody is. Past the bound, silence is the honest
     * answer, and the server's own freshness check does the rest.
     */
    private fun emitHeartbeat() {
        val location = lastLocation ?: return
        val config = Config.get(this).tracking
        val periodMs = config.heartbeatSeconds * 1000L
        val lastSentAt = Prefs.of(this).getLong(Prefs.LAST_SENT_AT, 0L)
        if (System.currentTimeMillis() - lastSentAt < periodMs) return

        val now = System.currentTimeMillis()
        if (Geo.heartbeatIsStale(lastLocationAtMs, now, periodMs)) {
            val ageMs = now - lastLocationAtMs
            Bus.warn("STALE", "Derniere position vieille de ${ageMs / 60_000} min : heartbeat supprime.")
            return
        }

        enqueue(location, heartbeat = true, clientId = UUID.randomUUID().toString())
        scheduleUpload()
    }

    /**
     * Only over fixes the filter kept, and only when the step is trustworthy —
     * Geo drops anything inside the accuracy noise, which is what keeps a parked
     * scooter from accumulating kilometres overnight.
     *
     * Centimetres in a Long: SharedPreferences has no Double, and a Float loses
     * sub-metre precision past a thousand kilometres.
     */
    private fun accumulateOdometer(previous: Geo.Fix?, next: Geo.Fix, maxAccuracyMeters: Double) {
        val step = Geo.odometerStep(previous, next, maxAccuracyMeters)
        if (step <= 0.0) return
        val preferences = Prefs.of(this)
        val total = preferences.getLong(Prefs.ODOMETER_METERS, 0L) + (step * 100).toLong()
        Prefs.putLong(this, Prefs.ODOMETER_METERS, total)
    }

    /** Location.isMock from API 31, isFromMockProvider before it. */
    @Suppress("DEPRECATION")
    private fun isMock(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else location.isFromMockProvider

    private fun handleFix(location: Location) {
        val config = Config.get(this).tracking
        val fix = Geo.Fix(
            latitude = location.latitude,
            longitude = location.longitude,
            timeMs = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else -1.0,
            // The one clock a forward wall-clock change cannot fool: it counts
            // since boot and nothing on the device can wind it back.
            elapsedRealtimeNanos = location.elapsedRealtimeNanos,
            isMock = isMock(location)
        )

        // A rejected fix is not an error the user can act on; the rejection is
        // the feature. Nothing is logged either: a position is personal data.
        val realElapsedMs = System.currentTimeMillis() - lastAcceptedAtMs
        val verdict = Geo.judge(
            lastAccepted,
            fix,
            realElapsedMs,
            config.maxAccuracyMeters,
            config.maxSpeedMps,
            config.rejectMock
        )
        // The one rejection the host must hear about: a rider feeding the app a
        // simulated route is fraud, not a bad sky view.
        if (verdict == Geo.Verdict.REJECT_MOCK && !mockWarned) {
            mockWarned = true
            Bus.error("MOCK_LOCATION", "Position simulee detectee : les points sont rejetes.")
        }
        if (verdict != Geo.Verdict.ACCEPT) return

        val moved = lastAccepted?.let { Geo.distanceMeters(it, fix) } ?: Double.MAX_VALUE
        accumulateOdometer(lastAccepted, fix, config.maxAccuracyMeters)
        lastAccepted = fix
        lastAcceptedAtMs = System.currentTimeMillis()
        lastLocation = location
        lastLocationAtMs = System.currentTimeMillis()
        Prefs.putLong(this, Prefs.LAST_FIX_AT, fix.timeMs)

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
        putBoolean("isMock", fix.isMock)
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
            put("is_mock", isMock(location))
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
