package com.wayy.ar

import android.content.Context
import com.google.ar.core.ArCoreApk

class ARCapabilityChecker(private val context: Context) {

    fun check(): ARCapabilityStatus {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability.isSupported) {
            return ARCapabilityStatus(ARCapability.ARCORE, "ARCore supported")
        }
        if (availability.isTransient) {
            return ARCapabilityStatus(ARCapability.CAMERA_FALLBACK, "ARCore availability pending")
        }
        return ARCapabilityStatus(ARCapability.CAMERA_FALLBACK, "ARCore unsupported; using camera fallback")
    }
}
