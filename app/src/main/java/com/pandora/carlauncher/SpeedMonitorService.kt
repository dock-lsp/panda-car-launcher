package com.pandora.carlauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 车速监测服务
 * 通过GPS获取实时车速，超过阈值时发送广播通知
 */
class SpeedMonitorService : Service() {

    companion object {
        private const val TAG = "SpeedMonitor"
        private const val CHANNEL_ID = "speed_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_SPEED_UPDATE = "com.pandora.carlauncher.ACTION_SPEED_UPDATE"
        const val EXTRA_SPEED = "extra_speed"
    }

    private var locationManager: LocationManager? = null
    private var currentSpeed = 0f

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentSpeed = location.speed * 3.6f // m/s -> km/h
            // 发送广播
            val intent = Intent(ACTION_SPEED_UPDATE).apply {
                putExtra(EXTRA_SPEED, currentSpeed)
            }
            sendBroadcast(intent)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startLocationMonitoring()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationMonitoring()
    }

    private fun startLocationMonitoring() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,  // 最小更新间隔1秒
                    0f,     // 最小位移0米
                    locationListener,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "GPS定位已启动")
            } else {
                // 尝试使用网络定位
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "网络定位已启动")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "定位权限不足", e)
        } catch (e: Exception) {
            Log.e(TAG, "定位启动失败", e)
        }
    }

    private fun stopLocationMonitoring() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e(TAG, "停止定位失败", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "车速监测",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "后台监测车速，保障行车安全"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("车速监测中")
            .setContentText("保障行车安全")
            .setSmallIcon(R.drawable.ic_navigation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
