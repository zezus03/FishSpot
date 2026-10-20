package com.example.fishspot.feature.spots

import com.example.fishspot.feature.fish.ArMeasureActivity
import com.example.fishspot.feature.weather.WeatherNetwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.focus.onFocusChanged
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.NaslovSekcije
import com.example.fishspot.core.theme.fishCipBoje
import com.example.fishspot.core.theme.fishPoljeBoje
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.fishspot.R
import com.example.fishspot.feature.fish.FishDetector
import com.example.fishspot.feature.fish.ucitajSlikuUspravno
import com.example.fishspot.feature.fish.TezinaRibe
import com.example.fishspot.feature.fish.VrstaRibe
import com.example.fishspot.feature.fish.VrsteRibaViewModel
import androidx.compose.ui.text.font.FontStyle

import com.example.fishspot.feature.fish.LovostajChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.fishspot.BuildConfig
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSpotScreen(
    spotsViewModel: SpotsViewModel = viewModel(),
    vrsteRibaViewModel: VrsteRibaViewModel = viewModel(),
    latitude: Double,
    longitude: Double,
    onSpotAdded: () -> Unit
) {
    val context = LocalContext.current

    var showArMeasureScreen by remember { mutableStateOf(false) }

    var naziv by remember { mutableStateOf("") }
    var vrstaRibe by remember { mutableStateOf("") } // Tekst u polju (ono što korisnik kuca)
    var izabranaVrsta by remember { mutableStateOf<VrstaRibe?>(null) } // Vrsta izabrana iz padajućeg menija
    var vrstaMeniOtvoren by remember { mutableStateOf(false) }
    val sveVrste by vrsteRibaViewModel.vrste.collectAsState()
    // Lovostaj i težina gledaju samo srpski naziv, bez latinskog
    val nazivZaProveru = izabranaVrsta?.naziv ?: vrstaRibe
    var isValidatingFish by remember { mutableStateOf(false) }
    var ribaPronadjena by remember { mutableStateOf(false) }

    var ocena by remember { mutableStateOf(3f) }
    var opis by remember { mutableStateOf("") }
    var slikaUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var duzinaRibe by remember { mutableStateOf("") }
    var tezinaRibe by remember { mutableStateOf("") }
    val mamci = listOf("Crv", "Glista", "Šećerac", "Pop-up", "Keder", "Varalica", "Mušica")
    var odabranMamac by remember { mutableStateOf(mamci[0]) }
    val tehnike = listOf("Plovak", "Dubinac", "Varalica", "Feeder", "Mušičarenje")
    var odabranaTehnika by remember { mutableStateOf(tehnike[0]) }
    var temperatura by remember { mutableStateOf("Nepoznato") }
    var pritisak by remember { mutableStateOf("Nepoznato") }
    var vetar by remember { mutableStateOf("Nepoznato") }

    // Težina se računa iz dužine i vrste dok je korisnik ne unese ručno
    var tezinaRucnoUneta by remember { mutableStateOf(false) }
    LaunchedEffect(nazivZaProveru, duzinaRibe) {
        if (!tezinaRucnoUneta) {
            val kg = TezinaRibe.proceniKg(nazivZaProveru, duzinaRibe.replace(',', '.').toDoubleOrNull())
            tezinaRibe = kg?.let { String.format(Locale.US, "%.2f", it) } ?: ""
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val mojApiKey = BuildConfig.VREMENSKA_KEY

    // --- 1. INICIJALIZACIJA LOKALNOG MODELA ---
    val fishDetector = remember { FishDetector(context) }
    var showLovostajUpozorenje by remember { mutableStateOf<String?>(null) }

    // Gašenje modela kada se ekran zatvori
    DisposableEffect(Unit) {
        onDispose {
            fishDetector.zatvori()
        }
    }

    // --- PROVERA LOKALNIM MODELOM: DA LI JE RIBA NA SLICI ---
    // Rezultat se vidi kao oznaka na fotografiji; bez prepoznate ribe ulov ne može da se sačuva
    LaunchedEffect(slikaUri) {
        ribaPronadjena = false
        val uri = slikaUri
        if (uri == null) {
            isValidatingFish = false
            return@LaunchedEffect
        }
        isValidatingFish = true
        try {
            // Slika se učitava uspravno (EXIF rotacija), kao što je korisnik vidi
            val rezultat = withContext(Dispatchers.IO) {
                fishDetector.analiziraj(ucitajSlikuUspravno(context, uri))
            }
            ribaPronadjena = rezultat.jeRiba
            when {
                rezultat.greska != null ->
                    Toast.makeText(context, "Greška pri proveri slike: ${rezultat.greska}", Toast.LENGTH_LONG).show()
                !rezultat.jeRiba ->
                    Toast.makeText(
                        context,
                        "⚠️ Riba nije prepoznata!\nMolimo Vas da slikate ribu iz blizine, jasnije ili pod boljim uglom kako bi je model uspešno detektovao. 🎣",
                        Toast.LENGTH_LONG
                    ).show()
            }
        } catch (e: Exception) {
            Log.e("AddSpotScreen", "Greška pri analizi slike", e)
            ribaPronadjena = false
            Toast.makeText(context, "Greška pri učitavanju slike: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            isValidatingFish = false
        }
    }

    // --- LAUNCHED EFFECT: VREMENSKA PROGNOZA ---
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = WeatherNetwork.retrofit.getWeather(lat = latitude, lon = longitude, apiKey = mojApiKey)

                withContext(Dispatchers.Main) {
                    temperatura = "${response.main.temp.toInt()} °C"
                    pritisak = "${response.main.pressure} hPa"

                    val pravci = arrayOf("S", "SI", "I", "JI", "J", "JZ", "Z", "SZ")
                    val indeks = ((response.wind.deg + 22.5) / 45.0).toInt() % 8
                    val vetarStrana = pravci[indeks]

                    vetar = "${response.wind.speed} m/s, $vetarStrana"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ⭐ OVDE JE SADA STAVLJENA AUTOMATSKA PROVERA LOVOSTAJA I MINIMALNE MERE ⭐
    // ═════════════════════════════════════════════════════════════════════════
    // Promenljive koje prate da li korisnik trenutno kuca u poljima
    var vrstaRibeImaFokus by remember { mutableStateOf(false) }
    var duzinaRibeImaFokus by remember { mutableStateOf(false) }

    // --- PROVERA LOVOSTAJA I MERE TEK KADA KORISNIK ZAVRŠI KUCAVRE (IZGUBI FOKUS) ---
    LaunchedEffect(vrstaRibeImaFokus, duzinaRibeImaFokus, izabranaVrsta) {
        // Proveravamo kad korisnik izabere vrstu iz menija ili kad oba polja izgube fokus
        val vrstaGotova = izabranaVrsta != null || !vrstaRibeImaFokus
        if (vrstaGotova && !duzinaRibeImaFokus && nazivZaProveru.isNotBlank()) {
            val duzinaDuplo = duzinaRibe.toDoubleOrNull() ?: 0.0

            val uLovostaju = LovostajChecker.isRibaULovostaju(nazivZaProveru)
            val ispodMere = LovostajChecker.isRibaIspodMere(nazivZaProveru, duzinaDuplo)

            if (uLovostaju || ispodMere) {
                showLovostajUpozorenje = LovostajChecker.getUpozorenje(nazivZaProveru, duzinaDuplo)
            } else {
                showLovostajUpozorenje = null
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> slikaUri = uri }
    )

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                slikaUri = tempCameraUri
            }
        }
    )

    fun otvoriKameru() {
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "fishspot_${System.currentTimeMillis()}.jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        tempCameraUri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        tempCameraUri?.let { uri -> cameraLauncher.launch(uri) }
    }

    // Pošto manifest traži CAMERA (zbog AR merenja), Android ne pušta ni slikanje
    // preko aplikacije za kameru dok korisnik ne odobri tu dozvolu
    val kameraDozvolaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { odobreno ->
        if (odobreno) {
            otvoriKameru()
        } else {
            Toast.makeText(context, "Za slikanje je potrebna dozvola za kameru", Toast.LENGTH_LONG).show()
        }
    }

    val trenutnaLokacija = remember(latitude, longitude) {
        android.location.Location("").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
    }

    val arLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val izmereno = result.data?.getDoubleExtra("DUZINA", 0.0)
            if (izmereno != null && izmereno > 0) {
                duzinaRibe = String.format(Locale.US, "%.1f", izmereno)
            }
        }
    }

    // AR je opcion: dugme za AR merenje prikazujemo samo na telefonima koji podržavaju ARCore
    var arPodrzan by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var dostupnost = ArCoreApk.getInstance().checkAvailability(context)
        while (dostupnost.isTransient) {
            delay(200)
            dostupnost = ArCoreApk.getInstance().checkAvailability(context)
        }
        arPodrzan = dostupnost.isSupported
    }

    // Šta još fali da bi ulov mogao da se sačuva (prikazuje se iznad dugmeta)
    val nedostaje = when {
        slikaUri == null -> "Dodaj fotografiju ulova"
        isValidatingFish -> "Proveravam fotografiju…"
        !ribaPronadjena -> "Na fotografiji nije prepoznata riba"
        izabranaVrsta == null -> "Izaberi vrstu ribe sa liste"
        else -> null
    }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    Scaffold(
        containerColor = FishBoje.Pozadina,
        topBar = {
            // Zaglavlje sa prelazom, kao u vremenskoj prognozi
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(FishBoje.ZaglavljeGore, FishBoje.Pozadina)))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Text("FISHSPOT • NOVI ULOV", color = FishBoje.TekstPriguseni, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Dodaj ulov", color = FishBoje.TekstPrimarni, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { backDispatcher?.onBackPressed() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                        .background(FishBoje.Krug, CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Zatvori", tint = FishBoje.TekstSekundarni,
                        modifier = Modifier.size(18.dp))
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FishBoje.Kartica)
            ) {
                HorizontalDivider(color = FishBoje.IvicaKartice)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (nedostaje != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                            Box(modifier = Modifier.size(7.dp).background(FishBoje.Upozorenje, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(nedostaje, color = FishBoje.TekstSekundarni, fontSize = 13.sp)
                        }
                    }
                    Button(
                        onClick = {
                            val vrsta = izabranaVrsta ?: return@Button
                            isSaving = true
                            spotsViewModel.addSpot(
                                context = context,
                                naziv = naziv, vrsta = vrsta, ocena = ocena, opis = opis, slikaUri = slikaUri,
                                location = trenutnaLokacija,
                                duzinaRibe = duzinaRibe.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                tezinaRibe = tezinaRibe.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                mamac = odabranMamac,
                                tehnika = odabranaTehnika,
                                temperatura = temperatura,
                                pritisak = pritisak,
                                vetar = vetar
                            ) { result ->
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Ulov uspešno sačuvan!", Toast.LENGTH_SHORT).show()
                                    onSpotAdded()
                                } else {
                                    val errorMessage = result.exceptionOrNull()?.message ?: "Nepoznata greška"
                                    Toast.makeText(context, "Greška: $errorMessage", Toast.LENGTH_LONG).show()
                                }
                                isSaving = false
                            }
                        },
                        enabled = !isSaving && nedostaje == null,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FishBoje.Akcenat,
                            contentColor = FishBoje.Pozadina,
                            disabledContainerColor = FishBoje.Krug,
                            disabledContentColor = FishBoje.TekstPriguseni
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = FishBoje.Pozadina, strokeWidth = 2.5.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sačuvaj ulov", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FotografijaUlova(
                slikaUri = slikaUri,
                proveravam = isValidatingFish,
                ribaPronadjena = ribaPronadjena,
                onSlikaj = {
                    val imaDozvolu = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.CAMERA
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (imaDozvolu) otvoriKameru()
                    else kameraDozvolaLauncher.launch(android.Manifest.permission.CAMERA)
                },
                onGalerija = { galleryLauncher.launch("image/*") }
            )

            // ─── RIBA ───
            Sekcija(naslov = "Riba", ikona = Icons.Default.SetMeal) {
                // Korisnik kuca ("dev") i bira vrstu iz liste ("Deverika (Abramis brama)").
                // Lista se pojavljuje tek od 3 slova, ispod polja, i ne uzima fokus tastaturi.
                val predlozi = remember(vrstaRibe, sveVrste) { vrsteRibaViewModel.pretrazi(vrstaRibe).take(6) }
                val prikaziPredloge = vrstaMeniOtvoren && izabranaVrsta == null &&
                    vrstaRibe.trim().length >= 3 && predlozi.isNotEmpty()
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = vrstaRibe,
                        onValueChange = {
                            vrstaRibe = it
                            izabranaVrsta = null // Kucanje poništava izbor dok se ne izabere ponovo
                            vrstaMeniOtvoren = true
                        },
                        label = { Text("Vrsta ribe") },
                        placeholder = { Text("Ukucaj bar 3 slova, npr. dev") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        isError = vrstaRibe.isNotBlank() && izabranaVrsta == null && !prikaziPredloge,
                        supportingText = {
                            if (vrstaRibe.isNotBlank() && izabranaVrsta == null && !prikaziPredloge) {
                                Text(if (vrstaRibe.trim().length < 3) "Ukucaj bar 3 slova" else "Izaberi vrstu sa liste")
                            }
                        },
                        trailingIcon = {
                            if (vrstaRibe.isNotEmpty()) {
                                IconButton(onClick = {
                                    vrstaRibe = ""
                                    izabranaVrsta = null
                                    vrstaMeniOtvoren = false
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Obriši vrstu")
                                }
                            }
                        },
                        singleLine = true,
                        colors = fishPoljeBoje(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                vrstaRibeImaFokus = focusState.isFocused
                            },
                        shape = RoundedCornerShape(14.dp)
                    )
                    DropdownMenu(
                        expanded = prikaziPredloge,
                        onDismissRequest = { vrstaMeniOtvoren = false },
                        properties = PopupProperties(focusable = false),
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .background(FishBoje.Kartica)
                    ) {
                        predlozi.forEach { vrsta ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(vrsta.naziv, color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold)
                                        if (vrsta.latinskiNaziv.isNotBlank()) {
                                            Text(vrsta.latinskiNaziv, color = FishBoje.Akcenat, fontSize = 12.sp,
                                                fontStyle = FontStyle.Italic)
                                        }
                                    }
                                },
                                onClick = {
                                    izabranaVrsta = vrsta
                                    vrstaRibe = vrsta.prikaz
                                    vrstaMeniOtvoren = false
                                }
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = duzinaRibe,
                        onValueChange = { duzinaRibe = it },
                        label = { Text("Dužina") },
                        suffix = { Text("cm") },
                        singleLine = true,
                        colors = fishPoljeBoje(),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                duzinaRibeImaFokus = focusState.isFocused
                            },
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = tezinaRibe,
                        onValueChange = {
                            tezinaRibe = it
                            // Brisanjem polja vraćamo automatski proračun
                            tezinaRucnoUneta = it.isNotBlank()
                        },
                        label = { Text("Težina") },
                        suffix = { Text("kg") },
                        singleLine = true,
                        colors = fishPoljeBoje(),
                        supportingText = {
                            if (!tezinaRucnoUneta && tezinaRibe.isNotBlank()) {
                                Text(
                                    if (TezinaRibe.poznataVrsta(nazivZaProveru)) "Procena po dužini" else "Okvirna procena",
                                    color = FishBoje.Akcenat
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                if (arPodrzan) {
                    OutlinedButton(
                        onClick = { arLauncher.launch(android.content.Intent(context, ArMeasureActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, FishBoje.AkcenatTamni),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = FishBoje.Akcenat.copy(alpha = 0.08f),
                            contentColor = FishBoje.Akcenat
                        )
                    ) {
                        Icon(Icons.Default.Straighten, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Izmeri dužinu AR metrom", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ─── KAKO JE UPECANA ───
            Sekcija(naslov = "Kako je upecana", ikona = Icons.Default.Phishing) {
                NaslovSekcije("Mamac")
                IzborCipovima(opcije = mamci, izabrano = odabranMamac, onIzbor = { odabranMamac = it })
                NaslovSekcije("Tehnika")
                IzborCipovima(opcije = tehnike, izabrano = odabranaTehnika, onIzbor = { odabranaTehnika = it })
            }

            // ─── MESTO ───
            Sekcija(naslov = "Mesto", ikona = Icons.Default.Place) {
                OutlinedTextField(
                    value = naziv,
                    onValueChange = { naziv = it },
                    label = { Text("Naziv mesta") },
                    placeholder = { Text("npr. Ada Ciganlija, kod mosta") },
                    singleLine = true,
                    colors = fishPoljeBoje(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = opis,
                    onValueChange = { opis = it },
                    label = { Text("Opis (opciono)") },
                    minLines = 2,
                    maxLines = 5,
                    colors = fishPoljeBoje(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                NaslovSekcije("Ocena mesta")
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

                // Vremenski uslovi se popunjavaju sami sa trenutne lokacije
                val uslovi = listOfNotNull(
                    temperatura.takeIf { it != "Nepoznato" }?.let { Icons.Default.Thermostat to it },
                    pritisak.takeIf { it != "Nepoznato" }?.let { Icons.Default.Speed to it },
                    vetar.takeIf { it != "Nepoznato" }?.let { Icons.Default.Air to it }
                )
                if (uslovi.isNotEmpty()) {
                    NaslovSekcije("Trenutni uslovi")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        uslovi.forEach { (ikona, tekst) ->
                            Row(
                                modifier = Modifier
                                    .background(FishBoje.Polje, RoundedCornerShape(20.dp))
                                    .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(ikona, contentDescription = null, tint = FishBoje.Akcenat, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(tekst, color = FishBoje.TekstSekundarni, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // --- DIJALOG KOJI ISKAČE AKO JE LOVOSTAJ ILI ISPOD MERE ---
    if (showLovostajUpozorenje != null) {
        AlertDialog(
            onDismissRequest = { showLovostajUpozorenje = null },
            containerColor = FishBoje.Kartica,
            titleContentColor = FishBoje.TekstPrimarni,
            textContentColor = FishBoje.TekstSekundarni,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = FishBoje.Upozorenje) },
            title = { Text("Zakonsko upozorenje", fontWeight = FontWeight.Bold) },
            text = { Text(showLovostajUpozorenje ?: "") },
            confirmButton = {
                Button(
                    onClick = { showLovostajUpozorenje = null },
                    colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Greska, contentColor = Color.White)
                ) {
                    Text("Razumem", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// Velika kartica za fotografiju: prazna sa dugmićima, ili slika sa oznakom da li je riba prepoznata
@Composable
private fun FotografijaUlova(
    slikaUri: Uri?,
    proveravam: Boolean,
    ribaPronadjena: Boolean,
    onSlikaj: () -> Unit,
    onGalerija: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(24.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(24.dp))
    ) {
        if (slikaUri == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(FishBoje.Akcenat.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, FishBoje.AkcenatTamni, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(34.dp),
                        tint = FishBoje.Akcenat)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Fotografiši svoj ulov", color = FishBoje.TekstPrimarni, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Riba treba da bude jasno vidljiva", color = FishBoje.TekstPriguseni, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onSlikaj,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = FishBoje.Pozadina)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Slikaj", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onGalerija,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, FishBoje.IvicaKartice),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FishBoje.TekstPrimarni)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Galerija")
                    }
                }
            }
        } else {
            AsyncImage(
                model = slikaUri,
                contentDescription = "Izabrani ulov",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Status prepoznavanja ribe
            val (tekst, boja) = when {
                proveravam -> "Proveravam fotografiju…" to FishBoje.Kartica
                ribaPronadjena -> "Riba prepoznata" to FishBoje.AkcenatTamni
                else -> "Riba nije prepoznata" to FishBoje.Greska
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(boja.copy(alpha = 0.92f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    proveravam -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = FishBoje.Akcenat)
                    ribaPronadjena -> Icon(Icons.Default.CheckCircle, contentDescription = null,
                        modifier = Modifier.size(18.dp), tint = Color.White)
                    else -> Icon(Icons.Default.ErrorOutline, contentDescription = null,
                        modifier = Modifier.size(18.dp), tint = Color.White)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(tekst, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            // Promena fotografije
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(Icons.Default.CameraAlt, "Slikaj ponovo", onSlikaj),
                    Triple(Icons.Default.PhotoLibrary, "Izaberi iz galerije", onGalerija)
                ).forEach { (ikona, opis, akcija) ->
                    IconButton(
                        onClick = akcija,
                        modifier = Modifier
                            .size(40.dp)
                            .background(FishBoje.Pozadina.copy(alpha = 0.75f), CircleShape)
                    ) {
                        Icon(ikona, contentDescription = opis, tint = FishBoje.TekstPrimarni, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// Kartica sekcije forme sa ikonom i naslovom (tamna kartica sa ivicom, kao u prognozi)
@Composable
private fun Sekcija(naslov: String, ikona: ImageVector, sadrzaj: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FishBoje.Kartica, RoundedCornerShape(20.dp))
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).background(FishBoje.Krug, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(ikona, contentDescription = null, tint = FishBoje.Akcenat, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(naslov, color = FishBoje.TekstPrimarni, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        sadrzaj()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IzborCipovima(opcije: List<String>, izabrano: String, onIzbor: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opcije.forEach { opcija ->
            FilterChip(
                selected = opcija == izabrano,
                onClick = { onIzbor(opcija) },
                label = { Text(opcija, fontWeight = if (opcija == izabrano) FontWeight.Bold else FontWeight.Normal) },
                leadingIcon = if (opcija == izabrano) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                shape = RoundedCornerShape(20.dp),
                colors = fishCipBoje(),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = opcija == izabrano,
                    borderColor = FishBoje.IvicaKartice,
                    selectedBorderColor = FishBoje.Akcenat
                )
            )
        }
    }
}
