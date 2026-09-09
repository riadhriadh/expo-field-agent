package expo.modules.fieldagent

import android.os.Bundle

/**
 * In-process hop from the service, the bubble and the alert to the JS module.
 *
 * A plain reference rather than a broadcast: everything here runs in the same
 * process, and LocalBroadcastManager would be a dependency plus a serialisation
 * round trip for nothing. When JS is not running the listener is simply null and
 * the event is dropped — that is the correct behaviour, not a lost message: the
 * durable state lives in the queue and in the preferences.
 */
object Bus {
    @Volatile
    var listener: ((String, Bundle) -> Unit)? = null

    fun emit(name: String, payload: Bundle = Bundle()) {
        listener?.invoke(name, payload)
    }

    fun error(code: String, message: String) {
        emit("error", Bundle().apply {
            putString("code", code)
            putString("message", message)
        })
    }
}
