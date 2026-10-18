package com.example.fishspot.viewmodel

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import androidx.annotation.Keep

// 1. Modeli podataka
@Keep
data class OsmResponse(
    val elements: List<OsmElement>
)

@Keep
data class OsmElement(
    val id: Long,
    val tags: Map<String, String>?,
    val lat: Double?,
    val lon: Double?,
    val center: Center?,
    val geometry: List<OsmGeometry>? = null
)

@Keep
data class Center(
    val lat: Double,
    val lon: Double
)

@Keep
data class OsmGeometry(
    val lat: Double,
    val lon: Double
)

// NOVI MODEL ZA LOKALNE VODE (Sada je samostalan na nivou paketa i vidljiv svuda)
@Keep
data class LocalWater(
    val id: Long,
    val name: String,
    val type: String,
    val lat: Double,
    val lon: Double
)

// 2. Interfejs
@Keep
interface OsmApiService {
    @GET("api/interpreter")
    suspend fun getNearbyWaters(@Query("data") query: String): OsmResponse
}

// 3. Mreža
object OsmNetwork {
    private const val BASE_URL = "https://overpass.private.coffee/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val retrofit: OsmApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OsmApiService::class.java)
    }
}