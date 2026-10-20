package com.example.fishspot.feature.map

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.NaslovSekcije
import com.example.fishspot.core.theme.fishPoljeBoje
import com.example.fishspot.feature.spots.FilterState
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
    val dateFormat = SimpleDateFormat("dd.MM.yyyy.", Locale.getDefault())

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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(FishBoje.Pozadina)
                .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(24.dp))
        ) {
            // Zaglavlje
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FishBoje.ZaglavljeGore)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Box(
                    modifier = Modifier.size(36.dp).background(FishBoje.Krug, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null, tint = FishBoje.Akcenat,
                        modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("PRETRAGA ULOVA", color = FishBoje.TekstPriguseni, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text("Pretraga i filteri", color = FishBoje.TekstPrimarni, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp).background(FishBoje.Krug, CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Zatvori", tint = FishBoje.TekstSekundarni,
                        modifier = Modifier.size(18.dp))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("Pretraži naziv, opis, vrstu...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    colors = fishPoljeBoje(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Udaljenost od trenutne lokacije
                NaslovSekcije("Udaljenost", Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (radius == 0f) "Sve udaljenosti" else "Do ${radius.roundToInt()} km",
                        color = FishBoje.TekstPrimarni, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (radius > 0f) {
                        Text("Poništi", color = FishBoje.Akcenat, fontSize = 13.sp,
                            modifier = Modifier.clickable { radius = 0f }.padding(4.dp))
                    }
                }
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 0f..50f, // od 0 do 50 km
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = FishBoje.Akcenat,
                        activeTrackColor = FishBoje.Akcenat,
                        inactiveTrackColor = FishBoje.Polje,
                        activeTickColor = FishBoje.Pozadina,
                        inactiveTickColor = FishBoje.IvicaKartice
                    )
                )

                NaslovSekcije("Ko i šta", Modifier.padding(top = 4.dp))
                OutlinedTextField(
                    value = autor,
                    onValueChange = { autor = it },
                    placeholder = { Text("Ime pecaroša") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    colors = fishPoljeBoje(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = vrstaRibe,
                    onValueChange = { vrstaRibe = it },
                    placeholder = { Text("Vrsta ribe, npr. dever") },
                    leadingIcon = { Icon(Icons.Default.SetMeal, contentDescription = null) },
                    singleLine = true,
                    colors = fishPoljeBoje(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Minimalna ocena mesta zvezdicama (dodir na izabranu zvezdicu je poništava)
                NaslovSekcije("Najmanja ocena mesta", Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (1..5).forEach { zvezda ->
                        IconButton(
                            onClick = { ocena = if (ocena.toInt() == zvezda) 0f else zvezda.toFloat() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                if (zvezda <= ocena.toInt()) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Ocena $zvezda",
                                tint = if (zvezda <= ocena.toInt()) FishBoje.Zvezdica else FishBoje.TekstPriguseni,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (ocena == 0f) "bilo koja" else "${ocena.toInt()}+", color = FishBoje.TekstSekundarni,
                        fontSize = 13.sp)
                }

                NaslovSekcije("Period", Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DugmeDatuma(
                        oznaka = "Od",
                        vrednost = startDate?.let { dateFormat.format(Date(it)) },
                        onClick = { startDatePickerDialog.show() },
                        onPonisti = { startDate = null },
                        modifier = Modifier.weight(1f)
                    )
                    DugmeDatuma(
                        oznaka = "Do",
                        vrednost = endDate?.let { dateFormat.format(Date(it)) },
                        onClick = { endDatePickerDialog.show() },
                        onPonisti = { endDate = null },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Dugmad
            HorizontalDivider(color = FishBoje.IvicaKartice)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FishBoje.Kartica)
                    .padding(14.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onApplyFilters("", "", 0f, null, null, "", null)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FishBoje.IvicaKartice),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FishBoje.TekstSekundarni),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Poništi sve")
                }
                Button(
                    onClick = {
                        val radiusToApply = if (radius > 0f) radius else null
                        onApplyFilters(autor, vrstaRibe, ocena, startDate, endDate, searchText, radiusToApply)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = FishBoje.Pozadina),
                    modifier = Modifier.weight(1.4f).height(50.dp)
                ) {
                    Text("Primeni", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun DugmeDatuma(
    oznaka: String,
    vrednost: String?,
    onClick: () -> Unit,
    onPonisti: () -> Unit,
    modifier: Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(FishBoje.Polje)
            .border(1.dp, if (vrednost != null) FishBoje.AkcenatTamni else FishBoje.IvicaKartice, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = FishBoje.Akcenat, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(oznaka, color = FishBoje.TekstPriguseni, fontSize = 11.sp)
            Text(vrednost ?: "Bilo kad", color = FishBoje.TekstPrimarni, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        if (vrednost != null) {
            Icon(Icons.Default.Close, contentDescription = "Poništi datum", tint = FishBoje.TekstPriguseni,
                modifier = Modifier.size(16.dp).clickable(onClick = onPonisti))
        }
    }
}
