package com.example.fishspot.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.FishPoruka
import com.example.fishspot.core.theme.NaslovSekcije
import com.example.fishspot.core.theme.fishPoljeBoje
import com.example.fishspot.feature.fish.VrstaRibe
import com.example.fishspot.feature.ranking.Izazov
import com.example.fishspot.feature.ranking.opisMerila
import com.example.fishspot.feature.ranking.stanjeIzazova
import com.google.firebase.functions.FirebaseFunctions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val MERILA = listOf("najteza", "ukupno", "broj")

// Odeljak Izazovi u admin panelu: spisak, novi izazov, izmena i brisanje (sve ide preko servera)
@Composable
fun AdminIzazoviOdeljak(izazovi: List<Izazov>, sveVrste: List<VrstaRibe>) {
    var forma by remember { mutableStateOf<Izazov?>(null) }
    var novaForma by remember { mutableStateOf(false) }
    var zaBrisanje by remember { mutableStateOf<Izazov?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Izazovi", color = FishBoje.TekstPrimarni, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(
                onClick = { novaForma = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = FishBoje.Pozadina)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Novi izazov", fontWeight = FontWeight.Bold)
            }
        }
        izazovi.forEach { iz ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FishBoje.Kartica)
                    .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(14.dp)).padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(iz.naziv, color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold)
                    Text("${opisMerila(iz.merilo)} · ${stanjeIzazova(iz)} · ${iz.brojUcesnika} učesnika",
                        color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                }
                IconButton(onClick = { forma = iz }) { Icon(Icons.Default.Edit, "Izmeni", tint = FishBoje.Akcenat) }
                IconButton(onClick = { zaBrisanje = iz }) { Icon(Icons.Default.DeleteOutline, "Obriši", tint = FishBoje.Greska) }
            }
        }
        if (izazovi.isEmpty()) Text("Još nema izazova.", color = FishBoje.TekstPriguseni, fontSize = 13.sp)
    }

    if (novaForma || forma != null) {
        IzazovForma(postojeci = forma, sveVrste = sveVrste, onZatvori = { novaForma = false; forma = null })
    }

    zaBrisanje?.let { iz ->
        AlertDialog(
            onDismissRequest = { zaBrisanje = null },
            containerColor = FishBoje.Kartica,
            titleContentColor = FishBoje.TekstPrimarni,
            textContentColor = FishBoje.TekstSekundarni,
            title = { Text("Obriši izazov?", fontWeight = FontWeight.Bold) },
            text = { Text("„${iz.naziv}“ i njegova rang lista se brišu zauvek. Ulovi i poeni ostaju.") },
            confirmButton = {
                Button(
                    onClick = {
                        FirebaseFunctions.getInstance().getHttpsCallable("adminObrisiIzazov").call(mapOf("id" to iz.id))
                            .addOnSuccessListener { FishPoruka.prikazi("Izazov je obrisan") }
                            .addOnFailureListener { FishPoruka.prikazi("Brisanje nije uspelo", dugo = true) }
                        zaBrisanje = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Greska)
                ) { Text("Obriši", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { zaBrisanje = null }) { Text("Otkaži", color = FishBoje.TekstSekundarni) } }
        )
    }
}

private fun datum(ms: Long) = SimpleDateFormat("d.M.yyyy.", Locale.getDefault()).format(Date(ms))

