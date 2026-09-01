package com.lioos.app.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Device-admin / device-owner receiver for lioOS kiosk mode. */
class LioDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, LioDeviceAdminReceiver::class.java)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
    }
}
