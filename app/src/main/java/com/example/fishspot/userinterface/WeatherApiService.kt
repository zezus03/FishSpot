package com.example.fishspot.userinterface

import com.example.fishspot.viewmodel.WeatherResponse
import com.example.fishspot.viewmodel.ForecastResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import androidx.annotation.Keep // <-- Dodat import

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

// Pravimo glavni objekat koji ćemo pozivati iz aplikacije
object WeatherNetwork {
    private const val BASE_URL = "https://api.openweathermap.org/"

    val retrofit: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }
}