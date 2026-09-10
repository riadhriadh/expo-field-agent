package expo.modules.fieldagent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import expo.modules.interfaces.permissions.PermissionsResponseListener
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

private const val RC_BACKGROUND_LOCATION = 0xFA20
private const val RC_OVERLAY = 0xFA21
private const val RC_DND = 0xFA22
private const val RC_BATTERY = 0xFA23
private const val RC_FULL_SCREEN = 0xFA24
private const val RC_AUTOSTART = 0xFA25

class FieldAgentModule : Module() {

    private val context: Context
        get() = appContext.reactContext?.applicationContext ?: throw Exceptions.ReactContextLost()

    private val main = Handler(Looper.getMainLooper())

    private var settingsRequestCode = 0
    private var settingsContinuation: ((Unit) -> Unit)? = null

    override fun definition() = ModuleDefinition {
        Name("FieldAgent")

        Events("position", "sent", "error", "alert", "bubblePress")

        OnCreate {
            Bus.listener = { name, payload -> runCatching { this@FieldAgentModule.sendEvent(name, payload) } }
            // A process killed mid-alert would otherwise leave the user's alarm
            // volume pinned at maximum with no idea why.
            Alerts.recover(context)
        }

        OnDestroy {
            Bus.listener = null
        }

        OnActivityResult { _, payload ->
            if (payload.requestCode == settingsRequestCode) {
                val resume = settingsContinuation
                settingsContinuation = null
                resume?.invoke(Unit)
            }
        }

        // --- Permissions ---------------------------------------------------

        AsyncFunction("getPermissions") { permissionsBundle() }

        AsyncFunction("requestPermissions") Coroutine { skip: List<String> ->
            requestLadder(skip.toSet())
            permissionsBundle()
        }

        AsyncFunction("openSettings") Coroutine { which: String ->
            openSettingsFor(which)
        }

        // --- Tracking ------------------------------------------------------

        AsyncFunction("start") { options: Map<String, Any?>? ->
            applyOverrides(options)
            val config = Config.get(context)
            if (config.tracking.url.isNullOrEmpty()) {
                throw IllegalStateException(
                    "tracking.url est absente : renseigne-la dans app.json ou passe start({ url })."
                )
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                throw IllegalStateException("ACCESS_FINE_LOCATION n'est pas accordee : appelle requestPermissions().")
            }

            // Idempotent: the persisted intention is set first so a boot or a
            // watchdog tick resumes even if this very start is refused.
            Prefs.setDesiredRunning(context, true)
            TrackingService.request(context, "js")
            Watchdog.arm(context)
        }

        AsyncFunction("stop") {
            TrackingService.stop(context)
        }

        AsyncFunction("isRunning") { TrackingService.isRunning }

        AsyncFunction("setAuthHeader") { value: String? ->
            if (value == null) {
                Prefs.putString(context, Prefs.AUTH_HEADER, null)
            } else {
                val encrypted = Crypto.encrypt(value)
                    ?: throw IllegalStateException("Le keystore a refuse de chiffrer l'en-tete d'authentification.")
                Prefs.putString(context, Prefs.AUTH_HEADER, encrypted)
            }
        }

        AsyncFunction("setIntervalSeconds") { seconds: Int ->
            require(seconds >= 1) { "setInterval attend un nombre de secondes >= 1." }
            TrackingService.send(
                context,
                TrackingService.ACTION_SET_INTERVAL,
                Bundle().apply { putInt(TrackingService.EXTRA_INTERVAL, seconds) }
            )
        }

        // Runs off the JS thread on the module queue, which is what makes a
        // blocking flush acceptable here.
        AsyncFunction("flush") {
            val (sent, queued) = Outbox.flush(context)
            Bundle().apply {
                putInt("sent", sent)
                putInt("queued", queued)
            }
        }

        AsyncFunction("getState") {
            val preferences = Prefs.of(context)
            val lastFix = preferences.getLong(Prefs.LAST_FIX_AT, 0L)
            val lastSent = preferences.getLong(Prefs.LAST_SENT_AT, 0L)
            // A Map rather than a Bundle: the contract says these are nullable,
            // and a Bundle can only omit a key, which reads as undefined in JS.
            mapOf(
                "running" to TrackingService.isRunning,
                "queued" to Outbox.size(context),
                "lastFixAt" to if (lastFix > 0) lastFix.toDouble() else null,
                "lastSentAt" to if (lastSent > 0) lastSent.toDouble() else null,
                "lastError" to preferences.getString(Prefs.LAST_ERROR, null)
            )
        }

        // --- Bubble --------------------------------------------------------

        AsyncFunction("showBubble") { Bubble.show(context) }

        AsyncFunction("hideBubble") { Bubble.hide(context) }

        AsyncFunction("setBubbleState") { state: String, text: String? ->
            Bubble.setState(context, state, text)
        }

        // --- Alert ---------------------------------------------------------

        AsyncFunction("triggerAlert") { payload: Map<String, Any?> ->
            val title = payload["title"] as? String
                ?: throw IllegalArgumentException("triggerAlert attend { title: string }.")
            val body = payload["body"] as? String
            @Suppress("UNCHECKED_CAST")
            val data = (payload["data"] as? Map<String, Any?>)?.let { JSONObject(it).toString() }
            val silent = ((payload["data"] as? Map<*, *>)?.get("silent") as? Boolean) == true

            val tag = payload["tag"] as? String
            val channelId = payload["channelId"] as? String

            // MediaPlayer, torch and the TTL handler all belong to the main thread.
            main.post { Alerts.trigger(context, title, body, data, silent, tag, channelId) }
        }

        AsyncFunction("dismissAlert") {
            main.post { Alerts.dismiss(context) }
        }

        AsyncFunction("setAlertSound") { enabled: Boolean ->
            Prefs.of(context).edit().putBoolean(Prefs.ALERT_SOUND_ENABLED, enabled).apply()
        }

        Function("getPendingAlertSync") {
            Alerts.peek(context)?.toBundle()
        }
    }

