package com.example.fishspot.feature.spots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.FishZaglavlje
import com.example.fishspot.core.theme.NaslovSekcije
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Svi ulovi jednog korisnika: zbir, broj ulova po vrstama i detaljne kartice ulova. */
@Composable
fun UloviKorisnikaScreen(
    uid: String,
    onNavigateBack: () -> Unit,
    viewModel: UloviKorisnikaViewModel = viewModel()
) {
    LaunchedEffect(uid) { viewModel.ucitaj(uid) }

    val ulovi by viewModel.ulovi.collectAsState()
    val poVrstama by viewModel.poVrstama.collectAsState()
    val ime by viewModel.imeKorisnika.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val mojiUlovi = remember(uid) { FirebaseAuth.getInstance().currentUser?.uid == uid }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FishBoje.Pozadina),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            FishZaglavlje(
                oznaka = "FishSpot • ulovi",
                naslov = if (mojiUlovi) "Moji ulovi" else "Ulovi: ${ime ?: "…"}",
                ikona = Icons.Default.SetMeal,
                onNazad = onNavigateBack
            )
        }

        when {
            isLoading -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FishBoje.Akcenat)
                }
            }
            ulovi.isEmpty() -> item {
                Text(
                    if (mojiUlovi) "Još nemaš nijedan ulov. Vreme je za pecanje! 🎣" else "Ovaj korisnik još nema ulova.",
                    color = FishBoje.TekstSekundarni,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            }
            else -> {
                item { Zbir(ulovi) }

                item { NaslovSekcije("Ulovljene vrste", Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)) }
                item { SpisakVrsta(poVrstama) }

                item { NaslovSekcije("Svi ulovi · ${ulovi.size}", Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)) }
                items(ulovi, key = { it.id }) { ulov -> DetaljnaKarticaUlova(ulov) }
            }
        }
    }
}

