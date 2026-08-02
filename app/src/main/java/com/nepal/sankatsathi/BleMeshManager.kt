package com.nepal.sankatsathi

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.*

class BleMeshManager(private val context: Context, private val onSosReceived: (String) -> Unit) : IBleMeshManager {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null

    // Unique UUIDs for SankatSathi
    private val SERVICE_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    private val LOCATION_CHAR_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
    private val NAME_CHAR_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")

    private val _scanResults = MutableSharedFlow<ScanResult>(extraBufferCapacity = 10)
    override val scanResults: SharedFlow<ScanResult> = _scanResults.asSharedFlow()

    private val _broadcastStatus = MutableSharedFlow<Pair<Boolean, String>>(extraBufferCapacity = 5)
    override val broadcastStatus: SharedFlow<Pair<Boolean, String>> = _broadcastStatus.asSharedFlow()

    private val _detectedSosDevices = MutableSharedFlow<SosDevice>(extraBufferCapacity = 20)
    override val detectedSosDevices: SharedFlow<SosDevice> = _detectedSosDevices.asSharedFlow()

    private var currentLocation: String = ""
    private var currentUserName: String = ""
    private var isScanning: Boolean = false
    private val processedAddresses = mutableMapOf<String, Long>()

    @SuppressLint("MissingPermission")
    override fun startBroadcasting(userName: String, location: String) {
        currentUserName = userName
        currentLocation = location

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _broadcastStatus.tryEmit(false to "Bluetooth Error")
            return
        }

        advertiser = bluetoothAdapter!!.bluetoothLeAdvertiser ?: return
        setupGattServer()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // Include ID in scan record if possible, or just the UUID for discovery
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // Enable this so scanner can see name immediately if supported
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    override fun stopBroadcasting() {
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        gattServer = null
    }

    @SuppressLint("MissingPermission")
    override fun startScanning() {
        if (isScanning) return
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) return
        scanner = bluetoothAdapter!!.bluetoothLeScanner ?: return
        
        isScanning = true
        processedAddresses.clear()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    override fun stopScanning() {
        if (!isScanning) return
        scanner?.stopScan(scanCallback)
        isScanning = false
    }

    @SuppressLint("MissingPermission")
    private fun setupGattServer() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        gattServer = manager.openGattServer(context, gattServerCallback)
        
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        val locationChar = BluetoothGattCharacteristic(
            LOCATION_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        val nameChar = BluetoothGattCharacteristic(
            NAME_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(locationChar)
        service.addCharacteristic(nameChar)
        gattServer?.addService(service)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = when (characteristic.uuid) {
                LOCATION_CHAR_UUID -> currentLocation.toByteArray()
                NAME_CHAR_UUID -> currentUserName.toByteArray()
                else -> null
            }
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address
            val now = System.currentTimeMillis()
            
            // Allow re-processing the same device every 3 seconds to ensure we catch the signal
            if (processedAddresses.containsKey(address) && now - (processedAddresses[address] ?: 0L) < 3000) return
            processedAddresses[address] = now

            if (processedAddresses.size > 100) processedAddresses.clear()

            _scanResults.tryEmit(result)
            // Auto-connect to fetch details
            connectToDevice(result.device)
        }
    }

    @SuppressLint("MissingPermission")
    override fun connectToDevice(device: BluetoothDevice) {
        device.connectGatt(context, false, object : BluetoothGattCallback() {
            private var tempName: String? = null
            private var tempLocation: String? = null

            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close()
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SERVICE_UUID)
                    service?.getCharacteristic(NAME_CHAR_UUID)?.let { gatt.readCharacteristic(it) }
                }
            }

            @SuppressLint("MissingPermission")
            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        characteristic.value
                    } else {
                        characteristic.value
                    }

                    if (data == null) {
                        gatt.disconnect()
                        return
                    }

                    when (characteristic.uuid) {
                        NAME_CHAR_UUID -> {
                            tempName = String(data)
                            gatt.getService(SERVICE_UUID)?.getCharacteristic(LOCATION_CHAR_UUID)?.let {
                                gatt.readCharacteristic(it)
                            }
                        }
                        LOCATION_CHAR_UUID -> {
                            tempLocation = String(data)
                            _detectedSosDevices.tryEmit(SosDevice(
                                device = gatt.device,
                                name = tempName ?: "Unknown",
                                location = tempLocation,
                                rssi = 0 
                            ))
                            gatt.disconnect()
                        }
                    }
                } else {
                    gatt.disconnect()
                }
            }
        })
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _broadcastStatus.tryEmit(true to "Broadcasting SOS")
        }
        override fun onStartFailure(errorCode: Int) {
            _broadcastStatus.tryEmit(false to "Broadcast Failed: $errorCode")
        }
    }

    override fun isBleSupported(): Boolean = bluetoothAdapter != null
}