    // --- Permission plumbing ----------------------------------------------

    private fun runtimeState(permission: String): String {
        if (ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return "granted"
        }
        // "Refused" and "never asked" are different things: only the first must
        // stop the host from asking again.
        return if (Prefs.of(context).getBoolean("asked_$permission", false)) "denied" else "undetermined"
    }

    private fun markAsked(vararg permissions: String) {
        val editor = Prefs.of(context).edit()
        permissions.forEach { editor.putBoolean("asked_$it", true) }
        editor.apply()
    }

    private fun permissionsBundle(): Bundle = Bundle().apply {
        putString("location", runtimeState(Manifest.permission.ACCESS_FINE_LOCATION))
        putString(
            "backgroundLocation",
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) "granted"
            else runtimeState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        )
        putString(
            "notifications",
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (Alerts.hasNotificationPermission(context)) "granted" else "denied"
            } else {
                runtimeState(Manifest.permission.POST_NOTIFICATIONS)
            }
        )
        putString("overlay", if (Overlay.isGranted(context)) "granted" else "denied")
        putString("batteryUnrestricted", if (Power.isUnrestricted(context)) "granted" else "denied")
        putString("dndAccess", if (Alerts.hasDndAccess(context)) "granted" else "denied")
        // Android 14 gates the full-screen intent behind a special access; without
        // it the locked-screen path silently degrades to a heads-up notification.
        putString(
            "fullScreenIntent",
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) "granted"
            else if (Alerts.canUseFullScreenIntent(context)) "granted" else "denied"
        )
        // Only meaningful when the host opted into the bridge; otherwise the
        // service is not even in the manifest and the access grants nothing.
        putString(
            "notificationAccess",
            when {
                !NotificationBridge.isEnabled(context) -> "unsupported"
                NotificationBridge.isGranted(context) -> "granted"
                else -> "denied"
            }
        )
        putString(
            "autostart",
            when {
                !Power.hasManufacturerScreen(context) -> "unsupported"
                Power.isConfirmed(context) -> "granted"
                else -> "undetermined"
            }
        )
    }

    private suspend fun requestLadder(skip: Set<String>) {
        // The order is Android's, not ours: foreground location before
        // background, notifications before anything that relies on them.
        if ("location" !in skip) {
            ask(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if ("notifications" !in skip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ask(Manifest.permission.POST_NOTIFICATIONS)
        }

        if ("backgroundLocation" !in skip &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            runtimeState(Manifest.permission.ACCESS_FINE_LOCATION) == "granted" &&
            runtimeState(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != "granted"
        ) {
            // Two steps, imposed by the platform. On API 29 the dialog can still
            // grant it; from API 30 only the settings page can, so a single
            // request never returns granted and the host must explain why in
            // between — Google Play requires that disclosure anyway.
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                ask(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                openForResult(Power.appDetailsIntent(context), RC_BACKGROUND_LOCATION)
                markAsked(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        if ("overlay" !in skip && !Overlay.isGranted(context)) {
            openForResult(overlayIntent(), RC_OVERLAY)
        }

        if ("dndAccess" !in skip && !Alerts.hasDndAccess(context)) {
            openForResult(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), RC_DND)
        }

        if ("fullScreenIntent" !in skip &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !Alerts.canUseFullScreenIntent(context)
        ) {
            openForResult(fullScreenIntentSettings(), RC_FULL_SCREEN)
        }

        if ("batteryUnrestricted" !in skip && !Power.isUnrestricted(context)) {
            openForResult(Power.batterySettingsIntent(), RC_BATTERY)
        }

        if ("autostart" !in skip && Power.hasManufacturerScreen(context) && !Power.isConfirmed(context)) {
            openForResult(Power.manufacturerIntent(context), RC_AUTOSTART)
            Power.markConfirmed(context)
        }
    }

    private fun overlayIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    private fun fullScreenIntentSettings(): Intent =
        Intent(
            "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT",
            Uri.parse("package:${context.packageName}")
        )

    private suspend fun openSettingsFor(which: String) {
        val intent = when (which) {
            "location", "backgroundLocation", "notifications" -> Power.appDetailsIntent(context)
            "overlay" -> overlayIntent()
            "dndAccess" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "batteryUnrestricted" -> Power.batterySettingsIntent()
            "fullScreenIntent" -> fullScreenIntentSettings()
            "autostart" -> Power.manufacturerIntent(context).also { Power.markConfirmed(context) }
            "notificationAccess" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            else -> Power.appDetailsIntent(context)
        }
        openForResult(intent, RC_BATTERY)
    }

    private suspend fun ask(vararg permissions: String) = suspendCancellableCoroutine<Unit> { continuation ->
        markAsked(*permissions)
        val manager = appContext.permissions
        if (manager == null) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        runCatching {
            manager.askForPermissions(
                PermissionsResponseListener { if (continuation.isActive) continuation.resume(Unit) },
                *permissions
            )
        }.onFailure { if (continuation.isActive) continuation.resume(Unit) }
    }

    /**
     * Settings screens cannot be awaited by any API, so the activity result —
     * which they always deliver, if only as RESULT_CANCELED — is what tells us
     * the user came back and the state is worth re-reading.
     */
    private suspend fun openForResult(intent: Intent, requestCode: Int) =
        suspendCancellableCoroutine<Unit> { continuation ->
            val activity = appContext.activityProvider?.currentActivity
            if (activity == null) {
                // No activity: still open the screen, just without a result.
                runCatching {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                continuation.resume(Unit)
                return@suspendCancellableCoroutine
            }
            settingsRequestCode = requestCode
            settingsContinuation = { if (continuation.isActive) continuation.resume(Unit) }
            runCatching { activity.startActivityForResult(intent, requestCode) }
                .onFailure {
                    settingsContinuation = null
                    Bus.error("SETTINGS", it.message ?: "ecran de reglages introuvable")
                    if (continuation.isActive) continuation.resume(Unit)
                }
        }

    private fun applyOverrides(options: Map<String, Any?>?) {
        if (options.isNullOrEmpty()) {
            Config.invalidate()
            return
        }
        val json = JSONObject()
        options.forEach { (key, value) -> if (value != null) json.put(key, value) }
        Config.setOverrides(context, json)
    }
}
