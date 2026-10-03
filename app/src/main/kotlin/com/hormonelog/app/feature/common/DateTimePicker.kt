package com.hormonelog.app.feature.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Date then time, returning one instant in [zone]. The date picker reports a UTC
 * midnight, so the calendar day is read back in UTC before the chosen wall-clock time
 * is applied — otherwise a late-evening pick lands on the wrong day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    seedMillis: Long,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
) {
    var pickedDateMillis by remember { mutableStateOf<Long?>(null) }
    val date = pickedDateMillis

    if (date == null) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = datePickerSeedMillis(seedMillis, zone))
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pickedDateMillis = dateState.selectedDateMillis ?: seedMillis }) {
                    Text("다음")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        ) { DatePicker(state = dateState) }
    } else {
        val seed = Instant.ofEpochMilli(seedMillis).atZone(zone)
        val timeState = rememberTimePickerState(
            initialHour = seed.hour,
            initialMinute = seed.minute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    onPicked(datePickerResultMillis(date, timeState.hour, timeState.minute, zone))
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
            text = { TimePicker(state = timeState) },
        )
    }
}

internal fun datePickerSeedMillis(seedMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(seedMillis).atZone(zone).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun datePickerResultMillis(dateMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
    val day = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}
