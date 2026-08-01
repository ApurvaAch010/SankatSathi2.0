package com.nepal.sankatsathi.goodsamaritan

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class Severity {
    MINOR_INJURY,
    SEVERE_INJURY,
    CRITICAL_RESCUE_NEEDED
}

enum class ReportStatus {
    LOCAL_ONLY,
    DISPATCHED_TO_AUTHORITIES
}

@Entity(tableName = "accident_reports")
data class AccidentReport(
    @PrimaryKey
    val reportId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val severity: Severity,
    val textDescription: String?,
    val voiceNoteUri: String?,
    val imageUri: String?,
    val latitude: Double,
    val longitude: Double,
    val status: ReportStatus
)
