package com.wayy.capture

object CaptureConfig {
    const val MAX_STORAGE_BYTES: Long = 2L * 1024L * 1024L * 1024L
    const val MAX_CLIP_DURATION_MS: Long = 3L * 60L * 1000L
    const val METADATA_EVENT_INTERVAL_MS: Long = 1500L
    const val MIN_STORAGE_BYTES: Long = 100L * 1024L * 1024L
    const val LOW_STORAGE_WARNING_BYTES: Long = 500L * 1024L * 1024L
    const val ESTIMATED_BYTES_PER_SECOND: Long = 8L * 1024L * 1024L
}
