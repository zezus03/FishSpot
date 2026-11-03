package com.example.fishspot.feature.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.FishPoruka
import com.example.fishspot.core.theme.NaslovSekcije
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ZLATNA = Color(0xFFFFC107)

fun opisMerila(merilo: String) = when (merilo) {
    "ukupno" -> "Najveća ukupna težina"
    "broj" -> "Najviše ulova"
    else -> "Najteža riba"
}

private fun kg(v: Double) = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.').replace('.', ',') + " kg"

private fun brojUlovaTekst(n: Long) = "$n " + if (n % 10 == 1L && n % 100 != 11L) "ulov" else "ulova"

private fun rezultatTekst(merilo: String, u: UcesnikIzazova): String = when (merilo) {
    "broj" -> brojUlovaTekst(u.rezultat.toLong())
    else -> kg(u.rezultat)
}

// Dodatni podatak ispod rezultata: kod broja ulova ukupna težina, kod ukupne težine broj ulova
private fun dodatakRezultata(merilo: String, u: UcesnikIzazova): String? = when (merilo) {
    "broj" -> if (u.brojUlova > 0) "ukupno " + kg(u.ukupnaTezina) else null
    "ukupno" -> brojUlovaTekst(u.brojUlova)
    else -> null
}

private fun datum(ms: Long) = SimpleDateFormat("d.M.yyyy.", Locale.getDefault()).format(Date(ms))

// "Još 5 dana", "Još 3 h", "Počinje za 2 dana", "Završen"
fun stanjeIzazova(iz: Izazov, sada: Long = System.currentTimeMillis()): String {
    fun trajanje(ms: Long): String {
        val dana = ms / 86_400_000L
        val sati = ms / 3_600_000L
        return when {
            dana >= 1 -> "$dana " + if (dana == 1L) "dan" else "dana"
            sati >= 1 -> "$sati h"
            else -> "${(ms / 60_000L).coerceAtLeast(1)} min"
        }
    }
    return when {
        iz.predstojeci(sada) -> "Počinje za " + trajanje(iz.od - sada)
        iz.aktivan(sada) -> "Još " + trajanje(iz.doKraja - sada)
        else -> "Završen"
    }
}

// Kartica Izazovi na rang listi: aktivni, predstojeći i završeni izazovi
@Composable
fun IzazoviSadrzaj(
    vm: IzazoviViewModel, nazivVrste: (String) -> String, onUlovSelected: (String) -> Unit = {},
    pocetniIzazov: String? = null
) {
    val izazovi by vm.izazovi.collectAsState()
    var otvoren by remember { mutableStateOf<Izazov?>(null) }
    // Iz obaveštenja: čim se izazovi učitaju, otvara se taj izazov (samo jednom)
    var pocetniOtvoren by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(izazovi, pocetniIzazov) {
        if (!pocetniOtvoren && pocetniIzazov != null) {
            izazovi.firstOrNull { it.id == pocetniIzazov }?.let { otvoren = it; pocetniOtvoren = true }
        }
    }
    val sada = System.currentTimeMillis()
    val aktivni = izazovi.filter { it.aktivan(sada) }.sortedBy { it.doKraja }
    val predstojeci = izazovi.filter { it.predstojeci(sada) }.sortedBy { it.od }
    val zavrseni = izazovi.filter { sada > it.doKraja }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (izazovi.isEmpty()) item {
            Text(
                "Trenutno nema izazova. Kad se pojavi novi, dobićeš obaveštenje.",
                color = FishBoje.TekstSekundarni, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp)
            )
        }
        listOf("Aktivni izazovi" to aktivni, "Uskoro počinju" to predstojeci, "Završeni" to zavrseni).forEach { (naslov, lista) ->
            if (lista.isNotEmpty()) {
                item { NaslovSekcije(naslov, Modifier.padding(top = 10.dp)) }
                items(lista, key = { it.id }) { iz -> KarticaIzazova(iz, nazivVrste) { otvoren = iz } }
            }
        }
    }

    otvoren?.let { iz ->
        // Uvek najnovija verzija izazova (broj učesnika se menja)
        val svez = izazovi.firstOrNull { it.id == iz.id } ?: iz
        IzazovDetaljiDialog(svez, vm, nazivVrste, onUlovSelected) { otvoren = null }
    }
}

