package com.example.fishspot.userinterface

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.fishspot.viewmodel.MapViewModel
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // ✅ NOVO: Držimo podatak da li korisnik ima postavljenu lozinku (Google podrška)
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

                // ✅ NOVO: Proveravamo provajdere da saznamo da li je registrovan preko šifre ili samo preko Google-a
                val providers = user.providerData.map { it.providerId }
                hasPassword = providers.contains("password")

                // Uvek učitavamo direktno iz Firestore-a (source = SERVER) da izbegnemo stare keširane podatke
                val doc = firestore.collection("Korisnici").document(user.uid)
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .await()
                username = doc.getString("userName") ?: ""
                profileImageUrl = doc.getString("slikaURL") ?: ""

                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Greška pri učitavanju profila: ${e.message}"
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moj profil", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Nazad")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ────── PROFILNA SLIKA ──────
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (newImageBitmap != null) {
                        // Lokalna bitmapa ostaje vidljiva i nema praznog/belog prostora dok se radi upload!
                        Image(
                            bitmap = newImageBitmap!!.asImageBitmap(),
                            contentDescription = "Nova profilna slika",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (profileImageUrl.isNotBlank()) {
                        key(profileImageUrl) {
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
                    } else {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "Nema slike",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(90.dp)
                        )
                    }
                }

                Text(
                    "Tapni na sliku da je promeniš",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ────── KORISNIČKO IME ──────
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    label = { Text("Korisničko ime") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ────── EMAIL (SAMO ČITANJE) ──────
                OutlinedTextField(
                    value = email,
                    onValueChange = {},
                    label = { Text("E-mail") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    enabled = false,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ────── ✅ NOVO: PREKIDAČ (SWITCH) ZA OBAVEŠTENJA O VODAMA ──────
                val vodeNotificationsEnabled by mapViewModel.vodeNotificationsEnabled.collectAsState()

                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Obaveštenja o vodama", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text("Primi notifikaciju kada si blizu reke ili jezera", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                        Switch(
                            checked = vodeNotificationsEnabled,
                            onCheckedChange = { upaljeno -> mapViewModel.setVodeNotificationsEnabled(context, upaljeno) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ────── PROMENA / POSTAVLJANJE LOZINKE ──────
                OutlinedButton(
                    onClick = { showPasswordDialog = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    // ✅ Dinamički tekst u zavisnosti od toga da li ima lozinku
                    Text(if (hasPassword) "Promeni lozinku" else "Postavi lozinku", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ────── DUGME ZA ČUVANJE PROMENA ──────
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
                                    // newImageBitmap ostaje vidljiv — ne resetujemo ga

                                    mapViewModel.ucitajPodatkeKorisnika(forceReload = true)
                                    mapViewModel.azurirajProfilnuSliku("$newImageUrl?t=${System.currentTimeMillis()}")

                                    successMessage = "Profil je uspešno ažuriran! ✓"
                                    isSaving = false
                                }
                            } catch (e: Exception) {
                                errorMessage = "Greška pri čuvanju: ${e.message}"
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Čuvanje...")
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sačuvaj promene", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ────── DUGME ZA ODJAVU ──────
                Button(
                    onClick = {
                        mapViewModel.logout()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Odjava", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ────── PORUKE O GREŠCI ──────
                errorMessage?.let { greska ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFB3261E), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(greska, color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // ────── PORUKE O USPEHU ──────
                successMessage?.let { uspeh ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(uspeh, color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ────── POZIV DIJALOGA ZA PROMENU / POSTAVLJANJE LOZINKE ──────
    if (showPasswordDialog) {
        ChangePasswordDialog(
            hasPassword = hasPassword,
            onDismiss = { showPasswordDialog = false },
            onSuccess = {
                showPasswordDialog = false
                hasPassword = true // Korisnik sada uspešno ima pridruženu šifru
                successMessage = "Lozinka je uspešno podešena! ✓"
            },
            onError = { porukaOdGreske ->
                errorMessage = porukaOdGreske
            }
        )
    }
}

// ────── DIJALOG ZA PROMENU/POSTAVLJANJE LOZINKE (PODRŽAVA GOOGLE LOGOVANJE) ──────
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
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = if (hasPassword) "Promeni lozinku" else "Postavi lozinku",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // ✅ Ako je korisnik sa Google-a i nema lozinku, polje za Trenutnu Lozinku se sakriva!
                if (hasPassword) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = {
                            currentPassword = it
                            localError = null
                        },
                        label = { Text("Trenutna lozinka") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
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
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        localError = null
                    },
                    label = { Text(if (hasPassword) "Nova lozinka" else "Unesi lozinku") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localError = null
                    },
                    label = { Text("Potvrdi lozinku") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp)
                )

                localError?.let {
                    Text(
                        it,
                        color = Color(0xFFB3261E),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss, enabled = !isLoading) {
                        Text("Otkaži")
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
                                                // Standardni proces izmene šifre
                                                val credential = EmailAuthProvider.getCredential(email, currentPassword)
                                                user.reauthenticate(credential).await()
                                                user.updatePassword(newPassword).await()
                                            } else {
                                                // ✅ REŠENJE ZA GOOGLE: Pošto nema lozinku, kreiramo novu akreditaciju i vezujemo (linkujemo) je za nalog
                                                val credential = EmailAuthProvider.getCredential(email, newPassword)
                                                user.linkWithCredential(credential).await()
                                            }

                                            onSuccess()
                                        } catch (e: Exception) {
                                            localError = when {
                                                e.message?.contains("password is invalid", ignoreCase = true) == true -> "Netačna trenutna lozinka"
                                                e.message?.contains("The password is invalid") == true -> "Netačna trenutna lozinka"
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
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (hasPassword) "Promeni" else "Postavi")
                        }
                    }
                }
            }
        }
    }
}