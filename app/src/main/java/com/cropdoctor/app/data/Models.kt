package com.cropdoctor.app.data

import com.cropdoctor.app.ai.Certainty

data class FieldPlot(
    val id: String,
    val name: String,
    val crop: String,          // crop key, e.g. "Tomato"
    val areaAcres: Double = 1.0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class AltRec(val classId: String, val p: Float)

data class ScanRecord(
    val id: String,
    val fieldId: String?,
    val timestamp: Long,
    val imageFile: String,           // file name inside filesDir/scans
    val classId: String?,            // null when it was not a leaf
    val confidence: Float,
    val certainty: Certainty,
    val severity: Int,               // 0 healthy .. 3 severe, -1 unknown
    val alternatives: List<AltRec> = emptyList(),
    val qualityNotes: List<String> = emptyList(),
    val reminderDays: Int? = null,
)

/** Direction of a field's health between its last two diagnosed scans. */
enum class Trend { NEW, BETTER, SAME, WORSE }
