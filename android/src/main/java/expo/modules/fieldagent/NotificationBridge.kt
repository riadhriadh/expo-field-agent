package expo.modules.fieldagent

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

/**
 * The one push case that no other client code can reach.
 *
 * When an FCM message carries a `notification` block and the app is not in the
 * foreground, the Firebase SDK posts that notification itself and never calls
 * into the app — not `onMessageReceived`, not a headless task, therefore never
 * `triggerAlert()`. Registering our own `FirebaseMessagingService` would change
 * nothing: the SDK short-circuits before any service we could declare.
 *
 * A notification listener is the only vantage point left, because it sees the
 * notification *after* the system posted it. That is why this exists, and it is
 * the only reason: it is not a general notification reader.
 *
 * Opt-in through `alert.notificationBridge: true`, and declared in the manifest
 * only then. BIND_NOTIFICATION_LISTENER_SERVICE draws a Google Play review on
 * any app that merely carries it, and most hosts can fix the problem on the
 * sender side by shipping data-only messages, which costs nothing.
 */
class NotificationBridge : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Our own mail and nobody else's. A listener is handed every
        // notification on the device; reading past our own package would be
        // surveillance we have no reason whatsoever to perform.
        if (sbn.packageName != packageName) return

        // Our own alert re-entering here would post itself again, forever.
        if (sbn.id == Alerts.ALERT_NOTIFICATION_ID || sbn.id == Alerts.SERVICE_NOTIFICATION_ID) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sbn.notification.channelId else null

        // Nothing to match on means nothing the pattern could accept.
        if (title.isEmpty() && sbn.tag == null && channelId == null) return

        // The FCM data payload is NOT in the posted notification: it only ever
        // reaches the app through the launch intent, when the user taps. What
        // survives is what the tray shows, so that is what is handed over, and
        // the host resolves the rest from its own API. Marked as coming from
        // here so the host can tell a thin alert from a complete one.
        val data = JSONObject()
            .put("source", "notificationBridge")
            .put("channelId", channelId ?: JSONObject.NULL)
            .put("tag", sbn.tag ?: JSONObject.NULL)
            .toString()

        val fired = runCatching {
            Alerts.trigger(this, title, body, data, false, sbn.tag, channelId)
        }.onFailure {
            Bus.error("BRIDGE", it.message ?: "pont de notification en echec")
        }.getOrDefault(false)

        // Two notifications for one event is worse than none: once our
        // full-screen alert has taken over, the tray copy is noise. Only the
        // one we replaced, and only when we actually replaced it.
        if (fired) runCatching { cancelNotification(sbn.key) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    companion object {
        /**
         * There is no API that grants notification access, and none that reports
         * it either — the enabled listeners live in a flat Secure setting, and
         * reading that string is what every app does.
         */
        fun isGranted(context: Context): Boolean {
            val enabled = runCatching {
                Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            }.getOrNull() ?: return false
            val expected = ComponentName(context, NotificationBridge::class.java)
            return enabled.split(':').any {
                val parsed = ComponentName.unflattenFromString(it)
                parsed?.packageName == expected.packageName &&
                    parsed.className == expected.className
            }
        }

        /** True only when the host asked for the bridge in app.json. */
        fun isEnabled(context: Context): Boolean =
            runCatching { Config.get(context).alert.notificationBridge }.getOrDefault(false)
    }
}
