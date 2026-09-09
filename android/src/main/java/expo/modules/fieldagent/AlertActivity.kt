package expo.modules.fieldagent

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.view.WindowCompat
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactActivityDelegate

/**
 * The full-screen alert.
 *
 * It hosts the app's own root component — the one `registerRootComponent` and
 * expo-router register as "main" — so the host's `<AlertHost>` mounts exactly as
 * it does in the normal app, and reads the alert synchronously through
 * `getPendingAlertSync()` at its first render. No second component to register,
 * no event racing the bundle load.
 *
 * showWhenLocked/turnScreenOn are declared on this activity and set again here,
 * because the manifest flags alone are ignored on a few skins.
 */
class AlertActivity : ReactActivity() {

    /**
     * Nothing here may touch instance state: ReactActivity's *constructor* calls
     * createReactActivityDelegate(), which runs before this subclass's own field
     * initializers — a `by lazy` here crashed with a null delegate. The real
     * component name is resolved later, from inside the delegate, when the
     * activity has a context to read the configuration from.
     */
    override fun createReactActivityDelegate(): ReactActivityDelegate =
        object : DefaultReactActivityDelegate(
            this,
            DEFAULT_ROOT_COMPONENT,
            DefaultNewArchitectureEntryPoint.fabricEnabled
        ) {
            override fun getMainComponentName(): String = resolveRootComponent(context)
        }

    override fun getMainComponentName(): String = resolveRootComponent(this)

    /**
     * One React tree at a time. If the user opens the app normally while this
     * screen is up, two surfaces of the same root would mount at once and the
     * navigation container would fight itself.
     */
    private val exclusivity = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity !== this@AlertActivity && activity !is AlertActivity) finish()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Android 15 forces edge-to-edge on anything targeting API 35. The JS
        // side handles the insets; the window just has to stop pretending.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)
        current = this
        application.registerActivityLifecycleCallbacks(exclusivity)

        // A tap on a stale notification, or a TTL that expired while the screen
        // was starting: nothing to show, so do not show an empty app.
        if (Alerts.peek(this) == null) finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (Alerts.peek(this) == null) finish()
    }

    override fun onDestroy() {
        runCatching { application.unregisterActivityLifecycleCallbacks(exclusivity) }
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var current: AlertActivity? = null

        /** What `registerRootComponent` and expo-router both register. */
        private const val DEFAULT_ROOT_COMPONENT = "main"

        private fun resolveRootComponent(context: Context?): String = runCatching {
            context?.let { Config.get(it).rootComponent }
        }.getOrNull() ?: DEFAULT_ROOT_COMPONENT

        const val EXTRA_ALERT_ID = "expo.modules.fieldagent.ALERT_ID"

        fun intent(context: Context, alertId: String): Intent =
            Intent(context, AlertActivity::class.java)
                .putExtra(EXTRA_ALERT_ID, alertId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        fun finishIfShowing() {
            current?.let { activity -> activity.runOnUiThread { activity.finish() } }
        }
    }
}
