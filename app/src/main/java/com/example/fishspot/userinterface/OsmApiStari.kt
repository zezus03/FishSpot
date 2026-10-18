package com.example.fishspot.userinterface

// Importujemo modele iz viewmodel foldera da ne bi prijavljivao grešku
import com.example.fishspot.viewmodel.OsmResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface OsmApiStari {
    @GET("api/interpreter")
    suspend fun getNearbyWaters(
        @Query("data") query: String
    ): OsmResponse
}

// Promenjeno ime u OsmNetworkStari
object OsmNetworkStari {
    private const val BASE_URL = "https://overpass-api.de/"

    val retrofit: OsmApiStari by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OsmApiStari::class.java)
    }
}