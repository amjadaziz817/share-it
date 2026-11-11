package com.shareit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.shareit.server.Server

// Keeps the app alive while the server runs, so copies continue when the screen locks or another app opens.
class ServerService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLocks = emptyList<WifiManager.WifiLock>()

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Sharing", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, ServerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("share-it is on")
            .setContentText(intent?.getStringExtra(EXTRA_URL))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        if (wakeLock == null) {
            // Without the wake lock, the CPU sleeps when the screen is off and the copy stops.
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "share-it:server").apply { acquire() }
            // LOW_LATENCY works only while the screen is on; HIGH_PERF keeps Wi-Fi fast when the screen is off on Android 13 and older.
            val wifi = getSystemService(WifiManager::class.java)
            @Suppress("DEPRECATION")
            wifiLocks = listOf(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, WifiManager.WIFI_MODE_FULL_HIGH_PERF)
                .map { wifi.createWifiLock(it, "share-it").apply { acquire() } }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Server.stop()
        url = null
        pass = null
        wakeLock?.release()
        wifiLocks.forEach { it.release() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        private const val ACTION_STOP = "stop"
        private const val CHANNEL = "sharing"

        // Set while the server runs; null when it is stopped.
        var url: String? = null
        var pass: String? = null
    }
}
