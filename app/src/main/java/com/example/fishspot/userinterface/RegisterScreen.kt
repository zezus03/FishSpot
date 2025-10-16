
package com.example.fishspot.userinterface

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.fishspot.data.AuthRepository
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@Composable
fun RegisterScreen(
    authRepository: AuthRepository,
    onRegistrationSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var slikaBytes by remember { mutableStateOf<ByteArray?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            slikaBytes = inputStream?.readBytes()
            inputStream?.close()
            slikaBytes?.let { bytes ->
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        bmp?.let {
            bitmap = it
            val stream = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            slikaBytes = stream.toByteArray()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Profilna slika",
                modifier = Modifier
                    .size(100.dp)
                    .padding(bottom = 16.dp)
            )
        }

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Korisničko ime") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Ime i prezime") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Telefon") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Šifra") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = { galleryLauncher.launch("image/*") }) {
                Text("Izaberi sliku")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { cameraLauncher.launch() }) {
                Text("Slikaj")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            scope.launch {
                val result = authRepository.registrujKorisnika(
                    email, password, username, fullName, phone, slikaBytes
                )
                if (result.isSuccess) {
                    onRegistrationSuccess()
                } else {
                    val errorMessage = result.exceptionOrNull()?.message ?: "Došlo je do nepoznate greške."
                    Toast.makeText(context, "Greška: $errorMessage", Toast.LENGTH_LONG).show()
                }
            }
        }) {
            Text("Registruj se")
        }
    }
}