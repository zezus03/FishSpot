package com.example.fishspot.feature.spots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
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
    val opis: String,
    val dodatneSlike: List<String>, // zadržane dodatne slike (linkovi)
    val noveSlike: List<android.net.Uri> = emptyList(), // nove dodatne slike sa telefona
    val cuvarka: Boolean = false,
    val cuvarkaVrste: List<VrstaRibe> = emptyList(),
    val sakrijLokaciju: Boolean = false
)

@Composable
fun IzmenaUlovaDialog(
    spot: FishSpot,
    onDismiss: () -> Unit,
    onSacuvaj: (IzmenaUlova) -> Unit,
    vrsteRibaViewModel: VrsteRibaViewModel = viewModel()
) {
    val sveVrste by vrsteRibaViewModel.vrste.collectAsState()
    val focusManager = LocalFocusManager.current

    var naziv by remember { mutableStateOf(spot.naziv) }
    var izabranaVrsta by remember {
        mutableStateOf(
            spot.vrstaId.takeIf { it.isNotBlank() }
                ?.let { VrstaRibe(it, spot.vrstaRibe.substringBefore(" (").trim(), spot.latinskiNaziv) }
        )
    }
    // Podaci o ribi mogu da se menjaju samo 30 minuta posle objave (zbog rang liste i takmičenja)
    val ribaOtkljucana = remember { spot.ribaMozeDaSeMenja }
    var cuvarka by remember { mutableStateOf(spot.cuvarka) }
    var vrsteCuvarke by remember {
        mutableStateOf(spot.cuvarkaVrste.map { VrstaRibe(it.id, it.naziv, it.latinskiNaziv) })
    }
    var vrstaUnos by remember { mutableStateOf(if (spot.cuvarka) "" else izabranaVrsta?.prikaz ?: "") }
    var duzina by remember { mutableStateOf(if (spot.duzinaRibe > 0) formatBroj(spot.duzinaRibe, 1) else "") }
    var tezina by remember { mutableStateOf(if (spot.tezinaRibe > 0) formatBroj(spot.tezinaRibe, 2) else "") }
    var tezinaRucno by remember { mutableStateOf(false) }
    var mamac by remember { mutableStateOf(spot.mamac.ifBlank { OpcijeUlova.mamci.first() }) }
    var tehnika by remember { mutableStateOf(spot.tehnika.ifBlank { OpcijeUlova.tehnike.first() }) }
    var ocena by remember { mutableStateOf(spot.ocena.coerceIn(1f, 5f)) }
    LaunchedEffect(mamac) {
        val dozvoljene = OpcijeUlova.dozvoljeneTehnike(mamac)
        if (tehnika !in dozvoljene) tehnika = dozvoljene.first()
    }
    var opis by remember { mutableStateOf(spot.opis) }

    // Dodatne slike: postojeće (linkovi) i nove sa telefona; glavna slika se ne menja (na njoj su provere za poene)
    val context = androidx.compose.ui.platform.LocalContext.current
    var zadrzane by remember { mutableStateOf(spot.dodatneSlike) }
    var nove by remember { mutableStateOf(listOf<android.net.Uri>()) }
    val slobodno = MAKS_SLIKA_ULOVA - 1 - zadrzane.size - nove.size
    val galerija = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris -> nove = (nove + uris.filter { it !in nove }).take(MAKS_SLIKA_ULOVA - 1 - zadrzane.size) }
    var kameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val kamera = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { uspeh -> val u = kameraUri; if (uspeh && u != null && slobodno > 0) nove = nove + u }
    fun slikaj() {
        val vrednosti = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "fishspot_${System.currentTimeMillis()}.jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        kameraUri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, vrednosti)
        kameraUri?.let { kamera.launch(it) }
    }
    val dozvolaKamere = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { odobreno -> if (odobreno) slikaj() }

    // Kad se promeni vrsta ili dužina, težina se ponovo procenjuje (osim ako je uneta ručno)
    var prviPut by remember { mutableStateOf(true) }
    LaunchedEffect(izabranaVrsta, duzina) {
        if (prviPut) { prviPut = false; return@LaunchedEffect }
        if (!tezinaRucno && !cuvarka) {
            val kg = TezinaRibe.proceniKg(izabranaVrsta?.naziv ?: "", duzina.replace(',', '.').toDoubleOrNull())
            if (kg != null) tezina = formatBroj(kg, 2)
        }
    }

    var sakrijLokaciju by remember { mutableStateOf(spot.skrivenaLokacija) }
    val duzinaBroj = duzina.replace(',', '.').toDoubleOrNull()
    val tezinaBroj = tezina.replace(',', '.').toDoubleOrNull()
    val greska = when {
        naziv.isBlank() -> "Unesi naziv mesta"
        !ribaOtkljucana -> null
        com.example.fishspot.feature.fish.ZasticeneVrste.greska(izabranaVrsta.takeIf { !cuvarka }, if (cuvarka) vrsteCuvarke else emptyList()) != null ->
            com.example.fishspot.feature.fish.ZasticeneVrste.greska(izabranaVrsta.takeIf { !cuvarka }, if (cuvarka) vrsteCuvarke else emptyList())
        cuvarka -> proveraCuvarke(vrsteCuvarke, tezinaBroj)
        izabranaVrsta == null -> "Izaberi vrstu ribe sa liste"
        else -> com.example.fishspot.feature.fish.OgranicenjaRibe.proveri(
            izabranaVrsta?.id, izabranaVrsta?.naziv ?: "", duzinaBroj, tezinaBroj
        )
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
                    NaslovSekcije("Slike")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        spot.slikaUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            SlicicaIzmene(model = url, glavna = true, onUkloni = null)
                        }
                        zadrzane.forEach { url -> SlicicaIzmene(model = url, glavna = false, onUkloni = { zadrzane = zadrzane - url }) }
                        nove.forEach { uri -> SlicicaIzmene(model = uri, glavna = false, onUkloni = { nove = nove - uri }) }
                        if (slobodno > 0 && !spot.slikaUrl.isNullOrBlank()) {
                            DugmeDodajSliku(Icons.Default.CameraAlt, "Slikaj") {
                                val ima = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                                    android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (ima) slikaj() else dozvolaKamere.launch(android.Manifest.permission.CAMERA)
                            }
                            DugmeDodajSliku(Icons.Default.PhotoLibrary, "Galerija") { galerija.launch("image/*") }
                        }
                    }
                    Text(
                        if (spot.slikaUrl.isNullOrBlank()) "Slike ulova se još šalju; dodatne slike možeš da menjaš kad stignu."
                        else "Glavna slika (zvezdica) se ne menja. Dodatnih slika može biti najviše ${MAKS_SLIKA_ULOVA - 1}.",
                        color = FishBoje.TekstPriguseni, fontSize = 12.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { NaslovSekcije(if (cuvarka) "Riba u čuvarki" else "Riba") }
                        CuvarkaDugme(ukljuceno = cuvarka) {
                            if (!ribaOtkljucana) return@CuvarkaDugme
                            if (!cuvarka) {
                                izabranaVrsta?.let { vrsteCuvarke = listOf(it) }
                                izabranaVrsta = null
                                duzina = ""
                                tezinaRucno = true
                            } else {
                                vrsteCuvarke.firstOrNull()?.let { izabranaVrsta = it }
                                vrsteCuvarke = emptyList()
                                tezinaRucno = false
                            }
                            vrstaUnos = if (cuvarka) izabranaVrsta?.prikaz ?: "" else ""
                            cuvarka = !cuvarka
                        }
                    }
                    if (!ribaOtkljucana) {
                        Text(
                            "Vrsta, dužina i težina mogu da se menjaju samo $MINUTA_ZA_IZMENU_RIBE minuta posle objave ulova.",
                            color = FishBoje.Upozorenje, fontSize = 13.sp
                        )
                    }
                    if (cuvarka) VrsteCuvarke(vrsteCuvarke, onUkloni = { v -> if (ribaOtkljucana) vrsteCuvarke = vrsteCuvarke - v })
                    val predlozi = remember(vrstaUnos, sveVrste) { vrsteRibaViewModel.pretrazi(vrstaUnos).take(5) }
                    OutlinedTextField(
                        value = vrstaUnos,
                        onValueChange = { vrstaUnos = it; izabranaVrsta = null },
                        label = { Text(if (cuvarka) "Dodaj vrstu u čuvarku" else "Vrsta ribe") },
                        singleLine = true,
                        enabled = ribaOtkljucana,
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
                            com.example.fishspot.feature.fish.LokalniNazivi.napomena(vrstaUnos)?.let { napomena ->
                                Text(napomena, color = FishBoje.Upozorenje, fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                            predlozi.forEach { vrsta ->
                                val lokalni = com.example.fishspot.feature.fish.LokalniNazivi.pogodak(vrsta.id, vrstaUnos)
                                Text(
                                    vrsta.prikaz + (lokalni?.let { "  ·  poznata i kao: $it" } ?: "") +
                                        (if (com.example.fishspot.feature.fish.ZasticeneVrste.jeZasticena(vrsta.id)) "  ·  TRAJNA ZABRANA ULOVA" else ""),
                                    color = if (com.example.fishspot.feature.fish.ZasticeneVrste.jeZasticena(vrsta.id)) FishBoje.Greska else FishBoje.TekstPrimarni,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (cuvarka) {
                                                if (vrsteCuvarke.none { it.id == vrsta.id } && vrsteCuvarke.size < MAKS_VRSTA_CUVARKE) {
                                                    vrsteCuvarke = vrsteCuvarke + vrsta
                                                }
                                                vrstaUnos = ""
                                            } else {
                                                izabranaVrsta = vrsta
                                                vrstaUnos = vrsta.prikaz
                                            }
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (cuvarka) Arrangement.Center else Arrangement.spacedBy(12.dp)
                    ) {
                        if (!cuvarka) OutlinedTextField(
                            value = duzina,
                            onValueChange = { duzina = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                            label = { Text("Dužina") },
                            suffix = { Text("cm") },
                            singleLine = true,
                            enabled = !cuvarka && ribaOtkljucana,
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
                            label = { Text(if (cuvarka) "Ukupna težina" else "Težina") },
                            enabled = ribaOtkljucana,
                            suffix = { Text("kg") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = fishPoljeBoje(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = if (cuvarka) Modifier.fillMaxWidth(0.6f) else Modifier.weight(1f)
                        )
                    }

                    NaslovSekcije("Mamac")
                    IzborMamca(izabrano = mamac, onIzbor = { mamac = it })
                    NaslovSekcije("Tehnika")
                    IzborCipovima(
                        opcije = OpcijeUlova.dozvoljeneTehnike(mamac), izabrano = tehnika,
                        onIzbor = { tehnika = it }
                    )

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
                    PrekidacSakrijLokaciju(sakrijLokaciju) { sakrijLokaciju = it }
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
                                    vrsta = (if (cuvarka) vrsteCuvarke.firstOrNull() else izabranaVrsta)
                                        ?: VrstaRibe(spot.vrstaId, spot.vrstaRibe, spot.latinskiNaziv),
                                    duzinaRibe = if (cuvarka) 0.0 else duzinaBroj ?: spot.duzinaRibe,
                                    tezinaRibe = tezinaBroj ?: spot.tezinaRibe,
                                    mamac = mamac,
                                    tehnika = tehnika,
                                    ocena = ocena,
                                    opis = opis.trim(),
                                    dodatneSlike = zadrzane,
                                    noveSlike = nove,
                                    cuvarka = cuvarka,
                                    cuvarkaVrste = if (cuvarka) vrsteCuvarke else emptyList(),
                                    sakrijLokaciju = sakrijLokaciju
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

@Composable
private fun SlicicaIzmene(model: Any, glavna: Boolean, onUkloni: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(if (glavna) 2.dp else 1.dp, if (glavna) FishBoje.Akcenat else FishBoje.IvicaKartice, RoundedCornerShape(14.dp))
    ) {
        coil.compose.AsyncImage(model = model, contentDescription = null, modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        if (glavna) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(4.dp).size(22.dp)
                    .background(FishBoje.AkcenatTamni, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Star, contentDescription = "Glavna slika", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp)) }
        }
        if (onUkloni != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(3.dp).size(22.dp)
                    .background(FishBoje.Pozadina.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onUkloni),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Close, contentDescription = "Ukloni sliku", tint = FishBoje.TekstPrimarni, modifier = Modifier.size(14.dp)) }
        }
    }
}

@Composable
private fun DugmeDodajSliku(ikona: androidx.compose.ui.graphics.vector.ImageVector, tekst: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FishBoje.Akcenat.copy(alpha = 0.08f))
            .border(1.dp, FishBoje.AkcenatTamni, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(ikona, contentDescription = null, tint = FishBoje.Akcenat, modifier = Modifier.size(22.dp))
        Text(tekst, color = FishBoje.Akcenat, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatBroj(vrednost: Double, decimala: Int): String =
    String.format(Locale.US, "%.${decimala}f", vrednost).trimEnd('0').trimEnd('.')
