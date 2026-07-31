package com.nepal.sankatsathi

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.nepal.sankatsathi.ui.theme.SankatSathiTheme
import java.util.Properties
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var vibrator: Vibrator? = null
    private lateinit var bleMeshManager: IBleMeshManager

    private var currentGForce = mutableStateOf(1.0f)
    private var maxGForceTracked = mutableStateOf(1.0f)
    private var isCountdownActive = mutableStateOf(false)
    private var secondsRemaining = mutableStateOf(15)
    private var isSOSSent = mutableStateOf(false)
    private var severityLevel = mutableStateOf(1)

    private val WARNING_THRESHOLD = 4.5f
    private val SEVERE_THRESHOLD = 15.0f

    private var countdownTimer: CountDownTimer? = null
    private var ringtoneAlarm: Ringtone? = null
    private val relayedPackets = mutableSetOf<String>()

    private val EMERGENCY_CONTACT_SMS = "9840035922"
    private val EMERGENCY_CONTACT_EMAIL = "adhirajmaharjan@gmail.com"

    // BACKEND DISPATCHER SETTINGS
    private val CONFIG_SENDER_EMAIL = "adhirajmahzs56@gmail.com"
    private val CONFIG_SENDER_PASSWORD = "ybcx muix gsct gmvb" // Change this to your 16-character security key

    private val CHANNEL_ID = "ICE_MEDICAL_CARD_CHANNEL"

    private val bluetoothSignals = mutableStateMapOf<String, Pair<Int, Boolean>>() // Address -> (RSSI, isSankatSathi)

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Bluetooth Enabled Successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth is required for Mesh SOS", Toast.LENGTH_LONG).show()
        }
    }

    private val respondedSenders = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sharedPreferences = getSharedPreferences("SankatSathi_Prefs", Context.MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        bleMeshManager = BleMeshManager(this) { relayedPayload ->
            relayMeshSOS(relayedPayload)
        }

        lifecycleScope.launch {
            bleMeshManager.scanResults.collectLatest { result ->
                val deviceName = result.device.name ?: "Unknown Device"
                val deviceAddress = result.device.address
                val rssi = result.rssi
                
                // Check if this device belongs to our Mesh
                val isSankatSathi = result.scanRecord?.serviceData?.any { (uuid, _) ->
                    uuid.toString().contains("feaa", ignoreCase = true)
                } ?: false
                
                if (isSankatSathi) {
                    bluetoothSignals["$deviceName ($deviceAddress)"] = Pair(rssi, true)
                    
                    // Auto-response feature: Connect and send location to the sender
                    if (!respondedSenders.contains(deviceAddress)) {
                        respondedSenders.add(deviceAddress)
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                val locStr = if (location != null) {
                                    "${location.latitude},${location.longitude}"
                                } else "27.7172,85.3240" // Fallback
                                
                                Log.d("SankatSathi", "Auto-responding to SOS from $deviceAddress with location $locStr")
                                bleMeshManager.connectAndSendLocation(result.device, locStr)
                            }
                        } catch (e: SecurityException) {
                            Log.e("SankatSathi", "Location permission missing for auto-response")
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            bleMeshManager.broadcastStatus.collectLatest { (success, message) ->
                // Keep alerts receiver-only for victim, but log the exact signal sent
                runOnUiThread {
                    if (success) {
                        Log.d("SankatSathi", "SOS Signal Sent: $message")
                        Toast.makeText(this@MainActivity, "📤 SIGNAL SENT: $message", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtoneAlarm = RingtoneManager.getRingtone(applicationContext, alarmUri)

        createNotificationChannel()
        showLockScreenNotification()

        setContent {
            SankatSathiTheme {
                val permissionsToRequest = mutableListOf(
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.VIBRATE
                ).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add(Manifest.permission.BLUETOOTH_SCAN)
                        add(Manifest.permission.BLUETOOTH_ADVERTISE)
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }.toTypedArray()

                var permissionsGranted by remember { mutableStateOf(hasPermissions(permissionsToRequest)) }
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { results -> 
                    permissionsGranted = results.values.all { it }
                    if (permissionsGranted) {
                        checkAndEnableBluetooth()
                    }
                }

                LaunchedEffect(Unit) {
                    if (!permissionsGranted) {
                        launcher.launch(permissionsToRequest)
                    } else {
                        checkAndEnableBluetooth()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (permissionsGranted) {
                        MainAppNavigation(
                            padding = innerPadding,
                            currentG = currentGForce.value,
                            maxG = maxGForceTracked.value,
                            isCountingDown = isCountdownActive.value,
                            secondsLeft = secondsRemaining.value,
                            sosTriggered = isSOSSent.value,
                            severity = severityLevel.value,
                            threshold = WARNING_THRESHOLD,
                            sharedPrefs = sharedPreferences,
                            bluetoothSignals = bluetoothSignals,
                            onCancel = { cancelEmergencyProtocol() },
                            onProfileSaved = { showLockScreenNotification() }
                        )
                    } else {
                        PermissionRequiredScreen(innerPadding) { launcher.launch(permissionsToRequest) }
                    }
                }
            }
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun checkAndEnableBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter != null && !adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            try {
                bluetoothEnableLauncher.launch(enableBtIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Bluetooth enable request failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        if (isCountdownActive.value || isSOSSent.value) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val accelerationSum = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val gForce = accelerationSum / SensorManager.GRAVITY_EARTH
        currentGForce.value = gForce

        if (gForce > maxGForceTracked.value) maxGForceTracked.value = gForce

        if (gForce >= SEVERE_THRESHOLD) {
            severityLevel.value = 2
            isSOSSent.value = true
            executeSOSProtocol()
        } else if (gForce >= WARNING_THRESHOLD) {
            severityLevel.value = 1
            startEmergencyCountdown()
        }
    }

    private fun startEmergencyCountdown() {
        isCountdownActive.value = true
        secondsRemaining.value = 15

        try { ringtoneAlarm?.play() } catch (e: Exception) { e.printStackTrace() }
        startVibrationAlert()

        countdownTimer = object : CountDownTimer(15000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsRemaining.value = (millisUntilFinished / 1000).toInt()
                if (secondsRemaining.value % 2 == 0) startVibrationAlert()
            }
            override fun onFinish() {
                isCountdownActive.value = false
                isSOSSent.value = true
                stopAlertSignals()
                executeSOSProtocol()
            }
        }.start()
    }

    private fun startVibrationAlert() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 600, 150, 600)
            val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1), attributes)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(1500)
        }
    }

    private fun stopAlertSignals() {
        ringtoneAlarm?.stop()
        vibrator?.cancel()
    }

    private fun relayMeshSOS(payload: String) {
        // If we pick up our own name, ignore
        val myName = sharedPreferences.getString("user_name", "User") ?: "User"
        if (payload.contains(myName.take(8))) return

        // Avoid duplicate relaying of the same packet
        if (relayedPackets.contains(payload)) return
        relayedPackets.add(payload)
        if (relayedPackets.size > 50) relayedPackets.clear() // Prevent memory leak

        runOnUiThread {
            Toast.makeText(this, "📩 SOS SIGNAL RECEIVED: $payload", Toast.LENGTH_LONG).show()
        }
        dispatchAlerts("", relayedPayload = "🚨 MESH RELAY SOS 🚨\n$payload\nRelayed by: $myName")
    }

    private fun cancelEmergencyProtocol() {
        countdownTimer?.cancel()
        stopAlertSignals()
        bleMeshManager.stopBroadcasting()
        isCountdownActive.value = false
        isSOSSent.value = false
        secondsRemaining.value = 15
        maxGForceTracked.value = 1.0f
        severityLevel.value = 1
        Toast.makeText(this, "Emergency Protocol Cancelled.", Toast.LENGTH_SHORT).show()
    }

    private fun executeSOSProtocol() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            dispatchAlerts("Unknown Location (Permissions Denied)")
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            val locationString = if (location != null) {
                "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            } else {
                "https://maps.google.com/?q=27.7172,85.3240 (Kathmandu Fallback Center)"
            }
            dispatchAlerts(locationString)
        }.addOnFailureListener {
            dispatchAlerts("Location Error (GPS Signal Unstable)")
        }

        // Activate BLE Mesh Broadcasting
        val deviceName = sharedPreferences.getString("user_name", "User") ?: "User"
        
        val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }

        if (hasPermissions(bluetoothPermissions)) {
            checkAndEnableBluetooth()
            bleMeshManager.startBroadcasting(deviceName)
        }
    }

    private fun dispatchAlerts(mapUrl: String, relayedPayload: String? = null) {
        val blood = sharedPreferences.getString("user_blood", "Not Provided")
        val name = sharedPreferences.getString("user_name", "Unregistered User")
        val modeText = if (severityLevel.value == 2) "🚨 EMERGENCY: CRASH DETECTED 🚨" else "⚠️ CRASH SUSPECTED ⚠️"

        val fullPayload = relayedPayload ?: "$modeText\nVictim: $name\nBlood: $blood\nLoc: $mapUrl"
        val emailSubject = if (relayedPayload != null) "🚨 RELAYED EMERGENCY ALERT 🚨" else "CRITICAL CRASH DISPATCH: $name"

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread {
                Toast.makeText(this, "SMS Permission Denied.", Toast.LENGTH_LONG).show()
            }
        } else {
            try {
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    this.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                val parts = smsManager.divideMessage(fullPayload)
                // Use a simpler send logic to avoid multi-part issues if possible, 
                // but keep multi-part as fallback for long messages.
                if (parts.size == 1) {
                    smsManager.sendTextMessage(EMERGENCY_CONTACT_SMS, null, fullPayload, null, null)
                } else {
                    smsManager.sendMultipartTextMessage(EMERGENCY_CONTACT_SMS, null, parts, null, null)
                }
                
                runOnUiThread {
                    Toast.makeText(this, "SOS SMS Handed to System.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "SMS System Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            Thread {
                try {
                    if (CONFIG_SENDER_PASSWORD == "YOUR_GOOGLE_APP_PASSWORD_HERE") {
                        runOnUiThread {
                            Toast.makeText(this, "SMTP Error: Please set your Google App Password in the code.", Toast.LENGTH_LONG).show()
                        }
                        return@Thread
                    }

                    val properties = Properties().apply {
                        put("mail.smtp.host", "smtp.gmail.com")
                        put("mail.smtp.socketFactory.port", "465")
                        put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                        put("mail.smtp.auth", "true")
                        put("mail.smtp.port", "465")
                        put("mail.smtp.timeout", "10000")
                        put("mail.smtp.connectiontimeout", "10000")
                    }

                    val session = Session.getInstance(properties, object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication {
                            return PasswordAuthentication(CONFIG_SENDER_EMAIL, CONFIG_SENDER_PASSWORD)
                        }
                    })

                    val message = MimeMessage(session).apply {
                        setFrom(InternetAddress(CONFIG_SENDER_EMAIL, "SankatSathi Engine"))
                        addRecipient(Message.RecipientType.TO, InternetAddress(EMERGENCY_CONTACT_EMAIL))
                        subject = emailSubject
                        setText(fullPayload + "\n\nSent automatically via SankatSathi System.")
                    }

                    Transport.send(message)

                    runOnUiThread {
                        Toast.makeText(this, "Background SOS Email Sent!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this, "Email Failed: Check Internet or App Password", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }, 3000)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Emergency ICE Medical Profile"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showLockScreenNotification() {
        val name = sharedPreferences.getString("user_name", "Not Set")
        val blood = sharedPreferences.getString("user_blood", "Not Set")
        val allergies = sharedPreferences.getString("user_allergies", "None")

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("🔴 ICE: Emergency Medical Profile ($name)")
            .setContentText("🩸 BLOOD: $blood | ⚠️ ALLERGIES: $allergies")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, builder.build())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onResume() { 
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } 
        
        val scanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (ContextCompat.checkSelfPermission(this, scanPermission) == PackageManager.PERMISSION_GRANTED) {
            bleMeshManager.startScanning()
        }
    }
    override fun onPause() { 
        super.onPause()
        if (isCountdownActive.value) cancelEmergencyProtocol()
        sensorManager.unregisterListener(this) 
        bleMeshManager.stopScanning()
    }
}

@Composable
fun MainAppNavigation(
    padding: PaddingValues,
    currentG: Float,
    maxG: Float,
    isCountingDown: Boolean,
    secondsLeft: Int,
    sosTriggered: Boolean,
    severity: Int,
    threshold: Float,
    sharedPrefs: SharedPreferences,
    bluetoothSignals: Map<String, Pair<Int, Boolean>>,
    onCancel: () -> Unit,
    onProfileSaved: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    if (isCountingDown || sosTriggered) {
        CrashDetectorScreen(padding, currentG, maxG, isCountingDown, secondsLeft, sosTriggered, severity, threshold, onCancel)
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Sensor") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Mesh Signals") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("ICE Profile") })
            }
            when (selectedTab) {
                0 -> CrashDetectorScreen(PaddingValues(0.dp), currentG, maxG, isCountingDown, secondsLeft, sosTriggered, severity, threshold, onCancel)
                1 -> MeshSignalScreen(bluetoothSignals)
                2 -> IceProfileScreen(sharedPrefs, onProfileSaved)
            }
        }
    }
}

@Composable
fun MeshSignalScreen(signals: Map<String, Pair<Int, Boolean>>) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nearby Bluetooth Nodes", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            // Pulse indicator to show scanning is active
            Box(modifier = Modifier.size(12.dp).background(Color(0xFF00C853), shape = CircleShape))
        }
        Text("Real-time signal strength (RSSI)", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (signals.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Searching for nearby SOS signals...", textAlign = TextAlign.Center)
                    Text("Ensure other phones are broadcasting SOS.", fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                signals.forEach { (device, data) ->
                    val (rssi, isSankatSathi) = data
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSankatSathi) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSankatSathi) "🛡️ $device" else device,
                                    fontWeight = if (isSankatSathi) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSankatSathi) Color(0xFF1976D2) else Color.Unspecified
                                )
                                if (isSankatSathi) {
                                    Text("SankatSathi Mesh Active", fontSize = 10.sp, color = Color(0xFF1976D2))
                                }
                            }
                            Text(
                                "${rssi} dBm",
                                fontWeight = FontWeight.Bold,
                                color = getSignalColor(rssi)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getSignalColor(rssi: Int): Color {
    return when {
        rssi > -60 -> Color(0xFF00C853) // Green
        rssi > -80 -> Color(0xFFFFAB00) // Orange
        else -> Color.Red
    }
}

@Composable
fun IceProfileScreen(sharedPrefs: SharedPreferences, onProfileSaved: () -> Unit) {
    var name by remember { mutableStateOf(sharedPrefs.getString("user_name", "") ?: "") }
    var bloodGroup by remember { mutableStateOf(sharedPrefs.getString("user_blood", "") ?: "") }
    var allergies by remember { mutableStateOf(sharedPrefs.getString("user_allergies", "") ?: "") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Offline Medical Identity Card", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = bloodGroup, onValueChange = { bloodGroup = it }, label = { Text("Blood Group") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = allergies, onValueChange = { allergies = it }, label = { Text("Critical Allergies / Conditions") }, modifier = Modifier.fillMaxWidth())

        Button(onClick = {
            sharedPrefs.edit().apply {
                putString("user_name", name)
                putString("user_blood", bloodGroup)
                putString("user_allergies", allergies)
                apply()
            }
            onProfileSaved()
        }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Save Profile & Pin Alert UI")
        }
    }
}

@Composable
fun PermissionRequiredScreen(padding: PaddingValues, onRequest: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("System Permissions Required", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Grant Permissions") }
    }
}

@Composable
fun CrashDetectorScreen(
    padding: PaddingValues,
    currentG: Float,
    maxG: Float,
    isCountingDown: Boolean,
    secondsLeft: Int,
    sosTriggered: Boolean,
    severity: Int,
    threshold: Float,
    onCancel: () -> Unit
) {
    val backgroundColor = when {
        sosTriggered && severity == 2 -> Color(0xFF3E2723)
        sosTriggered -> Color(0xFFB71C1C)
        isCountingDown -> Color(0xFFFF9800)
        else -> Color.White
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding).background(backgroundColor).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        Text(text = "SankatSathi Intelligent Crisis Engine", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (isCountingDown || sosTriggered) Color.White else Color.DarkGray, textAlign = TextAlign.Center)

        if (!isCountingDown && !sosTriggered) {
            Box(modifier = Modifier.size(180.dp).background(Color(0xFF00C853), shape = CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = String.format("%.2f G", currentG), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Text(text = "Live Force", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                }
            }
            Text(text = String.format("Peak Force Tracked: %.2f G", maxG), fontSize = 18.sp, color = Color.Black)
        }

        if (isCountingDown) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = "CRASH DETECTED (STAGE 1)", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(text = "$secondsLeft", color = Color.White, fontSize = 80.sp, fontWeight = FontWeight.Black)
                Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                    Text("I AM OKAY (Cancel)", color = Color.Black)
                }
            }
        }

        if (sosTriggered) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = if (severity == 2) "🚨 INSTANT DIRECT SOS SENT (>=15G SHOCK) 🚨" else "🚨 EMERGENCY PROTOCOL DISPATCHED 🚨",
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center
                )
                Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                    Text("Reset System", color = Color.Red)
                }
            }
        }
    }
}