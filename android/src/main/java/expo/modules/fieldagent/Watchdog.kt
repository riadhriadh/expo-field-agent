package expo.modules.fieldagent

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

    fun arm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = SystemClock.elapsedRealtime() + INTERVAL_MS
        runCatching {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent(context))
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
        // The persisted intention decides, not whatever this fresh process
        // happens to remember — which is nothing.
        if (!Prefs.isDesiredRunning(context)) return
        TrackingService.request(context, "watchdog")
        Watchdog.arm(context)
    }

    companion object {
        const val ACTION_CHECK = "expo.modules.fieldagent.WATCHDOG_CHECK"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in HANDLED) return
        if (!Prefs.isDesiredRunning(context)) return

        // Boot receivers are exempt from the Android 12 background start
        // restriction, which is exactly why the resume happens here.
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
