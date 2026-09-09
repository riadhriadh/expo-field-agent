package expo.modules.fieldagent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * The real cause of dead tracking.
 *
 * A tracker that stops is almost never a GPS that stopped. It is battery
 * optimisation, the manufacturer's task killer (MIUI, EMUI, ColorOS, FunTouch,
 * One UI — invisible from every standard Android setting) or power saving mode.
 *
 * No API disables any of it, and that is on purpose. All a plugin can do is open
 * the right screen on the right brand in one tap, with a clear instruction, and
 * remember that the user confirmed.
 *
 * Note the absence of REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: the direct dialog is
 * refused by Google Play for most apps, and the refusal pulls the app from the
 * store. The system list costs two more taps and endangers nobody.
 */
object Power {

    fun isUnrestricted(context: Context): Boolean {
        val manager = context.getSystemService(PowerManager::class.java) ?: return false
        return runCatching { manager.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
    }

    fun isConfirmed(context: Context): Boolean =
        Prefs.of(context).getBoolean(Prefs.POWER_CONFIRMED, false)

    fun markConfirmed(context: Context) {
        Prefs.of(context).edit().putBoolean(Prefs.POWER_CONFIRMED, true).apply()
    }

    /** The system allow-list, not the one-tap dialog. Returns the intent to start. */
    fun batterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))

    /**
     * Per-brand candidates: these components move between skin versions, so each
     * brand gets several, tried in order, with the app details page as the
     * last resort. Never assume the first one still exists.
     */
    private val CANDIDATES: Map<String, List<Pair<String, String>>> = mapOf(
        "xiaomi" to listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.miui.securitycenter" to "com.miui.powercenter.PowerSettings"
        ),
        "redmi" to listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        "poco" to listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        "huawei" to listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
        ),
        "honor" to listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity"
        ),
        "oppo" to listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity"
        ),
        "realme" to listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        "oneplus" to listOf(
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
        ),
        "vivo" to listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
        ),
        "iqoo" to listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        ),
        "samsung" to listOf(
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            "com.samsung.android.sm_cn" to "com.samsung.android.sm.ui.battery.BatteryActivity"
        ),
        "asus" to listOf(
            "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity"
        ),
        "letv" to listOf(
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity"
        ),
        "meizu" to listOf(
            "com.meizu.safe" to "com.meizu.safe.security.SHOW_APPSEC"
        ),
        "infinix" to listOf(
            "com.transsion.phonemanager" to "com.itel.autobootmanager.activity.AutoBootMgrActivity"
        ),
        "tecno" to listOf(
            "com.transsion.phonemanager" to "com.itel.autobootmanager.activity.AutoBootMgrActivity"
        ),
        "nokia" to listOf(
            "com.evenwell.powersaving.g3" to "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"
        )
    )

    /** Returns the first intent that actually resolves on this device, or the app page. */
    fun manufacturerIntent(context: Context): Intent {
        val brand = Build.MANUFACTURER.lowercase().trim()
        val candidates = CANDIDATES[brand] ?: CANDIDATES[Build.BRAND.lowercase().trim()] ?: emptyList()

        for ((pkg, activity) in candidates) {
            val intent = Intent().setComponent(ComponentName(pkg, activity))
            if (context.packageManager.resolveActivity(intent, 0) != null) return intent
        }
        return appDetailsIntent(context)
    }

    fun hasManufacturerScreen(context: Context): Boolean {
        val brand = Build.MANUFACTURER.lowercase().trim()
        val candidates = CANDIDATES[brand] ?: CANDIDATES[Build.BRAND.lowercase().trim()] ?: return false
        return candidates.any {
            context.packageManager.resolveActivity(Intent().setComponent(ComponentName(it.first, it.second)), 0) != null
        }
    }
}
