package com.example.fishspot.feature.spots

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.fishspot.core.theme.NaslovSekcije
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.fishspot.core.theme.FishBoje

// Čipovi sa osnovnim mamcima + "Ostalo", koje otvara listu kategorija sa konkretnim mamcima
@Composable
fun IzborMamca(izabrano: String, onIzbor: (String) -> Unit) {
    var listaOtvorena by remember { mutableStateOf(false) }
    var detaljiZa by remember { mutableStateOf<String?>(null) } // "Boila" ili "Pop-up" dok je prozor otvoren
    var podvrstaZa by remember { mutableStateOf<String?>(null) } // "Varalica" ili "Mušica" dok je lista otvorena
    val ostalo = OpcijeUlova.jeOstalo(izabrano)
    val osnova = OpcijeUlova.osnova(izabrano)

    IzborCipovima(
        opcije = OpcijeUlova.mamci,
        izabrano = if (ostalo) OpcijeUlova.OSTALO else osnova,
        onIzbor = {
            when {
                it == OpcijeUlova.OSTALO -> listaOtvorena = true
                OpcijeUlova.detalji(it) != null -> detaljiZa = it
                OpcijeUlova.podvrste(it) != null -> podvrstaZa = it
                else -> onIzbor(it)
            }
        }
    )

    OpcijeUlova.podvrsta(izabrano)?.let { vrsta ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(FishBoje.Kartica)
                .border(1.dp, FishBoje.Akcenat, RoundedCornerShape(14.dp))
                .clickable { podvrstaZa = osnova }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OkruglaSlika(vrsta.slika, 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(vrsta.naziv, color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold)
                Text(OpcijeUlova.naslovPodvrste(osnova), color = FishBoje.TekstSekundarni, fontSize = 12.sp)
            }
            Text("Promeni", color = FishBoje.Akcenat, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }

    podvrstaZa?.let { za ->
        PodvrsteDialog(
            osnova = za,
            izabrano = izabrano,
            onIzbor = { onIzbor(it); podvrstaZa = null },
            onDismiss = { podvrstaZa = null }
        )
    }

    val delovi = OpcijeUlova.rastavi(izabrano)
    if (delovi != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(FishBoje.Kartica)
                .border(1.dp, FishBoje.Akcenat, RoundedCornerShape(14.dp))
                .clickable { detaljiZa = osnova }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(osnova, color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold)
                Text("${delovi.first}  •  ${delovi.second}", color = FishBoje.TekstSekundarni, fontSize = 13.sp)
            }
            Text("Promeni", color = FishBoje.Akcenat, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }

    detaljiZa?.let { za ->
        DetaljiMamcaDialog(
            osnova = za,
            izabrano = izabrano,
            onIzbor = { onIzbor(it); detaljiZa = null },
            onDismiss = { detaljiZa = null }
        )
    }

    if (ostalo) {
        val kategorija = OpcijeUlova.kategorija(izabrano)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(FishBoje.Kartica)
                .border(1.dp, FishBoje.Akcenat, RoundedCornerShape(14.dp))
                .clickable { listaOtvorena = true }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (kategorija != null) SlicicaKategorije(kategorija, 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(izabrano, color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold)
                if (kategorija != null) Text(kategorija.naziv, color = FishBoje.TekstSekundarni, fontSize = 12.sp)
            }
            Text("Promeni", color = FishBoje.Akcenat, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }

    if (listaOtvorena) {
        OstaliMamciDialog(
            izabrano = izabrano,
            onIzbor = { onIzbor(it); listaOtvorena = false },
            onDismiss = { listaOtvorena = false }
        )
    }
}

// Lista vrsta varalica ili tipova mušica sa slikama
@Composable
private fun PodvrsteDialog(osnova: String, izabrano: String, onIzbor: (String) -> Unit, onDismiss: () -> Unit) {
    val lista = OpcijeUlova.podvrste(osnova) ?: return
    val trenutna = OpcijeUlova.podvrsta(izabrano).takeIf { OpcijeUlova.osnova(izabrano) == osnova }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = FishBoje.Pozadina
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        OpcijeUlova.naslovPodvrste(osnova), color = FishBoje.TekstPrimarni, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Zatvori", tint = FishBoje.TekstPrimarni)
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(lista, key = { it.naziv }) { vrsta ->
                        val jeIzabrana = vrsta == trenutna
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(FishBoje.Kartica)
                                .border(
                                    1.dp,
                                    if (jeIzabrana) FishBoje.Akcenat else FishBoje.IvicaKartice,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onIzbor(OpcijeUlova.mamacZaPodvrstu(osnova, vrsta)) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OkruglaSlika(vrsta.slika, 56)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                vrsta.naziv,
                                color = if (jeIzabrana) FishBoje.Akcenat else FishBoje.TekstPrimarni,
                                fontWeight = if (jeIzabrana) FontWeight.Bold else FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (jeIzabrana) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = FishBoje.Akcenat)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Izbor veličine i ukusa za boilu i pop-up
@Composable
private fun DetaljiMamcaDialog(
    osnova: String,
    izabrano: String,
    onIzbor: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val detalji = OpcijeUlova.detalji(osnova) ?: return
    val pocetno = OpcijeUlova.rastavi(izabrano).takeIf { OpcijeUlova.osnova(izabrano) == osnova }
    var velicina by remember { mutableStateOf(pocetno?.first?.takeIf { it in detalji.velicine } ?: "") }
    var ukus by remember { mutableStateOf(pocetno?.second ?: "") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = FishBoje.Pozadina
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        osnova, color = FishBoje.TekstPrimarni, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Zatvori", tint = FishBoje.TekstPrimarni)
                    }
                }
                Text(
                    "Izaberi veličinu i ukus",
                    color = FishBoje.TekstSekundarni, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NaslovSekcije("Veličina")
                    IzborCipovima(opcije = detalji.velicine, izabrano = velicina, onIzbor = { velicina = it })
                    Spacer(Modifier.height(4.dp))
                    NaslovSekcije("Ukus")
                    detalji.ukusi.forEach { (grupa, ukusi) ->
                        Text(grupa, color = FishBoje.TekstPriguseni, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        IzborCipovima(opcije = ukusi, izabrano = ukus, onIzbor = { ukus = it })
                    }
                }

                Button(
                    onClick = { onIzbor(OpcijeUlova.sastavi(osnova, velicina, ukus)) },
                    enabled = velicina.isNotBlank() && ukus.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FishBoje.Akcenat,
                        contentColor = FishBoje.Pozadina
                    )
                ) {
                    Text(
                        when {
                            velicina.isBlank() -> "Izaberi veličinu"
                            ukus.isBlank() -> "Izaberi ukus"
                            else -> "Potvrdi: $velicina, $ukus"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SlicicaKategorije(kategorija: KategorijaMamca, velicina: Int) = OkruglaSlika(kategorija.slika, velicina)

@Composable
private fun OkruglaSlika(@DrawableRes slika: Int?, velicina: Int) {
    if (slika == null) {
        Box(
            modifier = Modifier
                .size(velicina.dp)
                .clip(CircleShape)
                .background(FishBoje.Krug)
                .border(1.dp, FishBoje.IvicaKartice, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Phishing, contentDescription = null, tint = FishBoje.Akcenat,
                modifier = Modifier.size((velicina / 2).dp))
        }
        return
    }
    Image(
        painter = painterResource(slika),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(velicina.dp)
            .clip(CircleShape)
            .border(1.dp, FishBoje.IvicaKartice, CircleShape)
    )
}

@Composable
private fun OstaliMamciDialog(izabrano: String, onIzbor: (String) -> Unit, onDismiss: () -> Unit) {
    var otvorena by remember { mutableStateOf(OpcijeUlova.kategorija(izabrano)?.naziv) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = FishBoje.Pozadina
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Ostali mamci", color = FishBoje.TekstPrimarni, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Zatvori", tint = FishBoje.TekstPrimarni)
                    }
                }
                Text(
                    "Izaberi kategoriju, pa mamac",
                    color = FishBoje.TekstSekundarni, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(OpcijeUlova.ostaliMamci, key = { it.naziv }) { kategorija ->
                        val jeOtvorena = otvorena == kategorija.naziv
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(FishBoje.Kartica)
                                .border(
                                    1.dp,
                                    if (jeOtvorena) FishBoje.AkcenatTamni else FishBoje.IvicaKartice,
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { otvorena = if (jeOtvorena) null else kategorija.naziv }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SlicicaKategorije(kategorija, 48)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(kategorija.naziv, color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold)
                                    Text("${kategorija.mamci.size} mamaca", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                                }
                                Icon(
                                    if (jeOtvorena) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null, tint = FishBoje.TekstSekundarni
                                )
                            }
                            if (jeOtvorena) {
                                kategorija.mamci.forEach { mamac ->
                                    val jeIzabran = mamac == izabrano
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onIzbor(mamac) }
                                            .padding(start = 70.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            mamac,
                                            color = if (jeIzabran) FishBoje.Akcenat else FishBoje.TekstPrimarni,
                                            fontWeight = if (jeIzabran) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (jeIzabran) {
                                            Icon(Icons.Default.Check, contentDescription = null,
                                                tint = FishBoje.Akcenat, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
