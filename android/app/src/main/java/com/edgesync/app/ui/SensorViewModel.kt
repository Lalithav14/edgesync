package com.edgesync.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edgesync.app.data.BleRepository
import com.edgesync.app.data.ReadingRequest
import com.edgesync.app.data.SensorReading
import com.edgesync.app.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SensorUiState(
    val connected: Boolean = false,
    val latest: SensorReading? = null,
    val history: List<SensorReading> = emptyList(),
    val insight: String? = null,
    val usingSimulatedData: Boolean = true,
    val error: String? = null
)

class SensorViewModel(
    private val bleRepository: BleRepository,
    private val api: ApiService = ApiService.create()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()

    private var pollJob: kotlinx.coroutines.Job? = null

    /** Call with useSimulated = true until the ESP32 is flashed and wired up. */
    fun startStreaming(deviceId: String = "esp32-1", useSimulated: Boolean = true) {
        _uiState.value = _uiState.value.copy(usingSimulatedData = useSimulated)

        val flow = if (useSimulated) bleRepository.startSimulated(deviceId)
                    else bleRepository.startBleScan(deviceId)

        viewModelScope.launch {
            flow.collect { reading ->
                _uiState.value = _uiState.value.copy(
                    connected = true,
                    latest = reading,
                    history = bleRepository.getRecentReadings()
                )
                pushToCloud(reading, deviceId)
            }
        }

        startInsightPolling(deviceId)
    }

    private fun pushToCloud(reading: SensorReading, deviceId: String) {
        viewModelScope.launch {
            try {
                api.postReading(
                    ReadingRequest(device_id = deviceId, temp = reading.temp, ts = reading.ts)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Upload failed: ${e.message}")
            }
        }
    }

    private fun startInsightPolling(deviceId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                try {
                    val insight = api.getLatestInsight(deviceId)
                    _uiState.value = _uiState.value.copy(insight = insight.summary)
                } catch (e: Exception) {
                    // Non-fatal: insight service may not have run yet
                }
                kotlinx.coroutines.delay(15_000)
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
