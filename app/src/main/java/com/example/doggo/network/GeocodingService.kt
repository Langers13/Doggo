package com.example.doggo.network

import com.example.doggo.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.util.Log

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult>,
    val status: String
)

@Serializable
data class GeocodingResult(
    val geometry: Geometry
)

@Serializable
data class Geometry(
    val location: Location
)

@Serializable
data class Location(
    val lat: Double,
    val lng: Double
)

class GeocodingService {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    private val apiKey = BuildConfig.MAPS_API_KEY

    suspend fun getCoordinates(suburb: String, state: String): Pair<Double, Double>? {
        return try {
            val address = "$suburb, $state, Australia"
            val response: GeocodingResponse = client.get("https://maps.googleapis.com/maps/api/geocode/json") {
                parameter("address", address)
                parameter("key", apiKey)
            }.body()

            if (response.status == "OK" && response.results.isNotEmpty()) {
                val loc = response.results[0].geometry.location
                Log.d("GeocodingService", "Success: $address -> ${loc.lat}, ${loc.lng}")
                Pair(loc.lat, loc.lng)
            } else {
                Log.w("GeocodingService", "Failed: $address, status: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e("GeocodingService", "Error geocoding $suburb, $state", e)
            null
        }
    }
}
