package com.example.fishspot.feature.profile
import com.example.fishspot.core.theme.FishPoruka

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.FishZaglavlje
import com.example.fishspot.core.theme.NaslovSekcije
import com.example.fishspot.core.theme.fishPoljeBoje
import com.example.fishspot.feature.map.MapViewModel
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

// Stranica je u folderu hosting/ i objavljuje se sa "firebase deploy --only hosting"
const val POLITIKA_PRIVATNOSTI_URL = "https://fishspot-52735.web.app/privatnost.html"

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onPrikaziUlove: (uid: String) -> Unit = {},
    onPogledajUvod: () -> Unit = {},
    onAdminPanel: () -> Unit = {},
    mapViewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val currentUser = auth.currentUser

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var profileImageUrl by remember { mutableStateOf("") }
    var newImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var potvrdaBrisanjaNaloga by remember { mutableStateOf(false) }
    var brisanjeNaloga by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Statistika: poeni, broj ulova i mesto na rang listi
    var poeni by remember { mutableStateOf<Long?>(null) }
    var brojUlova by remember { mutableStateOf<Long?>(null) }
    var mestoNaListi by remember { mutableStateOf<Long?>(null) }

    // Da li korisnik ima postavljenu lozinku (Google nalozi je nemaju dok je ne postave)
    var hasPassword by remember { mutableStateOf(true) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                newImageBitmap = bitmap
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Greška pri učitavanju slike: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        mapViewModel.ucitajPreferences(context) // Osiguravamo učitavanje stanja prekidača obaveštenja
        currentUser?.let { user ->
            try {
                email = user.email ?: ""
                hasPassword = user.providerData.map { it.providerId }.contains("password")

                // Uvek učitavamo direktno iz Firestore-a (source = SERVER) da izbegnemo stare keširane podatke
                val doc = firestore.collection("Korisnici").document(user.uid)
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .await()
                // fotoUrl/username su stara imena polja (dokumenti pre usklađivanja)
                username = doc.getString("userName") ?: doc.getString("username") ?: ""
                profileImageUrl = (doc.getString("slikaURL") ?: doc.getString("fotoUrl"))
                    ?.takeIf { it != "null" } ?: ""
                poeni = doc.getLong("poeni") ?: 0L

                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Greška pri učitavanju profila: ${e.message}"
                isLoading = false
            }

            // Brojanje na serveru (count), bez preuzimanja samih dokumenata
            try {
                brojUlova = firestore.collection("MestaZaPecanje").whereEqualTo("authorId", user.uid)
                    .count().get(AggregateSource.SERVER).await().count
                poeni?.let { mojiPoeni ->
                    mestoNaListi = firestore.collection("Korisnici").whereGreaterThan("poeni", mojiPoeni)
                        .count().get(AggregateSource.SERVER).await().count + 1
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileScreen", "Statistika nije učitana", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FishBoje.Pozadina)
    ) {
        FishZaglavlje(
            oznaka = "FishSpot • profil",
            naslov = "Moj profil",
            ikona = Icons.Default.Person,
            onNazad = onNavigateBack
        )

        // Bez ranog "return" iz Column-a: u ovoj verziji Compose-a to ruši aplikaciju (Stack.pop)
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FishBoje.Akcenat)
            }
        } else Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ────── PROFILNA SLIKA SA ZNAČKOM ZA PROMENU ──────
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(FishBoje.Krug)
                        .border(3.dp, FishBoje.Akcenat, CircleShape)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        // Lokalna bitmapa ostaje vidljiva dok se radi upload
                        newImageBitmap != null -> Image(
                            bitmap = newImageBitmap!!.asImageBitmap(),
                            contentDescription = "Nova profilna slika",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        profileImageUrl.isNotBlank() -> key(profileImageUrl) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(context)
                                    .data(profileImageUrl)
                                    .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profilna slika",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        else -> Text(username.trim().firstOrNull()?.uppercase() ?: "?",
                            color = FishBoje.Akcenat, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(FishBoje.Akcenat, CircleShape)
                        .border(3.dp, FishBoje.Pozadina, CircleShape)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Promeni sliku",
                        tint = FishBoje.Pozadina, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(username.ifBlank { "Pecaroš" }, color = FishBoje.TekstPrimarni, fontSize = 22.sp,
                fontWeight = FontWeight.Bold)
            Text(email, color = FishBoje.TekstPriguseni, fontSize = 13.sp)

            // ────── STATISTIKA ──────
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlocicaStatistike(Icons.Default.Star, poeni?.toString() ?: "—", "poena",
                    FishBoje.ZelenaPlocica, Modifier.weight(1f))
                // Dodir otvara listu svih ulova sa brojem ulova po vrstama
                PlocicaStatistike(Icons.Default.SetMeal, brojUlova?.toString() ?: "—", "ulova ›",
                    FishBoje.PlavaPlocica, Modifier.weight(1f),
                    onClick = { currentUser?.uid?.let(onPrikaziUlove) })
                PlocicaStatistike(Icons.Default.EmojiEvents, mestoNaListi?.let { "$it." } ?: "—", "na listi",
                    FishBoje.NarandzastaPlocica, Modifier.weight(1f))
            }

            // ────── NALOG ──────
            Sekcija("Nalog") {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    label = { Text("Korisničko ime") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    colors = fishPoljeBoje(),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = {},
                    label = { Text("E-mail") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    readOnly = true,
                    colors = fishPoljeBoje(),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // ────── OBAVEŠTENJA ──────
            val vodeNotificationsEnabled by mapViewModel.vodeNotificationsEnabled.collectAsState()
            Sekcija("Obaveštenja") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IkonaUKrugu(Icons.Default.Notifications)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Obaveštenja o vodama", color = FishBoje.TekstPrimarni, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text("Primi notifikaciju kada si blizu reke ili jezera", color = FishBoje.TekstPriguseni,
                            fontSize = 12.sp)
                    }
                    Switch(
                        checked = vodeNotificationsEnabled,
                        onCheckedChange = { upaljeno -> mapViewModel.setVodeNotificationsEnabled(context, upaljeno) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = FishBoje.Pozadina,
                            checkedTrackColor = FishBoje.Akcenat,
                            uncheckedThumbColor = FishBoje.TekstPriguseni,
                            uncheckedTrackColor = FishBoje.Polje,
                            uncheckedBorderColor = FishBoje.IvicaKartice
                        )
                    )
                }
            }

            // ────── ADMIN (vidi se samo admin nalozima) ──────
            if (com.example.fishspot.feature.admin.Admini.jeAdmin()) {
                Sekcija("Administracija") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onAdminPanel)
                    ) {
                        IkonaUKrugu(Icons.Default.AdminPanelSettings)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Admin panel", color = FishBoje.TekstPrimarni, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Prijavljeni ulovi: vraćanje i brisanje", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = FishBoje.TekstPriguseni)
                    }
                }
            }

            // ────── UVOD ──────
            Sekcija("Pomoć") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onPogledajUvod)
                ) {
                    IkonaUKrugu(Icons.Default.School)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Uvod sa deda Perom", color = FishBoje.TekstPrimarni, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text("Ponovo pogledaj šta sve aplikacija ume", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = FishBoje.TekstPriguseni)
                }
            }

            // ────── PRIVATNOST ──────
            val deliLokaciju by mapViewModel.deliLokaciju.collectAsState()
            Sekcija("Privatnost") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IkonaUKrugu(Icons.Default.MyLocation)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Prikaži me na mapi", color = FishBoje.TekstPrimarni, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text("Drugi pecaroši vide gde si dok je aplikacija otvorena i mogu da ti pišu",
                            color = FishBoje.TekstPriguseni, fontSize = 12.sp)
                    }
                    Switch(
                        checked = deliLokaciju,
                        onCheckedChange = { upaljeno -> mapViewModel.setDeliLokaciju(context, upaljeno) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = FishBoje.Pozadina,
                            checkedTrackColor = FishBoje.Akcenat,
                            uncheckedThumbColor = FishBoje.TekstPriguseni,
                            uncheckedTrackColor = FishBoje.Polje,
                            uncheckedBorderColor = FishBoje.IvicaKartice
                        )
                    )
                }
            }

            // ────── BEZBEDNOST ──────
            Sekcija("Bezbednost") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showPasswordDialog = true }
                        .padding(vertical = 4.dp)
                ) {
                    IkonaUKrugu(Icons.Default.Lock)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (hasPassword) "Promeni lozinku" else "Postavi lozinku", color = FishBoje.TekstPrimarni,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (hasPassword) "Za prijavu e-mailom" else "Prijavljen si preko Google-a; dodaj i lozinku",
                            color = FishBoje.TekstPriguseni, fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = FishBoje.TekstPriguseni)
                }
            }

            // ────── PORUKE ──────
            errorMessage?.let { Poruka(it, Icons.Default.Error, FishBoje.Greska) }
            successMessage?.let { Poruka(it, Icons.Default.CheckCircle, FishBoje.Akcenat) }

            Spacer(Modifier.height(20.dp))

            // ────── ČUVANJE PROMENA ──────
            Button(
                onClick = {
                    if (username.isBlank()) {
                        errorMessage = "Korisničko ime ne može biti prazno!"
                        return@Button
                    }

                    isSaving = true
                    errorMessage = null
                    successMessage = null

                    scope.launch {
                        try {
                            currentUser?.let { user ->
                                var newImageUrl = profileImageUrl

                                if (newImageBitmap != null) {
                                    val ref = storage.reference.child("profilne_slike/${user.uid}.jpg")
                                    val baos = ByteArrayOutputStream()
                                    newImageBitmap!!.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                                    ref.putBytes(baos.toByteArray()).await()

                                    delay(2000)
                                    val resizedRef = storage.reference.child("profilne_slike/${user.uid}_200x200.jpg")
                                    newImageUrl = resizedRef.downloadUrl.await().toString()

                                    // Ažuriramo Firebase Auth profil sliku (sprečava pregaženje Google linkom)
                                    val profileUpdates = UserProfileChangeRequest.Builder()
                                        .setPhotoUri(Uri.parse(newImageUrl))
                                        .build()
                                    user.updateProfile(profileUpdates).await()

                                    // Brišemo staru sliku iz Coil keša da nova odmah bude vidljiva
                                    coil.Coil.imageLoader(context).memoryCache?.clear()
                                    coil.Coil.imageLoader(context).diskCache?.clear()
                                }

                                val updateMap = mutableMapOf<String, Any>(
                                    "userName" to username,
                                    "slikaURL" to newImageUrl
                                )
                                firestore.collection("Korisnici").document(user.uid).update(updateMap).await()

                                profileImageUrl = newImageUrl

                                mapViewModel.ucitajPodatkeKorisnika(forceReload = true)
                                mapViewModel.azurirajProfilnuSliku("$newImageUrl?t=${System.currentTimeMillis()}")

                                successMessage = "Profil je uspešno ažuriran!"
                                isSaving = false
                            }
                        } catch (e: Exception) {
                            errorMessage = "Greška pri čuvanju: ${e.message}"
                            isSaving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FishBoje.Akcenat,
                    contentColor = FishBoje.Pozadina,
                    disabledContainerColor = FishBoje.Krug,
                    disabledContentColor = FishBoje.TekstPriguseni
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = FishBoje.Pozadina, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Čuvanje...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sačuvaj promene", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ────── ODJAVA ──────
            OutlinedButton(
                onClick = {
                    mapViewModel.logout()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, FishBoje.Greska.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = FishBoje.Greska.copy(alpha = 0.08f),
                    contentColor = FishBoje.Greska
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Odjava", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(POLITIKA_PRIVATNOSTI_URL))
                    )
                }) {
                    Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = FishBoje.TekstSekundarni, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Politika privatnosti", color = FishBoje.TekstSekundarni)
                }
                TextButton(onClick = { potvrdaBrisanjaNaloga = true }, enabled = !brisanjeNaloga) {
                    Icon(Icons.Default.PersonRemove, contentDescription = null, tint = FishBoje.Greska, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Obriši nalog", color = FishBoje.Greska)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ────── BRISANJE NALOGA ──────
    if (potvrdaBrisanjaNaloga || brisanjeNaloga) {
        AlertDialog(
            onDismissRequest = { if (!brisanjeNaloga) potvrdaBrisanjaNaloga = false },
            containerColor = FishBoje.Kartica,
            titleContentColor = FishBoje.TekstPrimarni,
            textContentColor = FishBoje.TekstSekundarni,
            icon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = FishBoje.Greska) },
            title = { Text("Obriši nalog?", fontWeight = FontWeight.Bold) },
            text = {
                if (brisanjeNaloga) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = FishBoje.Greska, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Brisanje u toku...")
                    }
                } else {
                    Text(
                        "Trajno se brišu tvoj profil, svi ulovi sa slikama, komentari, poruke i poeni. " +
                            "Ovo ne može da se poništi."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        brisanjeNaloga = true
                        com.google.firebase.functions.FirebaseFunctions.getInstance()
                            .getHttpsCallable("obrisiNalog")
                            .call()
                            .addOnSuccessListener {
                                brisanjeNaloga = false
                                potvrdaBrisanjaNaloga = false
                                FishPoruka.prikazi("Nalog je obrisan", dugo = true)
                                mapViewModel.logout()
                                onLogout()
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("ProfileScreen", "Brisanje naloga nije uspelo", e)
                                brisanjeNaloga = false
                                potvrdaBrisanjaNaloga = false
                                errorMessage = "Brisanje naloga nije uspelo. Proveri internet i pokušaj ponovo."
                            }
                    },
                    enabled = !brisanjeNaloga,
                    colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Greska, contentColor = Color.White)
                ) { Text("Obriši trajno", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { potvrdaBrisanjaNaloga = false }, enabled = !brisanjeNaloga) {
                    Text("Otkaži", color = FishBoje.TekstSekundarni)
                }
            }
        )
    }

    // ────── DIJALOG ZA PROMENU / POSTAVLJANJE LOZINKE ──────
    if (showPasswordDialog) {
        ChangePasswordDialog(
            hasPassword = hasPassword,
            onDismiss = { showPasswordDialog = false },
            onSuccess = {
                showPasswordDialog = false
                hasPassword = true // Korisnik sada ima pridruženu lozinku
                successMessage = "Lozinka je uspešno podešena!"
            },
            onError = { porukaOdGreske -> errorMessage = porukaOdGreske }
        )
    }
}

