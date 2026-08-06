package com.nepal.sankatsathi.sos

import java.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

object SosPayloadCompressor {
    private const val TAG = "SankatSathiCompressor"
    private const val LAT_LNG_SCALE = 1e6
    private const val G_FORCE_SCALE = 100f

    /**
     * Packs SosRawData into a compact ByteArray under 100 bytes.
     * Estimated size: ~33 bytes.
     */
    fun compressPayload(data: SosRawData): ByteArray {
        // Allocate 100 bytes as requested, though we only use ~33
        val buffer = ByteBuffer.allocate(100).order(ByteOrder.BIG_ENDIAN)
        
        // Header (1 byte)
        buffer.put(data.type.value)
        
        // Timestamp (8 bytes)
        buffer.putLong(data.timestamp)
        
        // Location (8 bytes)
        buffer.putInt((data.latitude * LAT_LNG_SCALE).toInt())
        buffer.putInt((data.longitude * LAT_LNG_SCALE).toInt())
        
        // Telemetry (4 bytes)
        buffer.putShort(data.altitude)
        buffer.putShort((data.gForce * G_FORCE_SCALE).toInt().toShort())
        
        // Metadata: Truncated SHA-256 (12 bytes)
        val userHash = hashUserId(data.userId)
        buffer.put(userHash.take(12).toByteArray())
        
        val size = buffer.position()
        val result = ByteArray(size)
        buffer.flip()
        buffer.get(result)
        
        try {
            Log.d(TAG, "Payload generated. Size: $size bytes")
        } catch (e: Throwable) {
            // Fallback for non-Android environments (unit tests)
            println("[$TAG] Payload generated. Size: $size bytes")
        }
        return result
    }

    /**
     * Encodes the ByteArray into a Base64 string for transport.
     */
    fun toBase64Payload(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * Decompresses a Base64 string back into SosRawData.
     */
    fun decompressPayload(base64Str: String): SosRawData {
        val bytes = Base64.getDecoder().decode(base64Str)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        
        val typeByte = buffer.get()
        val type = SosType.fromByte(typeByte)
        val timestamp = buffer.getLong()
        val latInt = buffer.getInt()
        val lngInt = buffer.getInt()
        val altitude = buffer.getShort()
        val gForceShort = buffer.getShort()
        
        val userHashBytes = ByteArray(12)
        buffer.get(userHashBytes)
        
        return SosRawData(
            type = type,
            timestamp = timestamp,
            latitude = latInt / LAT_LNG_SCALE,
            longitude = lngInt / LAT_LNG_SCALE,
            altitude = altitude,
            gForce = gForceShort.toFloat() / G_FORCE_SCALE,
            userId = userHashBytes.joinToString("") { "%02x".format(it) }
        )
    }

    private fun hashUserId(userId: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(userId.toByteArray())
    }
    
    // Extension method as requested
    fun SosRawData.toCompressedBase64(): String {
        return toBase64Payload(compressPayload(this))
    }
}
