package com.example.doggo.scraper

import android.util.Log
import com.example.doggo.data.HouseSitJob
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

class TrustedHousesittersScraper : Scraper {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        install(HttpCookies)
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
    private val apiVersion = "2025-08-14"
    private val jwt = "JWT eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VyX2lkIjo4MTcwMTMsInVzZXJuYW1lIjoiX19USFNfX2M4M2E1MGUzZGJhZTQxY2I4NTlmZWQiLCJleHAiOjE3Nzg2NDAyNTcsImVtYWlsIjoic2xhbmd0b24xM0BnbWFpbC5jb20iLCJvcmlnX2lhdCI6MTc3NzQzMDY1NywiMmZhX2VuYWJsZWQiOnRydWV9.MT0jpX-aNbjoMTyBwaoVQ12gOJ-GYbxjlVhcSXhru-E"

    private fun HttpRequestBuilder.apiHeaders() {
        header("User-Agent", userAgent)
        header("Accept", "application/json, text/plain, */*")
        header("api-key", "null")
        header("api-version", apiVersion)
        header("authorization", jwt)
        header("ths-platform-detail", "web")
        header("timezone-offset", "10")
        header("referer", "https://www.trustedhousesitters.com/")
    }

    private fun HttpRequestBuilder.htmlHeaders() {
        header("User-Agent", userAgent)
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
    }

    override suspend fun scrape(onJobScraped: suspend (HouseSitJob) -> Boolean) {
        Log.d("THSScraper", "Starting JSON-State based THS scrape")
        val targets = listOf(
            "https://www.trustedhousesitters.com/house-and-pet-sitting-assignments/australia/queensland/" to "QLD",
            "https://www.trustedhousesitters.com/house-and-pet-sitting-assignments/australia/new-south-wales/" to "NSW"
        )

        for ((baseTargetUrl, stateCode) in targets) {
            var page = 1
            var continueScraping = true
            
            while (continueScraping) {
                val url = if (page == 1) baseTargetUrl else "$baseTargetUrl?page=$page"
                Log.d("THSScraper", "Fetching HTML to extract state: $url")

                try {
                    val response = client.get(url) { htmlHeaders() }
                    val html = response.bodyAsText()
                    
                    val ids = extractIdsFromInitialState(html)
                    Log.d("THSScraper", "Extracted ${ids.size} IDs from __INITIAL_STATE__ on page $page for $stateCode")

                    if (ids.isEmpty()) {
                        Log.d("THSScraper", "No IDs found in state. Ending $stateCode.")
                        break
                    }

                    for (id in ids) {
                        Log.d("THSScraper", "Fetching API details for ID: $id")
                        val jobData = fetchJobDetails(id, stateCode)
                        if (jobData != null) {
                            for (job in jobData) {
                                continueScraping = onJobScraped(job)
                                if (!continueScraping) break
                            }
                        }
                        if (!continueScraping) break
                        delay(500) // Small delay between API calls
                    }

                    if (!continueScraping) break
                    
                    // Simple pagination check in HTML
                    val doc = Jsoup.parse(html)
                    val hasNext = doc.select("a:contains(Next), a[aria-label*='Next'], a[href*='page=${page+1}']").isNotEmpty()
                    if (!hasNext) break

                    page++
                    delay(1000)
                } catch (e: Exception) {
                    Log.e("THSScraper", "Error on page $page: ${e.message}")
                    break
                }
            }
        }
    }

    private fun extractIdsFromInitialState(html: String): List<String> {
        return try {
            val marker = "window.__INITIAL_STATE__ ="
            val startIndex = html.indexOf(marker)
            if (startIndex == -1) {
                Log.w("THSScraper", "Could not find window.__INITIAL_STATE__ marker in HTML")
                return emptyList()
            }
            
            val jsonStart = html.indexOf("{", startIndex)
            if (jsonStart == -1) return emptyList()
            
            // Manually find balancing brace to handle trailing script content
            val jsonEnd = findBalancingBrace(html, jsonStart)
            if (jsonEnd == -1) {
                Log.w("THSScraper", "Could not find balancing brace for INITIAL_STATE")
                return emptyList()
            }
            
            val jsonStr = html.substring(jsonStart, jsonEnd + 1)
            val root = json.parseToJsonElement(jsonStr).jsonObject
            
            val ids = mutableListOf<String>()
            findIdsInListingNodes(root, ids)
            ids.distinct()
        } catch (e: Exception) {
            Log.e("THSScraper", "Failed to parse INITIAL_STATE JSON", e)
            emptyList()
        }
    }

