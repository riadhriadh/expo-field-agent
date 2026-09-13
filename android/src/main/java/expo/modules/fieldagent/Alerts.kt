package expo.modules.fieldagent

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.UUID
import java.util.regex.Pattern
import org.json.JSONObject

data class PendingAlert(
    val id: String,
    val title: String,
    val body: String?,
    val dataJson: String?,
    val receivedAt: Long,
    /** `alert.route` from app.json, carried through so a host can navigate instead of overlaying. */
    val route: String
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString("id", id)
        putString("title", title)
        body?.let { putString("body", it) }
        putDouble("receivedAt", receivedAt.toDouble())
        dataJson?.let { putString("dataJson", it) }
        putString("route", route)
    }

    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("body", body)
        put("dataJson", dataJson)
        put("receivedAt", receivedAt)
        put("route", route)
    }.toString()

    companion object {
        fun fromJson(raw: String): PendingAlert? = runCatching {
            val json = JSONObject(raw)
            PendingAlert(
                id = json.getString("id"),
                title = json.getString("title"),
                body = if (json.isNull("body")) null else json.optString("body"),
                dataJson = if (json.isNull("dataJson")) null else json.optString("dataJson"),
                receivedAt = json.optLong("receivedAt"),
                route = json.optString("route", "field-agent-alert")
            )
        }.getOrNull()
    }
}

/**
 * Notification channels, the ringtone, the alarm volume, audio focus, the torch
 * and the full-screen path.
 *
 * Almost every line here exists because of a device that misbehaved. Read the
 * comments before simplifying anything.
 */
object Alerts {

    const val SERVICE_NOTIFICATION_ID = 0xFA01
    const val ALERT_NOTIFICATION_ID = 0xFA02

    private const val PENDING_ALERT = "pending_alert"
    private const val TORCH_PERIOD_MS = 350L

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var pending: PendingAlert? = null

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var legacyFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var vibrator: Vibrator? = null

    /** Identity token: a beat already queued when the torch stops must not relight it. */
    private var torchToken: Any? = null
    private var torchCameraId: String? = null

    private var ttlRunnable: Runnable? = null
    private var pattern: Pattern? = null
    private var patternSource: String? = null

    // --- Channels ----------------------------------------------------------

    /**
     * Channel attributes are frozen at creation: importance, sound, vibration
     * and DND bypass are never re-read for a channel that already exists. So the
     * id carries everything that could change, and stale ids are deleted.
     */
    private fun channelIds(context: Context): Triple<String, String, String> {
        val version = Config.get(context).alert.channelVersion
        val bypass = if (hasDndAccess(context)) "_dnd" else ""
        return Triple(
            "fa_service_v$version",
            "fa_alert_v$version$bypass",
            "fa_alert_silent_v$version"
        )
    }

