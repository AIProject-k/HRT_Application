package com.hormonelog.app.feature.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The calendar day a date picker should open on, as the UTC midnight it expects. The picker
 * reads and writes dates as UTC midnights, so the local day has to be converted both ways —
 * otherwise a pick shortly after midnight in Seoul lands on the day before.
 */
internal fun datePickerSeedMillis(seedMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(seedMillis).atZone(zone).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun datePickerResultMillis(dateMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
    val day = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}

/** The calendar day of a picked UTC-midnight value. */
internal fun pickedDate(dateMillis: Long): LocalDate = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()

/** Days after [latest] (a local date) cannot be chosen; null = no limit. */
@OptIn(ExperimentalMaterial3Api::class)
private fun selectableUpTo(latest: LocalDate?) = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        latest == null || !pickedDate(utcTimeMillis).isAfter(latest)
}

/**
 * Date then time, returning one instant in [zone]. A moment after [now] cannot be picked:
 * a draw or a dose is something that has happened. The time step also refuses a time later
 * today and says so, rather than quietly changing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    seedMillis: Long,
    zone: ZoneId,
    is24Hour: Boolean,
    now: Instant,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
) {
    var pickedDateMillis by remember { mutableStateOf<Long?>(null) }
    var future by remember { mutableStateOf(false) }
    val date = pickedDateMillis

    if (date == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = datePickerSeedMillis(minOf(seedMillis, now.toEpochMilli()), zone),
            selectableDates = selectableUpTo(now.atZone(zone).toLocalDate()),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pickedDateMillis = dateState.selectedDateMillis ?: datePickerSeedMillis(seedMillis, zone) }) { Text("다음") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        ) { DatePicker(state = dateState) }
    } else {
        val seed = Instant.ofEpochMilli(minOf(seedMillis, now.toEpochMilli())).atZone(zone)
        val timeState = rememberTimePickerState(initialHour = seed.hour, initialMinute = seed.minute, is24Hour = is24Hour)
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerResultMillis(date, timeState.hour, timeState.minute, zone)
                    if (millis > now.toEpochMilli()) future = true else onPicked(millis)
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
            title = if (future) ({ Text("미래 시각은 고를 수 없어요") }) else null,
            text = { TimePicker(state = timeState) },
        )
    }
}

/** A day on its own (the first day of a plan, the end of one); [earliest]/[latest] bound the choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(
    seed: LocalDate,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
    latest: LocalDate? = null,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = seed.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = selectableUpTo(latest),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPicked(pickedDate(it)) } ?: onDismiss() }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    ) { DatePicker(state = state) }
}

/** A time of day on its own, as minutes after midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(
    initialMinutes: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onPicked: (Int) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPicked(state.hour * 60 + state.minute) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        text = { TimePicker(state = state) },
    )
}
