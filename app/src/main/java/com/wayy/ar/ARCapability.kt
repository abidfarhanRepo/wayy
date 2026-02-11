package com.wayy.ar

enum class ARCapability {
    ARCORE,
    HUAWEI_AR,
    CAMERA_FALLBACK,
    UNSUPPORTED
}

data class ARCapabilityStatus(
    val capability: ARCapability,
    val message: String? = null
)
