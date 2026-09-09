package expo.modules.fieldagent

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The queue plus the transport, in one place so the service and a direct
 * `flush()` from JS take the exact same path — two flush implementations would
 * drift, and the one that drifts is the one that loses points.
 *
 * Never called from the main thread: every entry point here does blocking I/O.
 */
object Outbox {

    private val main = Handler(Looper.getMainLooper())

    fun file(context: Context): File = File(File(context.filesDir, "field-agent"), "outbox.tsv")

    fun queue(context: Context): Queue =
        Queue(file(context), Config.get(context).tracking.queueSize)

    fun size(context: Context): Int = queue(context).size()

    fun authHeader(context: Context): String? {
        val stored = Prefs.of(context).getString(Prefs.AUTH_HEADER, null) ?: return null
        return Crypto.decrypt(stored)
    }

    fun add(context: Context, entry: Queue.Entry) {
        val dropped = queue(context).add(entry)
        if (dropped > 0) {
            Bus.error("QUEUE_FULL", "$dropped position(s) supprimee(s) : la file a atteint son plafond.")
        }
    }

    /** Returns sent count and what is left queued. */
    fun flush(context: Context): Pair<Int, Int> {
        val app = context.applicationContext
        val config = Config.get(app).tracking
        val outbox = queue(app)

        val url = config.url
        if (url == null) {
            Prefs.putString(app, Prefs.LAST_ERROR, "tracking.url absente")
            Bus.error("CONFIG", "tracking.url absente : rien ne peut etre envoye.")
            return 0 to outbox.size()
        }

        val power = app.getSystemService(PowerManager::class.java)
        // Partial wake lock only while there is real work, never permanently.
        val lock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "expo-field-agent:upload")
        runCatching { lock?.acquire(60_000L) }

        var sent = 0
        try {
            val auth = authHeader(app)
            while (true) {
                val batch = outbox.peek(config.batchSize)
                if (batch.isEmpty()) break

                val asBatch = batch.size > 1 && config.batchUrl != null
                val handled = if (asBatch) batch else listOf(batch.first())

                val response = if (asBatch) {
                    val body = JSONObject()
                        .put("positions", JSONArray().apply { batch.forEach { put(JSONObject(it.payload)) } })
                        .toString()
                    Uplink.post(config.batchUrl!!, body, auth)
                } else {
                    Uplink.post(url, batch.first().payload, auth)
                }

                if (response.ok) {
                    // Removal by id, never by count: points recorded while the
                    // batch was in flight must not be deleted with it.
                    outbox.remove(handled.map { it.clientId })
                    sent += handled.size
                    Prefs.putLong(app, Prefs.LAST_SENT_AT, System.currentTimeMillis())
                    Prefs.putString(app, Prefs.LAST_ERROR, null)
                    consumeAlert(app, response.body)
                    continue
                }

                Prefs.putString(app, Prefs.LAST_ERROR, "${response.errorCode}: ${response.message}")
                Bus.error(response.errorCode ?: "UPLINK", response.message ?: "envoi echoue")

                // A tunnel is not a dead session: transport failures keep everything.
                // A 4xx that is neither auth nor throttling will never succeed, and
                // keeping it would block every later point behind a poisoned one.
                if (!response.isRetryable && response.status !in setOf(401, 403)) {
                    outbox.remove(handled.map { it.clientId })
                }
                break
            }
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }

        val remaining = outbox.size()
        if (sent > 0) {
            Bus.emit("sent", Bundle().apply {
                putInt("count", sent)
                putInt("queued", remaining)
            })
        }
        return sent to remaining
    }

    /**
     * The server may hand an alert back on the position response. It is the one
     * alert path that needs no push at all and still works with the app closed,
     * because the service is what made the request.
     */
    private fun consumeAlert(context: Context, body: String?) {
        if (body.isNullOrBlank()) return
        runCatching {
            val alert = JSONObject(body).optJSONObject("alert") ?: return
            val title = alert.optString("title").ifEmpty { return }
            val payload = alert.optJSONObject("data")?.toString()
            val text = alert.optString("body").ifEmpty { null }
            val tag = alert.optString("tag").ifEmpty { null }
            val channelId = alert.optString("channelId").ifEmpty { null }
            main.post { Alerts.trigger(context, title, text, payload, false, tag, channelId) }
        }
    }
}
