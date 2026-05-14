package com.dalong.mocklocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MockLocationService : Service() {

    companion object {
        private const val CHANNEL_ID = "mock_location_channel"
        private const val NOTIFICATION_ID = 1
    }

    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun updateMockLocation(lat: Double, lng: Double) {
        try {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            val location = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = lat
                longitude = lng
                accuracy = 5f
                time = System.currentTimeMillis()
                bearing = 0f
                speed = 0f
            }
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "虚拟定位服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🐉 大龙虚拟定位")
            .setContentText("运行中...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
