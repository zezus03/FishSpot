package com.example.fishspot.feature.spots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SpotsListScreen(
    spotsViewModel: SpotsViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onSpotSelected: (spotId: String) -> Unit
) {
    val spots by spotsViewModel.filteredSpots.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FishBoje.Pozadina)
    ) {
        FishZaglavlje(
            oznaka = "FishSpot • ulovi",
            naslov = "Lista ulova",
            ikona = Icons.Default.SetMeal,
            onNazad = onNavigateBack,
            podnaslov = {
                Text(
                    if (spots.size == 1) "1 ulov" else "${spots.size} ulova",
                    color = FishBoje.TekstPriguseni,
                    fontSize = 13.sp
                )
            }
        )

        if (spots.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.SetMeal, contentDescription = null, tint = FishBoje.TekstPriguseni,
                        modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nema ulova koji odgovaraju filterima.", color = FishBoje.TekstSekundarni,
                        fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(spots, key = { it.id }) { spot ->
                    SpotListItem(spot = spot, onSpotClick = { onSpotSelected(spot.id) })
                }
            }
        }
    }
}

@Composable
fun SpotListItem(spot: FishSpot, onSpotClick: () -> Unit) {
    val datum = remember(spot.timestamp) {
        SimpleDateFormat("dd.MM.yyyy.", Locale.getDefault()).format(Date(spot.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(20.dp))
            // detectTapGestures umesto clickable: navigacija tokom ripple animacije je rušila aplikaciju
            .pointerInput(Unit) { detectTapGestures(onTap = { onSpotClick() }) }
    ) {
        // Slika sa nazivom vrste preko donjeg dela i težinom u uglu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(FishBoje.Polje)
        ) {
            if (!spot.slikaUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = spot.slikaUrl,
                    contentDescription = "Slika ulova: ${spot.vrstaRibe}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.SetMeal, contentDescription = null, tint = FishBoje.TekstPriguseni,
                    modifier = Modifier.size(56.dp).align(Alignment.Center))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, FishBoje.Kartica)))
            )
            if (spot.tezinaRibe > 0) {
                Text(
                    formatBroj(spot.tezinaRibe, "kg"),
                    color = FishBoje.Pozadina,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(FishBoje.Akcenat, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(spot.vrstaRibe.ifBlank { "Nepoznata vrsta" }, color = FishBoje.TekstPrimarni,
                    fontSize = 21.sp, fontWeight = FontWeight.Bold)
                if (spot.latinskiNaziv.isNotBlank()) {
                    Text(spot.latinskiNaziv, color = FishBoje.Akcenat, fontSize = 13.sp, fontStyle = FontStyle.Italic)
                }
            }
        }

        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = 4.dp)) {
            // Podaci o ulovu
            val podaci = listOfNotNull(
                spot.duzinaRibe.takeIf { it > 0 }?.let { Icons.Default.Straighten to formatBroj(it, "cm") },
                spot.tehnika.takeIf { it.isNotBlank() }?.let { Icons.Default.Phishing to it },
                spot.mamac.takeIf { it.isNotBlank() }?.let { Icons.Default.PestControl to it }
            )
            if (podaci.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    podaci.forEach { (ikona, tekst) -> Tablet(ikona, tekst) }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Autor, datum i mesto
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(FishBoje.Krug, CircleShape)
                        .border(1.dp, FishBoje.IvicaKartice, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(spot.authorUsername.trim().firstOrNull()?.uppercase() ?: "?", color = FishBoje.Akcenat,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(spot.authorUsername.ifBlank { "Nepoznat autor" }, color = FishBoje.TekstPrimarni,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(datum, color = FishBoje.TekstPriguseni, fontSize = 11.sp)
                }
                if (spot.naziv.isNotBlank()) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = FishBoje.Akcenat,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(spot.naziv, color = FishBoje.TekstSekundarni, fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                }
            }
        }
    }
}

@Composable
private fun Tablet(ikona: ImageVector, tekst: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(FishBoje.Polje, RoundedCornerShape(20.dp))
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(ikona, contentDescription = null, tint = FishBoje.Akcenat, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(tekst, color = FishBoje.TekstSekundarni, fontSize = 12.sp, maxLines = 1)
    }
}

private fun formatBroj(vrednost: Double, jedinica: String): String {
    val tekst = if (vrednost % 1.0 == 0.0) vrednost.toInt().toString()
    else String.format(Locale.getDefault(), "%.2f", vrednost).trimEnd('0').trimEnd(',', '.')
    return "$tekst $jedinica"
}
