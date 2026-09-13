package expo.modules.fieldagent

import android.content.Context
import org.json.JSONObject

/**
 * Every string the plugin shows to an end user, resolved in one place.
 *
 * The order is: what the host pushed from JS, then what `app.json` configured,
 * then the packaged resource. The host layer exists because a rider app almost
 * always carries its own language picker, and the phone being in French says
 * nothing about the driver having chosen Arabic in the app.
 *
 * Persisted, deliberately. The service comes back after a reboot with no JS
 * anywhere — a language that lived in memory would come back as the default,
 * and the driver would find their notification in a language they never chose.
 */
object Strings {

    const val SERVICE_CHANNEL_NAME = "serviceChannelName"
    const val SERVICE_TITLE = "serviceTitle"
    const val SERVICE_BODY = "serviceBody"
    const val ALERT_CHANNEL_NAME = "alertChannelName"
    const val ALERT_CHANNEL_NAME_SILENT = "alertChannelNameSilent"
    const val DISMISS = "dismiss"
    const val BUBBLE_LABEL = "bubbleLabel"
    const val BUBBLE_ACCESSIBILITY = "bubbleAccessibility"

    @Volatile
    private var cache: Map<String, String>? = null

    /** `null` clears every override and hands the strings back to app.json. */
    fun set(context: Context, values: Map<String, String>?) {
        val app = context.applicationContext
        if (values.isNullOrEmpty()) {
            Prefs.putString(app, Prefs.STRINGS, null)
            cache = emptyMap()
            return
        }
        val json = JSONObject()
        // Blank is not a translation. A host that maps a missing key to "" would
        // otherwise wipe a label rather than fall back to the configured one.
        values.forEach { (key, value) -> if (value.isNotBlank()) json.put(key, value) }
        Prefs.putString(app, Prefs.STRINGS, json.toString())
        cache = parse(json.toString())
    }

    fun all(context: Context): Map<String, String> {
        cache?.let { return it }
        val stored = Prefs.of(context.applicationContext).getString(Prefs.STRINGS, null)
        return parse(stored).also { cache = it }
    }

    private fun parse(stored: String?): Map<String, String> {
        if (stored.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(stored)
            json.keys().asSequence().mapNotNull { key ->
                val value = json.optString(key)
                if (value.isNullOrBlank()) null else key to value
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun override(context: Context, key: String): String? = all(context)[key]

    // --- The resolved strings ------------------------------------------------

    fun serviceChannelName(context: Context): String =
        override(context, SERVICE_CHANNEL_NAME) ?: Config.get(context).notification.channelName

    fun serviceTitle(context: Context): String =
        override(context, SERVICE_TITLE) ?: Config.get(context).notification.title

    fun serviceBody(context: Context): String =
        override(context, SERVICE_BODY) ?: Config.get(context).notification.body

    fun alertChannelName(context: Context): String =
        override(context, ALERT_CHANNEL_NAME) ?: Config.get(context).alert.channelName

    fun alertChannelNameSilent(context: Context): String =
        override(context, ALERT_CHANNEL_NAME_SILENT)
            ?: context.getString(R.string.field_agent_channel_silent, alertChannelName(context))

    fun dismiss(context: Context): String =
        override(context, DISMISS) ?: context.getString(R.string.field_agent_dismiss)

    fun bubbleLabel(context: Context): String =
        override(context, BUBBLE_LABEL) ?: Config.get(context).bubble.label

    /**
     * The label is substituted into the sentence when it carries a placeholder.
     * A host translation with a stray percent sign must degrade to its own text,
     * not crash the overlay it is describing.
     */
    fun bubbleAccessibility(context: Context, label: String): String {
        val template = override(context, BUBBLE_ACCESSIBILITY)
            ?: return context.getString(R.string.field_agent_bubble_description, label)
        return runCatching { String.format(template, label) }.getOrDefault(template)
    }
}
