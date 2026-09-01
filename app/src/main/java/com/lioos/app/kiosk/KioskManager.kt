package com.lioos.app.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build

/**
 * Turns lioOS into a locked-down appliance. When the app is a **device owner**
 * (set once over ADB), kiosk mode:
 *   - pins lioOS with Lock Task (Home / Recents / status bar blocked),
 *   - makes lioOS the persistent default HOME, so boot lands straight here,
 *   - disables the keyguard so it powers on into the app.
 *
 * Without device-owner it degrades to normal screen-pinning (startLockTask),
 * which the user can still exit — the full lockdown needs device-owner.
 */
class KioskManager(private val context: Context) {

    private val dpm =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin: ComponentName = LioDeviceAdminReceiver.componentName(context)

    private val prefs =
        context.getSharedPreferences("kiosk", Context.MODE_PRIVATE)

    val pkg: String get() = context.packageName

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(pkg)

    fun isKioskEnabled(): Boolean = prefs.getBoolean("enabled", false)

    fun setKioskEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
    }

    fun status(): String = when {
        isDeviceOwner() && isKioskEnabled() -> "Device owner · kiosk ON"
        isDeviceOwner() -> "Device owner · kiosk OFF"
        else -> "Not device owner (basic pinning only)"
    }

    /** Configures owner-level policy. Safe to call repeatedly. */
    fun applyOwnerPolicy() {
        if (!isDeviceOwner()) return
        dpm.setLockTaskPackages(admin, arrayOf(pkg))
        if (Build.VERSION.SDK_INT >= 28) {
            dpm.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                    DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
            )
        }
        // Make lioOS the persistent default launcher.
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val activity = ComponentName(pkg, "com.lioos.app.MainActivity")
        dpm.addPersistentPreferredActivity(admin, filter, activity)
    }

    fun clearOwnerPolicy() {
        if (!isDeviceOwner()) return
        dpm.clearPackagePersistentPreferredActivities(admin, pkg)
        dpm.setLockTaskPackages(admin, arrayOf())
    }

    /** Call from the Activity to enter the pinned/locked state. */
    fun enterLockTask(activity: Activity) {
        if (isDeviceOwner()) {
            applyOwnerPolicy()
            try { dpm.setKeyguardDisabled(admin, true) } catch (_: Exception) {}
            try { dpm.setStatusBarDisabled(admin, true) } catch (_: Exception) {}
        }
        try { activity.startLockTask() } catch (_: Exception) {}
    }

    fun exitLockTask(activity: Activity) {
        try { activity.stopLockTask() } catch (_: Exception) {}
        if (isDeviceOwner()) {
            try { dpm.setKeyguardDisabled(admin, false) } catch (_: Exception) {}
            try { dpm.setStatusBarDisabled(admin, false) } catch (_: Exception) {}
        }
    }

    /** ADB command the user runs once to grant device-owner. */
    fun deviceOwnerCommand(): String =
        "adb shell dpm set-device-owner " +
            "com.lioos.app/com.lioos.app.kiosk.LioDeviceAdminReceiver"
}
