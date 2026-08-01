package com.nepal.sankatsathi.goodsamaritan

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoodSamaritanRepository(context: Context) {
    private val db = GoodSamaritanDatabase.getDatabase(context)
    private val dao = db.dao()

    val allReports: Flow<List<AccidentReport>> = dao.getAllReports()
    val totalPoints: Flow<Int> = dao.getTotalPoints().map { it ?: 0 }

    suspend fun submitReport(
        severity: Severity,
        description: String?,
        voiceUri: String?,
        imageUri: String?,
        lat: Double,
        lng: Double,
        onDispatchNeeded: suspend (AccidentReport) -> Unit
    ) {
        val status = if (severity == Severity.MINOR_INJURY) {
            ReportStatus.LOCAL_ONLY
        } else {
            ReportStatus.DISPATCHED_TO_AUTHORITIES
        }

        val report = AccidentReport(
            severity = severity,
            textDescription = description,
            voiceNoteUri = voiceUri,
            imageUri = imageUri,
            latitude = lat,
            longitude = lng,
            status = status
        )

        dao.insertReport(report)

        if (status == ReportStatus.DISPATCHED_TO_AUTHORITIES) {
            onDispatchNeeded(report)
        }
    }
}
