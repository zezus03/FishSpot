package com.example.fishspot.feature.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.NaslovSekcije
import com.example.fishspot.core.theme.fishPoljeBoje
import com.example.fishspot.feature.fish.LovostajChecker
import java.util.Locale

// Boje za smer vode: rast plavo, pad narandžasto (kao boje parametara u prognozi)
private val VodaRaste = Color(0xFF4FC3F7)
private val VodaOpada = Color(0xFFFFA726)

// Ekran sa vodostajima reka (podaci RHMZ-a preko servera), u stilu vremenske prognoze
@Composable
fun VodostajiScreen(viewModel: WaterLevelViewModel, onDismiss: () -> Unit) {
    val stanice by viewModel.stanice.collectAsState()
    val merenje by viewModel.merenje.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val greska by viewModel.greska.collectAsState()

    var pretraga by remember { mutableStateOf("") }
    var izabranaReka by remember { mutableStateOf<String?>(null) }
    var otvorenaStanica by remember { mutableStateOf<String?>(null) }

    // Reke sa najviše stanica idu u filtere
    val glavneReke = remember(stanice) {
        stanice.groupingBy { it.reka }.eachCount().entries.sortedByDescending { it.value }.take(10).map { it.key }
    }
    val prikazane = remember(stanice, pretraga, izabranaReka) {
        val trazeno = LovostajChecker.normalizujTekst(pretraga)
        stanice.filter { s ->
            (izabranaReka == null || s.reka == izabranaReka) &&
                (trazeno.isEmpty() ||
                    LovostajChecker.normalizujTekst(s.reka).contains(trazeno) ||
                    LovostajChecker.normalizujTekst(s.stanica).contains(trazeno))
        }.groupBy { it.reka }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FishBoje.Pozadina)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { Zaglavlje(merenje = merenje, onDismiss = onDismiss) }

            item { Sazetak(stanice) }

            item {
                OutlinedTextField(
                    value = pretraga,
                    onValueChange = { pretraga = it },
                    placeholder = { Text("Pretraži reku ili stanicu") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (pretraga.isNotEmpty()) {
                            IconButton(onClick = { pretraga = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Obriši pretragu")
                            }
                        }
                    },
                    singleLine = true,
                    colors = fishPoljeBoje(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (glavneReke.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        item { FilterReke("Sve reke", izabranaReka == null) { izabranaReka = null } }
                        items(glavneReke) { reka ->
                            FilterReke(lepNaziv(reka), izabranaReka == reka) {
                                izabranaReka = if (izabranaReka == reka) null else reka
                            }
                        }
                    }
                }
            }

            when {
                isLoading && stanice.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FishBoje.Akcenat)
                    }
                }
                greska != null && stanice.isEmpty() -> item { Poruka(greska ?: "") }
                prikazane.isEmpty() -> item { Poruka("Nema stanica za \"$pretraga\".") }
            }

            prikazane.forEach { (reka, staniceReke) ->
                item(key = "reka_$reka") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
                    ) {
                        NaslovSekcije(lepNaziv(reka))
                        Spacer(Modifier.width(6.dp))
                        Text("· ${staniceReke.size}", color = FishBoje.TekstPriguseni, fontSize = 11.sp)
                    }
                }
                items(staniceReke, key = { it.id }) { stanica ->
                    KarticaStanice(
                        stanica = stanica,
                        otvorena = otvorenaStanica == stanica.id,
                        viewModel = viewModel,
                        onClick = {
                            if (otvorenaStanica == stanica.id) {
                                otvorenaStanica = null
                                viewModel.zatvoriDetalje()
                            } else {
                                otvorenaStanica = stanica.id
                                viewModel.ucitajDetalje(stanica.id)
                            }
                        }
                    )
                }
            }

            item {
                Text(
                    "Izvor: RHMZ Srbije (hidmet.gov.rs). Stanje se meri jednom dnevno u 8 časova, " +
                        "a merenja na sat po stanici osvežavamo svakog sata.",
                    color = FishBoje.TekstPriguseni,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun Zaglavlje(merenje: String?, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(FishBoje.ZaglavljeGore, FishBoje.Pozadina)))
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text(
            "VODOSTAJ REKA • RHMZ",
            color = FishBoje.TekstPriguseni,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.align(Alignment.TopStart)
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(36.dp)
                .background(FishBoje.Krug, CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Zatvori", tint = FishBoje.TekstSekundarni,
                modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.padding(top = 36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).background(FishBoje.Krug, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Waves, contentDescription = null, tint = FishBoje.Akcenat,
                        modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Stanje voda", color = FishBoje.TekstPrimarni, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            if (merenje != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(FishBoje.Krug, RoundedCornerShape(20.dp))
                        .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(Modifier.size(7.dp).background(FishBoje.Akcenat, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("Merenje: $merenje", color = FishBoje.TekstPrimarni, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// Koliko stanica beleži rast, pad i stagnaciju
@Composable
private fun Sazetak(stanice: List<StanicaVodostaja>) {
    if (stanice.isEmpty()) return
    // Po promeni u odnosu na prethodni dan (ista vrednost kao značka na kartici stanice)
    val raste = stanice.count { (it.promena ?: 0) > 0 }
    val opada = stanice.count { (it.promena ?: 0) < 0 }
    val miruje = stanice.size - raste - opada
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        PlocicaSazetka(Icons.Default.ArrowUpward, "$raste", "raste", VodaRaste, Modifier.weight(1f))
        PlocicaSazetka(Icons.Default.ArrowDownward, "$opada", "opada", VodaOpada, Modifier.weight(1f))
        PlocicaSazetka(Icons.Default.Remove, "$miruje", "bez promene", FishBoje.TekstSekundarni, Modifier.weight(1f))
    }
}

@Composable
private fun PlocicaSazetka(ikona: ImageVector, broj: String, oznaka: String, boja: Color, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(FishBoje.Kartica, RoundedCornerShape(16.dp))
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(boja.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(ikona, contentDescription = null, tint = boja, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(broj, color = FishBoje.TekstPrimarni, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(oznaka, color = FishBoje.TekstPriguseni, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun FilterReke(tekst: String, izabran: Boolean, onClick: () -> Unit) {
    Text(
        tekst,
        color = if (izabran) FishBoje.Pozadina else FishBoje.TekstSekundarni,
        fontSize = 13.sp,
        fontWeight = if (izabran) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (izabran) FishBoje.Akcenat else FishBoje.Kartica)
            .border(1.dp, if (izabran) FishBoje.Akcenat else FishBoje.IvicaKartice, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun KarticaStanice(
    stanica: StanicaVodostaja,
    otvorena: Boolean,
    viewModel: WaterLevelViewModel,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, if (otvorena) FishBoje.AkcenatTamni else FishBoje.IvicaKartice, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(lepNaziv(stanica.stanica), color = FishBoje.TekstPrimarni, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold)
                Text(lepNaziv(stanica.reka), color = FishBoje.TekstPriguseni, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(stanica.vodostaj?.toString() ?: "—", color = FishBoje.TekstPrimarni, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold)
                    if (stanica.vodostaj != null) {
                        Spacer(Modifier.width(3.dp))
                        Text("cm", color = FishBoje.TekstPriguseni, fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
                PromenaZnacka(stanica.promena)
            }
        }

        val dodatno = listOfNotNull(
            stanica.proticaj?.let { Icons.Default.Speed to "${formatBroj(it)} m³/s" },
            stanica.temperatura?.let { Icons.Default.Thermostat to "${formatBroj(it)} °C" }
        )
        if (dodatno.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dodatno.forEach { (ikona, tekst) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(FishBoje.Polje, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(ikona, contentDescription = null, tint = FishBoje.Akcenat, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tekst, color = FishBoje.TekstSekundarni, fontSize = 12.sp)
                    }
                }
            }
        }

        AnimatedVisibility(visible = otvorena, enter = expandVertically(), exit = shrinkVertically()) {
            DetaljiStanice(viewModel)
        }
    }
}

// "+15 cm ↑" plavo, "-3 cm ↓" narandžasto, "0 cm" sivo
@Composable
private fun PromenaZnacka(promena: Int?) {
    if (promena == null) return
    val (boja, ikona) = when {
        promena > 0 -> VodaRaste to Icons.Default.ArrowUpward
        promena < 0 -> VodaOpada to Icons.Default.ArrowDownward
        else -> FishBoje.TekstPriguseni to Icons.Default.Remove
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .background(boja.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Icon(ikona, contentDescription = null, tint = boja, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text("${if (promena > 0) "+" else ""}$promena cm", color = boja, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// Grafikon i statistika za poslednjih 7 dana (merenja na sat)
@Composable
private fun DetaljiStanice(viewModel: WaterLevelViewModel) {
    val merenja by viewModel.merenja.collectAsState()
    val isLoading by viewModel.isDetaljiLoading.collectAsState()
    val greska by viewModel.detaljiGreska.collectAsState()

    Column(modifier = Modifier.padding(top = 14.dp)) {
        HorizontalDivider(color = FishBoje.IvicaKartice)
        Spacer(Modifier.height(12.dp))
        NaslovSekcije("Poslednjih 7 dana")
        Spacer(Modifier.height(8.dp))

        when {
            isLoading -> Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FishBoje.Akcenat, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
            greska != null -> Text(greska ?: "", color = FishBoje.TekstPriguseni, fontSize = 13.sp)
            merenja.size < 2 -> Text("Za ovu stanicu nema merenja na sat.", color = FishBoje.TekstPriguseni,
                fontSize = 13.sp)
            else -> {
                val vrednosti = merenja.map { it.v }
                val poslednje = merenja.last()
                val dan = merenja.getOrNull(merenja.size - 25) // 24 sata ranije
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Statistika("Najviši", "${vrednosti.max()} cm", Modifier.weight(1f))
                    Statistika("Najniži", "${vrednosti.min()} cm", Modifier.weight(1f))
                    Statistika(
                        "Za 24 h",
                        dan?.let { "${if (poslednje.v - it.v > 0) "+" else ""}${poslednje.v - it.v} cm" } ?: "—",
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Grafikon(vrednosti)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(kratkoVreme(merenja.first().t), color = FishBoje.TekstPriguseni, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text("poslednje: ${kratkoVreme(poslednje.t)}", color = FishBoje.TekstPriguseni, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun Statistika(oznaka: String, vrednost: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(FishBoje.Polje, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(oznaka, color = FishBoje.TekstPriguseni, fontSize = 11.sp)
        Text(vrednost, color = FishBoje.TekstPrimarni, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// Linijski grafikon vodostaja sa providnim prelazom ispod linije
@Composable
private fun Grafikon(vrednosti: List<Int>) {
    val min = vrednosti.min()
    val max = vrednosti.max()
    val raspon = (max - min).coerceAtLeast(1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(FishBoje.Polje, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        val korak = size.width / (vrednosti.size - 1)
        fun tacka(i: Int) = Offset(i * korak, size.height - (vrednosti[i] - min).toFloat() / raspon * size.height)

        // Pomoćne linije
        for (k in 0..2) {
            val y = size.height * k / 2f
            drawLine(FishBoje.IvicaKartice, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        val linija = Path().apply {
            moveTo(tacka(0).x, tacka(0).y)
            for (i in 1 until vrednosti.size) lineTo(tacka(i).x, tacka(i).y)
        }
        val povrsina = Path().apply {
            moveTo(tacka(0).x, tacka(0).y)
            for (i in 1 until vrednosti.size) lineTo(tacka(i).x, tacka(i).y)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(povrsina, Brush.verticalGradient(listOf(FishBoje.Akcenat.copy(alpha = 0.3f), Color.Transparent)))
        drawPath(linija, FishBoje.Akcenat, style = Stroke(width = 2.5.dp.toPx()))
        drawCircle(FishBoje.Akcenat, radius = 4.dp.toPx(), center = tacka(vrednosti.size - 1))
    }
}

@Composable
private fun Poruka(tekst: String) {
    Text(
        tekst,
        color = FishBoje.TekstSekundarni,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    )
}

// "BAČKA PALANKA" -> "Bačka Palanka"
private fun lepNaziv(tekst: String): String =
    tekst.trim().lowercase(Locale("sr")).split(" ").joinToString(" ") { rec ->
        rec.replaceFirstChar { it.titlecase(Locale("sr")) }
    }

// "20.10.2026 15:00" -> "20.10. 15:00"
private fun kratkoVreme(t: String): String {
    val delovi = t.split(" ")
    val datum = delovi.getOrNull(0)?.split(".") ?: return t
    return if (datum.size >= 2) "${datum[0]}.${datum[1]}. ${delovi.getOrNull(1) ?: ""}".trim() else t
}

private fun formatBroj(vrednost: Double): String =
    if (vrednost % 1.0 == 0.0) vrednost.toInt().toString() else String.format(Locale.getDefault(), "%.1f", vrednost)
