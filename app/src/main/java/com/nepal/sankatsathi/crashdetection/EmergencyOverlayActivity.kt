package com.nepal.sankatsathi.crashdetection

import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nepal.sankatsathi.ui.theme.SankatSathiTheme
import kotlinx.coroutines.delay

class EmergencyOverlayActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    companion object {
        const val ACTION_SOS_CONFIRM = "com.nepal.sankatsathi.ACTION_SOS_CONFIRM"
        const val ACTION_SOS_CANCEL = "com.nepal.sankatsathi.ACTION_SOS_CANCEL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Sound and Vibration
        setupAlerts()
        startAlerts()
        
        // Lock screen visibility configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val sharedPrefs = getSharedPreferences("SankatSathi_Prefs", Context.MODE_PRIVATE)
        val name = sharedPrefs.getString("user_name", "Unknown User") ?: "Unknown User"
        val blood = sharedPrefs.getString("user_blood", "Not Provided") ?: "Not Provided"
        val emergencyPhone = sharedPrefs.getString("emergency_phone", "None") ?: "None"
        val gForce = intent.getFloatExtra("G_FORCE", 0.0f)

        setContent {
            SankatSathiTheme {
                EmergencyUI(
                    name = name,
                    blood = blood,
                    phone = emergencyPhone,
                    gForce = gForce,
                    onDismiss = {
                        stopAlerts()
                        sendBroadcast(Intent(ACTION_SOS_CANCEL))
                        finish()
                    },
                    onTimerFinish = {
                        sendBroadcast(Intent(ACTION_SOS_CONFIRM).apply {
                            putExtra("G_FORCE", gForce)
                        })
                    }
                )
            }
        }
    }

    private fun setupAlerts() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun startAlerts() {
        ringtone?.play()
        
        val timings = longArrayOf(0, 500, 200, 500)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(timings, 0)
        }
    }

    private fun stopAlerts() {
        ringtone?.stop()
        vibrator?.cancel()
    }

    override fun onDestroy() {
        stopAlerts()
        super.onDestroy()
    }
}

@Composable
fun EmergencyUI(
    name: String,
    blood: String,
    phone: String,
    gForce: Float,
    onDismiss: () -> Unit,
    onTimerFinish: () -> Unit
) {
    var timeLeft by remember { mutableIntStateOf(15) }
    var isConfirmed by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = isConfirmed) {
        if (!isConfirmed) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
            isConfirmed = true
            onTimerFinish()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFB71C1C) // Deep Red for Emergency
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Countdown Timer Circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Text(
                    text = if (isConfirmed) "SOS" else timeLeft.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(60.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                if (isConfirmed) "SOS PROTOCOL INITIATED" else "CRASH DETECTED",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Text(
                if (isConfirmed) "Broadcasting your location to authorities..." 
                else "Impact: ${String.format("%.1f", gForce)}G detected",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "EMERGENCY MEDICAL CARD",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    InfoRow("NAME", name)
                    InfoRow("BLOOD TYPE", blood)
                    InfoRow("EMERGENCY CALL", phone)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB71C1C))
            ) {
                Text(
                    if (isConfirmed) "I AM OKAY (CANCEL SOS)" else "I AM OKAY (DISMISS)", 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "Locked for privacy. SOS will be sent automatically.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}