@Composable
private fun KarticaIzazova(iz: Izazov, nazivVrste: (String) -> String, onClick: () -> Unit) {
    val aktivan = iz.aktivan()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, if (aktivan) FishBoje.AkcenatTamni else FishBoje.IvicaKartice, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iz.slikaUrl.isNotBlank()) {
                AsyncImage(iz.slikaUrl, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
            } else Box(
                Modifier.size(40.dp).background((if (aktivan) ZLATNA else FishBoje.TekstPriguseni).copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.EmojiEvents, null, tint = if (aktivan) ZLATNA else FishBoje.TekstPriguseni) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(iz.naziv, color = FishBoje.TekstPrimarni, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(opisMerila(iz.merilo) + " · " + vrsteTekst(iz, nazivVrste), color = FishBoje.TekstSekundarni,
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(stanjeIzazova(iz), color = if (aktivan) FishBoje.Akcenat else FishBoje.TekstPriguseni,
                fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Podatak(Icons.Default.CalendarMonth, "${datum(iz.od)} – ${datum(iz.doKraja)}")
            Podatak(Icons.Default.Group, "${iz.brojUcesnika} " + if (iz.brojUcesnika == 1L) "učesnik" else "učesnika")
        }
    }
}

@Composable
private fun Podatak(ikona: ImageVector, tekst: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(ikona, null, tint = FishBoje.Akcenat, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(tekst, color = FishBoje.TekstSekundarni, fontSize = 12.sp)
    }
}

private fun vrsteTekst(iz: Izazov, nazivVrste: (String) -> String) =
    if (iz.vrste.isEmpty()) "sve vrste" else iz.vrste.joinToString(", ") { nazivVrste(it) }

@Composable
private fun IzazovDetaljiDialog(
    iz: Izazov, vm: IzazoviViewModel, nazivVrste: (String) -> String,
    onUlovSelected: (String) -> Unit, onZatvori: () -> Unit
) {
    LaunchedEffect(iz.id) { vm.otvori(iz.id) }
    var izabraniUid by remember { mutableStateOf<String?>(null) }
    val ucesnici by vm.ucesnici.collectAsState()
    val prijavljujem by vm.prijavljujem.collectAsState()
    val mojIndeks = ucesnici.indexOfFirst { it.uid == vm.mojUid }
    val ucestvujem = mojIndeks >= 0
    val mozeDaSePrijavi = !ucestvujem && System.currentTimeMillis() <= iz.doKraja

    Dialog(onDismissRequest = onZatvori, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = FishBoje.Pozadina) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("FISHSPOT • IZAZOV", color = FishBoje.TekstPriguseni, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(iz.naziv, color = FishBoje.TekstPrimarni, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onZatvori) { Icon(Icons.Default.Close, "Zatvori", tint = FishBoje.TekstPrimarni) }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (iz.slikaUrl.isNotBlank()) item {
                        AsyncImage(iz.slikaUrl, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp)))
                    }
                    item {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FishBoje.Kartica).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Stanje levo, a dugme za učešće gore desno (uvek vidljivo, bez skrolovanja)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stanjeIzazova(iz), color = if (iz.aktivan()) FishBoje.Akcenat else FishBoje.TekstPriguseni,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (mozeDaSePrijavi) {
                                    Button(
                                        onClick = {
                                            vm.pridruziSe(iz.id) { greska ->
                                                FishPoruka.prikazi(greska ?: "Prijavljen si! Tvoji ulovi iz ovog perioda se već računaju.", dugo = true)
                                            }
                                        },
                                        enabled = !prijavljujem,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = FishBoje.Pozadina)
                                    ) {
                                        if (prijavljujem) CircularProgressIndicator(Modifier.size(18.dp), color = FishBoje.Pozadina, strokeWidth = 2.dp)
                                        else Text("Učestvuj", fontWeight = FontWeight.Bold)
                                    }
                                } else if (ucestvujem) {
                                    Text("✓ Učestvuješ", color = FishBoje.Akcenat, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                            .background(FishBoje.Akcenat.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 6.dp))
                                }
                            }
                            if (iz.opis.isNotBlank()) Text(iz.opis, color = FishBoje.TekstSekundarni, fontSize = 14.sp)
                            Pravilo(Icons.Default.EmojiEvents, opisMerila(iz.merilo))
                            Pravilo(Icons.Default.SetMeal, "Vrste: " + vrsteTekst(iz, nazivVrste))
                            Pravilo(Icons.Default.CalendarMonth, "${datum(iz.od)} – ${datum(iz.doKraja)}")
                            if (iz.samoKamera) Pravilo(Icons.Default.CameraAlt, "Samo slike iz kamere u aplikaciji")
                            if (iz.nagrada.isNotBlank()) Pravilo(Icons.Default.CardGiftcard, "Nagrada: ${iz.nagrada}")
                            Pravilo(Icons.Default.Group, "${iz.brojUcesnika} " + if (iz.brojUcesnika == 1L) "učesnik" else "učesnika")
                            Text(
                                "Računaju se provereni ulovi objavljeni tokom izazova (ribe preko 5 kg tek kad ih admin odobri).",
                                color = FishBoje.TekstPriguseni, fontSize = 12.sp
                            )
                        }
                    }
                    if (ucestvujem) item {
                        Text(
                            "Ti si ${mojIndeks + 1}. mesto · ${rezultatTekst(iz.merilo, ucesnici[mojIndeks])}",
                            color = FishBoje.Akcenat, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    item { NaslovSekcije("Rang lista", Modifier.padding(top = 8.dp)) }
                    if (ucesnici.isEmpty()) item {
                        Text("Još nema prijavljenih. Budi prvi!", color = FishBoje.TekstSekundarni, fontSize = 14.sp)
                    }
                    item {
                        Text("Dodirni učesnika da vidiš ulove sa kojima se takmiči.", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                    }
                    itemsIndexed(ucesnici, key = { _, u -> u.uid }) { i, u ->
                        RedUcesnika(i + 1, u, iz.merilo, u.uid == vm.mojUid) { izabraniUid = u.uid }
                    }
                }
            }
        }
    }

    // Uvek najnovija verzija učesnika (rezultat se menja dok je prozor otvoren)
    val izabrani = izabraniUid?.let { uid -> ucesnici.indexOfFirst { it.uid == uid } }?.takeIf { it >= 0 }
    if (izabrani != null) {
        UloviUcesnikaDialog(
            mesto = izabrani + 1, u = ucesnici[izabrani], merilo = iz.merilo,
            onUlov = { id -> izabraniUid = null; onZatvori(); onUlovSelected(id) },
            onZatvori = { izabraniUid = null }
        )
    }
}