    private fun ensureChannels(context: Context): Triple<String, String, String> {
        val ids = channelIds(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return ids

        val manager = context.getSystemService(NotificationManager::class.java) ?: return ids
        val config = Config.get(context)
        val keep = setOf(ids.first, ids.second, ids.third)

        // A v1 that shipped a muted channel would condemn every later version
        // until uninstall. Dropping the previous ids is the only way out.
        manager.notificationChannels
            .filter { it.id.startsWith("fa_") && it.id !in keep }
            .forEach { runCatching { manager.deleteNotificationChannel(it.id) } }

        if (manager.getNotificationChannel(ids.first) == null) {
            manager.createNotificationChannel(
                NotificationChannel(ids.first, Strings.serviceChannelName(context), NotificationManager.IMPORTANCE_LOW).apply {
                    description = Strings.serviceBody(context)
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }

        if (manager.getNotificationChannel(ids.second) == null) {
            manager.createNotificationChannel(
                NotificationChannel(ids.second, Strings.alertChannelName(context), NotificationManager.IMPORTANCE_HIGH).apply {
                    // The sound is ours: we play it on the alarm stream so it is
                    // audible on silent, which a channel sound never is.
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(hasDndAccess(context))
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
            )
        }

        if (manager.getNotificationChannel(ids.third) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    ids.third,
                    Strings.alertChannelNameSilent(context),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
            )
        }

        renameChannels(manager, context, ids)
        return ids
    }

    /**
     * Importance, sound and vibration are frozen at creation — but name and
     * description are not, and re-creating with the same id updates exactly
     * those two. Without this pass, a driver switching the app to Arabic would
     * keep a French channel name in system settings until they uninstall.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun renameChannels(
        manager: NotificationManager,
        context: Context,
        ids: Triple<String, String, String>
    ) {
        val wanted = listOf(
            ids.first to Strings.serviceChannelName(context),
            ids.second to Strings.alertChannelName(context),
            ids.third to Strings.alertChannelNameSilent(context)
        )
        for ((id, name) in wanted) {
            val existing = manager.getNotificationChannel(id) ?: continue
            if (existing.name?.toString() == name) continue
            runCatching {
                manager.createNotificationChannel(
                    NotificationChannel(id, name, existing.importance).apply {
                        description =
                            if (id == ids.first) Strings.serviceBody(context) else existing.description
                    }
                )
            }
        }
    }

    /**
     * Called when the host changes language. The ongoing service notification is
     * already on screen: rebuilding it is what makes the change visible now
     * rather than at the next restart.
     */
    fun refreshLocalisedSurfaces(context: Context) {
        val app = context.applicationContext
        runCatching { ensureChannels(app) }
        if (!Prefs.isDesiredRunning(app)) return
        runCatching {
            NotificationManagerCompat.from(app)
                .notify(SERVICE_NOTIFICATION_ID, buildServiceNotification(app))
        }
    }

    fun hasDndAccess(context: Context): Boolean =
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
        }.getOrDefault(false)

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return runCatching {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        }.getOrDefault(false)
    }

    // --- Triggering --------------------------------------------------------

    /**
     * The configured pattern is tested against the title, the notification tag
     * and the channel id — whichever the sender happened to fill in. A push
     * pipeline that routes by channel should not have to fake a title to be
     * recognised.
     */
    fun matches(context: Context, vararg candidates: String?): Boolean {
        val source = Config.get(context).alert.titlePattern
        if (pattern == null || patternSource != source) {
            pattern = runCatching { Pattern.compile(source, Pattern.CASE_INSENSITIVE) }
                .getOrElse { Pattern.compile(".*", Pattern.CASE_INSENSITIVE) }
            patternSource = source
        }
        val matcher = pattern!!
        return candidates.any { !it.isNullOrEmpty() && matcher.matcher(it).find() }
    }

    fun peek(context: Context): PendingAlert? {
        pending?.let { return it }
        // The process may have died between the notification and the tap on it.
        val stored = Prefs.of(context).getString(PENDING_ALERT, null) ?: return null
        return PendingAlert.fromJson(stored)?.also { pending = it }
    }

    /**
     * Returns false when the title does not match the configured pattern.
     * `silent` mutes this alert only; the persisted host setting mutes them all.
     */
    fun trigger(
        context: Context,
        title: String,
        body: String?,
        dataJson: String?,
        silent: Boolean,
        tag: String? = null,
        channelId: String? = null
    ): Boolean {
        val app = context.applicationContext
        if (!matches(app, title, tag, channelId)) return false

        // One alert, one ring: a duplicate push must not stack a second player.
        val existing = peek(app)
        if (existing != null && existing.title == title && existing.body == body) return true

        val alert = PendingAlert(
            id = UUID.randomUUID().toString(),
            title = title,
            body = body,
            dataJson = dataJson,
            receivedAt = System.currentTimeMillis(),
            route = Config.get(app).alert.route
        )
        pending = alert
        Prefs.putString(app, PENDING_ALERT, alert.toJson())

        val soundAllowed = !silent && Prefs.of(app).getBoolean(Prefs.ALERT_SOUND_ENABLED, true)
        postNotification(app, alert, soundAllowed)
        if (soundAllowed) startRinging(app) else stopRinging(app)

        launchActivity(app, alert)
        armTtl(app, Config.get(app).alert.ttlSeconds * 1000L)

        Bus.emit("alert", alert.toBundle())
        return true
    }

    fun dismiss(context: Context) {
        val app = context.applicationContext
        pending = null
        Prefs.putString(app, PENDING_ALERT, null)
        cancelTtl()
        stopRinging(app)
        NotificationManagerCompat.from(app).cancel(ALERT_NOTIFICATION_ID)
        AlertActivity.finishIfShowing()
    }

    private fun armTtl(context: Context, ttlMs: Long) {
        cancelTtl()
        // A ringtone that never stops because an end event was lost is how an
        // app gets uninstalled. The ceiling is not optional.
        val runnable = Runnable { dismiss(context) }
        ttlRunnable = runnable
        main.postDelayed(runnable, ttlMs)
    }

    private fun cancelTtl() {
        ttlRunnable?.let { main.removeCallbacks(it) }
        ttlRunnable = null
    }

    // --- Notification ------------------------------------------------------

    private fun smallIcon(context: Context): Int {
        val config = Config.get(context)
        config.notification.icon?.let { name ->
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id != 0) return id
        }
        return context.applicationInfo.icon
    }

