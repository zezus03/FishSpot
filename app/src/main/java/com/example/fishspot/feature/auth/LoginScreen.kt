package com.example.fishspot.feature.auth
import com.example.fishspot.core.theme.FishPoruka

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishspot.R

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    authViewModel: AuthViewModel, // Dodat ViewModel za Google Auth
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ─── GOOGLE SIGN IN PODEŠAVANJE ZA JETPACK COMPOSE ───
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            // Direktno prosleđuješ string iz google-services.json fajla
            .requestIdToken("154890695958-dacld07urlmn7rcsgn3eloopsokk3tpl.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    authViewModel.loginSaGoogleom(
                        idToken = idToken,
                        onSuccess = {
                            FishPoruka.prikazi("Uspešna prijava!")
                            onLoginSuccess() // ili onRegistrationSuccess() u RegisterScreen-u
                        },
                        onFailure = { e ->
                            Log.e("GoogleAuth", "Firebase prijava neuspešna", e)
                            FishPoruka.prikazi("Prijava nije uspela. Proveri internet i pokušaj ponovo.", dugo = true)
                        }
                    )
                }
            } catch (e: ApiException) {
                Log.e("GoogleAuth", "Google prijava neuspešna. Kod: ${e.statusCode}", e)
                // SADA ĆEŠ VIDETI TAČAN KOD GREŠKE (Ako piše 10, fali ti SHA-1 ili je loš Client ID)
                FishPoruka.prikazi("Prijava preko Google naloga nije uspela. Pokušaj ponovo.", dugo = true)
            }
        } else {
            FishPoruka.prikazi("Prozor za prijavu zatvoren.")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Crna pozadina, kao na logou
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black)
        )

        // 3. GLAVNI SADRŽAJ
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_fishspot),
                contentDescription = "FishSpot",
                modifier = Modifier.width(280.dp)
            )
            Text(
                text = "Tvoja digitalna čuvarka",
                fontSize = 16.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Kartica sa poljima
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // Email Polje
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email adresa") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email ikonica") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedLabelColor = Color.Black.copy(alpha = 0.7f),
                            unfocusedLabelColor = Color.Black.copy(alpha = 0.7f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                            focusedLeadingIconColor = Color.Black.copy(alpha = 0.6f),
                            unfocusedLeadingIconColor = Color.Black.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Šifra Polje
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Šifra") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Šifra ikonica") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedLabelColor = Color.Black.copy(alpha = 0.7f),
                            unfocusedLabelColor = Color.Black.copy(alpha = 0.7f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                            focusedLeadingIconColor = Color.Black.copy(alpha = 0.6f),
                            unfocusedLeadingIconColor = Color.Black.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Klasično dugme za prijavu
                    Button(
                        onClick = {
                            scope.launch {
                                val result = authRepository.prijaviKorisnika(email, password)
                                if (result.isSuccess) {
                                    onLoginSuccess()
                                } else {
                                    val errorMessage = result.exceptionOrNull()?.message ?: "Došlo je do nepoznate greške."
                                    FishPoruka.prikazi("Greška: $errorMessage", dugo = true)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Prijavi se", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Linija razgraničenja "ili"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.3f))
                        Text(
                            text = "ili",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.3f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ─── NOVO: GOOGLE DUGME ───
                    OutlinedButton(
                        onClick = {
                            googleSignInClient.signOut().addOnCompleteListener {
                                val signInIntent = googleSignInClient.signInIntent
                                googleLauncher.launch(signInIntent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // OVDE JE SADA UBAČEN LOGO GOOGLE-A
                            Image(
                                painter = painterResource(id = R.drawable.ic_google),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "Nastavi putem Google-a",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onNavigateToRegister) {
                Text(
                    text = "Nemaš nalog? Registruj se ovde",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}