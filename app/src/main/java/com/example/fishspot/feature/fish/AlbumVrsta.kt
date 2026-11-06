package com.example.fishspot.feature.fish

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.feature.spots.VrstaUAlbumu
import java.util.Locale

// Album vrsta na profilu: upecane vrste su u boji (broj proverenih ulova i najveća riba),
// a ostale sive, kao album sličica koji se popunjava. Zaštićene vrste se ne prikazuju kao prazna mesta.
@Composable
fun AlbumVrsta(album: List<VrstaUAlbumu>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var otvorena by remember { mutableStateOf<VrstaAtlasa?>(null) }
    otvorena?.let { AtlasDialog(it, onZatvori = { otvorena = null }) }

    // Upecano, po vrsti iz atlasa (zaštićena vrsta se prikazuje samo ako je ipak upisana)
    val upecano = remember(album) {
        album.mapNotNull { s ->
            val v = AtlasRiba.poIdUBazi(context, s.vrstaId) ?: AtlasRiba.poNazivu(context, s.naziv)
            v?.let { it to s }
        }.groupBy({ it.first.id }) { it }
            .mapValues { (_, l) -> l.first().first to VrstaUAlbumu(l.first().second.vrstaId, l.first().first.naziv, l.sumOf { it.second.broj }, l.maxOf { it.second.najvecaKg }) }
    }
    val svePrazne = remember {
        OgranicenjaRibe.sveVrste
            .mapNotNull { AtlasRiba.poIdUBazi(context, it) }
            .filter { it.status != StatusVrste.ZABRANA }
            .distinctBy { it.id }
    }
    val stavke = upecano.values.sortedByDescending { it.second.broj }.map { it.first to it.second } +
        svePrazne.filter { it.id !in upecano }.sortedBy { it.naziv }.map { it to null }
    val ukupno = (svePrazne.map { it.id } + upecano.keys).toSet().size

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${upecano.size}", color = FishBoje.Akcenat, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(" od $ukupno vrsta", color = FishBoje.TekstSekundarni, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(
            "Broje se provereni ulovi i svi ulovi iz prve verzije. Dodirni vrstu za opis.",
            color = FishBoje.TekstPriguseni, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp)
        )
        stavke.chunked(3).forEach { red ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                red.forEach { (vrsta, stanje) ->
                    PoljeAlbuma(vrsta, stanje, Modifier.weight(1f)) { otvorena = vrsta }
                }
                repeat(3 - red.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PoljeAlbuma(vrsta: VrstaAtlasa, stanje: VrstaUAlbumu?, modifier: Modifier, onClick: () -> Unit) {
    val upecana = stanje != null
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (upecana) FishBoje.Krug else FishBoje.Polje)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SlikaVrste(
            vrsta.id,
            siva = !upecana,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(8.dp)).alpha(if (upecana) 1f else 0.45f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            vrsta.naziv, color = if (upecana) FishBoje.TekstPrimarni else FishBoje.TekstPriguseni,
            fontSize = 12.sp, fontWeight = if (upecana) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        if (stanje != null) {
            Text(
                buildString {
                    append("×${stanje.broj}")
                    if (stanje.najvecaKg > 0) append(" · ${String.format(Locale.GERMANY, "%.1f", stanje.najvecaKg)} kg")
                },
                color = FishBoje.Akcenat, fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}
