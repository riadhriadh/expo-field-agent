package expo.modules.fieldagent

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent state. Everything the boot receiver and the watchdog consult lives
 * here rather than in memory: they run in a process that has no idea what the
 * previous one was doing.
 */
object Prefs {
    private const val FILE = "expo.modules.fieldagent"

    /** The intention "I am on duty" — the only thing boot and watchdog trust. */
    const val DESIRED_RUNNING = "desired_running"
    const val OVERRIDES = "overrides"
    const val AUTH_HEADER = "auth_header"
    /** Host-supplied translations, so a reboot does not undo the chosen language. */
    const val STRINGS = "strings"

    const val LAST_FIX_AT = "last_fix_at"
    const val LAST_SENT_AT = "last_sent_at"
    const val LAST_ERROR = "last_error"

    const val BUBBLE_VISIBLE = "bubble_visible"
    const val BUBBLE_X = "bubble_x"
    const val BUBBLE_Y = "bubble_y"
    const val BUBBLE_STATE = "bubble_state"
    /** Runtime image set by the host; survives a service restart with no JS. */
    const val BUBBLE_IMAGE = "bubble_image"
    const val BUBBLE_TEXT = "bubble_text"

    /** Saved before the alert raises the alarm stream, restored after. */
    const val SAVED_ALARM_VOLUME = "saved_alarm_volume"
    const val ALERT_SOUND_ENABLED = "alert_sound_enabled"

    /** Set once the user has been through their manufacturer's autostart screen. */
    const val POWER_CONFIRMED = "power_confirmed"

    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isDesiredRunning(context: Context): Boolean = of(context).getBoolean(DESIRED_RUNNING, false)

    fun setDesiredRunning(context: Context, value: Boolean) {
        of(context).edit().putBoolean(DESIRED_RUNNING, value).apply()
    }

    fun putLong(context: Context, key: String, value: Long) {
        of(context).edit().putLong(key, value).apply()
    }

    fun putString(context: Context, key: String, value: String?) {
        of(context).edit().putString(key, value).apply()
    }
}
