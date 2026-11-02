package com.example.fishspot.feature.bedzevi

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.fishspot.R
import com.example.fishspot.core.theme.FishBoje
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Opšti bedževi; isti spisak (id i uslovi) računa server u functions/bedzevi.js
// jedinstven: ne osvaja se ulovima i ne oduzima se (Pionir, Prijatelj FishSpot-a)
data class Bedz(val id: String, val naziv: String, val uslov: String, @DrawableRes val slika: Int, val jedinstven: Boolean = false)

data class OsvojenBedz(val id: String, val naziv: String, val dobijen: Long)

object Bedzevi {
    val svi = listOf(
        Bedz("pionir", "Pionir", "Uz FishSpot od samog početka: nalog napravljen 2026. godine", R.drawable.bedz_pionir, jedinstven = true),
        Bedz("prijatelj", "Prijatelj FishSpot-a", "Dodeljuje ga tim FishSpot-a za posebnu podršku aplikaciji", R.drawable.bedz_prijatelj, jedinstven = true),
        Bedz("pecaros_1", "Pecaroš (bronza)", "Prvi provereni ulov", R.drawable.bedz_pecaros_1),
        Bedz("pecaros_2", "Pecaroš (srebro)", "25 proverenih ulova", R.drawable.bedz_pecaros_2),
        Bedz("pecaros_3", "Pecaroš (zlato)", "100 proverenih ulova", R.drawable.bedz_pecaros_3),
        Bedz("trofej_1", "Trofej (bronza)", "Riba preko 3 kg", R.drawable.bedz_trofej_1),
        Bedz("trofej_2", "Trofej (srebro)", "Riba preko 8 kg", R.drawable.bedz_trofej_2),
        Bedz("trofej_3", "Trofej (zlato)", "Riba preko 15 kg", R.drawable.bedz_trofej_3),
        Bedz("kolekcionar_1", "Kolekcionar (bronza)", "5 različitih vrsta", R.drawable.bedz_kolekcionar_1),
        Bedz("kolekcionar_2", "Kolekcionar (srebro)", "10 različitih vrsta", R.drawable.bedz_kolekcionar_2),
        Bedz("kolekcionar_3", "Kolekcionar (zlato)", "20 različitih vrsta", R.drawable.bedz_kolekcionar_3),
        Bedz("varalicar", "Varaličar", "10 ulova na varalicu", R.drawable.bedz_varalicar),
        Bedz("musicar", "Mušičar", "5 ulova na mušicu", R.drawable.bedz_musicar),
        Bedz("fiderista", "Fiderista", "10 ulova na feeder", R.drawable.bedz_fiderista),
        Bedz("nocna_smena", "Noćna smena", "Ulov između 22 i 4 h", R.drawable.bedz_nocna_smena),
        Bedz("zimski", "Zimski pecaroš", "Ulov u decembru, januaru ili februaru", R.drawable.bedz_zimski),
        Bedz("fotograf", "Fotograf", "10 ulova slikanih u aplikaciji", R.drawable.bedz_fotograf),
    )

    fun po(id: String) = svi.firstOrNull { it.id == id }
}

// Osvojeni bedževi korisnika (uživo iz Korisnici/{uid}/bedzevi), najnoviji prvi
@Composable
fun rememberOsvojeniBedzevi(uid: String?): List<OsvojenBedz> {
    var osvojeni by remember(uid) { mutableStateOf(emptyList<OsvojenBedz>()) }
    DisposableEffect(uid) {
        val slusalac = uid?.takeIf { it.isNotBlank() }?.let { id ->
            FirebaseFirestore.getInstance().collection("Korisnici").document(id).collection("bedzevi")
                .addSnapshotListener { s, _ ->
                    osvojeni = s?.documents.orEmpty().map {
                        OsvojenBedz(it.id, it.getString("naziv") ?: it.id, it.getLong("dobijen") ?: 0L)
                    }.sortedByDescending { it.dobijen }
                }
        }
        onDispose { slusalac?.remove() }
    }
    return osvojeni
}

private fun datum(ms: Long) = SimpleDateFormat("d.M.yyyy.", Locale.getDefault()).format(Date(ms))

// Slika bedža; neosvojen je siv i providniji
@Composable
fun SlikaBedza(bedz: Bedz, osvojen: Boolean, velicina: Int) {
    Image(
        painter = painterResource(bedz.slika),
        contentDescription = bedz.naziv,
        colorFilter = if (osvojen) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        modifier = Modifier.size(velicina.dp).alpha(if (osvojen) 1f else 0.35f)
    )
}

// Red osvojenih bedževa za profil (tvoj i tuđi); dodir otvara sve bedževe
@Composable
fun RedBedzeva(osvojeni: List<OsvojenBedz>, onOtvori: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(16.dp))
            .clickable(onClick = onOtvori)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bedževi", color = FishBoje.TekstPrimarni, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${osvojeni.size}/${Bedzevi.svi.size} ›", color = FishBoje.Akcenat, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (osvojeni.isEmpty()) {
            Text("Još nema osvojenih bedževa.", color = FishBoje.TekstPriguseni, fontSize = 13.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(osvojeni, key = { it.id }) { o ->
                    val bedz = Bedzevi.po(o.id) ?: return@items
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                        SlikaBedza(bedz, osvojen = true, velicina = 56)
                        Text(bedz.naziv.substringBefore(" ("), color = FishBoje.TekstSekundarni, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// Svi bedževi: osvojeni (sa datumom) pa neosvojeni (sivi, sa uslovom)
@Composable
fun BedzeviDialog(ime: String?, osvojeni: List<OsvojenBedz>, onZatvori: () -> Unit) {
    val osvojeniIds = osvojeni.associateBy { it.id }
    val redosled = Bedzevi.svi.sortedBy { if (it.id in osvojeniIds) 0 else 1 }
    Dialog(onDismissRequest = onZatvori, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = FishBoje.Pozadina) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("FISHSPOT • BEDŽEVI", color = FishBoje.TekstPriguseni, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(ime?.let { "Bedževi: $it" } ?: "Moji bedževi", color = FishBoje.TekstPrimarni, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Osvojeno ${osvojeni.size} od ${Bedzevi.svi.size}", color = FishBoje.Akcenat, fontSize = 13.sp)
                    }
                    IconButton(onClick = onZatvori) { Icon(Icons.Default.Close, "Zatvori", tint = FishBoje.TekstPrimarni) }
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(redosled, key = { it.id }) { b ->
                        val o = osvojeniIds[b.id]
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(FishBoje.Kartica)
                                .border(1.dp, if (o != null) FishBoje.AkcenatTamni else FishBoje.IvicaKartice, RoundedCornerShape(16.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SlikaBedza(b, osvojen = o != null, velicina = 64)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(b.naziv, color = if (o != null) FishBoje.TekstPrimarni else FishBoje.TekstPriguseni,
                                        fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f, fill = false))
                                    if (b.jedinstven) {
                                        Spacer(Modifier.width(6.dp))
                                        Text("JEDINSTVEN", color = androidx.compose.ui.graphics.Color(0xFFCE93D8), fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                                .background(androidx.compose.ui.graphics.Color(0x339C27B0)).padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Text(b.uslov, color = FishBoje.TekstSekundarni, fontSize = 13.sp)
                                Text(
                                    if (o != null) "Osvojen ${datum(o.dobijen)}" else "Još nije osvojen",
                                    color = if (o != null) FishBoje.Akcenat else FishBoje.TekstPriguseni, fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
