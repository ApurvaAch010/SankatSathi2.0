package com.nepal.sankatsathi

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class EonetResponse(
    val events: List<EonetEvent>
)

data class EonetEvent(
    val id: String,
    val title: String,
    val description: String?,
    val link: String,
    val closed: String?,
    val categories: List<EonetCategory>,
    val sources: List<EonetSource>,
    val geometry: List<EonetGeometry>
)

data class EonetCategory(
    val id: String,
    val title: String
)

data class EonetSource(
    val id: String,
    val url: String
)

data class EonetGeometry(
    val magnitudeValue: Double?,
    val magnitudeUnit: String?,
    val date: String,
    val type: String,
    val coordinates: List<Double> // [longitude, latitude]
)

interface EonetApi {
    @GET("api/v3/events")
    suspend fun getEvents(
        @Query("status") status: String = "open"
    ): EonetResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://eonet.gsfc.nasa.gov/"

    val instance: EonetApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EonetApi::class.java)
    }
}
