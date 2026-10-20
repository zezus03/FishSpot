package com.example.fishspot.feature.spots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.NaslovSekcije
import com.example.fishspot.core.theme.fishPoljeBoje
import com.example.fishspot.feature.fish.TezinaRibe
import com.example.fishspot.feature.fish.VrstaRibe
import com.example.fishspot.feature.fish.VrsteRibaViewModel
import java.util.Locale

// Podaci koje autor može da promeni na svom ulovu
data class IzmenaUlova(
    val naziv: String,
    val vrsta: VrstaRibe,
    val duzinaRibe: Double,
    val tezinaRibe: Double,
    val mamac: String,
    val tehnika: String,
    val ocena: Float,
    val opis: String
)

@Composable
fun IzmenaUlovaDialog(
    spot: FishSpot,
    onDismiss: () -> Unit,
    onSacuvaj: (IzmenaUlova) -> Unit,
    vrsteRibaViewModel: VrsteRibaViewModel = viewModel()
) {
    val sveVrste by vrsteRibaViewModel.vrste.collectAsState()

    var naziv by remember { mutableStateOf(spot.naziv) }
    var izabranaVrsta by remember {
        mutableStateOf(
            spot.vrstaId.takeIf { it.isNotBlank() }
                ?.let { VrstaRibe(it, spot.vrstaRibe.substringBefore(" (").trim(), spot.latinskiNaziv) }
        )
    }
    var vrstaUnos by remember { mutableStateOf(izabranaVrsta?.prikaz ?: "") }
    var duzina by remember { mutableStateOf(if (spot.duzinaRibe > 0) formatBroj(spot.duzinaRibe, 1) else "") }
    var tezina by remember { mutableStateOf(if (spot.tezinaRibe > 0) formatBroj(spot.tezinaRibe, 2) else "") }
    var tezinaRucno by remember { mutableStateOf(false) }
    var mamac by remember { mutableStateOf(spot.mamac.ifBlank { OpcijeUlova.mamci.first() }) }
    var tehnika by remember { mutableStateOf(spot.tehnika.ifBlank { OpcijeUlova.tehnike.first() }) }
    var ocena by remember { mutableStateOf(spot.ocena.coerceIn(1f, 5f)) }
    var opis by remember { mutableStateOf(spot.opis) }

    // Kad se promeni vrsta ili dužina, težina se ponovo procenjuje (osim ako je uneta ručno)
    var prviPut by remember { mutableStateOf(true) }
    LaunchedEffect(izabranaVrsta, duzina) {
        if (prviPut) { prviPut = false; return@LaunchedEffect }
        if (!tezinaRucno) {
            val kg = TezinaRibe.proceniKg(izabranaVrsta?.naziv ?: "", duzina.replace(',', '.').toDoubleOrNull())
            if (kg != null) tezina = formatBroj(kg, 2)
        }
    }

    val duzinaBroj = duzina.replace(',', '.').toDoubleOrNull()
    val tezinaBroj = tezina.replace(',', '.').toDoubleOrNull()
    val greska = when {
        naziv.isBlank() -> "Unesi naziv mesta"
        izabranaVrsta == null -> "Izaberi vrstu ribe sa liste"
        duzinaBroj == null || duzinaBroj <= 0 || duzinaBroj > 300 -> "Unesi dužinu ribe (cm)"
        tezinaBroj == null || tezinaBroj <= 0 || tezinaBroj > 150 -> "Unesi težinu ribe (kg)"
        else -> null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = FishBoje.Pozadina) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Zatvori", tint = FishBoje.TekstPrimarni)
                    }
                    Text(
                        "Izmeni ulov",
                        color = FishBoje.TekstPrimarni,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    NaslovSekcije("Riba")
                    val predlozi = remember(vrstaUnos, sveVrste) { vrsteRibaViewModel.pretrazi(vrstaUnos).take(5) }
                    OutlinedTextField(
                        value = vrstaUnos,
                        onValueChange = { vrstaUnos = it; izabranaVrsta = null },
                        label = { Text("Vrsta ribe") },
                        singleLine = true,
                        isError = izabranaVrsta == null && vrstaUnos.trim().length >= 3 && predlozi.isEmpty(),
                        trailingIcon = if (vrstaUnos.isNotEmpty()) {
                            {
                                IconButton(onClick = { vrstaUnos = ""; izabranaVrsta = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Obriši")
                                }
                            }
                        } else null,
                        colors = fishPoljeBoje(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Predlozi se prikazuju ispod polja tek posle 3 slova
                    if (izabranaVrsta == null && vrstaUnos.trim().length >= 3 && predlozi.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(FishBoje.Kartica)
                                .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(14.dp))
                        ) {
                            predlozi.forEach { vrsta ->
                                Text(
                                    vrsta.prikaz,
                                    color = FishBoje.TekstPrimarni,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            izabranaVrsta = vrsta
                                            vrstaUnos = vrsta.prikaz
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = duzina,
                            onValueChange = { duzina = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                            label = { Text("Dužina") },
                            suffix = { Text("cm") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = fishPoljeBoje(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tezina,
                            onValueChange = {
                                tezina = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6)
                                tezinaRucno = true
                            },
                            label = { Text("Težina") },
                            suffix = { Text("kg") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = fishPoljeBoje(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    NaslovSekcije("Mamac")
                    IzborCipovima(opcije = OpcijeUlova.mamci, izabrano = mamac, onIzbor = { mamac = it })
                    NaslovSekcije("Tehnika")
                    IzborCipovima(opcije = OpcijeUlova.tehnike, izabrano = tehnika, onIzbor = { tehnika = it })

                    NaslovSekcije("Mesto")
                    OutlinedTextField(
                        value = naziv,
                        onValueChange = { naziv = it.take(60) },
                        label = { Text("Naziv mesta") },
                        singleLine = true,
                        colors = fishPoljeBoje(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        (1..5).forEach { zvezda ->
                            IconButton(onClick = { ocena = zvezda.toFloat() }, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    if (zvezda <= ocena.toInt()) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Ocena $zvezda",
                                    tint = if (zvezda <= ocena.toInt()) FishBoje.Zvezdica else FishBoje.TekstPriguseni,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = opis,
                        onValueChange = { opis = it.take(500) },
                        label = { Text("Opis") },
                        minLines = 3,
                        colors = fishPoljeBoje(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    if (greska != null) {
                        Text(greska, color = FishBoje.TekstPriguseni, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Button(
                        onClick = {
                            onSacuvaj(
                                IzmenaUlova(
                                    naziv = naziv.trim(),
                                    vrsta = izabranaVrsta!!,
                                    duzinaRibe = duzinaBroj!!,
                                    tezinaRibe = tezinaBroj!!,
                                    mamac = mamac,
                                    tehnika = tehnika,
                                    ocena = ocena,
                                    opis = opis.trim()
                                )
                            )
                        },
                        enabled = greska == null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FishBoje.Akcenat,
                            contentColor = FishBoje.Pozadina,
                            disabledContainerColor = FishBoje.Kartica,
                            disabledContentColor = FishBoje.TekstPriguseni
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Sačuvaj izmene", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

private fun formatBroj(vrednost: Double, decimala: Int): String =
    String.format(Locale.US, "%.${decimala}f", vrednost).trimEnd('0').trimEnd('.')
