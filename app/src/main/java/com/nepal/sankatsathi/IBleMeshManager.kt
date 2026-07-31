package com.nepal.sankatsathi

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import kotlinx.coroutines.flow.Flow

interface IBleMeshManager {
    fun startBroadcasting(uniqueId: String)
    fun stopBroadcasting()
    fun startScanning()
    fun stopScanning()
    fun isBleSupported(): Boolean
    fun connectAndSendLocation(device: BluetoothDevice, location: String)
    val scanResults: Flow<ScanResult>
    val broadcastStatus: Flow<Pair<Boolean, String>> // Success, Message
}
