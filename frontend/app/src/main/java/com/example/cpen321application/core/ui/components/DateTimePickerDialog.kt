package com.example.cpen321application.core.ui.components

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
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Picks a start time: a date picker, then a time picker, in the phone's time zone.
 * Past dates can't be picked; the caller still checks the time is in the future.
 * @param initial the time to start from, null for one hour from now
 * @param onDismiss called when the user cancels
 * @param onConfirm called with the picked time
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initial: Instant?,
    onDismiss: () -> Unit,
    onConfirm: (Instant) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val start = (initial ?: Instant.now().plus(1, ChronoUnit.HOURS)).atZone(zone)

    // null while the date picker is showing, then the picked date while the time picker is showing
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    val date = pickedDate
    if (date == null) {
        // The date picker works in UTC midnights, whatever the phone's time zone
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !utcDate(utcTimeMillis).isBefore(LocalDate.now(zone))
            }
        )

        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { dateState.selectedDateMillis?.let { pickedDate = utcDate(it) } },
                    enabled = dateState.selectedDateMillis != null
                ) {
                    Text("Next")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute)

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Start time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(date.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant())
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickedDate = null }) { Text("Back") }
            }
        )
    }
}

private fun utcDate(utcTimeMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
