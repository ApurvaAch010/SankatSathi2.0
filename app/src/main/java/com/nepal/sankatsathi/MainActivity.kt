package com.nepal.sankatsathi

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
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
import android.net.Uri
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
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
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
    private var activeMeshType = mutableStateOf("None")

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
    private val NEARBY_SERVICE_ID = "com.nepal.sankatsathi.SOS_MESH"

    private lateinit var connectionsClient: ConnectionsClient
    private val bluetoothSignals = mutableStateMapOf<String, SosDevice>() 

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Bluetooth Enabled Successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth is required for Mesh SOS", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sharedPreferences = getSharedPreferences("SankatSathi_Prefs", Context.MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        connectionsClient = Nearby.getConnectionsClient(this)
        bleMeshManager = BleMeshManager(this) { relayedPayload ->
            relayMeshSOS(relayedPayload)
        }

        lifecycleScope.launch {
            bleMeshManager.detectedSosDevices.collectLatest { sosDevice ->
                runOnUiThread {
                    bluetoothSignals[sosDevice.device.address] = sosDevice
                    Toast.makeText(this@MainActivity, "📍 SOS DEVICE DETECTED: ${sosDevice.name}", Toast.LENGTH_SHORT).show()
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
                            meshType = activeMeshType.value,
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

    private fun startSOSAdvertisingD2D(userName: String, location: String) {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startAdvertising(
            userName,
            NEARBY_SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.d("D2D", "D2D Advertising started successfully")
            updateMeshStatusBadge(isD2DActive = true)
        }.addOnFailureListener {
            Log.e("D2D", "D2D Advertising failed: ${it.message}")
        }
    }

    private fun startSOSDiscoveryD2D() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startDiscovery(
            NEARBY_SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Log.d("D2D", "D2D Discovery started successfully")
        }.addOnFailureListener {
            Log.e("D2D", "D2D Discovery failed: ${it.message}")
        }
    }

    private fun updateMeshStatusBadge(isBTActive: Boolean = false, isD2DActive: Boolean = false) {
        // Since this might be called from different triggers, we should be careful.
        // For simplicity, we'll check the managers' states or keep internal flags.
        // However, following the user's logic of adding alongside:
        val bt = if (isBTActive || isSOSSent.value) true else false // Approximate for now
        val d2d = isD2DActive

        activeMeshType.value = when {
            bt && d2d -> "Dual Mesh Active (BT + D2D)"
            d2d -> "D2D Nearby Mesh Active"
            bt -> "Bluetooth Mesh Active"
            else -> "None"
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // Automatically accept the connection to share SOS details
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d("D2D", "Connected to endpoint: $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("D2D", "Disconnected from endpoint: $endpointId")
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d("D2D", "SOS Endpoint found: ${info.endpointName}")
            runOnUiThread {
                Toast.makeText(this@MainActivity, "📍 D2D SOS DETECTED: ${info.endpointName}", Toast.LENGTH_SHORT).show()
            }
            // In a mesh, we could connect to fetch more details, but for now discovery is proof of signal
            connectionsClient.requestConnection(
                sharedPreferences.getString("user_name", "User") ?: "User",
                endpointId,
                connectionLifecycleCallback
            )
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d("D2D", "Endpoint lost: $endpointId")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val data = payload.asBytes()?.let { String(it) } ?: return
                Log.d("D2D", "Payload received: $data")
                relayMeshSOS(data)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun cancelEmergencyProtocol() {
        countdownTimer?.cancel()
        stopAlertSignals()
        bleMeshManager.stopBroadcasting()
        connectionsClient.stopAdvertising()
        isCountdownActive.value = false
        isSOSSent.value = false
        secondsRemaining.value = 15
        maxGForceTracked.value = 1.0f
        severityLevel.value = 1
        updateMeshStatusBadge(isBTActive = false, isD2DActive = false)
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

            // Activate BLE Mesh Broadcasting with user name and location
            val deviceName = sharedPreferences.getString("user_name", "User") ?: "User"
            val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                emptyArray()
            }

            if (hasPermissions(bluetoothPermissions)) {
                checkAndEnableBluetooth()
                bleMeshManager.startBroadcasting(deviceName, locationString)
                startSOSAdvertisingD2D(deviceName, locationString)
                updateMeshStatusBadge(isBTActive = true, isD2DActive = true)
            }
        }.addOnFailureListener {
            dispatchAlerts("Location Error (GPS Signal Unstable)")
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
            startSOSDiscoveryD2D()
        }
    }
    override fun onPause() { 
        super.onPause()
        if (isCountdownActive.value) cancelEmergencyProtocol()
        sensorManager.unregisterListener(this) 
        bleMeshManager.stopScanning()
        connectionsClient.stopDiscovery()
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
    bluetoothSignals: Map<String, SosDevice>,
    meshType: String,
    onCancel: () -> Unit,
    onProfileSaved: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val navItems = listOf(
        NavItem("Engine", Icons.Default.Speed, Icons.Default.Speed),
        NavItem("Mesh", Icons.Default.Share, Icons.Default.Share),
        NavItem("Profile", Icons.Default.Person, Icons.Default.Person)
    )

    Scaffold(
        bottomBar = {
            if (!isCountingDown && !sosTriggered) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                ) {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(if (selectedTab == index) item.selectedIcon else item.unselectedIcon, contentDescription = item.label) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        Box(modifier = Modifier.padding(scaffoldPadding)) {
            AnimatedContent(
                targetState = if (isCountingDown || sosTriggered) -1 else selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                }, label = ""
            ) { targetIndex ->
                when (targetIndex) {
                    -1 -> CrashDetectorScreen(padding, currentG, maxG, isCountingDown, secondsLeft, sosTriggered, severity, threshold, meshType, onCancel)
                    0 -> CrashDetectorScreen(PaddingValues(0.dp), currentG, maxG, isCountingDown, secondsLeft, sosTriggered, severity, threshold, meshType, onCancel)
                    1 -> MeshSignalScreen(bluetoothSignals) { locationUrl ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(locationUrl))
                        context.startActivity(intent)
                    }
                    2 -> IceProfileScreen(sharedPrefs, onProfileSaved)
                }
            }
        }
    }
}

data class NavItem(val label: String, val unselectedIcon: ImageVector, val selectedIcon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshSignalScreen(signals: Map<String, SosDevice>, onLocationClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Emergency Mesh",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Signals detected via unique SankatSathi protocol",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        if (signals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Scanning for signals...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            signals.values.forEach { sosDevice ->
                ElevatedCard(
                    onClick = { sosDevice.location?.let { onLocationClick(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                sosDevice.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Critical Distress Detected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun IceProfileScreen(sharedPrefs: SharedPreferences, onProfileSaved: () -> Unit) {
    var name by remember { mutableStateOf(sharedPrefs.getString("user_name", "") ?: "") }
    var bloodGroup by remember { mutableStateOf(sharedPrefs.getString("user_blood", "") ?: "") }
    var allergies by remember { mutableStateOf(sharedPrefs.getString("user_allergies", "") ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Medical Profile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Your information is stored locally and pinned to the lock screen for first responders.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Outlined.Person, null) }
        )

        OutlinedTextField(
            value = bloodGroup,
            onValueChange = { bloodGroup = it },
            label = { Text("Blood Group") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Bloodtype, null) }
        )

        OutlinedTextField(
            value = allergies,
            onValueChange = { allergies = it },
            label = { Text("Critical Allergies / Conditions") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Warning, null) },
            minLines = 3
        )

        Button(
            onClick = {
                sharedPrefs.edit().apply {
                    putString("user_name", name)
                    putString("user_blood", bloodGroup)
                    putString("user_allergies", allergies)
                    apply()
                }
                onProfileSaved()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("Update Emergency ID", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(32.dp))
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
    meshType: String,
    onCancel: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isCountingDown) 1.1f else 1.0f,
        animationSpec = tween(500), label = ""
    )

    val surfaceColor = when {
        sosTriggered && severity == 2 -> MaterialTheme.colorScheme.error
        sosTriggered -> MaterialTheme.colorScheme.errorContainer
        isCountingDown -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val contentColor = contentColorFor(surfaceColor)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(surfaceColor, MaterialTheme.colorScheme.background)
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Intelligent Crisis Engine",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor.copy(alpha = 0.8f)
                )
                
                // Mesh Status Badge
                if (meshType != "None") {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(3.dp)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = meshType.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Central Visualizer
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(280.dp)
                    .scale(scale)
            ) {
                // Background decorative circles
                repeat(3) { i ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1f - (i * 0.15f))
                            .alpha(0.05f),
                        shape = RoundedCornerShape(140.dp),
                        color = contentColor
                    ) {}
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isCountingDown) {
                        Text(
                            text = secondsLeft.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black,
                            color = contentColor
                        )
                    } else if (sosTriggered) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = contentColor
                        )
                    } else {
                        Text(
                            text = String.format("%.1f", currentG),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "LIVE G-FORCE",
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Bottom Actions & Info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isCountingDown && !sosTriggered) {
                    ElevatedCard(
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("PEAK FORCE RECORDED", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    String.format("%.2f G", maxG),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (isCountingDown || sosTriggered) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = contentColor,
                            contentColor = surfaceColor
                        )
                    ) {
                        Text(
                            text = if (isCountingDown) "I AM OKAY (CANCEL)" else "RESET SYSTEM",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