@Composable
private fun Sekcija(naslov: String, sadrzaj: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(20.dp))
    NaslovSekcije(naslov, Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FishBoje.Kartica, RoundedCornerShape(20.dp))
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = sadrzaj
    )
}

@Composable
private fun IkonaUKrugu(ikona: ImageVector) {
    Box(
        modifier = Modifier.size(36.dp).background(FishBoje.Krug, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(ikona, contentDescription = null, tint = FishBoje.Akcenat, modifier = Modifier.size(18.dp))
    }
}

// Pastelna pločica statistike (isti stil kao parametri u vremenskoj prognozi)
@Composable
private fun PlocicaStatistike(
    ikona: ImageVector,
    vrednost: String,
    oznaka: String,
    boje: Pair<Color, Color>,
    modifier: Modifier,
    onClick: (() -> Unit)? = null
) {
    val (pozadina, bojaIkone) = boje
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(pozadina)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(bojaIkone.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(ikona, contentDescription = null, tint = bojaIkone, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(vrednost, color = FishBoje.TekstNaPlocici, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(oznaka, color = FishBoje.PrigusenNaPlocici, fontSize = 11.sp)
    }
}

@Composable
private fun Poruka(tekst: String, ikona: ImageVector, boja: Color) {
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(boja.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .border(1.dp, boja.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(ikona, contentDescription = null, tint = boja, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(tekst, color = boja, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ────── DIJALOG ZA PROMENU/POSTAVLJANJE LOZINKE (PODRŽAVA GOOGLE NALOGE) ──────
@Composable
fun ChangePasswordDialog(
    hasPassword: Boolean,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FishBoje.Kartica, RoundedCornerShape(24.dp))
                .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(24.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IkonaUKrugu(Icons.Default.Lock)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (hasPassword) "Promeni lozinku" else "Postavi lozinku",
                    color = FishBoje.TekstPrimarni,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Google nalog bez lozinke nema "trenutnu lozinku"
            if (hasPassword) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        localError = null
                    },
                    label = { Text("Trenutna lozinka") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    colors = fishPoljeBoje(),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            OutlinedTextField(
                value = newPassword,
                onValueChange = {
                    newPassword = it
                    localError = null
                },
                label = { Text(if (hasPassword) "Nova lozinka" else "Unesi lozinku") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = fishPoljeBoje(),
                shape = RoundedCornerShape(14.dp)
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    localError = null
                },
                label = { Text("Potvrdi lozinku") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = fishPoljeBoje(),
                shape = RoundedCornerShape(14.dp)
            )

            localError?.let { Text(it, color = FishBoje.Greska, fontSize = 13.sp) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss, enabled = !isLoading) {
                    Text("Otkaži", color = FishBoje.TekstSekundarni)
                }

                Button(
                    onClick = {
                        when {
                            hasPassword && currentPassword.isBlank() -> localError = "Unesite trenutnu lozinku"
                            newPassword.isBlank() -> localError = "Unesite novu lozinku"
                            newPassword.length < 6 -> localError = "Lozinka mora biti najmanje 6 karaktera"
                            newPassword != confirmPassword -> localError = "Lozinke se ne poklapaju"
                            else -> {
                                isLoading = true
                                scope.launch {
                                    try {
                                        val user = auth.currentUser ?: throw Exception("Korisnik nije pronađen")
                                        val email = user.email ?: throw Exception("Email nije pronađen")

                                        if (hasPassword) {
                                            // Standardna izmena lozinke
                                            val credential = EmailAuthProvider.getCredential(email, currentPassword)
                                            user.reauthenticate(credential).await()
                                            user.updatePassword(newPassword).await()
                                        } else {
                                            // Google nalog: pravimo lozinku i vezujemo je za nalog
                                            val credential = EmailAuthProvider.getCredential(email, newPassword)
                                            user.linkWithCredential(credential).await()
                                        }

                                        onSuccess()
                                    } catch (e: Exception) {
                                        localError = when {
                                            e.message?.contains("password is invalid", ignoreCase = true) == true -> "Netačna trenutna lozinka"
                                            else -> "Greška: ${e.message}"
                                        }
                                        onError(localError ?: "Nepoznata greška")
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = FishBoje.Pozadina)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = FishBoje.Pozadina, strokeWidth = 2.dp)
                    } else {
                        Text(if (hasPassword) "Promeni" else "Postavi", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
