package com.hormonelog.app.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs [compute] off the main thread whenever one of [keys] changes and hands back its latest
 * result. The previous result stays on screen until the new one arrives — a chart that redraws
 * after a change does not flash empty in between. Null only before the very first result.
 */
@Composable
fun <T> rememberAsync(vararg keys: Any?, compute: () -> T): T? {
    var value by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(*keys) { value = withContext(Dispatchers.Default) { compute() } }
    return value
}
