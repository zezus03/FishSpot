// Fajl: userinterface/SpotsListScreen.kt

package com.example.fishspot.userinterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.fishspot.data.FishSpot
import com.example.fishspot.viewmodel.SpotsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotsListScreen(
    spotsViewModel: SpotsViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val spots by spotsViewModel.filteredSpots.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lista Ulova") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Nazad")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (spots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Nema ulova koji odgovaraju filterima.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(spots) { spot ->
                    SpotListItem(spot = spot)
                }
            }
        }
    }
}

@Composable
fun SpotListItem(spot: FishSpot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {

        Column {

            if (spot.slikaUrl != null && spot.slikaUrl.isNotEmpty()) {
                AsyncImage(
                    model = spot.slikaUrl,
                    contentDescription = "Slika ulova: ${spot.vrstaRibe}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop
                )
            }


            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = spot.vrstaRibe, style = MaterialTheme.typography.titleLarge)
                    if (spot.tezinaRibe > 0) {
                        Text(
                            text = "${spot.tezinaRibe} kg",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Mesto: ${spot.naziv}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Ulovio: ${spot.authorUsername}", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Tehnika: ${spot.tehnika} | Mamac: ${spot.mamac}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}