// WeatherModel.kt
package com.example.fishspot.viewmodel

import androidx.annotation.Keep

// MODEL ZA TRENUTNO VREME (zadržavamo staro)
@Keep
data class WeatherResponse(
    val main: MainData,
    val weather: List<WeatherData>,
    val wind: WindData,
    val name: String,
    val dt: Long,
    val visibility: Int, // DODAJ OVO
    val sys: SysData// Vreme merenja (Unix timestamp)
)

// NOVI MODEL ZA PROGNOZU (5 dana / 3 sata)
@Keep
data class ForecastResponse(
    val list: List<ForecastItem>,
    val city: CityData
)

@Keep
data class ForecastItem(
    val dt: Long, // Unix timestamp za vreme prognoze
    val main: MainData,
    val weather: List<WeatherData>,
    val wind: WindData,
    val dt_txt: String, // Formatirano vreme (npr. "2024-05-20 15:00:00")
    val pop: Double? = 0.0
)

@Keep
data class CityData(
    val name: String
)

@Keep
data class MainData(
    val temp: Double,
    val pressure: Int,
    val humidity: Int // Dodajemo i vlažnost, korisno je za prognozu
)

@Keep
data class WeatherData(
    val description: String,
    val icon: String
)

@Keep
data class WindData(
    val speed: Double,
    val deg: Int
)
@Keep
data class SysData(
    val sunrise: Long,
    val sunset: Long
)