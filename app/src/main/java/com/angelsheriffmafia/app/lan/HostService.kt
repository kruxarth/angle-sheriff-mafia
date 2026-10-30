package com.angelsheriffmafia.app.lan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.angelsheriffmafia.app.MainActivity
import com.angelsheriffmafia.app.R

/**
 * Keeps the app running while it hosts a table, with a notification saying so. It also keeps
 * the Wi-Fi awake and the processor running, so players' phones still reach the table when
 * the host's screen goes dark.
 */
class HostService : Service() {
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val table = intent?.getStringExtra(EXTRA_TABLE) ?: "your table"
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Hosting a table", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while friends can join this phone's table."
            },
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hosting $table")
            .setContentText("Friends can join over your Wi-Fi or hotspot.")
            .setContentIntent(open)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        holdLocks()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
        wakeLock = null
        super.onDestroy()
    }

    private fun holdLocks() {
        if (wifiLock == null) {
            val wifi = applicationContext.getSystemService(WifiManager::class.java)
            // High-performance mode keeps working with the screen off. From Android 14 the phone
            // gives a low-latency lock instead, which only helps while the screen is on.
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifi?.createWifiLock(mode, "AngelSheriffMafia:table")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wakeLock == null) {
            val power = getSystemService(PowerManager::class.java)
            wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AngelSheriffMafia:table")?.apply {
                setReferenceCounted(false)
                // A long evening of games at most. Closing the table lets go sooner.
                acquire(MAX_HOSTING_MILLIS)
            }
        }
    }

    companion object {
        private const val CHANNEL = "hosting"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_TABLE = "table"
        private const val MAX_HOSTING_MILLIS = 6 * 60 * 60 * 1000L

        fun start(context: Context, table: String) {
            ContextCompat.startForegroundService(context, Intent(context, HostService::class.java).putExtra(EXTRA_TABLE, table))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HostService::class.java))
        }
    }
}
