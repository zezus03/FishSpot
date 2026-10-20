package com.example.fishspot.feature.map

import com.example.fishspot.feature.weather.WeatherNetwork
import com.example.fishspot.feature.weather.WeatherScreen

import android.Manifest
import java.util.Calendar
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import com.example.fishspot.R
import com.example.fishspot.feature.spots.Comment
import com.example.fishspot.feature.spots.FishSpot
import com.example.fishspot.feature.chat.ActiveUsersViewModel

import com.example.fishspot.feature.spots.SpotsViewModel
import com.example.fishspot.feature.spots.UlovDetalji
import com.example.fishspot.core.theme.FishBoje
import com.google.android.gms.maps.model.MapStyleOptions
import com.example.fishspot.feature.weather.VodostajiScreen
import com.example.fishspot.feature.weather.WaterLevelViewModel
import com.example.fishspot.feature.chat.ActiveUser
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.fishspot.BuildConfig
import androidx.compose.ui.window.DialogProperties
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel(),
    spotsViewModel: SpotsViewModel = viewModel(),
    waterLevelViewModel: WaterLevelViewModel = viewModel(),
    focusOnUserCatch: String?,
    focusOnSpotId: String?,
    onLogout: () -> Unit,
    onNavigateToAddSpot: (Double, Double) -> Unit,
    onNavigateToList: () -> Unit,
    onNavigateToRanking: () -> Unit,
    onFocusDone: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToBotChat: () -> Unit,
    activeUsersViewModel: ActiveUsersViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    val userLocation by mapViewModel.userLocation.collectAsState()
    val userProfileImage by mapViewModel.userProfileImage.collectAsState() // ✅ NOVO
    val spots by spotsViewModel.filteredSpots.collectAsState()
    val currentFilterState by spotsViewModel.filterState.collectAsState()
    val comments by spotsViewModel.comments.collectAsState()
    val activeUsers by activeUsersViewModel.activeUsers.collectAsState()

    var isAddMode by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val scope = rememberCoroutineScope()
    var selectedSpotForComments by remember { mutableStateOf<FishSpot?>(null) }

    var isWeatherMode by remember { mutableStateOf(false) }

    var fetchedWeather by remember { mutableStateOf<ForecastDisplayData?>(null) }
    var isFetchingWeather by remember { mutableStateOf(false) }
    val mojApiKey = BuildConfig.OPENWEATHER_API_KEY
    var showWaterLevelDialog by remember { mutableStateOf(false) }

    var isSatelliteView by remember { mutableStateOf(false) }

    // Tamni stil u bojama aplikacije (res/raw/mapa_tamna.json), sa istaknutom vodom;
    // stil važi samo za NORMAL tip mape, satelitski prikaz ostaje kakav jeste
    val tamniStil = remember { MapStyleOptions.loadRawResourceStyle(context, R.raw.mapa_tamna) }
    val mapProperties = MapProperties(
        mapType = if (isSatelliteView) MapType.HYBRID else MapType.NORMAL,
        mapStyleOptions = if (isSatelliteView) null else tamniStil
    )
    val activeBubbles = remember { mutableStateListOf<ActiveUser>() }
    val bubbleOffsets = remember { mutableStateMapOf<String, IntOffset>() }
    var openedChatUserId by remember { mutableStateOf<String?>(null) }
    var lastMessageCounts by remember { mutableStateOf(mapOf<String, Int>()) }
    val screenHeight = LocalContext.current.resources.displayMetrics.heightPixels

    var hasCenteredOnUser by rememberSaveable { mutableStateOf(false) }
    val activity = context as? android.app.Activity
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(43.3209, 21.8958), 12f)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) {
                activeUsersViewModel.removeMyLocation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activeUsersViewModel.removeMyLocation()
        }
    }

    DisposableEffect(context) {
        val listener = androidx.core.util.Consumer<android.content.Intent> { noviIntent ->
            val lat = noviIntent.getDoubleExtra("ZUMIRAJ_LAT", 0.0)
            val lng = noviIntent.getDoubleExtra("ZUMIRAJ_LNG", 0.0)

            if (lat != 0.0 && lng != 0.0) {
                scope.launch {
                    val loc = LatLng(lat, lng)
                    cameraPositionState.animate(
                        update = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(loc, 15f),
                        durationMs = 2500
                    )
                }
                noviIntent.removeExtra("ZUMIRAJ_LAT")
                noviIntent.removeExtra("ZUMIRAJ_LNG")
            }
        }
        activity?.intent?.let { listener.accept(it) }
        val componentActivity = activity as? androidx.activity.ComponentActivity
        componentActivity?.addOnNewIntentListener(listener)
        onDispose {
            componentActivity?.removeOnNewIntentListener(listener)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            mapViewModel.startLocationUpdates(context)
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
        spotsViewModel.sacuvajFcmToken()
    }

    LaunchedEffect(userLocation) {
        userLocation?.let {
            spotsViewModel.updateUserLocation(it)
            activeUsersViewModel.updateMyLocationIfSharing(it.latitude, it.longitude)

            if (!isAddMode && !hasCenteredOnUser && focusOnSpotId == null && focusOnUserCatch == null) {
                val latLng = LatLng(it.latitude, it.longitude)
                cameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 15f), 1000)
                hasCenteredOnUser = true
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

    LaunchedEffect(focusOnSpotId) {
        if (focusOnSpotId != null) {
            val spotToFocus = spots.find { it.id == focusOnSpotId }
            spotToFocus?.let { spot ->
                val spotLocation = LatLng(spot.lat, spot.lng)
                cameraPositionState.animate(
                    update = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(spotLocation, 16f),
                    durationMs = 1500
                )
            }
            onFocusDone()
        }
    }

    if (showFilterDialog && openedChatUserId == null) {
        FilterDialog(
            currentFilterState = currentFilterState,
            onDismiss = { showFilterDialog = false },
            onApplyFilters = { autor, vrstaRibe, ocena, startDate, endDate, searchText, radius ->
                spotsViewModel.applyFilters(autor, vrstaRibe, ocena, startDate, endDate, searchText, radius)
            }
        )
    }

    LaunchedEffect(spots) {
        delay(50)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            // Bez Google tastera za zum (beli, virio je iza dugmeta "+"); zumira se prstima
            uiSettings = MapUiSettings(myLocationButtonEnabled = !isAddMode, zoomControlsEnabled = false)
        ) {
            // Pinovi u stilu aplikacije (pravimo ih tek ovde, kada je mapa inicijalizovana)
            val pinUlova = remember { vektorUPin(context, R.drawable.ic_pin_ulov) }
            val pinKorisnika = remember { vektorUPin(context, R.drawable.ic_pin_korisnik) }

            if (!isAddMode && !isWeatherMode) {
                userLocation?.let { location ->
                    val userPosition = LatLng(location.latitude, location.longitude)

                    // ✅ NOVO: Prikaži sliku korisnika na pin-u
                    var markerIcon by remember(userProfileImage) { mutableStateOf<BitmapDescriptor?>(null) }

                    LaunchedEffect(userProfileImage) {
                        if (!userProfileImage.isNullOrBlank()) {
                            try {
                                val request = coil.request.ImageRequest.Builder(context)
                                    .data(userProfileImage)
                                    .transformations(coil.transform.CircleCropTransformation())
                                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED)   // ← DODAJ
                                    .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                                    .build()

                                val result = context.imageLoader.execute(request)
                                if (result is coil.request.SuccessResult) {
                                    val bitmap = result.drawable.toBitmap(100, 100)
                                    markerIcon = BitmapDescriptorFactory.fromBitmap(bitmap)
                                } else {
                                    markerIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                                }
                            } catch (e: Exception) {
                                markerIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                            }
                        } else {
                            markerIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                        }
                    }

                    Marker(
                        state = rememberMarkerState(position = userPosition),
                        title = "Moja lokacija",
                        icon = markerIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                }

                for (spot in spots) {
                    val spotPosition = LatLng(spot.lat, spot.lng)
                    key(spot.id) {
                        Marker(
                            state = rememberMarkerState(key = spot.id, position = spotPosition),
                            title = spot.naziv,
                            snippet = "Riba: ${spot.vrstaRibe}, Klikni za komentar",
                            icon = pinUlova,
                            onClick = {
                                selectedSpotForComments = spot
                                spotsViewModel.loadComments(spot.id)
                                spotsViewModel.ucitajDetaljeUlova(spot)
                                scope.launch { sheetState.show() }
                                true
                            }
                        )
                    }
                }

                for (activeUser in activeUsers) {
                    val position = LatLng(activeUser.lat, activeUser.lng)
                    val actContext = LocalContext.current
                    var markerIcon by remember(activeUser.slikaUrl) { mutableStateOf<BitmapDescriptor?>(null) }

                    LaunchedEffect(activeUser.slikaUrl) {
                        if (activeUser.slikaUrl.isNotBlank()) {
                            try {
                                val request = coil.request.ImageRequest.Builder(actContext)
                                    .data(activeUser.slikaUrl)
                                    .transformations(coil.transform.CircleCropTransformation())
                                    .build()

                                val result = actContext.imageLoader.execute(request)
                                if (result is coil.request.SuccessResult) {
                                    val bitmap = result.drawable.toBitmap(100, 100)
                                    markerIcon = BitmapDescriptorFactory.fromBitmap(bitmap)
                                } else {
                                    markerIcon = pinKorisnika
                                }
                            } catch (e: Exception) {
                                markerIcon = pinKorisnika
                            }
                        } else {
                            markerIcon = pinKorisnika
                        }
                    }

                    key(activeUser.userId) {
                        Marker(
                            state = rememberMarkerState(key = activeUser.userId, position = position),
                            title = activeUser.username,
                            snippet = "Klikni za dopisivanje!",
                            icon = markerIcon ?: pinKorisnika,
                            onClick = {
                                if (!activeBubbles.any { it.userId == activeUser.userId }) {
                                    activeBubbles.add(activeUser)
                                    bubbleOffsets[activeUser.userId] = IntOffset(20, 300 + activeBubbles.size * 180)
                                }
                                openedChatUserId = activeUser.userId
                                activeUsersViewModel.loadPrivateMessages(activeUser.userId)
                                true
                            }
                        )
                    }
                }
            }
        }

        if (openedChatUserId == null) {

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tamni krugovi sa zelenim ikonama (paleta FishBoje)
                val iconModifier = Modifier
                    .size(44.dp)
                    .shadow(6.dp, CircleShape)
                    .background(FishBoje.Kartica, CircleShape)
                    .border(1.dp, FishBoje.IvicaKartice, CircleShape)
                    .clip(CircleShape)
                val bojaIkone = FishBoje.Akcenat

                IconButton(
                    onClick = onNavigateToProfile,
                    modifier = iconModifier
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profil",
                        tint = bojaIkone
                    )
                }

                IconButton(
                    onClick = { showWaterLevelDialog = true },
                    modifier = iconModifier
                ) {
                    Icon(
                        imageVector = Icons.Default.Waves,
                        contentDescription = "Vodostaji",
                        tint = bojaIkone
                    )
                }

                IconButton(
                    onClick = {
                        isWeatherMode = true
                        isAddMode = false
                    },
                    modifier = iconModifier
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ikona_vreme),
                        contentDescription = "Vreme",
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { showFilterDialog = true },
                    modifier = iconModifier
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter",
                        tint = bojaIkone
                    )
                }

                IconButton(
                    onClick = onNavigateToList,
                    modifier = iconModifier
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Lista ulova",
                        tint = bojaIkone
                    )
                }

                IconButton(
                    onClick = onNavigateToRanking,
                    modifier = iconModifier
                ) {
                    Icon(
                        imageVector = Icons.Default.Leaderboard,
                        contentDescription = "Rang lista",
                        tint = bojaIkone
                    )
                }

                IconButton(
                    onClick = { mapViewModel.logout(); onLogout() },
                    modifier = iconModifier
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Odjavi se",
                        tint = FishBoje.Greska
                    )
                }

            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                FloatingActionButton(
                    onClick = { isSatelliteView = !isSatelliteView },
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp).border(1.dp, FishBoje.IvicaKartice, CircleShape),
                    containerColor = FishBoje.Kartica,
                    contentColor = FishBoje.Akcenat
                ) {
                    Icon(
                        imageVector = if (isSatelliteView) Icons.Default.Terrain else Icons.Default.Satellite,
                        contentDescription = "Mapa",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        if (showWaterLevelDialog && openedChatUserId == null) {
            Dialog(
                onDismissRequest = {
                    showWaterLevelDialog = false
                    waterLevelViewModel.zatvoriDetalje()
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                VodostajiScreen(
                    viewModel = waterLevelViewModel,
                    onDismiss = {
                        showWaterLevelDialog = false
                        waterLevelViewModel.zatvoriDetalje()
                    }
                )
            }
        }

        if (isAddMode) {
            Icon(
                painter = painterResource(id = R.drawable.ic_pin_ulov),
                contentDescription = "Kursor za lokaciju",
                modifier = Modifier.align(Alignment.Center).size(44.dp, 54.dp).padding(bottom = 26.dp),
                tint = Color.Unspecified
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { isAddMode = false }, colors = dugmeOtkaziBoje(), modifier = Modifier.height(50.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Otkaži")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Otkaži")
                }
                Button(onClick = {
                    val selectedLat = cameraPositionState.position.target.latitude
                    val selectedLng = cameraPositionState.position.target.longitude
                    onNavigateToAddSpot(selectedLat, selectedLng)
                    isAddMode = false
                }, colors = dugmePotvrdiBoje(), modifier = Modifier.height(50.dp)) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Potvrdi")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Potvrdi lokaciju")
                }
            }
        } else if (!isWeatherMode && openedChatUserId == null) {
            // Pakujemo oba dugmeta u kolonu poravnatu u donji desni ugao
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), // Razmak između bota i plusa
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 🤖 NOVO DUGME ZA AI ASISTENTA (Iznad plusa, u istom stilu)
                FloatingActionButton(
                    onClick = onNavigateToBotChat,
                    shape = CircleShape,
                    containerColor = FishBoje.Kartica,
                    contentColor = Color.White,
                    modifier = Modifier.size(56.dp).border(2.dp, FishBoje.Akcenat, CircleShape)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_deda_pera),
                        contentDescription = "Pitaj AI",
                        modifier = Modifier
                            .size(46.dp) // Malo sam povećao sa 26 na 28 jer su kružne slike vizuelno sitnije
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                // ➕ TVOJE POSTOJEĆE DUGME ZA DODAVANJE ULOVA
                FloatingActionButton(
                    onClick = { isAddMode = true },
                    shape = CircleShape,
                    containerColor = FishBoje.Akcenat,
                    contentColor = FishBoje.Pozadina,
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Dodaj mesto",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        if (isWeatherMode) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Izaberi lokaciju za vreme",
                modifier = Modifier.align(Alignment.Center).size(48.dp).padding(bottom = 24.dp),
                tint = Color(0xFF4FC3F7)
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { isWeatherMode = false }, colors = dugmeOtkaziBoje(), modifier = Modifier.height(50.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Otkaži")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Otkaži")
                }

                Button(
                    onClick = {
                        isFetchingWeather = true
                        val selectedLat = cameraPositionState.position.target.latitude
                        val selectedLng = cameraPositionState.position.target.longitude

                        scope.launch {
                            try {
                                // 1. POZIVAMO TRENUTNO VREME (Za trenutni prikaz)
                                val currentResponse = WeatherNetwork.retrofit.getWeather(
                                    lat = selectedLat, lon = selectedLng, apiKey = mojApiKey
                                )
                                Log.d("API_DEBUG", "Trenutno vreme: $currentResponse")
                                // 2. POZIVAMO PROGNOZU ZA 5 DANA
                                val forecastResponse = WeatherNetwork.retrofit.getForecast(
                                    lat = selectedLat, lon = selectedLng, apiKey = mojApiKey
                                )
                                Log.d("API_DEBUG", "Prognoza: $forecastResponse")
                                // Analiza za Indeks Pecanja (trenutni uslovi)
                                val calculatedIndex = calculateFishingIndex(
                                    pressure = currentResponse.main.pressure,
                                    timestamp = currentResponse.dt * 1000L, // Koristimo 'dt' iz API-ja, ne trenutno vreme
                                    temp = currentResponse.main.temp.toInt() // .toInt() rešava Double vs Int grešku
                                )
                                // Grupisanje prognoze po danima (tražimo min/max temp i glavnu ikonu za taj dan)
                                val formatDatum = SimpleDateFormat("dd.MM.", Locale.getDefault())
                                val daysMap = mutableMapOf<String, MutableList<Int>>()
                                val iconMap = mutableMapOf<String, String>()

                                val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                                forecastResponse.list.forEach { item ->
                                    val dateStr = item.dt_txt.substringBefore(" ") // dobijamo npr "2024-05-20"
                                    if (dateStr != todayDateStr) { // Preskačemo današnji dan u prognozi
                                        val temp = item.main.temp.toInt()
                                        if (!daysMap.containsKey(dateStr)) {
                                            daysMap[dateStr] = mutableListOf()
                                            iconMap[dateStr] = item.weather.firstOrNull()?.icon ?: ""
                                        }
                                        daysMap[dateStr]?.add(temp)
                                    }
                                }

                                // Uzimamo samo naredna 3 dana
                                val upcoming = daysMap.entries.take(3).map { (dateStr, temps) ->
                                    val dateParsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                                    val formattedDate = formatDatum.format(dateParsed ?: Date())

                                    // Pronađi prosečnu šansu za kišu (pop) za taj dan iz API-ja
                                    // Uzimamo max vrednost 'pop' iz tog dana kao konzervativnu procenu
                                    val dailyItems = forecastResponse.list.filter { it.dt_txt.startsWith(dateStr) }
                                    val maxRainChance = ((dailyItems.map { it.pop ?: 0.0 }.maxOrNull() ?: 0.0) * 100).toInt()

                                    // Izračunaj fishing index (koristi tvoju postojeću funkciju)
                                    // Ovde možeš dodati logiku: npr. koristi pritisak iz prvog zapisa tog dana
                                    val firstItemPressure = dailyItems.firstOrNull()?.main?.pressure ?: 1013
                                    val fIndex = calculateFishingIndex(
                                        pressure = currentResponse.main.pressure,
                                        timestamp = currentResponse.dt * 1000L, // Koristimo vreme iz API-ja, ne trenutno sistemsko
                                        temp = currentResponse.main.temp.toInt() // Ovde pretvaramo Double u Int
                                    )
                                    DayForecast(
                                        date = formattedDate,
                                        tempMax = (temps.maxOrNull() ?: 0.0).toInt(),
                                        tempMin = (temps.minOrNull() ?: 0.0).toInt(),
                                        icon = iconMap[dateStr] ?: "",
                                        fishingIndex = calculatedIndex, // Koristimo izračunato
                                        rainChance = maxRainChance // Koristimo API podatak
                                    )
                                }
                                var riverTrendText = "Stabilan"

                                // Hidrologija (Zadržavamo tvoj kod)
                                var globalRiverDischarge = "Nema veće reke u krugu od 5km"
                                try {
                                    val floodUrl = "https://flood-api.open-meteo.com/v1/flood?latitude=$selectedLat&longitude=$selectedLng&daily=river_discharge&forecast_days=4"
                                    val floodJson = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        java.net.URL(floodUrl).readText()
                                    }
                                    val jsonObject = org.json.JSONObject(floodJson)
                                    if (jsonObject.has("daily")) {
                                        val daily = jsonObject.getJSONObject("daily")
                                        val dischargeArray = daily.getJSONArray("river_discharge")
                                        if (dischargeArray.length() >= 4 && !dischargeArray.isNull(0)) {
                                            val sutra = dischargeArray.optDouble(1, 0.0)
                                            val danas = dischargeArray.optDouble(0, sutra)
                                            globalRiverDischarge = "Sutra: ${(Math.round(sutra * 10.0) / 10.0)} m³/s"
                                        }
                                    }
                                } catch (e: Exception) { e.printStackTrace() }

                                // Čuvamo podatke za prikaz
                                // Konvertuj Unix timestamp u HH:mm format
                                fun unixtimeToHHmm(unixSec: Long): String {
                                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                    return sdf.format(Date(unixSec * 1000))
                                }

                                fetchedWeather = ForecastDisplayData(
                                    city = currentResponse.name,
                                    currentTemp = currentResponse.main.temp.toInt(),
                                    currentDesc = currentResponse.weather.firstOrNull()?.description ?: "",
                                    currentPressure = currentResponse.main.pressure,
                                    windInfo = "${currentResponse.wind.speed} m/s, ${pretvoriStepenUStraneSveta(currentResponse.wind.deg)}",
                                    windDirection = pretvoriStepenUStraneSveta(currentResponse.wind.deg),
                                    humidity = currentResponse.main.humidity,
                                    visibility = currentResponse.visibility ?: 10000,   // ← NOVO
                                    riverDischarge = globalRiverDischarge,
                                    riverTrend = riverTrendText,
                                    fishingIndex = calculatedIndex,
                                    upcomingDays = upcoming,
                                    sunrise = unixtimeToHHmm(currentResponse.sys.sunrise),  // ← NOVO
                                    sunset  = unixtimeToHHmm(currentResponse.sys.sunset)    // ← NOVO
                                )

                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Greška pri učitavanju prognoze", Toast.LENGTH_SHORT).show()
                            } finally {
                                isFetchingWeather = false
                                isWeatherMode = false
                            }
                        }
                    },
                    enabled = !isFetchingWeather,
                    colors = dugmePotvrdiBoje(),
                    modifier = Modifier.height(50.dp)
                ) {
                    if (isFetchingWeather) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = FishBoje.Pozadina)
                    } else {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Potvrdi")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vidi prognozu")
                    }
                }
            }
        }

        // NOVI DIZAJN DIJALOGA ZA PRIKAZ PROGNOZE
        if (fetchedWeather != null) {
            Dialog(
                onDismissRequest = { fetchedWeather = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                WeatherScreen(
                    weatherData = fetchedWeather!!,
                    onDismiss = { fetchedWeather = null },
                    onAddCatch = { isAddMode = true; fetchedWeather = null }
                )
            }
        }

        if (selectedSpotForComments != null) {
            val azuriraniSpot = spots.find { it.id == selectedSpotForComments!!.id } ?: selectedSpotForComments!!
            ModalBottomSheet(
                onDismissRequest = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        selectedSpotForComments = null
                        spotsViewModel.clearComments()
                    }
                },
                sheetState = sheetState,
                modifier = Modifier.fillMaxHeight(0.85f),
                containerColor = com.example.fishspot.core.theme.FishBoje.Pozadina,
                dragHandle = { BottomSheetDefaults.DragHandle(color = com.example.fishspot.core.theme.FishBoje.TekstPriguseni) }
            ) {
                val autorUlova by spotsViewModel.autorUlova.collectAsState()
                val ulovPrijavljen by spotsViewModel.ulovPrijavljen.collectAsState()
                UlovDetalji(
                    spot = azuriraniSpot,
                    autor = autorUlova,
                    comments = comments,
                    currentUserId = currentUserId,
                    vecPrijavljen = ulovPrijavljen,
                    onPrijavi = { razlog, opis ->
                        spotsViewModel.prijaviUlov(azuriraniSpot, razlog, opis) { uspeh ->
                            Toast.makeText(
                                context,
                                if (uspeh) "Ulov je prijavljen. Hvala!" else "Greška pri prijavi ulova",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onAddComment = { text ->
                        spotsViewModel.DodajKomentar(
                            azuriraniSpot.id,
                            text
                        ) { success ->
                            if (!success) {
                                Toast.makeText(context, "Greška pri slanju komentara", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onToggleLike = { spotId, isLiked ->
                        spotsViewModel.toggleLike(spotId, isLiked)
                    }
                )
            }
        }

        val messagesMap by activeUsersViewModel.messagesMap.collectAsState()
        val focusManager = LocalFocusManager.current

        LaunchedEffect(messagesMap) {
            messagesMap.forEach { (userId, userMessages) ->
                val oldSize = lastMessageCounts[userId] ?: userMessages.size
                if (userMessages.size > oldSize) {
                    val lastMsg = userMessages.last()
                    if (lastMsg.senderId != currentUserId && openedChatUserId != userId) {
                        Toast.makeText(context, "Nova poruka od: ${lastMsg.senderUsername} 💬", Toast.LENGTH_LONG).show()

                        val senderUser = activeUsers.find { it.userId == userId }
                        if (senderUser != null && !activeBubbles.any { it.userId == userId }) {
                            activeBubbles.add(senderUser)
                            bubbleOffsets[userId] = IntOffset(20, 300 + activeBubbles.size * 180)
                        }
                    }
                }
            }
            lastMessageCounts = messagesMap.mapValues { it.value.size }
        }

        if (openedChatUserId == null && activeBubbles.isNotEmpty()) {
            val isDraggingAnyDown = activeBubbles.any { (bubbleOffsets[it.userId]?.y ?: 0) > screenHeight * 0.6f }
            if (isDraggingAnyDown) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.BottomCenter) {
                    Icon(Icons.Default.Close, contentDescription = "Zatvori", modifier = Modifier.size(60.dp), tint = Color.Red)
                }
            }

            activeBubbles.toList().forEach { bubbleUser ->
                val offset = bubbleOffsets[bubbleUser.userId] ?: IntOffset.Zero

                Box(modifier = Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopStart) {
                    Box(
                        modifier = Modifier
                            .offset { offset }
                            .size(70.dp)
                            .clip(CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .background(Color.White)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { openedChatUserId = bubbleUser.userId })
                            }
                            .pointerInput(bubbleUser.userId) {
                                detectDragGestures(
                                    onDragEnd = {
                                        val finalY = bubbleOffsets[bubbleUser.userId]?.y ?: 0
                                        if (finalY > screenHeight * 0.6f) {
                                            activeBubbles.removeAll { it.userId == bubbleUser.userId }
                                            bubbleOffsets.remove(bubbleUser.userId)
                                            activeUsersViewModel.closeChat(bubbleUser.userId)
                                        }
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val currentOffset = bubbleOffsets[bubbleUser.userId] ?: IntOffset.Zero
                                    bubbleOffsets[bubbleUser.userId] = IntOffset(
                                        currentOffset.x + dragAmount.x.toInt(),
                                        currentOffset.y + dragAmount.y.toInt()
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (bubbleUser.slikaUrl.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model = bubbleUser.slikaUrl,
                                contentDescription = "Profilna",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }
        }

        if (openedChatUserId != null) {
            val openedUser = activeBubbles.find { it.userId == openedChatUserId }
            if (openedUser != null) {
                var novaPoruka by remember { mutableStateOf("") }
                val currentMessages = messagesMap[openedUser.userId] ?: emptyList()
                val listState = rememberLazyListState()
                val chatSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                LaunchedEffect(currentMessages.size) {
                    if (currentMessages.isNotEmpty()) {
                        listState.scrollToItem(currentMessages.size - 1)
                    }
                }

                androidx.activity.compose.BackHandler {
                    focusManager.clearFocus()
                    openedChatUserId = null
                }

                ModalBottomSheet(
                    onDismissRequest = {
                        focusManager.clearFocus()
                        openedChatUserId = null
                    },
                    sheetState = chatSheetState,
                    modifier = Modifier.fillMaxHeight(0.85f),
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                focusManager.clearFocus()
                                openedChatUserId = null
                            }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Nazad", tint = Color.White)
                            }
                            Text(
                                text = openedUser.username,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            items(currentMessages) { poruka ->
                                val isMojaPoruka = poruka.senderId == currentUserId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = if (isMojaPoruka) Arrangement.End else Arrangement.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isMojaPoruka) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = poruka.text,
                                            color = if (isMojaPoruka) Color.White else Color.Black
                                        )
                                    }
                                }
                            }
                        }

                        Divider()

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .imePadding()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = novaPoruka,
                                onValueChange = { novaPoruka = it },
                                placeholder = { Text("Poruka...") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.LightGray
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = {
                                    if (novaPoruka.isNotBlank()) {
                                        val mojeIme = activeUsersViewModel.auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                                            ?: activeUsersViewModel.auth.currentUser?.email?.substringBefore("@")
                                            ?: "Pecaroš"

                                        activeUsersViewModel.sendPrivateMessage(openedUser.userId, novaPoruka, mojeIme)
                                        novaPoruka = ""
                                    }
                                },
                                modifier = Modifier.size(52.dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Pošalji", tint = Color.White)
                            }
                        }
                    }
                }
            } else {
                openedChatUserId = null
            }
        }
    }
}
data class ForecastDisplayData(
    val city: String,
    val currentTemp: Int,
    val currentDesc: String,
    val currentPressure: Int,
    val windInfo: String,
    val windDirection: String,  // Mora biti ovde
    val humidity: Int,
    val visibility: Int,// Mora biti ovde
    val riverDischarge: String,
    val riverTrend: String,     // Mora biti ovde
    val fishingIndex: Int,
    val upcomingDays: List<DayForecast>,
    val sunrise: String,
    val sunset: String
)

data class DayForecast(val date: String, val tempMax: Int, val tempMin: Int, val icon: String,val fishingIndex: Int,val rainChance: Int)

fun pretvoriStepenUStraneSveta(stepeni: Int): String {
    val pravci = arrayOf(
        "Severni (↓)", "Severoistočni (↙)", "Istočni (←)",
        "Jugoistočni (↖)", "Južni (↑)", "Jugozapadni (↗)",
        "Zapadni (→)", "Severozapadni (↘)"
    )
    val indeks = ((stepeni + 22.5) / 45.0).toInt() % 8
    return pravci[indeks]
}

fun calculateFishingIndex(pressure: Int, timestamp: Long,temp: Int): Int {
    var score = 20 // Početna baza

    // 1. Atmosferski pritisak (tvoja postojeća logika)
    when {
        pressure in 1010..1020 -> score += 20
        pressure in 1005..1009 -> score += 10
        pressure > 1020 -> score -= 10
        pressure < 1000 -> score -= 20
    }

    // 2. Mesec (Lunarna faza - tvoja postojeća logika)
    val knownNewMoon = 1704974220000L
    val lunarCycle = 29.530588853 * 24 * 60 * 60 * 1000
    val diff = timestamp - knownNewMoon
    val currentPhase = (diff % lunarCycle) / lunarCycle
    when {
        currentPhase < 0.1 || currentPhase > 0.9 -> score += 10 // Mlad mesec
        currentPhase in 0.45..0.55 -> score += 5 // Pun mesec
    }

    // --- NOVA LOGIKA ---

    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val month = cal.get(Calendar.MONTH) // 0 = Jan, 1 = Feb, ... 11 = Dec
    val hour = cal.get(Calendar.HOUR_OF_DAY) // 0 - 23

    // 3. Sezonski faktor (Mesec)
    // Jan(0), Feb(1), Dec(11) -> Zima, metabolizam usporen
    // Mar(2), Apr(3) -> Mrest/oporavak
    when (month) {
        0, 1,10, 11 -> score -= 20 // Duboka zima, slaba aktivnost
        2 -> score -= 10
        3, 4,5 -> score += 5
        // Prolećno buđenje
        8, 9 -> score += 5 // Jesen, aktivna priprema za zimu
        else -> score += 10     // Leto/periodi aktivnosti
    }

    // 4. Doba dana (Sat)
    // Zlatni sati (zora i sumrak) su najbolji
    when (hour) {
        in 5..9 -> score += 10   // Jutarnje svitanje
        in 18..22 -> score += 10 // Večernji sumrak
        in 12..15 -> score -= 10 // Podne (često letargija, pretoplo)
        in 23..4 -> score -= 5  // Duboka noć (zavisi od vrste, ali generalno slabije)
    }
    when {
        temp in 15..25 -> score += 5
        temp > 30 || temp < 10 -> score -= 10

    }
    if (pressure < 990 || temp < 3) {
        score -= 30 // To je "udarac" na score koji teško može da se nadoknadi
    }

    return score.coerceIn(0, 100)
}

// Vektorski pin iz res/drawable pretvoren u ikonu za marker na mapi
fun vektorUPin(context: android.content.Context, resId: Int): BitmapDescriptor {
    val crtez = androidx.core.content.ContextCompat.getDrawable(context, resId)!!
    return BitmapDescriptorFactory.fromBitmap(crtez.toBitmap(crtez.intrinsicWidth, crtez.intrinsicHeight))
}

@Composable
private fun dugmeOtkaziBoje() = ButtonDefaults.buttonColors(
    containerColor = FishBoje.Kartica,
    contentColor = FishBoje.TekstPrimarni
)

@Composable
private fun dugmePotvrdiBoje() = ButtonDefaults.buttonColors(
    containerColor = FishBoje.Akcenat,
    contentColor = FishBoje.Pozadina
)