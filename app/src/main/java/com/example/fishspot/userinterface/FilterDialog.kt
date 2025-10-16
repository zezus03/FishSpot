
package com.example.fishspot.userinterface

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.fishspot.viewmodel.FilterState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun FilterDialog(
    currentFilterState: FilterState,
    onDismiss: () -> Unit,
    onApplyFilters: (String, String, Float, Long?, Long?, String, Float?) -> Unit
) {
    var autor by remember { mutableStateOf(currentFilterState.autor) }
    var vrstaRibe by remember { mutableStateOf(currentFilterState.vrstaRibe) }
    var ocena by remember { mutableStateOf(currentFilterState.minimalnaOcena) }
    var startDate by remember { mutableStateOf(currentFilterState.startDate) }
    var endDate by remember { mutableStateOf(currentFilterState.endDate) }
    var searchText by remember { mutableStateOf(currentFilterState.searchText) }
    var radius by remember { mutableStateOf(currentFilterState.radiusKm ?: 0f) }

    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    val startDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth, 0, 0, 0)
            startDate = calendar.timeInMillis
        },
        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
    )
    val endDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth, 23, 59, 59)
            endDate = calendar.timeInMillis
        },
        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pretraga i Filteri") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Pretraži naziv, opis...") }
                )

                val radiusText = if (radius == 0f) "Sve udaljenosti" else "Do ${radius.roundToInt()} km"
                Text(radiusText, Modifier.padding(top = 8.dp))
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 0f..50f, // od 0 do 50 km
                    steps = 9
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                OutlinedTextField(value = autor, onValueChange = { autor = it }, label = { Text("Filtriraj po autoru") })
                OutlinedTextField(value = vrstaRibe, onValueChange = { vrstaRibe = it }, label = { Text("Filtriraj po vrsti ribe") })

                Text("Minimalna ocena: ${ocena.toInt()}", Modifier.padding(top = 8.dp))
                Slider(value = ocena, onValueChange = { ocena = it }, valueRange = 0f..5f, steps = 4)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { startDatePickerDialog.show() }, modifier = Modifier.weight(1f)) {
                        Text(startDate?.let { dateFormat.format(Date(it)) } ?: "Od datuma")
                    }
                    Button(onClick = { endDatePickerDialog.show() }, modifier = Modifier.weight(1f)) {
                        Text(endDate?.let { dateFormat.format(Date(it)) } ?: "Do datuma")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val radiusToApply = if (radius > 0f) radius else null
                onApplyFilters(autor, vrstaRibe, ocena, startDate, endDate, searchText, radiusToApply)
                onDismiss()
            }) {
                Text("Primeni")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onApplyFilters("", "", 0f, null, null, "", null)
                onDismiss()
            }) {
                Text("Poništi sve")
            }
        }
    )
}