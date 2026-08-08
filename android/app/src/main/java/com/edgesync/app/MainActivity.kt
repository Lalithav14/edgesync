package com.edgesync.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.edgesync.app.data.BleRepository
import com.edgesync.app.ui.SensorScreen
import com.edgesync.app.ui.SensorViewModel
import com.edgesync.app.ui.theme.EdgeSyncTheme

class SensorViewModelFactory(private val bleRepository: BleRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SensorViewModel(bleRepository) as T
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bleRepository = BleRepository(applicationContext)

        setContent {
            EdgeSyncTheme {
                val viewModel: SensorViewModel = viewModel(
                    factory = SensorViewModelFactory(bleRepository)
                )

                // Start with simulated data — flip useSimulated to false once
                // the ESP32 is flashed, wired, and BLE permissions are granted.
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    viewModel.startStreaming(useSimulated = true)
                }

                SensorScreen(viewModel)
            }
        }
    }
}
