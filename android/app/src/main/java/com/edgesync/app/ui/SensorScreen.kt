package com.edgesync.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SensorScreen(viewModel: SensorViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("EdgeSync") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.connected) "● Live" else "○ Connecting…",
                    color = if (state.connected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(8.dp))
                if (state.usingSimulatedData) {
                    AssistChip(onClick = {}, label = { Text("Simulated data") })
                }
            }

            Spacer(Modifier.height(16.dp))

            state.latest?.let { reading ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Latest reading", style = MaterialTheme.typography.labelMedium)
                        Text("${reading.temp}°C", style = MaterialTheme.typography.displaySmall)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            state.insight?.let { insight ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("AI Insight", style = MaterialTheme.typography.labelMedium)
                        Text(insight, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text("Recent readings", style = MaterialTheme.typography.labelMedium)
            LazyColumn {
                items(state.history.reversed()) { reading ->
                    Text("${reading.temp}°C  •  ts=${reading.ts}")
                }
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