    fun buildServiceNotification(context: Context): android.app.Notification {
        val config = Config.get(context)
        val channelId = ensureChannels(context).first
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon(context))
            .setContentTitle(Strings.serviceTitle(context))
            .setContentText(Strings.serviceBody(context))
            .setColor(config.notification.color)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(launchAppIntent(context))
            .build()
    }

    private fun launchAppIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun postNotification(context: Context, alert: PendingAlert, sonorous: Boolean) {
        val ids = ensureChannels(context)
        val channelId = if (sonorous) ids.second else ids.third

        val fullScreen = PendingIntent.getActivity(
            context,
            1,
            AlertActivity.intent(context, alert.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, AlertActionReceiver::class.java).setAction(AlertActionReceiver.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon(context))
            .setContentTitle(alert.title)
            .setContentText(alert.body ?: "")
            .setColor(Config.get(context).notification.color)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // CATEGORY_CALL + full-screen intent + IMPORTANCE_HIGH is the blessed
            // path: it is the one that wakes a locked screen.
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setDeleteIntent(dismissIntent)
            .addAction(0, Strings.dismiss(context), dismissIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, notification) }
                .onFailure { Bus.error("NOTIFICATION", it.message ?: "notify a echoue") }
        } else {
            Bus.error("NOTIFICATION", "Les notifications sont desactivees : pas d'ecran d'alerte au verrouillage.")
        }

        if (!canUseFullScreenIntent(context)) {
            Bus.error(
                "FULL_SCREEN_INTENT",
                "Android 14+ : l'acces plein ecran n'est pas accorde, l'alerte comptera sur la superposition."
            )
        }
    }

    /**
     * Second path, and it is not redundant: since Android 10 an app cannot start
     * an activity from the background, but holding SYSTEM_ALERT_WINDOW buys the
     * exemption. That covers the devices where the full-screen intent is
     * throttled or refused.
     *
     * Skipped when the app is already on screen: there, the `alert` event and
     * the host's <AlertHost> are the surface, and starting a second React
     * surface on top of the live one would mount the app tree twice.
     */
    private fun launchActivity(context: Context, alert: PendingAlert) {
        if (isOnScreen(context)) return
        if (!Overlay.isGranted(context) && !canUseFullScreenIntent(context)) return
        runCatching {
            context.startActivity(
                AlertActivity.intent(context, alert.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Bus.error("ALERT_ACTIVITY", it.message ?: "lancement refuse") }
    }

    // --- Ringing -----------------------------------------------------------

    private fun startRinging(context: Context) {
        stopRinging(context)
        val audio = context.getSystemService(AudioManager::class.java) ?: return

        raiseAlarmVolume(context, audio)
        requestFocus(audio)

        val uri = resolveSoundUri(context)
        if (uri == null) {
            Bus.error("SOUND", "Aucune sonnerie lisible : ni la ressource embarquee ni les sons systeme.")
        } else {
            val created = MediaPlayer()
            try {
                created.setAudioAttributes(
                    AudioAttributes.Builder()
                        // The alarm stream is the only one Android does not mute
                        // on silent — it is how an alarm clock still rings.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                created.setDataSource(context, uri)
                created.isLooping = true
                created.prepare()
                created.start()
                player = created
            } catch (error: Exception) {
                // A prepare() that throws still holds a native decoder. Leaking
                // one per failed alert takes a device down in a shift.
                runCatching { created.release() }
                Bus.error("SOUND", error.message ?: "MediaPlayer a echoue")
            }
        }

        startVibration(context)
        if (Config.get(context).alert.torch) startTorch(context)
    }

    private fun stopRinging(context: Context) {
        player?.let { active ->
            runCatching { if (active.isPlaying) active.stop() }
            runCatching { active.release() }
        }
        player = null

        stopVibration()
        stopTorch(context)

        val audio = context.getSystemService(AudioManager::class.java)
        if (audio != null) {
            abandonFocus(audio)
            restoreAlarmVolume(context, audio)
        }
    }

    /**
     * The bundled file first when the host shipped one: it is the only sound
     * that does not depend on the ROM. RingtoneManager defaults are the fallback
     * chain, not the other way round — on Xiaomi and Samsung they routinely come
     * back null or unreadable, and a channel or a player built on a null sound is
     * silent forever.
     */
    private fun resolveSoundUri(context: Context): Uri? {
        Config.get(context).alert.sound?.let { name ->
            val id = context.resources.getIdentifier(name, "raw", context.packageName)
            if (id != 0) {
                val uri = Uri.parse("android.resource://${context.packageName}/$id")
                if (isReadable(context, uri)) return uri
                Bus.error("SOUND", "res/raw/$name est present mais illisible : verifie noCompress.")
            }
        }
        for (type in intArrayOf(
            RingtoneManager.TYPE_ALARM,
            RingtoneManager.TYPE_RINGTONE,
            RingtoneManager.TYPE_NOTIFICATION
        )) {
            val uri = runCatching { RingtoneManager.getDefaultUri(type) }.getOrNull() ?: continue
            if (isReadable(context, uri)) return uri
        }
        return null
    }

    private fun isReadable(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun raiseAlarmVolume(context: Context, audio: AudioManager) {
        val config = Config.get(context).alert
        // Opted out: the alert still rings on the alarm stream, just at whatever
        // level the user chose. Nothing is saved, so nothing is restored later.
        if (!config.forceVolume) return

        val preferences = Prefs.of(context)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val target = Volume.target(current, max, config.volumeLevel)

        // Already loud enough. Writing anyway would save a "previous" value we
        // would then restore over a level the user is happy with.
        if (target <= current) return

        // Saved to disk, not to a field: a process killed mid-alert would
        // otherwise leave the user's alarm pinned at maximum forever.
        if (!preferences.contains(Prefs.SAVED_ALARM_VOLUME)) {
            preferences.edit().putInt(Prefs.SAVED_ALARM_VOLUME, current).commit()
        }

        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) }
        // The raise can be refused in silence (DND, manufacturer policy).
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) < target) {
            Bus.error("VOLUME", "Le volume d'alarme n'a pas pu etre monte au niveau demande.")
        }
    }

    private fun restoreAlarmVolume(context: Context, audio: AudioManager) {
        val preferences = Prefs.of(context)
        if (!preferences.contains(Prefs.SAVED_ALARM_VOLUME)) return
        val saved = preferences.getInt(Prefs.SAVED_ALARM_VOLUME, -1)
        preferences.edit().remove(Prefs.SAVED_ALARM_VOLUME).commit()
        if (saved >= 0) runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, saved, 0) }
    }

    /**
     * Called at startup. A process that died mid-alert leaves two things behind:
     * the alarm volume pinned at maximum, and a pending alert whose TTL handler
     * died with it. Both are cleaned up here, or the user keeps a stuck
     * notification and a maxed-out alarm with no idea why.
     */
    fun recover(context: Context) {
        val stale = peek(context)
        if (stale != null) {
            val ttlMs = Config.get(context).alert.ttlSeconds * 1000L
            if (System.currentTimeMillis() - stale.receivedAt > ttlMs) {
                dismiss(context)
            }
            // A live alert owns the volume; leave it alone.
            return
        }
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        restoreAlarmVolume(context, audio)
    }

    private fun requestFocus(audio: AudioManager) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // Without exclusive focus, Waze and music cover the alert, and on a
        // Bluetooth headset the sound leaves for the earpiece while the phone
        // stays mute in the pocket.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .build()
            focusRequest = request
            runCatching { audio.requestAudioFocus(request) }
        } else {
            val listener = AudioManager.OnAudioFocusChangeListener { }
            legacyFocusListener = listener
            @Suppress("DEPRECATION")
            runCatching {
                audio.requestAudioFocus(
                    listener,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        }
    }

    private fun abandonFocus(audio: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            legacyFocusListener?.let { runCatching { audio.abandonAudioFocus(it) } }
            legacyFocusListener = null
        }
    }

    private fun startVibration(context: Context) {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        vibrator = device
        val pattern = longArrayOf(0, 600, 400)
        // The VibrationAttributes replacement only exists from API 33, and the
        // alarm-usage hint is what keeps the buzz alive under Do Not Disturb —
        // so the deprecated overload stays until minSdk moves.
        @Suppress("DEPRECATION")
        runCatching {
            device.vibrate(
                VibrationEffect.createWaveform(pattern, 0),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
        }
    }

    private fun stopVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun startTorch(context: Context) {
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        val cameraId = runCatching {
            manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull() ?: return

        torchCameraId = cameraId
        val token = Any()
        torchToken = token
        var lit = false

        // The beat lives on the main thread; the stop usually comes from a
        // background one. Without the token check, a beat already queued relights
        // the torch after the stop and it stays on.
        lateinit var beat: Runnable
        beat = Runnable {
            if (torchToken !== token) return@Runnable
            lit = !lit
            runCatching { manager.setTorchMode(cameraId, lit) }
            main.postDelayed(beat, TORCH_PERIOD_MS)
        }
        main.post(beat)
    }

    private fun stopTorch(context: Context) {
        torchToken = null
        val cameraId = torchCameraId ?: return
        torchCameraId = null
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        main.post { runCatching { manager.setTorchMode(cameraId, false) } }
    }

    // --- Utilities ---------------------------------------------------------

    /**
     * A visible activity, not merely a running process. IMPORTANCE_FOREGROUND is
     * the activity threshold; the tracking service sits one notch lower at
     * IMPORTANCE_FOREGROUND_SERVICE, so a background shift does not count as
     * "on screen".
     */
    private fun isOnScreen(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        val mine = manager.runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }
            ?: return false
        mine.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }.getOrDefault(false)

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