// Ukupno ulova, ukupna težina i najveći ulov
@Composable
private fun Zbir(ulovi: List<FishSpot>) {
    val ukupnaTezina = ulovi.sumOf { it.tezinaRibe }
    val najveci = ulovi.maxByOrNull { it.tezinaRibe }?.takeIf { it.tezinaRibe > 0 }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        PlocicaZbira(Icons.Default.SetMeal, "${ulovi.size}", "ulova", FishBoje.PlavaPlocica, Modifier.weight(1f))
        PlocicaZbira(Icons.Default.Scale, formatBroj(ukupnaTezina, "kg"), "ukupno", FishBoje.ZelenaPlocica, Modifier.weight(1f))
        PlocicaZbira(
            Icons.Default.EmojiEvents,
            najveci?.let { formatBroj(it.tezinaRibe, "kg") } ?: "—",
            najveci?.vrstaRibe?.ifBlank { null } ?: "najveći",
            FishBoje.NarandzastaPlocica,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlocicaZbira(
    ikona: ImageVector,
    vrednost: String,
    oznaka: String,
    boje: Pair<Color, Color>,
    modifier: Modifier
) {
    val (pozadina, bojaIkone) = boje
    Column(
        modifier = modifier
            .background(pozadina, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(bojaIkone.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(ikona, contentDescription = null, tint = bojaIkone, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(vrednost, color = FishBoje.TekstNaPlocici, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(oznaka, color = FishBoje.PrigusenNaPlocici, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// "Deverika  ×2" sa trakom koja pokazuje udeo u svim ulovima
@Composable
private fun SpisakVrsta(poVrstama: List<BrojPoVrsti>) {
    val najvise = poVrstama.maxOfOrNull { it.broj } ?: 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(FishBoje.Kartica, RoundedCornerShape(20.dp))
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        poVrstama.forEach { vrsta ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(vrsta.vrsta, color = FishBoje.TekstPrimarni, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (vrsta.latinskiNaziv.isNotBlank()) {
                            Text(vrsta.latinskiNaziv, color = FishBoje.TekstPriguseni, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                        }
                    }
                    Text("×${vrsta.broj}", color = FishBoje.Akcenat, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(FishBoje.Polje)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(vrsta.broj.toFloat() / najvise)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(FishBoje.AkcenatTamni, FishBoje.Akcenat)))
                    )
                }
            }
        }
    }
}

// Kartica ulova sa svim podacima
@Composable
private fun DetaljnaKarticaUlova(ulov: FishSpot) {
    val datum = remember(ulov.timestamp) {
        SimpleDateFormat("dd.MM.yyyy. HH:mm", Locale.getDefault()).format(Date(ulov.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(20.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(FishBoje.Polje)
        ) {
            if (!ulov.slikaUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ulov.slikaUrl,
                    contentDescription = "Slika ulova",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.SetMeal, contentDescription = null, tint = FishBoje.TekstPriguseni,
                    modifier = Modifier.size(48.dp).align(Alignment.Center))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, FishBoje.Kartica)))
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(ulov.vrstaRibe.ifBlank { "Nepoznata vrsta" }, color = FishBoje.TekstPrimarni, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)
                if (ulov.latinskiNaziv.isNotBlank()) {
                    Text(ulov.latinskiNaziv, color = FishBoje.Akcenat, fontSize = 13.sp, fontStyle = FontStyle.Italic)
                }
            }
            if (ulov.likedBy.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(FishBoje.Pozadina.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = "Lajkovi", tint = FishBoje.Greska,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${ulov.likedBy.size}", color = FishBoje.TekstPrimarni, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Podatak(Icons.Default.Straighten, "Dužina",
                    if (ulov.duzinaRibe > 0) formatBroj(ulov.duzinaRibe, "cm") else "—", Modifier.weight(1f))
                Podatak(Icons.Default.Scale, "Težina",
                    if (ulov.tezinaRibe > 0) formatBroj(ulov.tezinaRibe, "kg") else "—", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Podatak(Icons.Default.Phishing, "Tehnika", ulov.tehnika.ifBlank { "—" }, Modifier.weight(1f))
                Podatak(Icons.Default.PestControl, "Mamac", ulov.mamac.ifBlank { "—" }, Modifier.weight(1f))
            }

            // Mesto, datum i ocena mesta
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, contentDescription = null, tint = FishBoje.Akcenat, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(ulov.naziv.ifBlank { "Bez naziva mesta" }, color = FishBoje.TekstPrimarni, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(datum, color = FishBoje.TekstPriguseni, fontSize = 11.sp)
                }
                Row {
                    (1..5).forEach { zvezda ->
                        Icon(
                            if (zvezda <= ulov.ocena.toInt()) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (zvezda <= ulov.ocena.toInt()) FishBoje.Zvezdica else FishBoje.TekstPriguseni,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Vremenski uslovi u trenutku ulova
            val uslovi = listOfNotNull(
                ulov.temperatura.takeIf { it.isNotBlank() && it != "Nepoznato" }?.let { Icons.Default.Thermostat to it },
                ulov.pritisak.takeIf { it.isNotBlank() && it != "Nepoznato" }?.let { Icons.Default.Speed to it },
                ulov.vetar.takeIf { it.isNotBlank() && it != "Nepoznato" }?.let { Icons.Default.Air to it }
            )
            if (uslovi.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    uslovi.forEach { (ikona, tekst) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(FishBoje.Polje, RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(ikona, contentDescription = null, tint = FishBoje.Akcenat, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(tekst, color = FishBoje.TekstSekundarni, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }

            if (ulov.opis.isNotBlank()) {
                Text(ulov.opis, color = FishBoje.TekstSekundarni, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Podatak(ikona: ImageVector, oznaka: String, vrednost: String, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(FishBoje.Polje, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(ikona, contentDescription = null, tint = FishBoje.Akcenat, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(oznaka, color = FishBoje.TekstPriguseni, fontSize = 10.sp)
            Text(vrednost, color = FishBoje.TekstPrimarni, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatBroj(vrednost: Double, jedinica: String): String {
    val tekst = if (vrednost % 1.0 == 0.0) vrednost.toInt().toString()
    else String.format(Locale.getDefault(), "%.2f", vrednost).trimEnd('0').trimEnd(',', '.')
    return "$tekst $jedinica"
}
