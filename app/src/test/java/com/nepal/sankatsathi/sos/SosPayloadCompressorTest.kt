package com.nepal.sankatsathi.sos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SosPayloadCompressorTest {

    @Test
    fun testCompressionAndDecompression() {
        val rawData = SosRawData(
            type = SosType.VEHICLE_CRASH,
            timestamp = 1723000000000L,
            latitude = 27.7172,
            longitude = 85.3240,
            altitude = 1400,
            gForce = 5.25f,
            userId = "user-12345"
        )

        val compressed = SosPayloadCompressor.compressPayload(rawData)
        
        // Assert size is under 100 bytes (should be around 33)
        assertTrue("Payload size should be under 100 bytes, but was ${compressed.size}", compressed.size < 100)
        assertEquals(33, compressed.size)

        val base64 = SosPayloadCompressor.toBase64Payload(compressed)
        val decompressed = SosPayloadCompressor.decompressPayload(base64)

        assertEquals(rawData.type, decompressed.type)
        assertEquals(rawData.timestamp, decompressed.timestamp)
        assertEquals(rawData.latitude, decompressed.latitude, 0.000001)
        assertEquals(rawData.longitude, decompressed.longitude, 0.000001)
        assertEquals(rawData.altitude, decompressed.altitude)
        assertEquals(rawData.gForce, decompressed.gForce, 0.01f)
        
        // userId in decompressed should be the hex hash
        assertTrue(decompressed.userId.length == 24) // 12 bytes * 2 hex chars
    }
}
