package expo.modules.fieldagent

import android.content.Context
import android.os.Bundle

/**
 * In-process hop from the service, the bubble and the alert to the JS module.
 *
 * A plain reference rather than a broadcast: everything here runs in the same
 * process, and LocalBroadcastManager would be a dependency plus a serialisation
 * round trip for nothing.
 *
 * What changed: a dropped event used to be the whole story. When JS is not
 * running the listener is null — and that is precisely the boot, the watchdog
 * tick and the kill, the three moments something actually goes wrong. An error
 * raised there vanished without a trace, which is why a driver reporting "it
 * stopped last Tuesday" could never be answered. Events still drop, because a
 * position has nowhere to go without a listener; errors no longer do.
 */
object Bus {
    @Volatile
    var listener: ((String, Bundle) -> Unit)? = null

    /**
     * The application Context, so an error can be written down from a process
     * that has no module. Set from both entry points — the module for the app
     * process, the service for the one the system restarts on its own — because
     * neither is guaranteed to exist when the other does.
     */
    @Volatile
    private var appContext: Context? = null

    /**
     * Idempotent, and called from EVERY entry point rather than once.
     *
     * A receiver runs in a process where the module may never be created: the
     * boot resume, the watchdog tick and the app update all reached `Bus` before
     * anything had attached it, so the three events most worth recording were
     * the three it silently dropped. Attaching costs a field write, so the right
     * number of call sites is "all of them".
     */
    fun attach(context: Context) {
        val app = context.applicationContext
        appContext = app
        // The log reads its own threshold through this seam rather than importing
        // Config, which keeps Log testable without a manifest.
        Log.levelProvider = { ctx -> Config.get(ctx).let { it.logLevel to it.logMaxDays } }
    }

    fun emit(name: String, payload: Bundle = Bundle()) {
        listener?.invoke(name, payload)
    }

    /**
     * Reaches JS when JS is there, and the disk always. The preference pair is
     * what `getState()` reads back later: without the timestamp, an error from
     * three days ago and one from three seconds ago are the same string.
     */
    fun error(code: String, message: String) {
        record(Log.Level.ERROR, code, message)
        emit("error", Bundle().apply {
            putString("code", code)
            putString("message", message)
        })
    }

    /** Written down, never surfaced as an error event: it is context, not a fault. */
    fun warn(code: String, message: String) = record(Log.Level.WARN, code, message)

    fun info(code: String, message: String) = record(Log.Level.INFO, code, message)

    fun debug(code: String, message: String) = record(Log.Level.DEBUG, code, message)

    private fun record(level: Log.Level, code: String, message: String) {
        val app = appContext ?: return
        runCatching {
            Log.write(app, level, code, message)
            // Only a real fault overwrites the slot getState() reads. A warning
            // that replaced the last error would erase the very thing the host
            // is watching for.
            if (level == Log.Level.ERROR) {
                Prefs.putString(app, Prefs.LAST_ERROR, "$code: $message")
                Prefs.putLong(app, Prefs.LAST_ERROR_AT, System.currentTimeMillis())
            }
        }
    }
}
