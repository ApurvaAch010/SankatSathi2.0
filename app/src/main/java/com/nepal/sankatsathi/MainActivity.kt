package com.nepal.sankatsathi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nepal.sankatsathi.crashdetection.CrashDetectionUtils
import com.nepal.sankatsathi.goodsamaritan.*
import com.nepal.sankatsathi.ui.theme.SankatSathiTheme
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Safezone(val name: String, val latitude: Double, val longitude: Double, val mapUrl: String)

data class DisasterAlert(
    val title: String,
    val category: String,
    val distanceKm: Double,
    val date: String
)

class MainActivity : ComponentActivity(), SensorEventListener {

    private


    lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val lastAccelerometer = FloatArray(3)
    private val lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false
    private val deviceRotationMatrix = FloatArray(9)
    private val deviceOrientation = FloatArray(3)

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var vibrator: Vibrator? = null
    private lateinit var bleMeshManager: IBleMeshManager
    private lateinit var goodSamaritanRepository: GoodSamaritanRepository

    private var currentGForce = mutableStateOf(1.0f)
    private var maxGForceTracked = mutableStateOf(1.0f)
    private var isCountdownActive = mutableStateOf(false)
    private var secondsRemaining = mutableStateOf(15)
    private var isSOSSent = mutableStateOf(false)
    private var isDisasterSOS = mutableStateOf(false)
    private var isProfileComplete = mutableStateOf(false)
    private var severityLevel = mutableStateOf(1)
    private var activeMeshType = mutableStateOf("None")

    private var azimuth = mutableStateOf(0f)
    private var userLocation = mutableStateOf<Location?>(null)
    private var targetSafezone = mutableStateOf(Safezone("Primary Safezone (BG Mall)", 27.7346, 85.3126, "https://maps.app.goo.gl/k3HCZmystZYpBN4Q6"))
    private var disasterAlerts = mutableStateListOf<DisasterAlert>()
    private var isDisasterNearby = mutableStateOf(false)
    private var isAtSafezone = mutableStateOf(false)

