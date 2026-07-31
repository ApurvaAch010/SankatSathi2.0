package com.nepal.sankatsathi

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.ByteBuffer
import java.util.*

class BleMeshManager(private val context: Context, private val onSosReceived: (String) -> Unit) : IBleMeshManager {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null

    private val SERVICE_UUID = UUID.fromString("0000feaa-0000-1000-8000-00805f9b34fb")
    private val LOCATION_CHAR_UUID = UUID.fromString("0000feab-0000-1000-8000-00805f9b34fb")
    private val SOS_PREFIX = "SANKAT" 

    private var isAdvertising = false
    private var isScanning = false

    private val _scanResults = MutableSharedFlow<ScanResult>(extraBufferCapacity = 10)
    override val scanResults: SharedFlow<ScanResult> = _scanResults.asSharedFlow()

    private val _broadcastStatus = MutableSharedFlow<Pair<Boolean, String>>(extraBufferCapacity = 5)
    override val broadcastStatus: SharedFlow<Pair<Boolean, String>> = _broadcastStatus.asSharedFlow()

    @SuppressLint("MissingPermission")
    override fun startBroadcasting(uniqueId: String) {
        Log.d("BleMesh", "Attempting to start broadcasting for ID: $uniqueId")
        if (bluetoothAdapter == null) {
            _broadcastStatus.tryEmit(false to "Bluetooth Hardware Not Found")
            return
        }
        if (!bluetoothAdapter!!.isEnabled) {
            _broadcastStatus.tryEmit(false to "Please Enable Bluetooth")
            return
        }
        
        advertiser = bluetoothAdapter!!.bluetoothLeAdvertiser
        if (advertiser == null) {
            _broadcastStatus.tryEmit(false to "Device Doesn't Support BLE Advertising")
            return
        }

        // Setup GATT Server to receive location from helpers
        setupGattServer()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true) // Must be connectable for GATT
            .build()

        // Construct data: Prefix (6 bytes) + Short UniqueID (8 bytes) + Timestamp (8 bytes)
        // Total ~22 bytes, fits in 31 byte limit
        val timestamp = System.currentTimeMillis()
        val dataBuffer = ByteBuffer.allocate(22)
        dataBuffer.put(SOS_PREFIX.toByteArray())
        
        val paddedId = uniqueId.take(8).let { 
            if (it.length < 8) it + " ".repeat(8 - it.length) else it
        }
        dataBuffer.put(paddedId.toByteArray())
        dataBuffer.putLong(timestamp)

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), dataBuffer.array())
            .setIncludeDeviceName(false) // Must be false to fit our 22-byte custom payload
            .setIncludeTxPowerLevel(false)
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        isAdvertising = true
        _broadcastStatus.tryEmit(true to "BROADCASTING: $SOS_PREFIX | $paddedId")
        Log.d("BleMesh", "Started SOS Broadcasting: $uniqueId")
    }

    @SuppressLint("MissingPermission")
    override fun stopBroadcasting() {
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        gattServer = null
        isAdvertising = false
    }

    @SuppressLint("MissingPermission")
    override fun startScanning() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) return
        scanner = bluetoothAdapter!!.bluetoothLeScanner
        if (scanner == null) return

        // Empty filters to see ALL nearby devices
        val filters = emptyList<ScanFilter>()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(filters, settings, scanCallback)
        isScanning = true
        Log.d("BleMesh", "Started Scanning for ALL nearby devices")
    }

    @SuppressLint("MissingPermission")
    override fun stopScanning() {
        scanner?.stopScan(scanCallback)
        isScanning = false
    }

    @SuppressLint("MissingPermission")
    override fun connectAndSendLocation(device: BluetoothDevice, location: String) {
        Log.d("BleMesh", "Connecting to ${device.address} to send location: $location")
        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BleMesh", "Connected to sender, discovering services...")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(LOCATION_CHAR_UUID)
                    if (characteristic != null) {
                        characteristic.value = location.toByteArray()
                        val success = gatt.writeCharacteristic(characteristic)
                        Log.d("BleMesh", "Location write triggered: $success")
                    } else {
                        Log.e("BleMesh", "Location characteristic not found!")
                        gatt.disconnect()
                    }
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                Log.d("BleMesh", "Location successfully sent to sender! Status: $status")
                gatt.disconnect()
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun setupGattServer() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        gattServer = manager.openGattServer(context, gattServerCallback)
        
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val locationChar = BluetoothGattCharacteristic(
            LOCATION_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(locationChar)
        gattServer?.addService(service)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            if (characteristic.uuid == LOCATION_CHAR_UUID) {
                val location = value?.let { String(it) } ?: "Unknown"
                Log.d("BleMesh", "Received location from helper ${device.address}: $location")
                onSosReceived("HELPER NEARBY at $location (ID: ${device.address})")
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            _broadcastStatus.tryEmit(true to "SOS Signal Broadcasting Active")
            Log.d("BleMesh", "BLE Advertising Started Successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            val errorMessage = when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> "Already Broadcasting"
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Packet Data Too Large"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "BLE Advertising Not Supported"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "System Busy (Too Many Advertisers)"
                else -> "Broadcast Failed (Error: $errorCode)"
            }
            _broadcastStatus.tryEmit(false to errorMessage)
            Log.e("BleMesh", "BLE Advertising Failed: $errorCode")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { _scanResults.tryEmit(it) }
            
            val record = result?.scanRecord ?: return
            
            // Check every possible data source in the advertisement
            // 1. Check Service Data map
            record.serviceData.forEach { (uuid, data) ->
                if (uuid.toString().contains("feaa", ignoreCase = true)) {
                    processSankatData(data)
                }
            }
            
            // 2. Fallback: Some devices put the data in manufacturer data or custom fields
            // but the most reliable way for cross-device is Service Data under a standard UUID
        }

        private fun processSankatData(data: ByteArray) {
            if (data.size < 22) return
            try {
                val buffer = ByteBuffer.wrap(data)
                val prefixBytes = ByteArray(6)
                buffer.get(prefixBytes)
                val prefix = String(prefixBytes)

                if (prefix == SOS_PREFIX) {
                    val idBytes = ByteArray(8)
                    buffer.get(idBytes)
                    val id = String(idBytes).trim()
                    val timestamp = buffer.long
                    
                    val payload = "RELAYED SOS from ID: $id (at $timestamp)"
                    onSosReceived(payload)
                    Log.d("BleMesh", "✅ VALID SOS PACKET DECODED! ID: $id")
                }
            } catch (e: Exception) {
                Log.e("BleMesh", "Error parsing SOS packet: ${e.message}")
            }
        }
    }
    
    override fun isBleSupported(): Boolean = bluetoothAdapter != null
}
