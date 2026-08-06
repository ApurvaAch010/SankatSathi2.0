package com.nepal.sankatsathi.sos

enum class SosType(val value: Byte) {
    VEHICLE_CRASH(0x01),
    MEDICAL(0x03),
    MANUAL(0x05),
    UNKNOWN(0x00);

    companion object {
        fun fromByte(value: Byte): SosType = entries.find { it.value == value } ?: UNKNOWN
    }
}

data class SosRawData(
    val type: SosType,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Short,
    val gForce: Float,
    val userId: String
)
