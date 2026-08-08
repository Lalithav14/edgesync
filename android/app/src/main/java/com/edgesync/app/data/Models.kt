package com.edgesync.app.data

/** A single sensor reading, either from BLE or simulated. */
data class SensorReading(
    val deviceId: String,
    val temp: Double,
    val ts: Long
)

/** Body sent to the ingestion API. */
data class ReadingRequest(
    val device_id: String,
    val temp: Double,
    val ts: Long
)

data class ReadingResponse(
    val status: String
)

/** AI-generated anomaly summary fetched from the insight endpoint. */
data class InsightResponse(
    val summary: String?,
    val generated_at: Long?
)