@Composable
private fun UloviUcesnikaDialog(
    mesto: Int, u: UcesnikIzazova, merilo: String, onUlov: (String) -> Unit, onZatvori: () -> Unit
) {
    Dialog(onDismissRequest = onZatvori, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = FishBoje.Pozadina) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(FishBoje.Krug), contentAlignment = Alignment.Center) {
                        if (u.slika.isNotBlank()) AsyncImage(u.slika, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text(u.ime.take(1).uppercase(), color = FishBoje.Akcenat, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$mesto. MESTO", color = FishBoje.TekstPriguseni, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(u.ime, color = FishBoje.TekstPrimarni, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onZatvori) { Icon(Icons.Default.Close, "Zatvori", tint = FishBoje.TekstPrimarni) }
                }
                // Sažetak: rezultat, broj ulova i ukupna težina
                Row(
                    Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp)).background(FishBoje.Kartica).padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Statistika("Rezultat", rezultatTekst(merilo, u))
                    Statistika("Ulova", "${u.brojUlova}")
                    Statistika("Ukupno", kg(u.ukupnaTezina))
                }
                if (u.ulovi.isEmpty()) {
                    Text(
                        "Još nema ulova koji se računaju u ovom izazovu.",
                        color = FishBoje.TekstSekundarni, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(u.ulovi, key = { it.id }) { ulov -> RedUlova(ulov) { onUlov(ulov.id) } }
                }
            }
        }
    }
}

@Composable
private fun Statistika(naziv: String, vrednost: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(vrednost, color = FishBoje.TekstPrimarni, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(naziv, color = FishBoje.TekstPriguseni, fontSize = 12.sp)
    }
}

@Composable
private fun RedUlova(ulov: UlovUIzazovu, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(FishBoje.Krug), contentAlignment = Alignment.Center) {
            if (ulov.slikaUrl.isNotBlank()) AsyncImage(ulov.slikaUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Default.SetMeal, null, tint = FishBoje.TekstPriguseni)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ulov.vrsta.ifBlank { "Riba" }, color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (ulov.vreme > 0) Text(datum(ulov.vreme), color = FishBoje.TekstPriguseni, fontSize = 12.sp)
        }
        Text(kg(ulov.tezina), color = FishBoje.TekstPrimarni, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun Pravilo(ikona: ImageVector, tekst: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(ikona, null, tint = FishBoje.Akcenat, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(tekst, color = FishBoje.TekstPrimarni, fontSize = 14.sp)
    }
}

@Composable
private fun RedUcesnika(mesto: Int, u: UcesnikIzazova, merilo: String, jaSam: Boolean, onClick: () -> Unit) {
    val boja = when (mesto) { 1 -> ZLATNA; 2 -> Color(0xFFB0BEC5); 3 -> Color(0xFFCD7F32); else -> FishBoje.TekstPriguseni }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (jaSam) FishBoje.Akcenat.copy(alpha = 0.12f) else FishBoje.Kartica)
            .border(1.dp, if (jaSam) FishBoje.AkcenatTamni else FishBoje.IvicaKartice, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$mesto.", color = boja, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(32.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).background(FishBoje.Krug), contentAlignment = Alignment.Center) {
            if (u.slika.isNotBlank()) AsyncImage(u.slika, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(u.ime.take(1).uppercase(), color = FishBoje.Akcenat, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(u.ime + if (jaSam) " (ti)" else "", color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (u.najboljiVrsta.isNotBlank()) Text(u.najboljiVrsta, color = FishBoje.TekstPriguseni, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (u.najboljiSlika.isNotBlank()) {
            AsyncImage(u.najboljiSlika, null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(10.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(rezultatTekst(merilo, u), color = FishBoje.TekstPrimarni, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            dodatakRezultata(merilo, u)?.let { Text(it, color = FishBoje.TekstPriguseni, fontSize = 12.sp) }
        }
    }
}
