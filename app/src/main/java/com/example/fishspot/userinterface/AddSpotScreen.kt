
package com.example.fishspot.userinterface

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fishspot.viewmodel.SpotsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSpotScreen(
    spotsViewModel: SpotsViewModel = viewModel(),
    latitude: Double,
    longitude: Double,
    onSpotAdded: () -> Unit
) {
    val context = LocalContext.current

    var naziv by remember { mutableStateOf("") }
    var vrstaRibe by remember { mutableStateOf("") }
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

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> slikaUri = uri }
    )

    val trenutnaLokacija = remember(latitude, longitude) {
        android.location.Location("").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Dodaj novi ulov", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = naziv, onValueChange = { naziv = it }, label = { Text("Naziv mesta") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = vrstaRibe, onValueChange = { vrstaRibe = it }, label = { Text("Vrsta ribe") }, modifier = Modifier.fillMaxWidth())

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = duzinaRibe,
                onValueChange = { duzinaRibe = it },
                label = { Text("Dužina (cm)") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = tezinaRibe,
                onValueChange = { tezinaRibe = it },
                label = { Text("Težina (kg)") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        ExposedDropdownMenuBox(
            expanded = mamacExpanded,
            onExpandedChange = { mamacExpanded = !mamacExpanded },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            OutlinedTextField(
                value = odabranMamac,
                onValueChange = {},
                readOnly = true,
                label = { Text("Mamac") },
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


        ExposedDropdownMenuBox(
            expanded = tehnikaExpanded,
            onExpandedChange = { tehnikaExpanded = !tehnikaExpanded },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            OutlinedTextField(
                value = odabranaTehnika,
                onValueChange = {},
                readOnly = true,
                label = { Text("Tehnika") },
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

        OutlinedTextField(value = opis, onValueChange = { opis = it }, label = { Text("Opis") }, modifier = Modifier.fillMaxWidth())

        Text("Ocena mesta: ${ocena.toInt()}", modifier = Modifier.padding(top = 16.dp))
        Slider(value = ocena, onValueChange = { ocena = it }, valueRange = 1f..5f, steps = 3)

        Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.padding(top = 16.dp)) {
            Text(if (slikaUri == null) "Izaberi sliku" else "Promeni sliku")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isSaving = true
                spotsViewModel.addSpot(
                    naziv = naziv, vrstaRibe = vrstaRibe, ocena = ocena, opis = opis, slikaUri = slikaUri,
                    location = trenutnaLokacija,
                    duzinaRibe = duzinaRibe.toDoubleOrNull() ?: 0.0,
                    tezinaRibe = tezinaRibe.toDoubleOrNull() ?: 0.0,
                    mamac = odabranMamac,
                    tehnika = odabranaTehnika
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
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Sačuvaj ulov")
            }
        }
    }
}