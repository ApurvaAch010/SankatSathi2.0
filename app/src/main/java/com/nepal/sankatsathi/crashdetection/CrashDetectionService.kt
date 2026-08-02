package com.nepal.sankatsathi.crashdetection

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nepal.sankatsathi.MainActivity
import com.nepal.sankatsathi.R
import kotlin.math.sqrt

class CrashDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastTriggerTime: Long = 0
    private val COOLDOWN_MS = 10000L // 10 seconds
    private val G_THRESHOLD = 4.0f

    private val CHANNEL_ID = "CrashDetectionChannel"
    private val NOTIFICATION_ID = 101
    private val EMERGENCY_NOTIFICATION_ID = 102

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createSilentNotification())

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val accelerationSum = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val gForce = accelerationSum / SensorManager.GRAVITY_EARTH

            if (gForce > G_THRESHOLD) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTriggerTime > COOLDOWN_MS) {
                    lastTriggerTime = currentTime
                    triggerEmergencyOverlay(gForce)
                }
            }
        }
    }

    private fun triggerEmergencyOverlay(gForce: Float) {
        Log.d("CrashDetection", "Impact Detected: $gForce G")
        
        val intent = Intent(this, EmergencyOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("G_FORCE", gForce)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (!notificationManager.canUseFullScreenIntent()) {
                // Should handle permission request in UI, but we trigger standard notification as fallback
                Log.w("CrashDetection", "Full screen intent permission missing")
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Using system icon for simplicity
            .setContentTitle("🚨 CRASH DETECTED")
            .setContentText("Heavy impact detected: ${String.format("%.1f", gForce)}G")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)

        notificationManager.notify(EMERGENCY_NOTIFICATION_ID, builder.build())
        
        // Directly start activity as well to ensure wake-up
        startActivity(intent)
    }

    private fun createSilentNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SankatSathi Protection Active")
            .setContentText("Monitoring for emergency impacts in background.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Crash Detection Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitors sensors for heavy impacts"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }
}
