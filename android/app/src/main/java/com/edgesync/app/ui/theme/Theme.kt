package com.edgesync.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val EdgeSyncColors = lightColorScheme()

@Composable
fun EdgeSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EdgeSyncColors,
        content = content
    )
}
