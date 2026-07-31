package com.nepal.sankatsathi

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import kotlinx.coroutines.flow.Flow

interface IBleMeshManager {
    fun startBroadcasting(userName: String, location: String)
    fun stopBroadcasting()
    fun startScanning()
    fun stopScanning()
    fun isBleSupported(): Boolean
    fun connectToDevice(device: BluetoothDevice)
    val scanResults: Flow<ScanResult>
    val broadcastStatus: Flow<Pair<Boolean, String>>
    val detectedSosDevices: Flow<SosDevice>
}

data class SosDevice(
    val device: BluetoothDevice,
    val name: String,
    val location: String?,
    val rssi: Int,
    val timestamp: Long = System.currentTimeMillis()
)
