package com.example.fishspot.userinterface



import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─── Boje teme ────────────────────────────────────────────────────────────────
private val BgDark        = Color(0xFF0B1612)
private val BgCard        = Color(0xFF132218)
private val BgCardBorder  = Color(0xFF1E3328)
private val BgParamRow    = Color(0xFF0F1E18)
private val AccentGreen   = Color(0xFF2ECC71)
private val AccentGreenDim= Color(0xFF1A7A44)
private val TextMuted     = Color(0xFF6B8078)
private val TextSecondary = Color(0xFFB0BDB9)
private val TextPrimary   = Color.White

// ─── Entry point ──────────────────────────────────────────────────────────────
@Composable
fun WeatherScreen(
    weatherData: ForecastDisplayData,
    onDismiss: () -> Unit,
    onAddCatch: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            item {
                WeatherHeader(weatherData = weatherData, onDismiss = onDismiss)
            }

            // ── Temp + Uspešnost krug ───────────────────────────────────────
            item {
                TempAndSuccessRow(weatherData = weatherData)
            }

            // ── Sunrise/Sunset/Faza ─────────────────────────────────────────
            item {
                // Prosleđujemo stvarne vrednosti iz API-ja
                SunMoonRow(
                    sunrise = weatherData.sunrise,
                    sunset = weatherData.sunset,
                    timestamp = System.currentTimeMillis()
                )
            }

            // ── Indeks za pecanje ───────────────────────────────────────────
            item {
                FishingIndexBar(fishingIndex = weatherData.fishingIndex)
            }

            // ── Sekcija "Parametri pecanja" ─────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "PARAMETRI PECANJA",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Red 1: Pritisak, Vetar, Temperatura ────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WeatherParamCard(
                        icon = Icons.Default.Speed,
                        value = "${weatherData.currentPressure}",
                        unit = "hPa",
                        label = "Pritisak",
                        statusText = if (weatherData.currentPressure in 1010..1020) "Raste • Odlično" else "Stabilan",
                        statusPositive = weatherData.currentPressure in 1010..1020,
                        bgColor = Color(0xFFEDF7F0),
                        iconColor = Color(0xFF1B6B3A),
                        modifier = Modifier.weight(1f)
                    )
                    WeatherParamCard(
                        icon = Icons.Default.Air,
                        value = weatherData.windInfo.substringBefore(" "),
                        unit = "m/s",
                        label = "Vetar",
                        statusText = weatherData.windDirection,
                        statusPositive = true,
                        bgColor = Color(0xFFECF4FB),
                        iconColor = Color(0xFF1565C0),
                        modifier = Modifier.weight(1f)
                    )
                    WeatherParamCard(
                        icon = Icons.Default.Thermostat,
                        value = "${weatherData.currentTemp}",
                        unit = "°C",
                        label = "Temperatura",
                        statusText = when {
                            weatherData.currentTemp < 5  -> "Hladno"
                            weatherData.currentTemp < 15 -> "Sveže"
                            weatherData.currentTemp < 25 -> "Optimalna"
                            else                          -> "Toplo"
                        },
                        statusPositive = weatherData.currentTemp in 10..25,
                        bgColor = Color(0xFFFBEDE9),
                        iconColor = Color(0xFFBF360C),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(10.dp)) }

            // ── Red 2: Vlažnost, Vidljivost, Protok ────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WeatherParamCard(
                        icon = Icons.Default.WaterDrop,
                        value = "${weatherData.humidity}",
                        unit = "%",
                        label = "Vlažnost",
                        statusText = when {
                            weatherData.humidity < 30 -> "Suvo"
                            weatherData.humidity < 60 -> "Normalna"
                            weatherData.humidity < 80 -> "Povišena"
                            else                      -> "Visoka"
                        },
                        statusPositive = weatherData.humidity in 30..70,
                        bgColor = Color(0xFFE0F4F1),
                        iconColor = Color(0xFF00695C),
                        modifier = Modifier.weight(1f)
                    )
                    WeatherParamCard(
                        icon = Icons.Default.Visibility,
                        value = if (weatherData.visibility >= 1000)
                            "${weatherData.visibility / 1000}"
                        else
                            "${weatherData.visibility}",
                        unit = if (weatherData.visibility >= 1000) "km" else "m",
                        label = "Vidljivost",
                        statusText = when {
                            weatherData.visibility >= 10000 -> "Odlična"
                            weatherData.visibility >= 5000  -> "Dobra"
                            weatherData.visibility >= 1000  -> "Smanjena"
                            else                            -> "Slaba"
                        },
                        statusPositive = weatherData.visibility >= 5000,
                        bgColor = Color(0xFFF2E8FC),
                        iconColor = Color(0xFF6A1B9A),
                        modifier = Modifier.weight(1f)
                    )

                }
            }

            // ── 3-dnevna prognoza ───────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(20.dp))
                ThreeDayForecast(days = weatherData.upcomingDays)
            }

            // ── Bottom akcije ───────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(20.dp))

            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────
