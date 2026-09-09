package expo.modules.fieldagent

import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * The transport. HttpURLConnection on purpose: OkHttp is already in every React
 * Native app, but depending on it from a library pins a version the host may not
 * want, and this module posts a few kilobytes of JSON — the platform client is
 * exactly the right size for that.
 */
object Uplink {

    data class Response(
        val ok: Boolean,
        val status: Int,
        val body: String?,
        /** OFFLINE, TIMEOUT, HTTP_401, HTTP_500... — null when ok. */
        val errorCode: String?,
        val message: String?
    ) {
        /**
         * A tunnel is not a logout. Only the server saying so ends a session;
         * a transport failure must leave the queue and the auth header alone.
         */
        val isTransport: Boolean get() = errorCode == "OFFLINE" || errorCode == "TIMEOUT"

        /** 5xx and 429: worth replaying later. 4xx: the payload itself is wrong. */
        val isRetryable: Boolean get() = isTransport || status >= 500 || status == 429 || status == 408
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    fun post(url: String, json: String, authHeader: String?): Response {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                authHeader?.let { setRequestProperty("Authorization", it) }
            }

            connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }

            if (status in 200..299) {
                Response(true, status, body, null, null)
            } else {
                Response(false, status, body, "HTTP_$status", "Le serveur a repondu $status")
            }
        } catch (error: SocketTimeoutException) {
            Response(false, 0, null, "TIMEOUT", error.message ?: "delai depasse")
        } catch (error: UnknownHostException) {
            Response(false, 0, null, "OFFLINE", error.message ?: "hote injoignable")
        } catch (error: ConnectException) {
            Response(false, 0, null, "OFFLINE", error.message ?: "connexion refusee")
        } catch (error: IOException) {
            Response(false, 0, null, "OFFLINE", error.message ?: "erreur reseau")
        } catch (error: Exception) {
            Response(false, 0, null, "UPLINK", error.message ?: error.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }
}
