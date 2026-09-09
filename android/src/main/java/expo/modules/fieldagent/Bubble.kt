package expo.modules.fieldagent

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

object Overlay {
    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
}

/**
 * The floating bubble.
 *
 * It cannot draw over the lock screen — no overlay window can, on any Android
 * version. On a locked screen the surface is the full-screen notification; the
 * bubble comes back on unlock. That is a platform rule, not a shortcut.
 */
object Bubble {

    private val main = Handler(Looper.getMainLooper())

    private var root: LinearLayout? = null
    private var dot: View? = null
    private var label: TextView? = null

    private const val SIZE_DP = 56
    private const val ICON_DP = 24

    /** False when the overlay permission is missing — never a silent no-op. */
    fun show(context: Context): Boolean {
        val app = context.applicationContext
        if (!Overlay.isGranted(app)) return false
        main.post { attach(app) }
        Prefs.of(app).edit().putBoolean(Prefs.BUBBLE_VISIBLE, true).apply()
        return true
    }

    fun hide(context: Context) {
        val app = context.applicationContext
        Prefs.of(app).edit().putBoolean(Prefs.BUBBLE_VISIBLE, false).apply()
        main.post { detach(app) }
    }

    fun setState(context: Context, state: String, text: String?) {
        val app = context.applicationContext
        Prefs.of(app).edit()
            .putString(Prefs.BUBBLE_STATE, state)
            .putString(Prefs.BUBBLE_TEXT, text)
            .apply()
        main.post { paint(app) }
    }

    /** Called when the service comes back up so the bubble survives a restart. */
    fun restoreIfWanted(context: Context) {
        val app = context.applicationContext
        if (!Prefs.of(app).getBoolean(Prefs.BUBBLE_VISIBLE, false)) return
        if (!Overlay.isGranted(app)) return
        main.post { attach(app) }
    }

    private fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun attach(context: Context) {
        if (root != null) {
            paint(context)
            return
        }
        val windows = context.getSystemService(WindowManager::class.java) ?: return
        val config = Config.get(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // 48 dp is the accessibility floor for a touch target; 56 dp keeps
            // the label readable next to the dot.
            minimumHeight = dp(context, SIZE_DP)
            minimumWidth = dp(context, SIZE_DP)
            setPadding(dp(context, 8), dp(context, 8), dp(context, 12), dp(context, 8))
            contentDescription = context.getString(R.string.field_agent_bubble_description, config.bubble.label)
            isFocusable = true
        }

        val indicator = if (config.bubble.icon != null) {
            ImageView(context).apply {
                val resource = context.resources.getIdentifier(config.bubble.icon, "drawable", context.packageName)
                if (resource != 0) setImageResource(resource)
                layoutParams = LinearLayout.LayoutParams(dp(context, ICON_DP), dp(context, ICON_DP))
            }
        } else {
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(context, 16), dp(context, 16))
            }
        }

        val text = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(context, 8), 0, 0, 0)
            text = config.bubble.label
        }

        container.addView(indicator)
        container.addView(text)

        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // Not focusable: the bubble must never steal keyboard input from the
            // app underneath it.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Prefs.of(context).getInt(Prefs.BUBBLE_X, 0)
            y = Prefs.of(context).getInt(Prefs.BUBBLE_Y, dp(context, 160))
        }

        container.setOnTouchListener(dragListener(context, windows, layout))

        runCatching { windows.addView(container, layout) }
            .onFailure {
                Bus.error("BUBBLE", it.message ?: "addView a echoue")
                return
            }

        root = container
        dot = indicator
        label = text
        paint(context)
    }

    private fun dragListener(
        context: Context,
        windows: WindowManager,
        layout: WindowManager.LayoutParams
    ): View.OnTouchListener {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        return View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layout.x
                    startY = layout.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    layout.x = startX + dx
                    layout.y = startY + dy
                    runCatching { windows.updateViewLayout(view, layout) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) snapToEdge(context, windows, view, layout) else {
                        view.performClick()
                        Bus.emit("bubblePress")
                        openApp(context)
                    }
                    true
                }

                // A cancel is the system taking the gesture away — a dialog
                // opening over the overlay, a window focus change. Treating it
                // like a tap fires phantom bubblePress events; only the position
                // is worth keeping.
                MotionEvent.ACTION_CANCEL -> {
                    if (moved) snapToEdge(context, windows, view, layout)
                    true
                }

                else -> false
            }
        }
    }

    /**
     * A chat head that does not bring the app up is a decoration. The tap is
     * usually made from another app, where JS is not running and could not do
     * this itself; SYSTEM_ALERT_WINDOW — already required for the bubble to
     * exist — is what makes the background activity launch legal.
     */
    private fun openApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { context.startActivity(intent) }
            .onFailure { Bus.error("BUBBLE", it.message ?: "impossible d'ouvrir l'application") }
    }

    private fun snapToEdge(
        context: Context,
        windows: WindowManager,
        view: View,
        layout: WindowManager.LayoutParams
    ) {
        val metrics = context.resources.displayMetrics
        val width = if (view.width > 0) view.width else dp(context, SIZE_DP)
        val height = if (view.height > 0) view.height else dp(context, SIZE_DP)

        layout.x = if (layout.x + width / 2 < metrics.widthPixels / 2) 0 else metrics.widthPixels - width
        layout.y = layout.y.coerceIn(0, (metrics.heightPixels - height).coerceAtLeast(0))
        runCatching { windows.updateViewLayout(view, layout) }

        Prefs.of(context).edit()
            .putInt(Prefs.BUBBLE_X, layout.x)
            .putInt(Prefs.BUBBLE_Y, layout.y)
            .apply()
    }

    private fun paint(context: Context) {
        val container = root ?: return
        val config = Config.get(context)
        val preferences = Prefs.of(context)
        val state = preferences.getString(Prefs.BUBBLE_STATE, "ok") ?: "ok"
        val text = preferences.getString(Prefs.BUBBLE_TEXT, null)

        val color = when (state) {
            "warn" -> config.bubble.warn
            "bad" -> config.bubble.bad
            "urgent" -> config.bubble.urgent
            else -> config.bubble.ok
        }

        container.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, SIZE_DP / 2).toFloat()
            setColor(color)
        }
        dot?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        label?.text = text ?: config.bubble.label
        container.contentDescription =
            context.getString(R.string.field_agent_bubble_description, text ?: config.bubble.label)
    }

    private fun detach(context: Context) {
        val container = root ?: return
        val windows = context.getSystemService(WindowManager::class.java)
        runCatching { windows?.removeView(container) }
        // Listeners removed with the view; keeping the references would pin an
        // Activity-free context for the life of the process.
        root = null
        dot = null
        label = null
    }
}
