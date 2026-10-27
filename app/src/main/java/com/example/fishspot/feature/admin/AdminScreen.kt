package com.example.fishspot.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.FishZaglavlje
import com.example.fishspot.core.theme.NaslovSekcije
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Filter(val naziv: String) { SVI("Svi"), AKTIVNI("Aktivni"), UKLONJENI("Uklonjeni") }

@Composable
fun AdminScreen(onNavigateBack: () -> Unit, viewModel: AdminViewModel = viewModel()) {
    val prijavljeni by viewModel.prijavljeni.collectAsState()
    val statistika by viewModel.statistika.collectAsState()
    val ucitava by viewModel.ucitava.collectAsState()
    val uToku by viewModel.uToku.collectAsState()
    val trebaPotvrda by viewModel.trebaPotvrda.collectAsState()
    var filter by remember { mutableStateOf(Filter.SVI) }
    var potvrda by remember { mutableStateOf<Pair<PrijavljenUlov, Boolean>?>(null) } // (ulov, brisanje?)

    val prikazani = prijavljeni.filter {
        when (filter) {
            Filter.SVI -> true
            Filter.AKTIVNI -> it.status == "aktivan" || it.status == "ponovo"
            Filter.UKLONJENI -> it.status == "uklonjen"
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(FishBoje.Pozadina)) {
        FishZaglavlje(
            oznaka = "FishSpot • admin",
            naslov = "Admin panel",
            onNazad = onNavigateBack,
            ikona = Icons.Default.AdminPanelSettings
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (trebaPotvrda) item {
                PotvrdaAdrese(onPosalji = { viewModel.posaljiLinkZaPotvrdu() }, onPotvrdio = { viewModel.proveriPotvrdu() })
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Plocica(Icons.Default.People, "${statistika.korisnika}", "korisnika", Modifier.weight(1f))
                    Plocica(Icons.Default.Phishing, "${statistika.ulova}", "ulova", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Plocica(Icons.Default.Flag, "${statistika.prijavaDanas}", "prijava danas", Modifier.weight(1f))
                    Plocica(Icons.Default.Block, "${statistika.uklonjenih}", "čeka odluku", Modifier.weight(1f), FishBoje.Greska)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(Filter.values().toList()) { f ->
                            val izabran = f == filter
                            Text(
                                f.naziv,
                                color = if (izabran) FishBoje.Pozadina else FishBoje.TekstSekundarni,
                                fontWeight = if (izabran) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (izabran) FishBoje.Akcenat else FishBoje.Kartica)
                                    .border(1.dp, if (izabran) FishBoje.Akcenat else FishBoje.IvicaKartice, RoundedCornerShape(20.dp))
                                    .clickable { filter = f }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.osvezi() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Osveži", tint = FishBoje.Akcenat)
                    }
                }
            }
            when {
                ucitava && prijavljeni.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FishBoje.Akcenat)
                    }
                }
                prikazani.isEmpty() -> item {
                    Text("Nema prijava koje čekaju odluku.", color = FishBoje.TekstPriguseni,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
                else -> items(prikazani, key = { it.id }) { ulov ->
                    KarticaPrijave(
                        ulov = ulov,
                        uToku = uToku == ulov.id,
                        onURedu = { potvrda = ulov to false },
                        onObrisi = { potvrda = ulov to true }
                    )
                }
            }
        }
    }

    potvrda?.let { (ulov, brisanje) ->
        AlertDialog(
            onDismissRequest = { potvrda = null },
            containerColor = FishBoje.Kartica,
            titleContentColor = FishBoje.TekstPrimarni,
            textContentColor = FishBoje.TekstSekundarni,
            title = { Text(if (brisanje) "Obriši ulov trajno?" else "Ulov je u redu?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (brisanje) "${ulov.vrstaRibe} (${ulov.autorUlova}) se briše zauvek, zajedno sa slikom i komentarima. Ovo ne može da se poništi."
                    else "${ulov.vrstaRibe} (${ulov.autorUlova}) se vraća na mapu, autoru se vraćaju poeni, a nove prijave ga više ne uklanjaju."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (brisanje) viewModel.obrisiTrajno(ulov.id) else viewModel.ulovURedu(ulov.id)
                        potvrda = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (brisanje) FishBoje.Greska else FishBoje.Akcenat,
                        contentColor = if (brisanje) Color.White else FishBoje.Pozadina
                    )
                ) { Text(if (brisanje) "Obriši trajno" else "Vrati ulov", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { potvrda = null }) { Text("Otkaži", color = FishBoje.TekstSekundarni) } }
        )
    }
}

