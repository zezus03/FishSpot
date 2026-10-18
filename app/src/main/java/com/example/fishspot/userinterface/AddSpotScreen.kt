package com.example.fishspot.userinterface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.focus.onFocusChanged
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Straighten
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
import com.example.fishspot.ml.GeminiClassifier
import com.example.fishspot.viewmodel.FishDetector
import com.example.fishspot.viewmodel.SpotsViewModel
import com.example.fishspot.viewmodel.LovostajChecker // UVEZEN TVOJ PROŠIRENI ČEKER
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.fishspot.BuildConfig
fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap {
    val inputStream = context.contentResolver.openInputStream(uri)
    return BitmapFactory.decodeStream(inputStream).also {
        inputStream?.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSpotScreen(
    spotsViewModel: SpotsViewModel = viewModel(),
    latitude: Double,
    longitude: Double,
    onSpotAdded: () -> Unit
) {
    val context = LocalContext.current

    var showArMeasureScreen by remember { mutableStateOf(false) }

    var naziv by remember { mutableStateOf("") }
    var vrstaRibe by remember { mutableStateOf("") }
    var isValidatingFish by remember { mutableStateOf(false) }
    var ribaPronadjena by remember { mutableStateOf(false) }

    var ocena by remember { mutableStateOf(3f) }
    var opis by remember { mutableStateOf("") }
    var slikaUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var duzinaRibe by remember { mutableStateOf("") }
    var tezinaRibe by remember { mutableStateOf("") }
    val mamci = listOf("Crv", "Glista", "Šećerac", "Pop-up", "Keder", "Varalica")
    var odabranMamac by remember { mutableStateOf(mamci[0]) }
    var mamacExpanded by remember { mutableStateOf(false) }
    val tehnike = listOf("Plovak", "Dubinac", "Varalica", "Feeder")
    var odabranaTehnika by remember { mutableStateOf(tehnike[0]) }
    var tehnikaExpanded by remember { mutableStateOf(false) }
    var temperatura by remember { mutableStateOf("Nepoznato") }
    var pritisak by remember { mutableStateOf("Nepoznato") }
    var vetar by remember { mutableStateOf("Nepoznato") }

    val gemini = remember { GeminiClassifier() }
    var isAnalyzing by remember { mutableStateOf(false) }
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

    // --- LAUNCHED EFFECT: PROVERA LOKALNOG MODELA (DA LI JE RIBA NA SLICI) ---
    LaunchedEffect(slikaUri) {
        ribaPronadjena = false
        isValidatingFish = true

        slikaUri?.let { uri ->
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = loadBitmapFromUri(context, uri)

                    val testResult = fishDetector.testModel(bitmap)
                    val detailLog = fishDetector.getLastDetectionReport()

                    Log.d("AddSpotScreen-DEBUG", """
                    ════════════════════════════════════════════════════
                    🧪 MODEL TEST REZULTAT
                    ════════════════════════════════════════════════════
                    Fish Score:     ${String.format("%.4f", testResult.fishScore)}
                    NoFish Score:   ${String.format("%.4f", testResult.noFishScore)}
                    Is Fish:        ${testResult.isFish}
                    Confidence:     ${String.format("%.2f%%", testResult.confidence * 100)}
                    Difference:     ${String.format("%.4f", testResult.difference)}
                    Error:          ${testResult.error ?: "NEMA"}
                    ════════════════════════════════════════════════════
                    DETALJAN IZVEŠTAJ:
                    $detailLog
                """.trimIndent())

                    withContext(Dispatchers.Main) {
                        val imaRibe = fishDetector.imaRibuNaSlici(bitmap)
                        ribaPronadjena = imaRibe

                        if (!imaRibe) {
                            Toast.makeText(
                                context,
                                "⚠️ Riba nije prepoznata!\nMolimo Vas da slikate ribu iz blizine, jasnije ili pod boljim uglom kako bi je model uspešno detektovao. 🎣",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "✅ Riba uspešno prepoznata!\nPouzdanost modela: ${String.format("%.1f%%", testResult.confidence * 100)}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        isValidatingFish = false
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e("AddSpotScreen-ERROR", "Greška pri analizi: ${e.message}", e)
                        ribaPronadjena = false
                        isValidatingFish = false
                        Toast.makeText(context, "❌ Greška: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
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
    LaunchedEffect(vrstaRibeImaFokus, duzinaRibeImaFokus) {
        // Pokrećemo proveru SAMO ako su oba polja izgubila fokus (korisnik je kliknuo negde drugde)
        if (!vrstaRibeImaFokus && !duzinaRibeImaFokus && vrstaRibe.isNotBlank()) {
            val duzinaDuplo = duzinaRibe.toDoubleOrNull() ?: 0.0

            val uLovostaju = LovostajChecker.isRibaULovostaju(vrstaRibe)
            val ispodMere = LovostajChecker.isRibaIspodMere(vrstaRibe, duzinaDuplo)

            if (uLovostaju || ispodMere) {
                showLovostajUpozorenje = LovostajChecker.getUpozorenje(vrstaRibe, duzinaDuplo)
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

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.pp),
            contentDescription = "Pozadina",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "Dodaj novi ulov",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = naziv,
                        onValueChange = { naziv = it },
                        label = { Text("Naziv mesta") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = vrstaRibe,
                        onValueChange = { vrstaRibe = it },
                        label = { Text("Vrsta ribe") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                vrstaRibeImaFokus = focusState.isFocused
                            },
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = duzinaRibe,
                            onValueChange = { duzinaRibe = it },
                            label = { Text("Dužina (cm)") },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    duzinaRibeImaFokus = focusState.isFocused
                                },
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = if (arPodrzan) {
                                {
                                    IconButton(onClick = {
                                        val intent = android.content.Intent(context, ArMeasureActivity::class.java)
                                        arLauncher.launch(intent)
                                    }) {
                                        Icon(Icons.Default.Straighten, contentDescription = "Izmeri AR metrom", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } else null
                        )
                        OutlinedTextField(
                            value = tezinaRibe,
                            onValueChange = { tezinaRibe = it },
                            label = { Text("Težina (kg)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = mamacExpanded,
                        onExpandedChange = { mamacExpanded = !mamacExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = odabranMamac,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Mamac") },
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mamacExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = mamacExpanded,
                            onDismissRequest = { mamacExpanded = false }
                        ) {
                            mamci.forEach { mamac ->
                                DropdownMenuItem(
                                    text = { Text(mamac) },
                                    onClick = {
                                        odabranMamac = mamac
                                        mamacExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = tehnikaExpanded,
                        onExpandedChange = { tehnikaExpanded = !tehnikaExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = odabranaTehnika,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tehnika") },
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tehnikaExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = tehnikaExpanded,
                            onDismissRequest = { tehnikaExpanded = false }
                        ) {
                            tehnike.forEach { tehnika ->
                                DropdownMenuItem(
                                    text = { Text(tehnika) },
                                    onClick = {
                                        odabranaTehnika = tehnika
                                        tehnikaExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = opis,
                        onValueChange = { opis = it },
                        label = { Text("Opis") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Ocena mesta: ${ocena.toInt()}", fontWeight = FontWeight.Bold)
                    Slider(value = ocena, onValueChange = { ocena = it }, valueRange = 1f..5f, steps = 3)

                    slikaUri?.let { uri ->
                        Spacer(modifier = Modifier.height(16.dp))

                        AsyncImage(
                            model = uri,
                            contentDescription = "Izabrani ulov",
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                isAnalyzing = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val bitmap = loadBitmapFromUri(context, uri)
                                        val trenutnaDuzina = duzinaRibe.toDoubleOrNull()

                                        val rezultat = gemini.prepoznajRibu(bitmap, trenutnaDuzina)

                                        withContext(Dispatchers.Main) {
                                            if (rezultat != null && !rezultat.first.contains("Nepoznato", ignoreCase = true)) {
                                                vrstaRibe = rezultat.first
                                                if (rezultat.second != "0" && rezultat.second.isNotEmpty()) {
                                                    tezinaRibe = rezultat.second
                                                    Toast.makeText(context, "AI: ${rezultat.first} teži oko ${rezultat.second} kg! \uD83C\uDFA3", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "AI: Prepoznat ${rezultat.first}", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "AI nije siguran šta je na slici.", Toast.LENGTH_SHORT).show()
                                            }
                                            isAnalyzing = false
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isAnalyzing = false
                                            Toast.makeText(context, "Greška: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isAnalyzing,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Pitaj AI model za vrstu i težinu")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Galerija")
                            Spacer(Modifier.width(8.dp))
                            Text("Galerija")
                        }

                        Button(
                            onClick = {
                                val contentValues = android.content.ContentValues().apply {
                                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "fishspot_${System.currentTimeMillis()}.jpg")
                                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                }
                                tempCameraUri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                                tempCameraUri?.let { uri ->
                                    cameraLauncher.launch(uri)
                                }
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Kamera")
                            Spacer(Modifier.width(8.dp))
                            Text("Slikaj")
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            isSaving = true
                            spotsViewModel.addSpot(
                                context = context,
                                naziv = naziv, vrstaRibe = vrstaRibe, ocena = ocena, opis = opis, slikaUri = slikaUri,
                                location = trenutnaLokacija,
                                duzinaRibe = duzinaRibe.toDoubleOrNull() ?: 0.0,
                                tezinaRibe = tezinaRibe.toDoubleOrNull() ?: 0.0,
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
                        enabled = !isSaving && slikaUri != null && ribaPronadjena,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Sačuvaj ulov", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // --- 3. DIALOG KOJI ISKAČE AKO JE LOVOSTAJ ILI ISPOD MERE ---
        if (showLovostajUpozorenje != null) {
            AlertDialog(
                onDismissRequest = { showLovostajUpozorenje = null },
                title = {
                    Text("⚠️ Zakonsko upozorenje", color = Color.Red, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(showLovostajUpozorenje ?: "")
                },
                confirmButton = {
                    Button(
                        onClick = { showLovostajUpozorenje = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Razumem")
                    }
                }
            )
        }
    }
}