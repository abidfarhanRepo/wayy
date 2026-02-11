package com.wayy.ar

import android.content.Context
import com.google.ar.core.ArCoreApk

class ARCapabilityChecker(private val context: Context) {

    fun check(): ARCapabilityStatus {
        val huaweiAvailable = context.packageManager
            .getInstalledPackages(0)
            .any { it.packageName == HUAWEI_AR_PACKAGE }
        if (huaweiAvailable) {
            return ARCapabilityStatus(ARCapability.HUAWEI_AR, "Huawei AR Engine available")
        }

        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability.isSupported) {
            return ARCapabilityStatus(ARCapability.ARCORE, "ARCore supported")
        }
        if (availability.isTransient) {
            return ARCapabilityStatus(ARCapability.CAMERA_FALLBACK, "ARCore availability pending")
        }
        return ARCapabilityStatus(ARCapability.CAMERA_FALLBACK, "ARCore unsupported; using camera fallback")
    }

    companion object {
        private const val HUAWEI_AR_PACKAGE = "com.huawei.arengine.service"
    }
}
