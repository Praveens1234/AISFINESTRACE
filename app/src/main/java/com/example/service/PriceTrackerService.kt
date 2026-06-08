package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.example.data.repository.PriceMonitorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PriceTrackerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var monitorManager: PriceMonitorManager

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        monitorManager = PriceMonitorManager.getInstance(this)

        // Request a partial wake lock to keep cpu active when screen goes off
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fintrace:CpuWakeLock").apply {
            acquire(3600*1000L) // limit to 1 hr safe duration, re-acquired on ticks
        }

        Log.d("PriceTrackerService", "Foreground service created successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground with FGS Notification (ID 1001)
        val activeSymbols = monitorManager.activeSymbols.value
        val notification = NotificationHelper.buildFgsNotification(this, activeSymbols)
        startForeground(NotificationHelper.FGS_NOTIFICATION_ID, notification)

        scope.launch {
            monitorManager.activeSymbols.collect { active ->
                val fgsNotif = NotificationHelper.buildFgsNotification(this@PriceTrackerService, active)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(NotificationHelper.FGS_NOTIFICATION_ID, fgsNotif)
            }
        }

        monitorManager.startMonitoringLoop()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        monitorManager.stopMonitoring()
        scope.cancel()
        Log.d("PriceTrackerService", "Foreground service destroyed")
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, PriceTrackerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PriceTrackerService::class.java)
            context.stopService(intent)
        }
    }
}