@Composable
private fun WeatherHeader(weatherData: ForecastDisplayData, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF142D22), BgDark),
                    startY = 0f,
                    endY = 300f
                )
            )
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text(
            text = "VREMENSKA PROGNOZA • PECANJE",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart)
        )

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(36.dp)
                .background(Color(0xFF1B2D28), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Zatvori",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.padding(top = 36.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF1B3028), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = weatherData.city,
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Izabrano ribolovno područje",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFF1A3028), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF2A4A3A), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(AccentGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Uživo", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ─── Temperatura + Uspešnost krug ─────────────────────────────────────────────
@Composable
private fun TempAndSuccessRow(weatherData: ForecastDisplayData) {
    val animatedProgress by animateFloatAsState(
        targetValue = weatherData.fishingIndex / 100f,
        animationSpec = tween(durationMillis = 1200),
        label = "fishingProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(BgCard, RoundedCornerShape(20.dp))
            .border(1.dp, BgCardBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${weatherData.currentTemp}°C",
                    color = TextPrimary,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Thin
                )
                Text(
                    text = weatherData.currentDesc.replaceFirstChar { it.uppercase() },
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                    CircularProgressIndicator(
                        progress = 1f,
                        color = Color(0xFF1A3028),
                        strokeWidth = 6.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                    CircularProgressIndicator(
                        progress = animatedProgress,
                        color = when {
                            weatherData.fishingIndex > 75 -> AccentGreen
                            weatherData.fishingIndex > 50 -> Color(0xFFF1C40F)
                            else -> Color(0xFFE74C3C)
                        },
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round,
                        trackColor = Color.Transparent,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SetMeal,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${weatherData.fishingIndex}%",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "USPEŠNOST",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── Sunce / Mesec traka ──────────────────────────────────────────────────────
@Composable
private fun SunMoonRow(sunrise: String, sunset: String, timestamp: Long) {
    val moonPhaseLabel = getMoonPhaseLabel(timestamp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(BgCard, RoundedCornerShape(14.dp))
            .border(1.dp, BgCardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SunMoonItem(icon = Icons.Default.WbSunny, label = "Izlazak", value = sunrise, tint = Color(0xFFF39C12))
        Divider(
            modifier = Modifier
                .height(30.dp)
                .width(1.dp),
            color = BgCardBorder
        )
        SunMoonItem(icon = Icons.Default.Nightlight, label = "Zalazak", value = sunset, tint = Color(0xFFE67E22))
        Divider(
            modifier = Modifier
                .height(30.dp)
                .width(1.dp),
            color = BgCardBorder
        )
        SunMoonItem(icon = Icons.Default.DarkMode, label = "Mesec", value = moonPhaseLabel, tint = Color(0xFF9B59B6))
    }
}

@Composable
private fun SunMoonItem(icon: ImageVector, label: String, value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Column {
            Text(text = label, color = TextMuted, fontSize = 10.sp)
            Text(text = value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── Indeks za pecanje ────────────────────────────────────────────────────────
@Composable
private fun FishingIndexBar(fishingIndex: Int) {
    val animatedProgress by animateFloatAsState(
        targetValue = fishingIndex / 100f,
        animationSpec = tween(durationMillis = 1000),
        label = "barProgress"
    )
    val usloviTekst = when {
        fishingIndex > 75 -> "Odlični uslovi"
        fishingIndex > 50 -> "Dobri uslovi"
        else -> "Osrednji uslovi"
    }
    val usloviBoja = when {
        fishingIndex > 75 -> AccentGreen
        fishingIndex > 50 -> Color(0xFFF1C40F)
        else -> Color(0xFFE74C3C)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(BgCard, RoundedCornerShape(16.dp))
            .border(1.dp, BgCardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("INDEKS ZA PECANJE", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(usloviTekst, color = usloviBoja, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A3028))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(AccentGreenDim, usloviBoja)
                        )
                    )
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Loše", color = TextMuted, fontSize = 10.sp)
            Text("Odlično", color = TextMuted, fontSize = 10.sp)
        }
    }
}

// ─── Kartica parametra ────────────────────────────────────────────────────────
@Composable
private fun WeatherParamCard(
    icon: ImageVector,
    value: String,
    unit: String,
    label: String,
    statusText: String,
    statusPositive: Boolean,
    bgColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(118.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Icon(
                    imageVector = if (statusPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = if (statusPositive) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                    modifier = Modifier.size(14.dp)
                )
            }

            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        color = Color(0xFF1A1A1A),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = unit,
                        color = Color(0xFF555555),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(text = label, color = Color(0xFF777777), fontSize = 10.sp)
            }

            Box(
                modifier = Modifier
                    .background(iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = statusText,
                    color = iconColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─── 3-dnevna prognoza ────────────────────────────────────────────────────────
@Composable
private fun ThreeDayForecast(days: List<DayForecast>) {
    // 1. Provera pre nego što išta krene. Ako je lista prazna, samo izađi iz funkcije.
    if (days.isEmpty()) return

    // 2. Sada je bezbedno definisati stanje
    var selectedDay by remember { mutableStateOf(days.first()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(BgCard, RoundedCornerShape(20.dp))
            .border(1.dp, BgCardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "3-DNEVNA PROGNOZA",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 1. OVO JE ROW ZA LISTU DANA
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.forEachIndexed { index, day ->
                val fishIndex = day.fishingIndex
                val indexColor = when {
                    fishIndex > 75 -> AccentGreen
                    fishIndex > 55 -> Color(0xFFF1C40F)
                    else -> Color(0xFFE74C3C)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(indexColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            if (selectedDay == day) AccentGreen else indexColor,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedDay = day }
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(getDayName(day.date, index), color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    AsyncImage(
                        model = "https://openweathermap.org/img/wn/${day.icon}@2x.png",
                        contentDescription = null,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${day.tempMax}°", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${day.tempMin}°", color = TextMuted, fontSize = 12.sp)
                }
            }
        } // Ovde se zatvara prvi Row

        // 2. OVO JE SPACER I ROW ZA DETALJE (Sada su van prvog Row-a)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1E18), RoundedCornerShape(12.dp))
                .border(1.dp, BgCardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = "https://openweathermap.org/img/wn/${selectedDay.icon}@2x.png",
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Indeks: ${selectedDay.fishingIndex}%",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Kiša: ${selectedDay.rainChance}% • ${selectedDay.tempMax}° / ${selectedDay.tempMin}°",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// ─── Donji akcioni tasteri ────────────────────────────────────────────────────







// ─── Helper funkcije ──────────────────────────────────────────────────────────
private fun getMoonPhaseLabel(timestamp: Long): String {
    // 29.53 dana je lunarni ciklus
    val lunarCycle = 2551443000L // u milisekundama
    val knownNewMoon = 1673434800000L // 11. Jan 2023. (treba datum blizu sadašnjosti)

    val diff = timestamp - knownNewMoon
    val phase = (diff % lunarCycle).toDouble() / lunarCycle.toDouble()

    return when {
        phase < 0.05 || phase > 0.95 -> "Mlad"
        phase < 0.20 -> "Rastući srp"
        phase < 0.30 -> "Prva četvrt"
        phase < 0.45 -> "Rastuća izbočina"
        phase < 0.55 -> "Pun"
        phase < 0.70 -> "Opadajuća izbočina"
        phase < 0.80 -> "Poslednja četvrt"
        else -> "Opadajući srp"
    }
}
private fun getDayName(dateStr: String, fallbackIndex: Int): String {
    return try {
        val parsed = SimpleDateFormat("dd.MM.", Locale.getDefault()).parse(dateStr)
        val cal = Calendar.getInstance()
        parsed?.let { cal.time = it }
        val days = arrayOf("Ned", "Pon", "Uto", "Sre", "Čet", "Pet", "Sub")
        days[cal.get(Calendar.DAY_OF_WEEK) - 1]
    } catch (e: Exception) {
        when (fallbackIndex) {
            0 -> "Sreda"; 1 -> "Četvrtak"; else -> "Petak"
        }
    }
}