// Početak izabranog dana (00:00) ili kraj (23:59:59), po lokalnom vremenu
private fun danUMs(utcPonoc: Long, kraj: Boolean): Long {
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcPonoc }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        if (kraj) { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun IzazovForma(postojeci: Izazov?, sveVrste: List<VrstaRibe>, onZatvori: () -> Unit) {
    val danas = remember { danUMs(System.currentTimeMillis().let { it - it % 86_400_000L }, false) }
    var naziv by remember { mutableStateOf(postojeci?.naziv ?: "") }
    var opis by remember { mutableStateOf(postojeci?.opis ?: "") }
    var nagrada by remember { mutableStateOf(postojeci?.nagrada ?: "") }
    var od by remember { mutableStateOf(postojeci?.od ?: danas) }
    var doKraja by remember { mutableStateOf(postojeci?.doKraja ?: (danas + 7 * 86_400_000L - 1000)) }
    var merilo by remember { mutableStateOf(postojeci?.merilo ?: "najteza") }
    var vrste by remember { mutableStateOf(postojeci?.vrste?.toSet() ?: emptySet()) }
    var samoKamera by remember { mutableStateOf(postojeci?.samoKamera ?: true) }
    var biramDatum by remember { mutableStateOf<Boolean?>(null) } // true = početak, false = kraj
    var cuvam by remember { mutableStateOf(false) }
    // Slika izazova: nova (JPEG, base64) ili uklanjanje postojeće
    val kontekst = androidx.compose.ui.platform.LocalContext.current
    var novaSlika by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var ukloniSliku by remember { mutableStateOf(false) }
    val biracSlike = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        novaSlika = try { com.example.fishspot.feature.fish.ucitajSlikuUspravno(kontekst, uri, 1080) } catch (e: Exception) { null }
        if (novaSlika != null) ukloniSliku = false else FishPoruka.prikazi("Slika nije učitana")
    }
    val greska = when {
        naziv.isBlank() -> "Unesi naziv"
        doKraja <= od -> "Kraj mora biti posle početka"
        else -> null
    }

    Dialog(onDismissRequest = onZatvori, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = FishBoje.Pozadina) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onZatvori) { Icon(Icons.Default.Close, "Zatvori", tint = FishBoje.TekstPrimarni) }
                    Text(if (postojeci == null) "Novi izazov" else "Izmeni izazov", color = FishBoje.TekstPrimarni,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(naziv, { naziv = it.take(80) }, label = { Text("Naziv, npr. Najveći šaran novembra") },
                        singleLine = true, colors = fishPoljeBoje(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(opis, { opis = it.take(600) }, label = { Text("Opis (opciono)") }, minLines = 2,
                        colors = fishPoljeBoje(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nagrada, { nagrada = it.take(120) }, label = { Text("Nagrada (opciono)") }, singleLine = true,
                        colors = fishPoljeBoje(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())

                    NaslovSekcije("Slika izazova (opciono)")
                    val postojecaSlika = postojeci?.slikaUrl.orEmpty().takeIf { it.isNotBlank() && !ukloniSliku }
                    if (novaSlika != null || postojecaSlika != null) {
                        Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp))) {
                            novaSlika?.let {
                                androidx.compose.foundation.Image(it.asImageBitmap(), null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } ?: coil.compose.AsyncImage(postojecaSlika, null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { biracSlike.launch("image/*") }, shape = RoundedCornerShape(12.dp)) {
                            Text(if (novaSlika != null || postojecaSlika != null) "Promeni sliku" else "Izaberi sliku", color = FishBoje.TekstPrimarni)
                        }
                        if (novaSlika != null || postojecaSlika != null) {
                            TextButton(onClick = { novaSlika = null; ukloniSliku = true }) { Text("Ukloni", color = FishBoje.Greska) }
                        }
                    }

                    NaslovSekcije("Trajanje")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { biramDatum = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                            Text("Od: ${datum(od)}", color = FishBoje.TekstPrimarni)
                        }
                        OutlinedButton(onClick = { biramDatum = false }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                            Text("Do: ${datum(doKraja)}", color = FishBoje.TekstPrimarni)
                        }
                    }

                    NaslovSekcije("Šta se meri")
                    com.example.fishspot.feature.spots.IzborCipovima(
                        opcije = MERILA.map { opisMerila(it) },
                        izabrano = opisMerila(merilo),
                        onIzbor = { izbor -> merilo = MERILA.first { opisMerila(it) == izbor } }
                    )

                    NaslovSekcije(if (vrste.isEmpty()) "Vrste (sve)" else "Vrste (${vrste.size})")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        sveVrste.forEach { v ->
                            val izabrana = v.id in vrste
                            Text(
                                v.naziv,
                                color = if (izabrana) FishBoje.Pozadina else FishBoje.TekstSekundarni,
                                fontSize = 13.sp,
                                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                    .background(if (izabrana) FishBoje.Akcenat else FishBoje.Kartica)
                                    .border(1.dp, if (izabrana) FishBoje.Akcenat else FishBoje.IvicaKartice, RoundedCornerShape(16.dp))
                                    .clickable { vrste = if (izabrana) vrste - v.id else vrste + v.id }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Samo slike iz kamere u aplikaciji", color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold)
                            Text("Preporuka za takmičenja", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                        }
                        Switch(checked = samoKamera, onCheckedChange = { samoKamera = it }, colors = SwitchDefaults.colors(
                            checkedThumbColor = FishBoje.Pozadina, checkedTrackColor = FishBoje.Akcenat,
                            uncheckedThumbColor = FishBoje.TekstPriguseni, uncheckedTrackColor = FishBoje.Polje
                        ))
                    }
                    if (postojeci == null) {
                        Text("Svi korisnici dobijaju obaveštenje o novom izazovu.", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                    } else {
                        Text("Posle izmene server ponovo računa rezultate svih učesnika.", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (greska != null) Text(greska, color = FishBoje.TekstPriguseni, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
                Button(
                    onClick = {
                        cuvam = true
                        val podaci = mutableMapOf<String, Any>(
                            "naziv" to naziv.trim(), "opis" to opis.trim(), "nagrada" to nagrada.trim(),
                            "od" to od, "do" to doKraja, "merilo" to merilo, "vrste" to vrste.toList(), "samoKamera" to samoKamera
                        )
                        postojeci?.let { podaci["id"] = it.id }
                        novaSlika?.let { b ->
                            val izlaz = java.io.ByteArrayOutputStream()
                            b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, izlaz)
                            podaci["slika"] = android.util.Base64.encodeToString(izlaz.toByteArray(), android.util.Base64.NO_WRAP)
                        }
                        if (ukloniSliku) podaci["ukloniSliku"] = true
                        FirebaseFunctions.getInstance().getHttpsCallable("adminSacuvajIzazov").call(podaci)
                            .addOnSuccessListener { FishPoruka.prikazi(if (postojeci == null) "Izazov je objavljen" else "Izazov je izmenjen"); onZatvori() }
                            .addOnFailureListener { cuvam = false; FishPoruka.prikazi("Čuvanje nije uspelo: ${it.message}", dugo = true) }
                    },
                    enabled = greska == null && !cuvam,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = FishBoje.Pozadina)
                ) {
                    if (cuvam) CircularProgressIndicator(Modifier.size(22.dp), color = FishBoje.Pozadina, strokeWidth = 2.dp)
                    else Text(if (postojeci == null) "Objavi izazov" else "Sačuvaj izmene", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    biramDatum?.let { pocetak ->
        val stanje = rememberDatePickerState(initialSelectedDateMillis = (if (pocetak) od else doKraja).let {
            val c = Calendar.getInstance().apply { timeInMillis = it }
            Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
        })
        DatePickerDialog(
            onDismissRequest = { biramDatum = null },
            confirmButton = {
                TextButton(onClick = {
                    stanje.selectedDateMillis?.let { if (pocetak) od = danUMs(it, false) else doKraja = danUMs(it, true) }
                    biramDatum = null
                }) { Text("U redu") }
            },
            dismissButton = { TextButton(onClick = { biramDatum = null }) { Text("Otkaži") } }
        ) { DatePicker(state = stanje) }
    }
}
