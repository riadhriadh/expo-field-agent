package expo.modules.fieldagent

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import org.json.JSONObject

data class TrackingConfig(
    val url: String?,
    val batchUrl: String?,
    val intervalSeconds: Int,
    val idleIntervalSeconds: Int,
    val distanceFilterMeters: Double,
    val batchSize: Int,
    val queueSize: Int,
    val heartbeatSeconds: Int
)

data class NotificationConfig(
    val channelName: String,
    val title: String,
    val body: String,
    val icon: String?,
    val color: Int
)

data class AlertConfig(
    val titlePattern: String,
    val sound: String?,
    val soundExtension: String?,
    val channelName: String,
    val route: String,
    val ttlSeconds: Int,
    val torch: Boolean,
    val channelVersion: Int
)

data class BubbleConfig(
    val icon: String?,
    val label: String,
    val ok: Int,
    val warn: Int,
    val bad: Int,
    val urgent: Int
)

data class FieldAgentConfig(
    val tracking: TrackingConfig,
    val notification: NotificationConfig,
    val alert: AlertConfig,
    val bubble: BubbleConfig,
    val rootComponent: String
)

/**
 * Reads the blob the config plugin wrote into the manifest, then layers the
 * runtime overrides from `start(options)` on top.
 *
 * Cached, and invalidated explicitly, because the service reads it on every fix
 * and a PackageManager round trip per fix is a battery bill for nothing.
 */
object Config {
    private const val META = "expo.modules.fieldagent.CONFIG"

    @Volatile
    private var cached: FieldAgentConfig? = null

    fun get(context: Context): FieldAgentConfig {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val resolved = parse(readManifest(context), readOverrides(context))
            cached = resolved
            return resolved
        }
    }

    fun invalidate() {
        cached = null
    }

    /** Persists the overrides so a service restarted at boot uses the same settings. */
    fun setOverrides(context: Context, overrides: JSONObject?) {
        Prefs.putString(context, Prefs.OVERRIDES, overrides?.toString())
        invalidate()
    }

    private fun readOverrides(context: Context): JSONObject {
        val raw = Prefs.of(context).getString(Prefs.OVERRIDES, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun readManifest(context: Context): JSONObject {
        val raw = runCatching {
            val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            info.metaData?.getString(META)
        }.getOrNull()
        return runCatching { JSONObject(raw ?: "{}") }.getOrDefault(JSONObject())
    }

    private fun JSONObject.child(name: String): JSONObject = optJSONObject(name) ?: JSONObject()

    private fun JSONObject.str(name: String, fallback: String?): String? =
        if (isNull(name)) fallback else optString(name, fallback ?: "").ifEmpty { fallback }

    private fun color(source: JSONObject, name: String, fallback: String): Int =
        runCatching { Color.parseColor(source.optString(name, fallback)) }
            .getOrElse { Color.parseColor(fallback) }

    private fun parse(manifest: JSONObject, overrides: JSONObject): FieldAgentConfig {
        val tracking = manifest.child("tracking")
        val notification = manifest.child("notification")
        val alert = manifest.child("alert")
        val bubble = manifest.child("bubble")
        val bubbleColors = bubble.child("colors")

        // Overrides only ever touch tracking: everything else is baked into
        // resources and notification channels at build time anyway.
        fun trackInt(name: String, fallback: Int) =
            overrides.optInt(name, tracking.optInt(name, fallback)).coerceAtLeast(1)

        return FieldAgentConfig(
            tracking = TrackingConfig(
                url = overrides.str("url", tracking.str("url", null)),
                batchUrl = overrides.str("batchUrl", tracking.str("batchUrl", null)),
                intervalSeconds = trackInt("intervalSeconds", 15),
                idleIntervalSeconds = trackInt("idleIntervalSeconds", 60),
                distanceFilterMeters = overrides.optDouble(
                    "distanceFilterMeters",
                    tracking.optDouble("distanceFilterMeters", 15.0)
                ).coerceAtLeast(0.0),
                batchSize = trackInt("batchSize", 50),
                queueSize = trackInt("queueSize", 1000),
                heartbeatSeconds = trackInt("heartbeatSeconds", 120)
            ),
            notification = NotificationConfig(
                channelName = notification.str("channelName", "Suivi en service")!!,
                title = notification.str("title", "En service")!!,
                body = notification.str("body", "Ta position est partagee.")!!,
                icon = notification.str("icon", null),
                color = color(notification, "color", "#FF6B2C")
            ),
            alert = AlertConfig(
                titlePattern = alert.str("titlePattern", ".*")!!,
                sound = alert.str("sound", null),
                soundExtension = alert.str("soundExtension", null),
                channelName = alert.str("channelName", "Alertes")!!,
                route = alert.str("route", "field-agent-alert")!!,
                ttlSeconds = alert.optInt("ttlSeconds", 45).coerceIn(1, 600),
                torch = alert.optBoolean("torch", false),
                channelVersion = alert.optInt("channelVersion", 1).coerceAtLeast(1)
            ),
            bubble = BubbleConfig(
                icon = bubble.str("icon", null),
                label = bubble.str("label", "Suivi")!!,
                ok = color(bubbleColors, "ok", "#1DB954"),
                warn = color(bubbleColors, "warn", "#F5A623"),
                bad = color(bubbleColors, "bad", "#E5484D"),
                urgent = color(bubbleColors, "urgent", "#E5484D")
            ),
            rootComponent = manifest.str("rootComponent", "main")!!
        )
    }
}