    private fun findBalancingBrace(text: String, startIndex: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in startIndex until text.length) {
            val c = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            when (c) {
                '\\' -> escaped = true
                '"' -> inString = !inString
                '{' -> if (!inString) depth++
                '}' -> if (!inString) {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun findIdsInListingNodes(element: JsonElement, ids: MutableList<String>) {
        when (element) {
            is JsonObject -> {
                // If this object is named "listing", its children keys are usually the IDs
                element["listing"]?.jsonObject?.let { listingMap ->
                    listingMap.keys.forEach { key ->
                        if (key.all { it.isDigit() }) {
                            ids.add(key)
                        }
                    }
                }
                
                // Also check if this object HAS an "id" and "title" (standard listing shape)
                val id = element["id"]?.jsonPrimitive?.contentOrNull
                val hasTitle = element.containsKey("title")
                if (id != null && id.all { it.isDigit() } && hasTitle) {
                    ids.add(id)
                }

                // Continue searching deeper
                element.values.forEach { findIdsInListingNodes(it, ids) }
            }
            is JsonArray -> {
                element.forEach { findIdsInListingNodes(it, ids) }
            }
            else -> {}
        }
    }

    private suspend fun fetchJobDetails(id: String, stateCode: String): List<HouseSitJob>? {
        return try {
            val apiUrl = "https://www.trustedhousesitters.com/api/v3/search/listings/$id/"
            val response: ThsApiResponse = client.get(apiUrl) { apiHeaders() }.body()
            
            val suburb = response.location.name
            val description = response.title
            val imageUrl = response.photos.firstOrNull()?.publicId?.let { 
                "https://res.cloudinary.com/trustedhousesitters/image/upload/t_film_ratio,f_auto/v1/$it"
            } ?: ""
            
            val animals = response.pets.map { it.animal.name.replaceFirstChar { c -> c.uppercase() } }.distinct()

            response.assignments.map { assignment ->
                HouseSitJob(
                    id = "${id}_${assignment.id}",
                    suburb = suburb,
                    state = stateCode,
                    imageUrl = imageUrl,
                    description = description,
                    animals = animals,
                    latitude = response.location.coordinates.lat,
                    longitude = response.location.coordinates.lon,
                    startDate = parseIsoDate(assignment.startDate),
                    endDate = parseIsoDate(assignment.endDate),
                    listingUrl = "https://www.trustedhousesitters.com/house-and-pet-sitting-assignments/australia/${response.location.admin1Slug}/${response.location.slug}/l/$id/",
                    locationDescriptor = suburb,
                    source = "TrustedHousesitters"
                )
            }
        } catch (e: Exception) {
            Log.e("THSScraper", "Failed to fetch details for $id: ${e.message}")
            null
        }
    }

    private fun parseIsoDate(isoStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.parse(isoStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}

@Serializable
data class ThsApiResponse(
    val id: String,
    val title: String,
    val location: ThsLocation,
    val photos: List<ThsPhoto>,
    val pets: List<ThsPet>,
    val assignments: List<ThsAssignment>
)

@Serializable
data class ThsLocation(
    val name: String,
    val slug: String,
    val admin1Slug: String,
    val coordinates: ThsCoordinates
)

@Serializable
data class ThsCoordinates(
    val lat: Double,
    val lon: Double
)

@Serializable
data class ThsPhoto(
    val publicId: String
)

@Serializable
data class ThsPet(
    val animal: ThsAnimal
)

@Serializable
data class ThsAnimal(
    val name: String
)

@Serializable
data class ThsAssignment(
    val id: String,
    val startDate: String,
    val endDate: String
)
