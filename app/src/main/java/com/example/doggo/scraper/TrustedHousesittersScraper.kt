package com.example.doggo.scraper

import android.util.Log
import com.example.doggo.data.HouseSitJob
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

class TrustedHousesittersScraper : Scraper {

    private val client = HttpClient(OkHttp) {
        install(HttpCookies)
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val baseUrl = "https://www.trustedhousesitters.com"

    private fun HttpRequestBuilder.commonHeaders() {
        header("User-Agent", userAgent)
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        header("Accept-Language", "en-US,en;q=0.9")
        header("Cache-Control", "no-cache")
    }

    override suspend fun scrape(onJobScraped: suspend (HouseSitJob) -> Boolean) {
        Log.d("THSScraper", "Starting scrape process")
        val regions = listOf(
            "queensland" to "QLD",
            "new-south-wales" to "NSW"
        )

        for ((regionSlug, stateCode) in regions) {
            var page = 1
            var continueScraping = true
            Log.d("THSScraper", "Scraping region: $regionSlug")

            try {
                while (continueScraping) {
                    val url = "$baseUrl/house-and-pet-sitting-assignments/australia/$regionSlug/?page=$page"
                    Log.d("THSScraper", "Requesting URL: $url")

                    val response = client.get(url) {
                        commonHeaders()
                    }

                    if (!response.status.toString().startsWith("2")) {
                        Log.e("THSScraper", "Failed to fetch page $page: ${response.status}")
                        break
                    }

                    val html = response.bodyAsText()
                    val doc = Jsoup.parse(html)
                    
                    // The first version that "worked" used a broad link-based search
                    // Let's refine it to get individual cards without collapsing them
                    val listings = doc.select("a[href*='/house-and-pet-sitting-assignments/australia/'][href*='/$regionSlug/']").mapNotNull { link ->
                        // Try to find a reasonable parent container that looks like a card or list item
                        link.parents().firstOrNull { 
                            val className = it.className().lowercase()
                            className.contains("card") || className.contains("item") || it.tagName() == "article"
                        } ?: link.parent() // Fallback to immediate parent
                    }.distinctBy { it.outerHtml() }
                    
                    Log.d("THSScraper", "Found ${listings.size} listings on page $page for $regionSlug")

                    if (listings.isEmpty()) {
                        Log.d("THSScraper", "No listings found on page $page. Stopping region.")
                        break
                    }

                    var validOnPage = 0
                    for (listing in listings) {
                        val job = parseListing(listing, stateCode)
                        if (job != null) {
                            validOnPage++
                            continueScraping = onJobScraped(job)
                            if (!continueScraping) break
                        }
                    }

                    if (!continueScraping) break
                    
                    // Security break: if we found listings but none were valid, don't just keep going
                    if (validOnPage == 0) {
                        Log.w("THSScraper", "Found ${listings.size} listings but 0 were valid jobs. Stopping to avoid loop.")
                        break
                    }

                    // Pagination: Check for 'Next' link
                    val hasNext = doc.select("a[href*='page=${page + 1}']").isNotEmpty() || 
                                 doc.select("a:contains(Next), a[aria-label*='Next']").isNotEmpty()
                    
                    if (!hasNext) {
                        Log.d("THSScraper", "No next page link found. Stopping region.")
                        break
                    }
                    
                    page++
                    delay(2000)
                }
            } catch (e: Exception) {
                Log.e("THSScraper", "Scraping failed for region $regionSlug", e)
            }
        }
        Log.d("THSScraper", "Scrape process completed successfully")
    }

    private fun parseListing(element: Element, stateCode: String): HouseSitJob? {
        return try {
            val linkElement = element.select("a[href*='/house-and-pet-sitting-assignments/']").firstOrNull() ?: return null
            val relativeUrl = linkElement.attr("href").trimEnd('/')
            val listingUrl = if (relativeUrl.startsWith("http")) relativeUrl else "$baseUrl$relativeUrl"
            
            // 1. ID Extraction (handling /l/ pattern)
            // Example: .../wamberal-north/l/1040627/
            val segments = relativeUrl.split("/")
            val id = segments.lastOrNull()?.takeIf { it.all { c -> c.isDigit() } } ?: ""
            if (id.isEmpty()) return null

            // 2. Suburb Extraction
            var suburb = ""
            val idIndex = segments.lastIndexOf(id)
            if (idIndex > 1 && segments[idIndex - 1] == "l") {
                suburb = segments[idIndex - 2]
            } else if (idIndex > 0) {
                suburb = segments[idIndex - 1]
            }
            suburb = suburb.replace("-", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

            // 3. Image Extraction
            // Try meta tag if present, then srcset, then src
            var imageUrl = element.select("meta[property='og:image']").attr("content").ifEmpty {
                element.select("img").firstOrNull { it.attr("src").contains("cloudinary") || it.attr("src").contains("trustedhousesitters") }?.let {
                    it.attr("srcset").split(" ").firstOrNull() ?: it.attr("src")
                } ?: ""
            }
            if (imageUrl.startsWith("//")) imageUrl = "https:$imageUrl"

            // 4. Description Extraction
            val title = element.select("h3, .title, [class*='title']").text().trim()
            val description = element.select("meta[property='og:description']").attr("content").trim().ifEmpty { title }

            val locationText = element.select(".location, [class*='location']").text().trim().ifEmpty { 
                "$suburb, $stateCode, Australia"
            }

            // 5. Dates
            val dateText = element.text()
            val dateMatch = Regex("""(\d{1,2}\s+[A-Za-z]{3}\s+\d{4})\s*-\s*(\d{1,2}\s+[A-Za-z]{3}\s+\d{4})""").find(dateText)
            
            val startDate = dateMatch?.groupValues?.getOrNull(1)?.let { parseDate(it) } ?: System.currentTimeMillis()
            val endDate = dateMatch?.groupValues?.getOrNull(2)?.let { parseDate(it) } ?: (startDate + 86400000)

            // 6. Animals
            val animals = mutableListOf<String>()
            val petText = element.text().lowercase()
            if (petText.contains("dog")) animals.add("Dog")
            if (petText.contains("cat")) animals.add("Cat")
            
            HouseSitJob(
                id = id,
                suburb = suburb.ifEmpty { "Unknown" },
                state = stateCode,
                imageUrl = imageUrl,
                description = description,
                animals = animals,
                latitude = 0.0,
                longitude = 0.0,
                startDate = startDate,
                endDate = endDate,
                listingUrl = listingUrl,
                locationDescriptor = locationText,
                source = "TrustedHousesitters"
            )
        } catch (e: Exception) {
            Log.e("THSScraper", "Error parsing listing", e)
            null
        }
    }

    private fun parseDate(dateStr: String): Long? {
        return try {
            dateFormat.parse(dateStr.trim())?.time
        } catch (e: Exception) {
            null
        }
    }
}
