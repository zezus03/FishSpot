package com.example.fishspot.feature.spots
import com.example.fishspot.core.theme.FishPoruka

import com.example.fishspot.feature.fish.ArMeasureActivity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.focus.onFocusChanged
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.sync.withLock
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
    val focusManager = LocalFocusManager.current

    var showArMeasureScreen by remember { mutableStateOf(false) }

    var naziv by remember { mutableStateOf("") }
    // Naziv mesta sa mape (Geocoder) se upisuje sam dok korisnik ne počne da kuca
    var predlogMesta by remember { mutableStateOf<String?>(null) }
    var nazivRucno by remember { mutableStateOf(false) }
    LaunchedEffect(latitude, longitude) {
        // Mesto (Geocoder, traži internet) i najbliža voda (lokalna mapa) se traže istovremeno,
        // pa se spoje: "Babušnica, Lužnica (reka)". Bilo koji deo može da izostane.
        val voda = async { NajblizaVoda.nadji(context, latitude, longitude) }
        val mesto = NazivMesta.predlog(context, latitude, longitude)
        val predlog = listOfNotNull(mesto, voda.await()).joinToString(", ").take(100).takeIf { it.isNotBlank() }
        predlogMesta = predlog
        if (predlog != null && !nazivRucno && naziv.isBlank()) naziv = predlog
    }
    var vrstaRibe by remember { mutableStateOf("") } // Tekst u polju (ono što korisnik kuca)
    var izabranaVrsta by remember { mutableStateOf<VrstaRibe?>(null) } // Vrsta izabrana iz padajućeg menija
    var vrstaMeniOtvoren by remember { mutableStateOf(false) }
    val sveVrste by vrsteRibaViewModel.vrste.collectAsState()
    // Lovostaj i težina gledaju samo srpski naziv, bez latinskog
    val nazivZaProveru = izabranaVrsta?.naziv ?: vrstaRibe
    // Do 5 slika: glavna mora imati prepoznatu ribu (na njoj rade provere za poene),
    // ostale (ekipa, štap, varalica) su dodatne
    var slike by remember { mutableStateOf(listOf<SlikaUlova>()) }
    var izabranaGlavna by remember { mutableStateOf<Uri?>(null) }
    val glavna = slike.firstOrNull { it.uri == izabranaGlavna && it.riba == true } ?: slike.firstOrNull { it.riba == true }
    val isValidatingFish = slike.any { it.riba == null }
    val ribaPronadjena = glavna != null
    val izvorSlike = glavna?.izvor

    var ocena by remember { mutableStateOf(3f) }
    var opis by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var duzinaRibe by remember { mutableStateOf("") }
    var tezinaRibe by remember { mutableStateOf("") }
    val mamci = OpcijeUlova.mamci
    var odabranMamac by remember { mutableStateOf(mamci[0]) }
    val tehnike = OpcijeUlova.tehnike
    var odabranaTehnika by remember { mutableStateOf(tehnike[0]) }
    // Promena mamca prebacuje tehniku na prvu koja ima smisla (npr. Varalica -> Varalica)
    LaunchedEffect(odabranMamac) {
        val dozvoljene = OpcijeUlova.dozvoljeneTehnike(odabranMamac)
        if (odabranaTehnika !in dozvoljene) odabranaTehnika = dozvoljene.first()
    }
    var temperatura by remember { mutableStateOf("Nepoznato") }
    var pritisak by remember { mutableStateOf("Nepoznato") }
    var vetar by remember { mutableStateOf("Nepoznato") }

    // Čuvarka: više riba i vrsta zajedno, ukupna težina (do 30 kg), bez dužine i granica po vrsti
    var cuvarka by remember { mutableStateOf(false) }
    var vrsteCuvarke by remember { mutableStateOf(listOf<VrstaRibe>()) }

    // Težina se računa iz dužine i vrste dok je korisnik ne unese ručno (ne kod čuvarke)
    var tezinaRucnoUneta by remember { mutableStateOf(false) }
    LaunchedEffect(nazivZaProveru, duzinaRibe) {
        if (!tezinaRucnoUneta && !cuvarka) {
            val kg = TezinaRibe.proceniKg(nazivZaProveru, duzinaRibe.replace(',', '.').toDoubleOrNull())
            tezinaRibe = kg?.let { String.format(Locale.US, "%.2f", it) } ?: ""
        }
    }
    val coroutineScope = rememberCoroutineScope()

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
    // Svaka slika se proverava posebno (jedna po jedna, model nije za istovremeni rad);
    // ulov može da se sačuva kad je riba prepoznata bar na jednoj slici
    val modelZauzet = remember { kotlinx.coroutines.sync.Mutex() }
    fun dodajSlike(nove: List<Uri>, izKamere: Boolean) {
        val slobodno = MAKS_SLIKA_ULOVA - slike.size
        val dodate = nove.distinct().filter { u -> slike.none { it.uri == u } }.take(slobodno)
        if (nove.size > slobodno) FishPoruka.prikazi("Ulov može da ima najviše $MAKS_SLIKA_ULOVA slika", dugo = true)
        if (dodate.isEmpty()) return
        slike = slike + dodate.map { SlikaUlova(it, izKamere) }
        dodate.forEach { uri ->
            coroutineScope.launch {
                var riba = false
                var izvor: IzvorSlike? = null
                try {
                    withContext(Dispatchers.IO) {
                        // Slika se učitava uspravno (EXIF rotacija) i smanjena, kao što je korisnik vidi
                        riba = modelZauzet.withLock { fishDetector.analiziraj(ucitajSlikuUspravno(context, uri)) }.jeRiba
                        izvor = if (izKamere) IzvorSlike.APLIKACIJA else ProveraSlike.oceni(context, uri)
                    }
                } catch (e: Exception) {
                    Log.e("AddSpotScreen", "Greška pri analizi slike", e)
                }
                slike = slike.map { if (it.uri == uri) it.copy(riba = riba, izvor = izvor) else it }
                if (slike.none { it.riba == null } && slike.none { it.riba == true }) {
                    FishPoruka.prikazi("⚠️ Riba nije prepoznata ni na jednoj slici!\nSlikaj ribu iz blizine, jasnije ili pod boljim uglom. 🎣", dugo = true)
                }
            }
        }
    }

    fun ukloniSliku(uri: Uri) {
        slike = slike.filterNot { it.uri == uri }
        if (izabranaGlavna == uri) izabranaGlavna = null
    }

    fun postaviGlavnu(slika: SlikaUlova) {
        when (slika.riba) {
            true -> izabranaGlavna = slika.uri
            false -> FishPoruka.prikazi("Glavna slika mora biti slika na kojoj je prepoznata riba")
            null -> FishPoruka.prikazi("Slika se još proverava")
        }
    }

    // --- LAUNCHED EFFECT: VREMENSKA PROGNOZA ---
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = com.example.fishspot.feature.weather.Prognoza.trenutno(lat = latitude, lng = longitude)

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

    // Izvor glavne slike odlučuje o poenima (kamera u aplikaciji +20%, skinuta slika ili screenshot bez poena);
    // određuje se za svaku sliku u dodajSlike
    // Sistemski izbor fajlova (ne birač slika): samo on vraća pravo ime fajla (npr. IMG-...-WA0003.jpg),
    // po kome se prepoznaju slike iz Vibera, WhatsApp-a i Messengera. Birač slika ime sakriva ("1000046227.jpg").
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris -> if (uris.isNotEmpty()) dodajSlike(uris, izKamere = false) }
    )

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            val uri = tempCameraUri
            if (success && uri != null) dodajSlike(listOf(uri), izKamere = true)
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
            FishPoruka.prikazi("Za slikanje je potrebna dozvola za kameru", dugo = true)
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
        slike.isEmpty() -> "Dodaj fotografiju ulova"
        isValidatingFish -> "Proveravam fotografije…"
        !ribaPronadjena -> "Ni na jednoj fotografiji nije prepoznata riba"
        cuvarka -> proveraCuvarke(vrsteCuvarke, tezinaRibe.replace(',', '.').toDoubleOrNull())
        izabranaVrsta == null -> "Izaberi vrstu ribe sa liste"
        else -> com.example.fishspot.feature.fish.OgranicenjaRibe.proveri(
            izabranaVrsta?.id, izabranaVrsta?.naziv ?: "",
            duzinaRibe.replace(',', '.').toDoubleOrNull(),
            tezinaRibe.replace(',', '.').toDoubleOrNull()
        )
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
                    } else {
                        // Koliko poena ulov donosi (konačno ih dodeljuje server kad stigne slika)
                        PoeniUlova.proceni(tezinaRibe.replace(',', '.').toDoubleOrNull(), izvorSlike, cuvarka)?.let { p ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (p.poeni > 0) Icons.Default.Star else Icons.Default.Warning, contentDescription = null,
                                        tint = if (p.poeni > 0) FishBoje.Zvezdica else FishBoje.Upozorenje, modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        when {
                                            p.poeni == 0 -> "Ovaj ulov ne donosi poene"
                                            p.cekaAdmina -> "Ovaj ulov donosi +${p.poeni} poena kad ga admin odobri"
                                            else -> "Ovaj ulov donosi +${p.poeni} poena"
                                        },
                                        color = FishBoje.TekstPrimarni, fontSize = 15.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                                if (p.poeni > 0 && !p.cekaAdmina) {
                                    Text(p.opis, color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val vrsta = (if (cuvarka) vrsteCuvarke.firstOrNull() else izabranaVrsta) ?: return@Button
                            val glavnaUri = glavna?.uri ?: return@Button
                            isSaving = true
                            coroutineScope.launch {
                            // Ista slika ne sme dvaput kao ulov
                            if (spotsViewModel.slikaVecObjavljena(context, glavnaUri)) {
                                FishPoruka.prikazi("Ova slika je već objavljena u FishSpot-u. Izaberi drugu glavnu sliku.", dugo = true)
                                isSaving = false
                                return@launch
                            }
                            spotsViewModel.addSpot(
                                context = context,
                                naziv = naziv, vrsta = vrsta, ocena = ocena, opis = opis,
                                glavnaSlika = glavnaUri,
                                dodatneSlike = slike.map { it.uri }.filter { it != glavna?.uri },
                                location = trenutnaLokacija,
                                duzinaRibe = if (cuvarka) 0.0 else duzinaRibe.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                cuvarka = cuvarka,
                                cuvarkaVrste = if (cuvarka) vrsteCuvarke else emptyList(),
                                tezinaRibe = tezinaRibe.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                mamac = odabranMamac,
                                tehnika = odabranaTehnika,
                                temperatura = temperatura,
                                pritisak = pritisak,
                                vetar = vetar,
                                izvorSlike = izvorSlike?.kod ?: IzvorSlike.NEPOZNATA.kod
                            ) { result ->
                                if (result.isSuccess) {
                                    val kg = tezinaRibe.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    FishPoruka.prikazi(
                                        when {
                                            izvorSlike?.donosiPoene != true -> "Ulov sačuvan, ali bez poena zbog izvora slike."
                                            kg > 5.0 && !cuvarka -> "Ulov sačuvan! Riba preko 5 kg dobija poene kad je admin pregleda."
                                            else -> "Ulov sačuvan! Poeni stižu čim se slika pošalje."
                                        },
                                        dugo = kg > 5.0 && !cuvarka
                                    )
                                    onSpotAdded()
                                } else {
                                    val errorMessage = result.exceptionOrNull()?.message ?: "Nepoznata greška"
                                    FishPoruka.prikazi("Greška: $errorMessage", dugo = true)
                                }
                                isSaving = false
                            }
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
                slike = slike,
                glavna = glavna,
                onIzaberiGlavnu = { postaviGlavnu(it) },
                onUkloni = { ukloniSliku(it.uri) },
                onSlikaj = {
                    val imaDozvolu = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.CAMERA
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (imaDozvolu) otvoriKameru()
                    else kameraDozvolaLauncher.launch(android.Manifest.permission.CAMERA)
                },
                onGalerija = { galleryLauncher.launch(arrayOf("image/*")) }
            )

            izvorSlike?.let { izvor ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background((if (izvor.donosiPoene) FishBoje.Akcenat else FishBoje.Upozorenje).copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (izvor.donosiPoene) Icons.Default.Star else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (izvor.donosiPoene) FishBoje.Akcenat else FishBoje.Upozorenje,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(izvor.opis, color = FishBoje.TekstPrimarni, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (izvor != IzvorSlike.APLIKACIJA) {
                            Text("Slikaj u aplikaciji za +20% poena", color = FishBoje.TekstSekundarni, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ─── RIBA ───
            Sekcija(naslov = if (cuvarka) "Riba u čuvarki" else "Riba", ikona = Icons.Default.SetMeal, akcija = {
                CuvarkaDugme(ukljuceno = cuvarka) {
                    if (!cuvarka) {
                        // Uključivanje: izabrana vrsta prelazi u čuvarku, dužina se briše
                        izabranaVrsta?.let { vrsteCuvarke = listOf(it) }
                        izabranaVrsta = null
                        vrstaRibe = ""
                        duzinaRibe = ""
                        tezinaRibe = ""
                        tezinaRucnoUneta = true
                    } else {
                        // Isključivanje: prva vrsta iz čuvarke postaje vrsta ulova
                        vrsteCuvarke.firstOrNull()?.let { izabranaVrsta = it; vrstaRibe = it.prikaz }
                        vrsteCuvarke = emptyList()
                        tezinaRibe = ""
                        tezinaRucnoUneta = false
                    }
                    cuvarka = !cuvarka
                    showLovostajUpozorenje = null
                }
            }) {
                if (cuvarka) VrsteCuvarke(vrsteCuvarke, onUkloni = { v -> vrsteCuvarke = vrsteCuvarke - v })
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
                        label = { Text(if (cuvarka) "Dodaj vrstu u čuvarku" else "Vrsta ribe") },
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
                        // Napomena za nazive koji se često mešaju (npr. karaš / babuška)
                        com.example.fishspot.feature.fish.LokalniNazivi.napomena(vrstaRibe)?.let { napomena ->
                            Text(napomena, color = FishBoje.Upozorenje, fontSize = 12.sp, lineHeight = 16.sp,
                                modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        predlozi.forEach { vrsta ->
                            val lokalni = com.example.fishspot.feature.fish.LokalniNazivi.pogodak(vrsta.id, vrstaRibe)
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(vrsta.naziv, color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold)
                                        if (vrsta.latinskiNaziv.isNotBlank()) {
                                            Text(vrsta.latinskiNaziv, color = FishBoje.Akcenat, fontSize = 12.sp,
                                                fontStyle = FontStyle.Italic)
                                        }
                                        if (lokalni != null && !LovostajChecker.normalizujTekst(vrsta.naziv)
                                                .startsWith(LovostajChecker.normalizujTekst(vrstaRibe))) {
                                            Text("Poznata i kao: $lokalni", color = FishBoje.TekstSekundarni, fontSize = 12.sp)
                                        }
                                    }
                                },
                                onClick = {
                                    if (cuvarka) {
                                        // Čuvarka: vrsta se dodaje na spisak, polje se prazni za sledeću
                                        if (vrsteCuvarke.none { it.id == vrsta.id } && vrsteCuvarke.size < MAKS_VRSTA_CUVARKE) {
                                            vrsteCuvarke = vrsteCuvarke + vrsta
                                        }
                                        vrstaRibe = ""
                                        vrstaMeniOtvoren = false
                                        focusManager.clearFocus()
                                        if (LovostajChecker.isRibaULovostaju(vrsta.naziv)) {
                                            showLovostajUpozorenje = LovostajChecker.getUpozorenje(vrsta.naziv, 0.0)
                                        }
                                        return@DropdownMenuItem
                                    }
                                    izabranaVrsta = vrsta
                                    vrstaRibe = vrsta.prikaz
                                    vrstaMeniOtvoren = false
                                    // Tastatura se zatvara da ne prekriva dužinu i težinu
                                    focusManager.clearFocus()
                                }
                            )
                        }
                    }
                }

                // Kod čuvarke nema dužine: ukupna težina stoji sama, na sredini
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (cuvarka) Arrangement.Center else Arrangement.spacedBy(12.dp)
                ) {
                    if (!cuvarka) OutlinedTextField(
                        value = duzinaRibe,
                        onValueChange = { duzinaRibe = it },
                        label = { Text("Dužina") },
                        suffix = { Text("cm") },
                        singleLine = true,
                        enabled = !cuvarka,
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
                        label = { Text(if (cuvarka) "Ukupna težina" else "Težina") },
                        suffix = { Text("kg") },
                        singleLine = true,
                        colors = fishPoljeBoje(),
                        supportingText = {
                            if (cuvarka) {
                                Text("Sve ribe zajedno", color = FishBoje.TekstPriguseni)
                            } else if (!tezinaRucnoUneta && tezinaRibe.isNotBlank()) {
                                Text(
                                    if (TezinaRibe.poznataVrsta(nazivZaProveru)) "Procena po dužini" else "Okvirna procena",
                                    color = FishBoje.Akcenat
                                )
                            }
                        },
                        modifier = if (cuvarka) Modifier.fillMaxWidth(0.6f) else Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                if (arPodrzan && !cuvarka) {
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
                IzborMamca(izabrano = odabranMamac, onIzbor = { odabranMamac = it })
                NaslovSekcije("Tehnika")
                IzborCipovima(
                    // Prikazuju se samo tehnike koje idu uz izabrani mamac
                    opcije = OpcijeUlova.dozvoljeneTehnike(odabranMamac), izabrano = odabranaTehnika,
                    onIzbor = { odabranaTehnika = it }
                )
            }

            // ─── MESTO ───
            Sekcija(naslov = "Mesto", ikona = Icons.Default.Place) {
                OutlinedTextField(
                    value = naziv,
                    onValueChange = { naziv = it.take(100); nazivRucno = true },
                    label = { Text("Naziv mesta") },
                    placeholder = { Text("npr. Ada Ciganlija, kod mosta") },
                    singleLine = true,
                    colors = fishPoljeBoje(),
                    trailingIcon = {
                        when {
                            naziv.isNotEmpty() -> IconButton(onClick = { naziv = ""; nazivRucno = true }) {
                                Icon(Icons.Default.Clear, contentDescription = "Obriši naziv mesta")
                            }
                            predlogMesta != null -> IconButton(onClick = { naziv = predlogMesta ?: "" }) {
                                Icon(Icons.Default.Place, contentDescription = "Upiši mesto sa mape", tint = FishBoje.Akcenat)
                            }
                        }
                    },
                    supportingText = {
                        val predlog = predlogMesta
                        when {
                            predlog != null && naziv == predlog -> Text("Popunjeno sa mape, možeš da dopuniš ili obrišeš")
                            predlog != null && naziv.isEmpty() -> Text("Dodirni ikonicu da upišeš: $predlog")
                        }
                    },
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

const val MAKS_SLIKA_ULOVA = 5

// Slika u formi za ulov; riba == null dok model još proverava
data class SlikaUlova(val uri: Uri, val izKamere: Boolean, val riba: Boolean? = null, val izvor: IzvorSlike? = null)

// Kartica sa fotografijama: prazna sa dugmićima, ili velika glavna slika i traka sa svim slikama
@Composable
private fun FotografijaUlova(
    slike: List<SlikaUlova>,
    glavna: SlikaUlova?,
    onIzaberiGlavnu: (SlikaUlova) -> Unit,
    onUkloni: (SlikaUlova) -> Unit,
    onSlikaj: () -> Unit,
    onGalerija: () -> Unit
) {
    if (slike.isNotEmpty()) {
        FotografijeUlova(slike, glavna, onIzaberiGlavnu, onUkloni, onSlikaj, onGalerija)
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(24.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(24.dp))
    ) {
        run {
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
                Text("Do $MAKS_SLIKA_ULOVA slika, na bar jednoj jasno vidljiva riba", color = FishBoje.TekstPriguseni, fontSize = 13.sp)
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
        }
    }
}

// Velika glavna slika (ili prva, dok se ne nađe riba) i traka sa svim slikama
@Composable
private fun FotografijeUlova(
    slike: List<SlikaUlova>,
    glavna: SlikaUlova?,
    onIzaberiGlavnu: (SlikaUlova) -> Unit,
    onUkloni: (SlikaUlova) -> Unit,
    onSlikaj: () -> Unit,
    onGalerija: () -> Unit
) {
    val prikazana = glavna ?: slike.first()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(24.dp))
                .background(FishBoje.Kartica)
                .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(24.dp))
        ) {
            AsyncImage(
                model = prikazana.uri,
                contentDescription = "Glavna slika ulova",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            val (tekst, boja) = when {
                prikazana.riba == null -> "Proveravam fotografiju…" to FishBoje.Kartica
                prikazana == glavna -> "Glavna slika · riba prepoznata" to FishBoje.AkcenatTamni
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
                    prikazana.riba == null -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = FishBoje.Akcenat)
                    prikazana == glavna -> Icon(Icons.Default.Star, contentDescription = null,
                        modifier = Modifier.size(18.dp), tint = Color.White)
                    else -> Icon(Icons.Default.ErrorOutline, contentDescription = null,
                        modifier = Modifier.size(18.dp), tint = Color.White)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(tekst, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slike.forEach { slika ->
                SlicicaUlova(slika, jeGlavna = slika == glavna, onClick = { onIzaberiGlavnu(slika) }, onUkloni = { onUkloni(slika) })
            }
            if (slike.size < MAKS_SLIKA_ULOVA) {
                DodajSlikuDugme(Icons.Default.CameraAlt, "Slikaj", onSlikaj)
                DodajSlikuDugme(Icons.Default.PhotoLibrary, "Galerija", onGalerija)
            }
        }
        Text(
            "Slika sa zvezdicom je glavna (na njoj mora biti riba). Dodirni drugu sliku sa ribom da postane glavna. " +
                "Ostale slike (ekipa, štap, varalica) su dodatne. ${slike.size}/$MAKS_SLIKA_ULOVA",
            color = FishBoje.TekstPriguseni, fontSize = 12.sp, lineHeight = 16.sp
        )
    }
}

@Composable
private fun SlicicaUlova(slika: SlikaUlova, jeGlavna: Boolean, onClick: () -> Unit, onUkloni: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(if (jeGlavna) 2.dp else 1.dp, if (jeGlavna) FishBoje.Akcenat else FishBoje.IvicaKartice, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(model = slika.uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // Oznaka u donjem levom uglu: provera, glavna, riba ili bez ribe
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .size(22.dp)
                .background(
                    when {
                        slika.riba == null -> FishBoje.Kartica
                        slika.riba -> FishBoje.AkcenatTamni
                        else -> FishBoje.Pozadina.copy(alpha = 0.8f)
                    }, CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                slika.riba == null -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = FishBoje.Akcenat)
                jeGlavna -> Icon(Icons.Default.Star, contentDescription = "Glavna slika", tint = Color.White, modifier = Modifier.size(14.dp))
                slika.riba -> Icon(Icons.Default.SetMeal, contentDescription = "Riba prepoznata", tint = Color.White, modifier = Modifier.size(13.dp))
                else -> Icon(Icons.Default.PhotoLibrary, contentDescription = "Dodatna slika", tint = FishBoje.TekstSekundarni, modifier = Modifier.size(12.dp))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(22.dp)
                .background(FishBoje.Pozadina.copy(alpha = 0.8f), CircleShape)
                .clickable(onClick = onUkloni),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "Ukloni sliku", tint = FishBoje.TekstPrimarni, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun DodajSlikuDugme(ikona: ImageVector, tekst: String, onClick: () -> Unit) {
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

// Kartica sekcije forme sa ikonom i naslovom (tamna kartica sa ivicom, kao u prognozi)
@Composable
private fun Sekcija(
    naslov: String,
    ikona: ImageVector,
    akcija: (@Composable () -> Unit)? = null,
    sadrzaj: @Composable ColumnScope.() -> Unit
) {
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
            Text(naslov, color = FishBoje.TekstPrimarni, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            akcija?.invoke()
        }
        sadrzaj()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IzborCipovima(
    opcije: List<String>,
    izabrano: String,
    onIzbor: (String) -> Unit,
    dozvoljeno: List<String> = opcije
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opcije.forEach { opcija ->
            FilterChip(
                selected = opcija == izabrano,
                onClick = { onIzbor(opcija) },
                enabled = opcija in dozvoljeno,
                label = { Text(opcija, fontWeight = if (opcija == izabrano) FontWeight.Bold else FontWeight.Normal) },
                leadingIcon = if (opcija == izabrano) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                shape = RoundedCornerShape(20.dp),
                colors = fishCipBoje(),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = opcija in dozvoljeno,
                    selected = opcija == izabrano,
                    borderColor = FishBoje.IvicaKartice,
                    selectedBorderColor = FishBoje.Akcenat
                )
            )
        }
    }
}