@Composable
private fun KarticaPrijave(ulov: PrijavljenUlov, uToku: Boolean, onURedu: () -> Unit, onObrisi: () -> Unit) {
    val (boja, oznaka) = when (ulov.status) {
        "uklonjen" -> FishBoje.Greska to "Uklonjen (oduzeto ${ulov.oduzetoPoena} poena)"
        "ponovo" -> FishBoje.Upozorenje to "Ponovo prijavljen posle vraćanja"
        else -> FishBoje.Upozorenje to "Aktivan na mapi"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, if (ulov.status == "uklonjen") boja.copy(alpha = 0.5f) else FishBoje.IvicaKartice, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row {
            if (ulov.slikaUrl.isNotBlank()) {
                AsyncImage(
                    model = ulov.slikaUrl, contentDescription = ulov.vrstaRibe, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("$oznaka · ${ulov.brojPrijava} prijav${if (ulov.brojPrijava == 1) "a" else "e"}",
                    color = boja, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(ulov.vrstaRibe.ifBlank { "Nepoznata vrsta" }, color = FishBoje.TekstPrimarni, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${broj(ulov.duzinaRibe)} cm · ${broj(ulov.tezinaRibe)} kg · ${ulov.mamac} / ${ulov.tehnika}",
                    color = FishBoje.TekstSekundarni, fontSize = 13.sp)
                Text("Autor: ${ulov.autorUlova}", color = FishBoje.TekstSekundarni, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        NaslovSekcije("Razlozi prijave")
        ulov.razlozi.forEach { r ->
            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).background(boja, CircleShape))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(r.razlog + if (r.opis.isNotBlank()) " – ${r.opis}" else "", color = FishBoje.TekstPrimarni, fontSize = 14.sp)
                    Text("${r.prijavio} · ${r.vreme?.let { vreme(it) } ?: ""}", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                }
            }
        }
        run {
            Spacer(Modifier.height(12.dp))
            if (uToku) {
                LinearProgressIndicator(color = FishBoje.Akcenat, trackColor = FishBoje.Krug, modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    run {
                        Button(
                            onClick = onURedu,
                            colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = FishBoje.Pozadina),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("U redu je", fontWeight = FontWeight.Bold) }
                    }
                    OutlinedButton(
                        onClick = onObrisi,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FishBoje.Greska),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FishBoje.Greska.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Obriši trajno", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun PotvrdaAdrese(onPosalji: () -> Unit, onPotvrdio: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, FishBoje.Upozorenje.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MarkEmailUnread, null, tint = FishBoje.Upozorenje)
            Spacer(Modifier.width(10.dp))
            Text("Potvrdi e-adresu", color = FishBoje.TekstPrimarni, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Radi bezbednosti admin panel radi samo sa potvrđenom e-adresom. Pošalji link, otvori mail, klikni na link, " +
                "pa se vrati ovde i pritisni \"Potvrdio sam\".",
            color = FishBoje.TekstSekundarni, fontSize = 14.sp, lineHeight = 20.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onPosalji,
                colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = FishBoje.Pozadina),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)
            ) { Text("Pošalji link", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = onPotvrdio,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FishBoje.Akcenat),
                border = androidx.compose.foundation.BorderStroke(1.dp, FishBoje.Akcenat),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)
            ) { Text("Potvrdio sam", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Plocica(ikona: ImageVector, broj: String, oznaka: String, modifier: Modifier, boja: Color = FishBoje.Akcenat) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(FishBoje.Kartica, RoundedCornerShape(16.dp))
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Box(Modifier.size(34.dp).background(boja.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(ikona, null, tint = boja, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(broj, color = FishBoje.TekstPrimarni, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(oznaka, color = FishBoje.TekstPriguseni, fontSize = 12.sp)
        }
    }
}

private fun broj(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
private fun vreme(ms: Long) = SimpleDateFormat("d.M. HH:mm", Locale.getDefault()).format(Date(ms))
