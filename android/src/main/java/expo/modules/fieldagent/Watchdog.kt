package expo.modules.fieldagent

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock

/**
 * The only thing that catches a process killed for memory.
 *
 * setAndAllowWhileIdle fires even in Doze, and needs no permission — unlike the
 * exact-alarm family, which Google Play now restricts to alarm and calendar
 * apps. Roughly every fifteen minutes is also the floor the system enforces on
 * while-idle alarms, so asking for less would only be wishful.
 */
object Watchdog {

    private const val REQUEST_CODE = 0xFA10
    val INTERVAL_MS: Long = 15 * 60 * 1000L

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WatchdogReceiver::class.java).setAction(WatchdogReceiver.ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * Exact alarms are opt-in, and deliberately half of what the platform offers.
     *
     * USE_EXACT_ALARM is granted at install time and is never declared here:
     * Google Play reserves it for clocks and calendars, and shipping it in a
     * delivery app gets the release pulled — the same trap as
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, refused for the same reason (see
     * Power.kt). SCHEDULE_EXACT_ALARM is the one a user can grant, so it is the
     * only one the plugin adds, and only when the host asks for it.
     *
     * It matters because an inexact while-idle alarm is not on Android 12's list
     * of exemptions for starting a foreground service from the background: an
     * exact one is. Without it, the watchdog can only try, and be refused.
     */
    fun canScheduleExact(context: Context): Boolean {
        if (!Config.get(context).tracking.exactAlarms) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching { alarms.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /** granted / denied / unsupported, for getPermissions(). */
    fun exactAlarmState(context: Context): String = when {
        !Config.get(context).tracking.exactAlarms -> "unsupported"
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "granted"
        canScheduleExact(context) -> "granted"
        else -> "denied"
    }

    fun arm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = SystemClock.elapsedRealtime() + INTERVAL_MS
        val pending = intent(context)

        // The permission can be revoked between the check and the call, so the
        // inexact path below is a fallback and not just an else branch.
        if (canScheduleExact(context)) {
            val scheduled = runCatching {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            }.isSuccess
            if (scheduled) return
        }

        runCatching {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        }.onFailure { Bus.error("WATCHDOG", it.message ?: "alarme refusee") }
    }

    fun disarm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarms.cancel(intent(context)) }
    }
}

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CHECK) return
        Bus.attach(context)
        // The persisted intention decides, not whatever this fresh process
        // happens to remember — which is nothing.
        if (!Prefs.isDesiredRunning(context)) return

        Bus.info("WATCHDOG", "reveil du watchdog")
        reportSilence(context)
        TrackingService.request(context, "watchdog")
        Watchdog.arm(context)
    }

    /**
     * A service that is running and producing nothing looks exactly like a
     * healthy one from the outside — and that is the failure the field actually
     * reports. The watchdog is the only thing that wakes up regardless, so it is
     * the only place positioned to notice the silence and date it.
     */
    private fun reportSilence(context: Context) {
        val lastFix = Prefs.of(context).getLong(Prefs.LAST_FIX_AT, 0L)
        if (lastFix <= 0L) return
        val silentForMs = System.currentTimeMillis() - lastFix
        val allowedMs = Config.get(context).tracking.intervalSeconds * 2_000L
        if (silentForMs <= maxOf(allowedMs, Watchdog.INTERVAL_MS)) return
        Bus.error(
            "NO_FIX",
            "Aucune position depuis ${silentForMs / 60_000} min alors que le suivi est actif."
        )
    }

    companion object {
        const val ACTION_CHECK = "expo.modules.fieldagent.WATCHDOG_CHECK"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in HANDLED) return
        Bus.attach(context)
        if (!Prefs.isDesiredRunning(context)) return

        // Boot receivers are exempt from the Android 12 background start
        // restriction, which is exactly why the resume happens here.
        Bus.info("BOOT", "redemarrage apres $action")
        TrackingService.request(context, "boot")
        Watchdog.arm(context)
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}

class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_DISMISS) return
        Alerts.dismiss(context)
    }

    companion object {
        const val ACTION_DISMISS = "expo.modules.fieldagent.ALERT_DISMISS"
    }
}

/**
 * Location switched off, or airplane mode.
 *
 * Nothing else notices: Fused simply stops delivering, with no callback and no
 * exception, so a driver who pulled down the shade and tapped the location tile
 * disappears from the map with the service still green. The broadcast is the
 * only signal, and it is also what tells us when to come back.
 */
class ProvidersChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        Bus.attach(context)

        val manager = context.getSystemService(LocationManager::class.java)
        val gps = runCatching {
            manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        }.getOrDefault(false)
        val network = runCatching {
            manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        }.getOrDefault(false)
        val enabled = gps || network

        Bus.emit("providerChange", Bundle().apply {
            putBoolean("enabled", enabled)
            putBoolean("gps", gps)
            putBoolean("network", network)
        })

        if (!Prefs.isDesiredRunning(context)) return
        if (!enabled) {
            Bus.error("LOCATION_OFF", "La localisation est desactivee : plus aucune position ne sera captee.")
            return
        }
        // Back on. Fused does not resume a request that was dropped while the
        // providers were down, so the service has to re-ask for one.
        TrackingService.request(context, "providers")
    }

    companion object {
        const val ACTION = "android.location.PROVIDERS_CHANGED"
    }
}