    private var isBluetoothEnabled = mutableStateOf(false)
    private var isDiscoveringD2D = false
    private var loginState = mutableStateOf(LoginState.CHOOSING_TYPE)

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent?.action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
            }
        }
    }

    private val sosBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                com.nepal.sankatsathi.crashdetection.EmergencyOverlayActivity.ACTION_SOS_CONFIRM -> {
                    if (!isSOSSent.value) {
                        isSOSSent.value = true
                        isCountdownActive.value = false
                        stopAlertSignals()
                        executeSOSProtocol()
                    }
                }
                com.nepal.sankatsathi.crashdetection.EmergencyOverlayActivity.ACTION_SOS_CANCEL -> {
                    cancelEmergencyProtocol()
                }
                EmergencyService.ACTION_SOS_TRIGGERED -> {
                    // We let EmergencyOverlayActivity handle the UI and countdown
                    // but we can still show the G-Force on the dashboard if needed
                    val gForce = intent.getFloatExtra(EmergencyService.EXTRA_GFORCE, 0f)
                    if (gForce > maxGForceTracked.value) maxGForceTracked.value = gForce
                }
            }
        }
    }

    enum class LoginState { CHOOSING_TYPE, ORGANIZATION_LOGIN, INDIVIDUAL_REGISTRATION, LOGGED_IN }

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
    private val d2dSignals = mutableStateMapOf<String, SosDevice>() 

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
        
        // Initialize Osmdroid Configuration
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        enableEdgeToEdge()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        isBluetoothEnabled.value = bluetoothManager.adapter?.isEnabled == true
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        
        val sosFilter = IntentFilter().apply {
            addAction(EmergencyService.ACTION_SOS_TRIGGERED)
            addAction(com.nepal.sankatsathi.crashdetection.EmergencyOverlayActivity.ACTION_SOS_CONFIRM)
            addAction(com.nepal.sankatsathi.crashdetection.EmergencyOverlayActivity.ACTION_SOS_CANCEL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sosBroadcastReceiver, sosFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(sosBroadcastReceiver, sosFilter)
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        sharedPreferences = getSharedPreferences("SankatSathi_Prefs", Context.MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        connectionsClient = Nearby.getConnectionsClient(this)
        goodSamaritanRepository = GoodSamaritanRepository(this)
        bleMeshManager = BleMeshManager(this) { relayedPayload ->
            relayMeshSOS(relayedPayload)
        }

        // Initialize user location
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc -> userLocation.value = loc }
                startLocationTracking()
            }
        } catch (e: Exception) { e.printStackTrace() }

        // --- ADDED: Auto-Expiry Cleanup Loop (30s retention) ---
        lifecycleScope.launch {
            while (true) {
                delay(5000) // Check every 5 seconds
                val now = System.currentTimeMillis()
                val staleThreshold = 30000 // 30 seconds

                // Clean up Bluetooth Signals
                val itBt = bluetoothSignals.entries.iterator()
                while (itBt.hasNext()) {
                    if (now - itBt.next().value.timestamp > staleThreshold) {
                        itBt.remove()
                    }
                }

                // Clean up D2D Signals
                val itD2d = d2dSignals.entries.iterator()
                while (itD2d.hasNext()) {
                    if (now - itD2d.next().value.timestamp > staleThreshold) {
                        itD2d.remove()
                    }
                }
            }
        }

        lifecycleScope.launch {
            bleMeshManager.detectedSosDevices.collect { sosDevice ->
                runOnUiThread {
                    // DEDUPLICATION FIX: Use full Name|ID as key to support multiple victims even with same ID
                    bluetoothSignals[sosDevice.name] = sosDevice
                    
                    triggerSOSVibration()
                    Toast.makeText(this@MainActivity, "📍 BLE SOS DETECTED: ${sosDevice.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        lifecycleScope.launch {
            bleMeshManager.broadcastStatus.collect { (success, message) ->
                // Keep alerts receiver-only for victim, but log the exact signal sent
                runOnUiThread {
                    if (success && isSOSSent.value) { // ONLY SHOW TOAST IF SOS IS INTENTIONALLY ACTIVE
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

        performAutoLogin()

        setContent {
            SankatSathiTheme {
                val permissionsToRequest = mutableListOf(
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.VIBRATE,
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
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
                        // Start Persistent Crash Detection Service
                        CrashDetectionUtils.startCrashService(this@MainActivity)
                        
                        // Check for Battery Optimization & Full Screen Intent
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            CrashDetectionUtils.requestIgnoreBatteryOptimizations(this@MainActivity)
                        }
                        if (!CrashDetectionUtils.checkFullScreenIntentPermission(this@MainActivity)) {
                            CrashDetectionUtils.requestFullScreenIntentPermission(this@MainActivity)
                        }
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (permissionsGranted) {
                        if (!isBluetoothEnabled.value) {
                            MandatoryBluetoothScreen(
                                padding = innerPadding,
                                onEnable = { checkAndEnableBluetooth() }
                            )
                        } else {
                            when (loginState.value) {
                                LoginState.CHOOSING_TYPE -> LoginChoiceScreen(innerPadding) { type ->
                                    if (type == "Individual") {
                                        loginState.value = LoginState.INDIVIDUAL_REGISTRATION
                                    } else {
                                        loginState.value = LoginState.ORGANIZATION_LOGIN
                                    }
                                }
                                LoginState.ORGANIZATION_LOGIN -> OrganizationLoginScreen(innerPadding, { rememberMe ->
                                    sharedPreferences.edit()
                                        .putBoolean("is_org", true)
                                        .putBoolean("remember_me", rememberMe)
                                        .apply()
                                    loginState.value = LoginState.LOGGED_IN
                                    isProfileComplete.value = true
                                }, {
                                    loginState.value = LoginState.CHOOSING_TYPE
                                })
                                LoginState.INDIVIDUAL_REGISTRATION -> IceProfileScreen(sharedPreferences, 0, emptyList(), null) {
                                    loginState.value = LoginState.LOGGED_IN
                                    isProfileComplete.value = true
                                    showLockScreenNotification()
                                }
                                LoginState.LOGGED_IN -> {
                                    val reports by goodSamaritanRepository.allReports.collectAsState(initial = emptyList())
                                    val points by goodSamaritanRepository.totalPoints.collectAsState(initial = 0)
                                    
                                    MainAppNavigation(
                                        padding = innerPadding,
                                        currentG = currentGForce.value,
                                        maxG = maxGForceTracked.value,
                                        isCountingDown = isCountdownActive.value,
                                        secondsLeft = secondsRemaining.value,
                                        sosTriggered = isSOSSent.value,
                                        isDisaster = isDisasterSOS.value,
                                        isProfileSet = true,
                                        severity = severityLevel.value,
                                        threshold = WARNING_THRESHOLD,
                                        sharedPrefs = sharedPreferences,
                                        bluetoothSignals = bluetoothSignals,
                                        d2dSignals = d2dSignals,
                                        meshType = activeMeshType.value,
                                        userLoc = userLocation.value,
                                        deviceAzimuth = azimuth.value,
                                        safezone = targetSafezone.value,
                                        disasterAlerts = disasterAlerts,
                                        isDisasterClose = isDisasterNearby.value,
                                        isArrivedAtSafezone = isAtSafezone.value,
                                        samaritanPoints = points,
                                        samaritanReports = reports,
                                        onCancel = { cancelEmergencyProtocol() },
                                        onProfileSaved = { 
                                            isProfileComplete.value = true
                                            showLockScreenNotification()
                                        },
                                        onLogout = { handleLogout() },
                                        onUpdateSafezone = { targetSafezone.value = it },
                                        onSimulate = { simulateDisaster() },
                                        onManualSafezone = { isAtSafezone.value = true },
                                        onSubmitSamaritanReport = { sev, desc, voice, img ->
                                            lifecycleScope.launch {
                                                goodSamaritanRepository.submitReport(
                                                    sev, desc, voice, img,
                                                    userLocation.value?.latitude ?: 0.0,
                                                    userLocation.value?.longitude ?: 0.0
                                                ) { report ->
                                                    val mediaInfo = buildString {
                                                        if (report.imageUri != null) append("\n📸 Image Attached")
                                                        if (report.voiceNoteUri != null) append("\n🎤 Voice Note Recorded")
                                                    }
                                                    dispatchAlerts(
                                                        mapUrl = "https://maps.google.com/?q=${report.latitude},${report.longitude}",
                                                        relayedPayload = "🚑 GOOD SAMARITAN EMERGENCY DISPATCH 🚑\nSeverity: ${report.severity}\nDetails: ${report.textDescription}$mediaInfo\nLoc: https://maps.google.com/?q=${report.latitude},${report.longitude}"
                                                    )
                                                }
                                                Toast.makeText(this@MainActivity, "Report Submitted! +Points Earned", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        PermissionRequiredScreen(innerPadding) { launcher.launch(permissionsToRequest) }
                    }
                }
            }
        }
    }

    private fun performAutoLogin() {
        val name = sharedPreferences.getString("user_name", "") ?: ""
        val phone = sharedPreferences.getString("user_phone", "") ?: ""
        val emergencyPhone = sharedPreferences.getString("emergency_phone", "") ?: ""
        val isOrg = sharedPreferences.getBoolean("is_org", false)
        val rememberMe = sharedPreferences.getBoolean("remember_me", false)
        
        val isIndividualComplete = name.isNotBlank() && phone.length == 10 && emergencyPhone.length == 10
        isProfileComplete.value = isOrg || isIndividualComplete

        if (isProfileComplete.value && rememberMe) {
            loginState.value = LoginState.LOGGED_IN
        } else {
            loginState.value = LoginState.CHOOSING_TYPE
        }
    }

    private fun handleLogout() {
        sharedPreferences.edit().clear().apply()
        loginState.value = LoginState.CHOOSING_TYPE
        isProfileComplete.value = false
        isSOSSent.value = false
        isCountdownActive.value = false
        isDisasterSOS.value = false
        isAtSafezone.value = false
        cancelEmergencyProtocol()
        
        // Remove notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1)
        
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
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

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 2000
        ).build()
        
        fusedLocationClient.requestLocationUpdates(locationRequest, object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                val lastLoc = result.lastLocation
                userLocation.value = lastLoc
                if (lastLoc != null) {
                    fetchNearbyDisasters(lastLoc.latitude, lastLoc.longitude)
                    
                    // Auto-detect Safezone Arrival (within 50 meters)
                    val safeLoc = Location("").apply {
                        latitude = targetSafezone.value.latitude
                        longitude = targetSafezone.value.longitude
                    }
                    if (lastLoc.distanceTo(safeLoc) < 50f && !isAtSafezone.value) {
                        isAtSafezone.value = true
                        Toast.makeText(this@MainActivity, "🏁 REACHED SAFEZONE!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }, Looper.getMainLooper())
    }

    private fun cacheDisasterAlerts(alerts: List<DisasterAlert>) {
        val json = Gson().toJson(alerts)
        sharedPreferences.edit().putString("cached_disasters", json).apply()
    }

    private fun getCachedDisasterAlerts(): List<DisasterAlert> {
        val json = sharedPreferences.getString("cached_disasters", null) ?: return emptyList()
        val type = object : TypeToken<List<DisasterAlert>>() {}.type
        return Gson().fromJson(json, type)
    }

    private fun fetchNearbyDisasters(lat: Double, lng: Double) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getEvents()
                val nearby = response.events.mapNotNull { event ->
                    val geom = event.geometry.firstOrNull() ?: return@mapNotNull null
                    val eventLon = geom.coordinates[0]
                    val eventLat = geom.coordinates[1]
                    
                    val results = FloatArray(1)
                    Location.distanceBetween(lat, lng, eventLat, eventLon, results)
                    val distanceKm = results[0] / 1000.0
                    
                    if (distanceKm <= 200.0) {
                        DisasterAlert(
                            title = event.title,
                            category = event.categories.firstOrNull()?.title ?: "Natural Event",
                            distanceKm = distanceKm,
                            date = geom.date
                        )
                    } else null
                }.sortedBy { it.distanceKm }
                
                disasterAlerts.clear()
                disasterAlerts.addAll(nearby)
                cacheDisasterAlerts(nearby)
                
                isDisasterNearby.value = nearby.any { it.distanceKm <= 10.0 }
                
            } catch (e: Exception) {
                Log.e("SankatSathi", "NASA API Error: ${e.message}")
                val cached = getCachedDisasterAlerts()
                if (disasterAlerts.isEmpty() && cached.isNotEmpty()) {
                    disasterAlerts.addAll(cached)
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
            lastAccelerometerSet = true
            handleCrashDetection(event)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
            lastMagnetometerSet = true
        }

        if (lastAccelerometerSet && lastMagnetometerSet) {
            SensorManager.getRotationMatrix(deviceRotationMatrix, null, lastAccelerometer, lastMagnetometer)
            SensorManager.getOrientation(deviceRotationMatrix, deviceOrientation)
            
            val azimuthInRadians = deviceOrientation[0]
            val azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
            
            // Low-pass filter for smoothing compass jitter
            azimuth.value = azimuth.value * 0.9f + azimuthInDegrees * 0.1f
        }
    }

    private fun handleCrashDetection(event: SensorEvent) {
        if (isCountdownActive.value || isSOSSent.value || loginState.value != LoginState.LOGGED_IN) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val accelerationSum = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val gForce = accelerationSum / SensorManager.GRAVITY_EARTH
        
        // Optimize UI updates: Only update if the change is significant to reduce recompositions
        if (Math.abs(currentGForce.value - gForce) > 0.05f) {
            currentGForce.value = gForce
        }

        if (gForce > maxGForceTracked.value) maxGForceTracked.value = gForce

        // INTERNAL COUNTDOWN REMOVED - BACKGROUND SERVICE HANDLES SOS OVERLAY
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

    private fun triggerSOSVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 400, 200, 400, 200, 400)
            val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(1000)
        }
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
        // If we pick up our own name or phone, ignore
        val myName = sharedPreferences.getString("user_name", "User") ?: "User"
        val myPhone = sharedPreferences.getString("user_phone", "") ?: ""
        
        if (payload.contains(myName.take(8)) || (myPhone.isNotEmpty() && payload.contains(myPhone))) return

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
        if (location == "Unknown" || location.isEmpty()) {
            Log.e("D2D", "D2D Mesh skipped: No valid location available yet")
            return
        }
        
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startAdvertising(
            userName,
            NEARBY_SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.d("D2D", "D2D Advertising started successfully with location")
            updateMeshStatusBadge(isD2DActive = true)
        }.addOnFailureListener {
            Log.e("D2D", "D2D Advertising failed: ${it.message}")
        }
    }

    private fun startSOSDiscoveryD2D() {
        if (isDiscoveringD2D) return
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startDiscovery(
            NEARBY_SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Log.d("D2D", "D2D Discovery started successfully")
            isDiscoveringD2D = true
        }.addOnFailureListener {
            Log.e("D2D", "D2D Discovery failed: ${it.message}")
            isDiscoveringD2D = false
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
                // IF WE ARE THE VICTIM AND SOS IS ACTIVE, SEND THE FULL SOS DETAILS TO THE MESH
                if (isSOSSent.value) {
                    val name = sharedPreferences.getString("user_name", "User") ?: "User"
                    val phone = sharedPreferences.getString("user_phone", "000") ?: "000"
                    val blood = sharedPreferences.getString("user_blood", "Not Set")
                    val loc = if (userLocation.value != null) {
                        "https://maps.google.com/?q=${userLocation.value?.latitude},${userLocation.value?.longitude}"
                    } else "Unknown"
                    
                    // Construct the same unique mesh ID used for advertising
                    val coords = if (loc.contains("?q=")) {
                        loc.substringAfter("?q=").substringBefore(" ").split(",")
                    } else null
                    
                    val victimId = if (coords != null && coords.size >= 2) {
                        "$phone|$name|${coords[0].trim()}|${coords[1].trim()}"
                    } else {
                        "$phone|$name"
                    }

                    val payloadStr = "🚨 SOS MESH 🚨\nVictim: $victimId\nPhone: $phone\nBlood: $blood\nLoc: $loc"
                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(payloadStr.toByteArray()))
                }
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
                val victimId = info.endpointName
                val parts = victimId.split("|")
                // Robust extraction: parts[2] is lat, parts[3] is lng
                val locUrl = if (parts.size >= 4) {
                    val lat = parts[2].trim()
                    val lng = parts[3].trim()
                    "https://maps.google.com/?q=$lat,$lng"
                } else null

                // Mock device for d2dSignals
                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val dummyDevice = bluetoothManager.adapter.getRemoteDevice("00:00:00:00:00:00")
                d2dSignals[victimId] = SosDevice(dummyDevice, victimId, locUrl, 0)
                
                triggerSOSVibration()
                Toast.makeText(this@MainActivity, "📍 D2D SOS DETECTED: $victimId", Toast.LENGTH_SHORT).show()
            }
            
            // ALWAYS request connection in D2D mesh to ensure full payload (Blood group, etc) is received
            connectionsClient.requestConnection(
                sharedPreferences.getString("user_name", "Rescuer") ?: "Rescuer",
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
                
                // Try to parse location from payload if it matches D2D SOS pattern
                if (data.contains("Loc:")) {
                    val victimId = data.substringAfter("Victim: ").substringBefore("\n").trim()
                    val loc = data.substringAfter("Loc: ").substringBefore("\n").trim()
                    
                    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val dummyDevice = bluetoothManager.adapter.getRemoteDevice("00:00:00:00:00:00")
                    d2dSignals[victimId] = SosDevice(dummyDevice, victimId, loc, 0)
                }
                
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
        isDisasterSOS.value = false
        isAtSafezone.value = false
        secondsRemaining.value = 15
        maxGForceTracked.value = 1.0f
        severityLevel.value = 1
        updateMeshStatusBadge(isBTActive = false, isD2DActive = false)
        Toast.makeText(this, "Emergency Protocol Cancelled.", Toast.LENGTH_SHORT).show()
    }

    private fun simulateDisaster() {
        isDisasterSOS.value = true
        severityLevel.value = 2 
        isSOSSent.value = true
        executeSOSProtocol()
        Toast.makeText(this, "Disaster Simulation Started!", Toast.LENGTH_SHORT).show()
    }

    private fun executeSOSProtocol() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            dispatchAlerts("Unknown Location (Permissions Denied)")
            // Still start broadcasting even without precise location
            startBroadcastMesh("Unknown")
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            val locationString = if (location != null) {
                "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            } else {
                "https://maps.google.com/?q=27.7172,85.3240 (Kathmandu Fallback Center)"
            }
            dispatchAlerts(locationString)
            startBroadcastMesh(locationString)
        }.addOnFailureListener {
            dispatchAlerts("Location Error (GPS Signal Unstable)")
            startBroadcastMesh("Unknown")
        }
    }

    private fun startBroadcastMesh(locationString: String) {
        // Activate BLE Mesh Broadcasting with unique user ID and location
        val userName = sharedPreferences.getString("user_name", "User") ?: "User"
        val userPhone = sharedPreferences.getString("user_phone", "000") ?: "000"

        // Extract Lat/Lng for compact discovery (Fast Path)
        // locationString format: "https://maps.google.com/?q=LAT,LNG"
        val coords = if (locationString.contains("?q=")) {
            locationString.substringAfter("?q=").substringBefore(" ").split(",")
        } else null
        
        val broadcastName = if (coords != null && coords.size >= 2) {
            val lat = coords[0].trim()
            val lng = coords[1].trim()
            "$userPhone|$userName|$lat|$lng"
        } else {
            "$userPhone|$userName"
        }

        val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }

        if (hasPermissions(bluetoothPermissions)) {
            checkAndEnableBluetooth()
            // PRIORITIZE BLE FIRST (Broadcasting usually more reliable for initial blip)
            bleMeshManager.startBroadcasting(broadcastName, locationString)
            
            // DELAY D2D TO ENSURE BLE IS BROADCASTING FIRST AND LOCATION IS STABLE
            Handler(Looper.getMainLooper()).postDelayed({
                startSOSAdvertisingD2D(broadcastName, locationString)
            }, 5000) // Increased to 5s to ensure location lock is solid
            
            updateMeshStatusBadge(isBTActive = true, isD2DActive = false)
        }
    }

    private fun dispatchAlerts(mapUrl: String, relayedPayload: String? = null) {
        val blood = sharedPreferences.getString("user_blood", "Not Provided")
        val name = sharedPreferences.getString("user_name", "Unregistered User")
        val userPhone = sharedPreferences.getString("user_phone", "")
        val emergencyContactPhone = sharedPreferences.getString("emergency_phone", "")
        
        val modeText = when {
            isDisasterSOS.value -> "🚨 DISASTER ALERT: EVACUATION INITIATED 🚨"
            severityLevel.value == 2 -> "🚨 EMERGENCY: CRASH DETECTED 🚨"
            else -> "⚠️ CRASH SUSPECTED ⚠️"
        }

        val fullPayload = relayedPayload ?: "$modeText\nVictim: $name\nPhone: $userPhone\nBlood: $blood\nLoc: $mapUrl"
        val emailSubject = when {
            relayedPayload != null -> "🚨 RELAYED EMERGENCY ALERT 🚨"
            isDisasterSOS.value -> "CRITICAL DISASTER EVACUATION: $name"
            else -> "CRITICAL CRASH DISPATCH: $name"
        }

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
                
                // Send to System Emergency Contact
                if (parts.size == 1) {
                    smsManager.sendTextMessage(EMERGENCY_CONTACT_SMS, null, fullPayload, null, null)
                } else {
                    smsManager.sendMultipartTextMessage(EMERGENCY_CONTACT_SMS, null, parts, null, null)
                }

                // Send to Personal Emergency Contact
                if (!emergencyContactPhone.isNullOrBlank() && emergencyContactPhone.length == 10) {
                    if (parts.size == 1) {
                        smsManager.sendTextMessage(emergencyContactPhone, null, fullPayload, null, null)
                    } else {
                        smsManager.sendMultipartTextMessage(emergencyContactPhone, null, parts, null, null)
                    }
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothStateReceiver)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onResume() { 
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) } 
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        
        // --- ADDED: Repeated Permission Check ---
        val permissionsToRequest = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.VIBRATE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
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

        if (!hasPermissions(permissionsToRequest)) {
            Toast.makeText(this, "⚠️ CRITICAL: All permissions are required for background protection!", Toast.LENGTH_LONG).show()
        }
        
        // Background location check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "📍 PLEASE ALLOW 'ALL THE TIME' LOCATION ACCESS in Settings for full protection.", Toast.LENGTH_LONG).show()
            }
        }

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
        // We do NOT stop scanning here to allow background detection
        // sensorManager.unregisterListener(this) // Keep sensors for crash detection?
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
    isDisaster: Boolean,
    isProfileSet: Boolean,
    severity: Int,
    threshold: Float,
    sharedPrefs: SharedPreferences,
    bluetoothSignals: Map<String, SosDevice>,
    d2dSignals: Map<String, SosDevice>,
    meshType: String,
    userLoc: Location?,
    deviceAzimuth: Float,
    safezone: Safezone,
    disasterAlerts: List<DisasterAlert>,
    isDisasterClose: Boolean,
    isArrivedAtSafezone: Boolean,
    samaritanPoints: Int,
    samaritanReports: List<AccidentReport>,
    onCancel: () -> Unit,
    onProfileSaved: () -> Unit,
    onLogout: () -> Unit,
    onUpdateSafezone: (Safezone) -> Unit,
    onSimulate: () -> Unit,
    onManualSafezone: () -> Unit,
    onSubmitSamaritanReport: (Severity, String, String?, String?) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(if (isProfileSet) 0 else 4) }
    var showSamaritanDialog by remember { mutableStateOf(false) }
    
    // Auto-switch to Safezone tab ONLY if a REAL NASA disaster is within 10km
    LaunchedEffect(isDisasterClose) {
        if (isDisasterClose) {
            selectedTab = 3 // Index for Safezone tab
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    val navItems = listOf(
        NavItem("Engine", Icons.Default.Speed, Icons.Default.Speed),
        NavItem("Mesh", Icons.Default.Share, Icons.Default.Share),
        NavItem("Simulate", Icons.Default.BugReport, Icons.Default.BugReport),
        NavItem("Safezone", Icons.Default.Explore, Icons.Default.Explore),
        NavItem("Profile", Icons.Default.Person, Icons.Default.Person)
    )

    Scaffold(
        bottomBar = {
            if (!isCountingDown && (!sosTriggered || isDisaster) && isProfileSet) {
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
                targetState = when {
                    !isProfileSet -> 4
                    isCountingDown -> -1
                    sosTriggered && !isDisaster -> -1
                    else -> selectedTab
                },
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                }, label = ""
            ) { targetIndex ->
                when (targetIndex) {
                    -1 -> CrashDetectorScreen(padding, currentG, maxG, isCountingDown, secondsLeft, sosTriggered, isDisaster, severity, threshold, meshType, disasterAlerts, onCancel, { selectedTab = 3 }, { showSamaritanDialog = true })
                    0 -> CrashDetectorScreen(PaddingValues(0.dp), currentG, maxG, isCountingDown, secondsLeft, sosTriggered, isDisaster, severity, threshold, meshType, disasterAlerts, onCancel, { selectedTab = 3 }, { showSamaritanDialog = true })
                    1 -> MeshSignalScreen(bluetoothSignals, d2dSignals) { locationUrl ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(locationUrl))
                        context.startActivity(intent)
                    }
                    2 -> SimulationScreen(sosTriggered, { 
                        onSimulate() 
                        selectedTab = 0 // Show Crisis Engine screen first
                    }, onCancel)
                    3 -> SafezoneNavigationScreen(userLoc, deviceAzimuth, safezone, isArrivedAtSafezone, onUpdateSafezone, onManualSafezone)
                    4 -> IceProfileScreen(sharedPrefs, samaritanPoints, samaritanReports, onLogout, onProfileSaved)
                }
            }
        }

        if (showSamaritanDialog) {
            ReportAccidentDialog(
                onDismiss = { showSamaritanDialog = false },
                onSubmit = { sev, desc, voice, img ->
                    onSubmitSamaritanReport(sev, desc, voice, img)
                    showSamaritanDialog = false
                }
            )
        }
    }
}

data class NavItem(val label: String, val unselectedIcon: ImageVector, val selectedIcon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshSignalScreen(
    bleSignals: Map<String, SosDevice>,
    d2dSignals: Map<String, SosDevice>,
    onLocationClick: (String) -> Unit
) {
    // Unify and deduplicate signals from both mesh types
    val unifiedSignals = remember(bleSignals, d2dSignals) {
        val merged = mutableMapOf<String, SosDevice>()
        // D2D usually has better details if connected
        merged.putAll(d2dSignals)
        // BLE signals might overwrite with fresh data
        bleSignals.forEach { (id, device) ->
            val existing = merged[id]
            if (existing == null || device.location != null) {
                merged[id] = device
            }
        }
        merged
    }

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
            "Signals detected via unique protocol",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (unifiedSignals.isEmpty()) {
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
            Text(
                "Nearby Distress Signals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            unifiedSignals.values.sortedByDescending { it.timestamp }.forEach { sosDevice ->
                val type = if (d2dSignals.containsKey(sosDevice.name) && bleSignals.containsKey(sosDevice.name)) {
                    "Dual Mesh"
                } else if (d2dSignals.containsKey(sosDevice.name)) {
                    "D2D Nearby"
                } else {
                    "BLE Mesh"
                }
                SosSignalCard(sosDevice, type, onLocationClick)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosSignalCard(sosDevice: SosDevice, type: String, onLocationClick: (String) -> Unit) {
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
                val displayName = if (sosDevice.name.contains("|")) {
                    val parts = sosDevice.name.split("|")
                    if (parts.size >= 2) parts[1] else sosDevice.name
                } else {
                    sosDevice.name
                }
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$type - Distress Detected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (sosDevice.location == null) {
                    Text(
                        "Connecting for location...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun SimulationScreen(isSosActive: Boolean, onSimulate: () -> Unit, onStop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PowerSettingsNew,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = if (isSosActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Disaster Simulation",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Test the emergency protocol and mesh broadcasting without a real crash.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        
        if (!isSosActive) {
            Button(
                onClick = onSimulate,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("START DISASTER SIMULATION", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("STOP SIMULATION / RESET", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SafezoneNavigationScreen(
    userLocation: Location?,
    azimuth: Float,
    safezone: Safezone,
    isArrived: Boolean,
    onUpdateSafezone: (Safezone) -> Unit,
    onManualArrival: () -> Unit
) {
    var safezoneName by remember { mutableStateOf(safezone.name) }
    var safezoneLat by remember { mutableStateOf(safezone.latitude.toString()) }
    var safezoneLon by remember { mutableStateOf(safezone.longitude.toString()) }
    var safezoneUrl by remember { mutableStateOf(safezone.mapUrl) }

    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Manage MapView Lifecycle
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK) // Default, will be overridden by offline
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.5)
            
            // Setup Offline Source if available
            try {
                val mapFile = File(context.filesDir, "offline_map.zip")
                if (!mapFile.exists()) {
                    // Bundled asset extraction (simulation of user requirement)
                    context.assets.open("maps/kathmandu.zip").use { input ->
                        FileOutputStream(mapFile).use { output -> input.copyTo(output) }
                    }
                }
                
                if (mapFile.exists()) {
                    val archive = ArchiveFileFactory.getArchiveFile(mapFile)
                    val offlineProvider = OfflineTileProvider(
                        org.osmdroid.tileprovider.util.SimpleRegisterReceiver(context),
                        arrayOf(mapFile)
                    )
                    this.tileProvider = offlineProvider
                    
                    // Set source based on archive internal folder name
                    val sourceName = archive.tileSources.firstOrNull() ?: "tiles"
                    this.setTileSource(FileBasedTileSource(sourceName, 0, 20, 256, ".png", arrayOf("")))
                }
            } catch (e: Exception) {
                Log.e("Osmdroid", "Offline map loading failed: ${e.message}")
            }
        }
    }

    // User Location Overlay
    val locationOverlay = remember(mapView) {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            enableFollowLocation()
            setPersonIcon(android.graphics.BitmapFactory.decodeResource(context.resources, android.R.drawable.ic_menu_compass))
        }
    }

    // Target Marker
    val targetMarker = remember(mapView, safezone) {
        Marker(mapView).apply {
            position = GeoPoint(safezone.latitude, safezone.longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = safezone.name
            icon = ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)
        }
    }

    LaunchedEffect(userLocation) {
        userLocation?.let {
            mapView.invalidate()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Safezone Navigation",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            // Offline Status Badge
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF2E7D32), RoundedCornerShape(3.dp)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("OFFLINE MAP ACTIVE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
            }
        }
        
        if (isArrived) {
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "YOU HAVE ARRIVED AT THE SAFEZONE",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Offline Map Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = {
                        mapView.apply {
                            overlays.add(locationOverlay)
                            overlays.add(targetMarker)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = {
                        it.controller.setCenter(GeoPoint(safezone.latitude, safezone.longitude))
                    }
                )
                
                // GPS Lock Status
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GpsFixed, null, tint = Color.Green, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GPS LOCKED", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Bearing Direction Overlay
                val distance = if (userLocation != null) {
                    val dest = Location("").apply {
                        latitude = safezone.latitude
                        longitude = safezone.longitude
                    }
                    userLocation.distanceTo(dest)
                } else null

                val bearing = if (userLocation != null) {
                    val dest = Location("").apply {
                        latitude = safezone.latitude
                        longitude = safezone.longitude
                    }
                    userLocation.bearingTo(dest)
                } else 0f

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .rotate(bearing - azimuth)
                            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(32.dp))
                            .padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(20.dp)) {
                        Text(
                            text = if (isArrived) "ARRIVED" else if (distance != null) String.format("%.2f km to Safezone", distance / 1000) else "Locating...",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        if (!isArrived) {
            Button(
                onClick = onManualArrival,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
            ) {
                Text("I'M ALREADY HERE (MANUAL)")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Configure Target Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = safezoneName,
            onValueChange = { safezoneName = it },
            label = { Text("Hub Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Place, null) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = safezoneLat,
                onValueChange = { safezoneLat = it },
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            )
            OutlinedTextField(
                value = safezoneLon,
                onValueChange = { safezoneLon = it },
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val lat = safezoneLat.toDoubleOrNull() ?: safezone.latitude
                val lon = safezoneLon.toDoubleOrNull() ?: safezone.longitude
                onUpdateSafezone(Safezone(safezoneName, lat, lon, safezoneUrl))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("Update Navigation Target", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IceProfileScreen(
    sharedPrefs: SharedPreferences,
    samaritanPoints: Int,
    samaritanReports: List<AccidentReport>,
    onLogout: (() -> Unit)? = null,
    onProfileSaved: () -> Unit
) {
    var name by remember { mutableStateOf(sharedPrefs.getString("user_name", "") ?: "") }
    var phone by remember { mutableStateOf(sharedPrefs.getString("user_phone", "") ?: "") }
    var emergencyName by remember { mutableStateOf(sharedPrefs.getString("emergency_name", "") ?: "") }
    var emergencyPhone by remember { mutableStateOf(sharedPrefs.getString("emergency_phone", "") ?: "") }
    var bloodGroup by remember { mutableStateOf(sharedPrefs.getString("user_blood", "") ?: "") }
    var allergies by remember { mutableStateOf(sharedPrefs.getString("user_allergies", "") ?: "") }
    var rememberMe by remember { mutableStateOf(sharedPrefs.getBoolean("remember_me", false)) }

    var showOtpDialog by remember { mutableStateOf(false) }
    var otpInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val bloodGroups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    var expanded by remember { mutableStateOf(false) }

    val isInputValid = name.isNotBlank() && 
                      phone.length == 10 && phone.all { it.isDigit() } &&
                      emergencyPhone.length == 10 && emergencyPhone.all { it.isDigit() } &&
                      bloodGroup.isNotBlank() &&
                      emergencyName.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Individual Rescue Profile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Verify your identity to enable mesh emergency services.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SamaritanPointsCard(samaritanPoints)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name *") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Outlined.Person, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { if (it.length <= 10) phone = it },
            label = { Text("Your Phone (10 Digits) *") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Phone, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            singleLine = true,
            isError = phone.isNotEmpty() && phone.length != 10,
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
        )
        
        // ... (existing blood group dropdown)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = bloodGroup,
                onValueChange = {},
                readOnly = true,
                label = { Text("Blood Group *") },
                leadingIcon = { Icon(Icons.Default.Bloodtype, null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                bloodGroups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group) },
                        onClick = {
                            bloodGroup = group
                            expanded = false
                        }
                    )
                }
            }
        }

        Divider()
        Text("Emergency Contact (SMS will be sent here)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = emergencyName,
            onValueChange = { emergencyName = it },
            label = { Text("Contact Person Name *") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
        )

        OutlinedTextField(
            value = emergencyPhone,
            onValueChange = { if (it.length <= 10) emergencyPhone = it },
            label = { Text("Contact Phone (10 Digits) *") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.ContactPhone, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            singleLine = true,
            isError = emergencyPhone.isNotEmpty() && emergencyPhone.length != 10,
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
        )

        OutlinedTextField(
            value = allergies,
            onValueChange = { allergies = it },
            label = { Text("Critical Allergies / Conditions") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Warning, null) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
            Text("Remember Me", style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = {
                if (isInputValid) {
                    showOtpDialog = true
                }
            },
            enabled = isInputValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isInputValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text("Send OTP & Verify", style = MaterialTheme.typography.titleMedium)
        }

        if (onLogout != null) {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout from System", style = MaterialTheme.typography.titleMedium)
            }
        }

        Divider()
        AccidentHistoryList(samaritanReports)

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Rewards & Benefits", fontWeight = FontWeight.Bold)
                Text("Work in Progress 🚧 - Redeemable rewards and partner benefits coming soon!")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showOtpDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showOtpDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("OTP Verification", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Enter the 6-digit code sent to $phone",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = otpInput,
                        onValueChange = { code: String -> if (code.length <= 6) otpInput = code },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("6-Digit Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (otpInput == "123456") { // Dummy OTP
                                sharedPrefs.edit().apply {
                                    putString("user_name", name)
                                    putString("user_phone", phone)
                                    putString("emergency_name", emergencyName)
                                    putString("emergency_phone", emergencyPhone)
                                    putString("user_blood", bloodGroup)
                                    putString("user_allergies", if (allergies.isBlank()) "None" else allergies)
                                    putBoolean("is_org", false)
                                    putBoolean("remember_me", rememberMe)
                                    apply()
                                }
                                showOtpDialog = false
                                onProfileSaved()
                            } else {
                                // Show error
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = otpInput.length == 6
                    ) {
                        Text("Verify & Complete Registration")
                    }
                }
            }
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
fun MandatoryBluetoothScreen(padding: PaddingValues, onEnable: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f))
            .padding(padding)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(32.dp)
        ) {
            Icon(
                Icons.Default.Bluetooth,
                null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Bluetooth Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "SankatSathi requires Bluetooth to detect nearby SOS signals and broadcast your location when the network is down. This is critical for your survival.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onEnable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("ENABLE BLUETOOTH", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginChoiceScreen(padding: PaddingValues, onChoice: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.HealthAndSafety,
            null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Welcome to SankatSathi",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            "Select your rescue profile type to continue",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        
        ElevatedCard(
            onClick = { onChoice("Individual") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Individual Citizen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Register with health and emergency info")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ElevatedCard(
            onClick = { onChoice("Organization") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalHospital, null, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Rescue Organization", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Army, Police, Medical Dispatchers")
                }
            }
        }
    }
}

@Composable
fun OrganizationLoginScreen(padding: PaddingValues, onLogin: (Boolean) -> Unit, onBack: () -> Unit) {
    var orgId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Default.ArrowBack, null)
        }
        Text(
            "Organization Dispatch",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Access professional rescue dashboard",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = orgId,
            onValueChange = { orgId = it },
            label = { Text("Organization ID") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Badge, null) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Security Access Key") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.VpnKey, null) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
            Text("Remember Me", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { onLogin(rememberMe) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("ENTER DISPATCH CENTER")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("DUMMY LOGIN: ANY ID/KEY WORKS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
    isDisaster: Boolean,
    severity: Int,
    threshold: Float,
    meshType: String,
    disasterAlerts: List<DisasterAlert>,
    onCancel: () -> Unit,
    onNavigateToSafezone: () -> Unit,
    onReportAccident: () -> Unit
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
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
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

            Spacer(modifier = Modifier.height(48.dp))

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

            Spacer(modifier = Modifier.height(48.dp))

                if (!isCountingDown && !sosTriggered) {
                    NASAAlertsSection(disasterAlerts)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = onReportAccident,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.VolunteerActivism, null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Report Accident / Help Others", fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
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
                        onClick = {
                            if (sosTriggered && isDisaster) {
                                onNavigateToSafezone()
                            } else {
                                onCancel()
                            }
                        },
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
                            text = when {
                                isCountingDown -> "I AM OKAY (CANCEL)"
                                sosTriggered && isDisaster -> "NAVIGATE TO SAFEZONE"
                                else -> "RESET SYSTEM"
                            },
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

@Composable
fun NASAAlertsSection(alerts: List<DisasterAlert>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "NASA Active Disaster Alerts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        if (alerts.isEmpty()) {
            Surface(
                color = Color(0xFFE8F5E9), // Light green
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, "Safe", tint = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "No active NASA hazards within 200km",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            alerts.forEach { alert ->
                DisasterAlertCard(alert)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun DisasterAlertCard(alert: DisasterAlert) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        alert.category.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${String.format("%.1f", alert.distanceKm)} km away",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(alert.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Detected: ${alert.date.substringBefore("T")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

