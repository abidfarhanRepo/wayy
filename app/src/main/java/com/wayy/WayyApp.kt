package com.wayy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.wayy.map.MapLibreManager
import com.wayy.map.TileCacheManager

class WayyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        MapLibreManager(this).initialize()

        TileCacheManager(this).initialize()

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Navigation Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing navigation notifications"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "navigation_channel"
    }
}
