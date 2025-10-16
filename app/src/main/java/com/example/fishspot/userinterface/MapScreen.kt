package com.example.fishspot.userinterface

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fishspot.viewmodel.MapViewModel
import com.example.fishspot.viewmodel.SpotsViewModel
import com.example.fishspot.viewmodel.WatersViewModel
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel(),
    spotsViewModel: SpotsViewModel = viewModel(),
    watersViewModel: WatersViewModel = viewModel(),
    focusOnUserCatch: String?,
    onLogout: () -> Unit,
    onNavigateToAddSpot: (Double, Double) -> Unit,
    onNavigateToList: () -> Unit,
    onNavigateToRanking: () -> Unit,
    onFocusDone: () -> Unit
) {
    val context = LocalContext.current
    val userLocation by mapViewModel.userLocation.collectAsState()
    val spots by spotsViewModel.filteredSpots.collectAsState()
    val currentFilterState by spotsViewModel.filterState.collectAsState()
    val waters by watersViewModel.filteredWaters.collectAsState()

    var isAddMode by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> if (isGranted) { mapViewModel.startLocationUpdates(context) } }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(43.3209, 21.8958), 12f)
    }

    LaunchedEffect(userLocation) {
        userLocation?.let {
            spotsViewModel.updateUserLocation(it)
            watersViewModel.updateUserLocation(it)
            if (!isAddMode) {
                val latLng = LatLng(it.latitude, it.longitude)
                cameraPositionState.animate( com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 15f), 1000)
            }
        }
    }

    LaunchedEffect(focusOnUserCatch) {
        if (focusOnUserCatch != null) {
            val spotToFocus = spots.find { it.authorId == focusOnUserCatch }

            spotToFocus?.let { spot ->
                val spotLocation = LatLng(spot.lat, spot.lng)
                cameraPositionState.animate(
                    update = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(spotLocation, 15f),
                    durationMs = 1500
                )
            }
            onFocusDone()
        }
    }

    if (showFilterDialog) {
        FilterDialog(
            currentFilterState = currentFilterState,
            onDismiss = { showFilterDialog = false },
            onApplyFilters = { autor, vrstaRibe, ocena, startDate, endDate, searchText, radius ->
                spotsViewModel.applyFilters(autor, vrstaRibe, ocena, startDate, endDate, searchText, radius)
                watersViewModel.applyFilters(searchText, radius)
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(myLocationButtonEnabled = !isAddMode)
        ) {
            if (!isAddMode) {
                userLocation?.let { location ->
                    val userPosition = LatLng(location.latitude, location.longitude)
                    Marker(
                        state = rememberMarkerState(position = userPosition),
                        title = "Moja lokacija"
                    )
                }
                for (spot in spots) {
                    val spotPosition = LatLng(spot.lat, spot.lng)
                    Marker(
                        state = rememberMarkerState(key = spot.id, position = spotPosition),
                        title = spot.naziv,
                        snippet = "Riba: ${spot.vrstaRibe}, Ocena: ${spot.ocena}",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                }
                for (water in waters) {
                    val waterPosition = LatLng(water.lat, water.lng)
                    Marker(
                        state = rememberMarkerState(key = water.id, position = waterPosition),
                        title = water.naziv,
                        snippet = "Tip: ${water.tip}",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = { showFilterDialog = true }) {
                Icon(imageVector = Icons.Default.FilterList, contentDescription = "Filter")
            }
            IconButton(onClick = onNavigateToList) {
                Icon(imageVector = Icons.Default.List, contentDescription = "Lista ulova")
            }
            IconButton(onClick = onNavigateToRanking) {
                Icon(imageVector = Icons.Default.Leaderboard, contentDescription = "Rang lista")
            }
            IconButton(onClick = { mapViewModel.logout(); onLogout() }) {
                Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Odjavi se")
            }
        }

        if (isAddMode) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Kursor za lokaciju",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = { isAddMode = false }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Otkaži")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Otkaži")
                }
                Button(onClick = {
                    val selectedLat = cameraPositionState.position.target.latitude
                    val selectedLng = cameraPositionState.position.target.longitude
                    onNavigateToAddSpot(selectedLat, selectedLng)
                    isAddMode = false
                }) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Potvrdi")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Potvrdi lokaciju")
                }
            }
        } else {
            FloatingActionButton(
                onClick = { isAddMode = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Dodaj mesto")
            }
        }
    }
}