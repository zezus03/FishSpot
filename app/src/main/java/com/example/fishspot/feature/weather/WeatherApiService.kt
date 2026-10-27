package com.example.fishspot.feature.weather

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import androidx.annotation.Keep
import kotlinx.coroutines.tasks.await

@Keep
interface WeatherApi {
    // Putanja do besplatnog vremenskog API-ja
    @GET("data/2.5/weather")
    suspend fun getWeather(
        @Query("lat") lat: Double, // Geografska širina
        @Query("lon") lon: Double, // Geografska dužina
        @Query("appid") apiKey: String, // Tvoj tajni ključ
        @Query("units") units: String = "metric", // Da dobijemo Celzijuse, a ne Farenhajte
        @Query("lang") lang: String = "sr" // Da opis bude na srpskom!
    ): WeatherResponse

    // 2. NOVA RUTA: Prognoza na 3 sata za narednih 5 dana
    @GET("data/2.5/forecast")
    suspend fun getForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "sr"
    ): ForecastResponse
}

// Prognoza ide preko naše Cloud Function (OpenWeather ključ je samo na serveru, odgovori se keširaju)
object Prognoza {
    private val gson = com.google.gson.Gson()

    private suspend fun pozovi(tip: String, lat: Double, lng: Double): String {
        val rezultat = com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("vremenskaPrognoza")
            .call(mapOf("tip" to tip, "lat" to lat, "lng" to lng))
            .await()
        @Suppress("UNCHECKED_CAST")
        return (rezultat.data as Map<String, Any?>)["json"] as String
    }

    suspend fun trenutno(lat: Double, lng: Double): WeatherResponse =
        gson.fromJson(pozovi("weather", lat, lng), WeatherResponse::class.java)

    suspend fun petDana(lat: Double, lng: Double): ForecastResponse =
        gson.fromJson(pozovi("forecast", lat, lng), ForecastResponse::class.java)
}
