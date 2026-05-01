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

class MindaHomeScraper : Scraper {

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
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)
    private val baseUrl = "https://mindahome.com.au"

    private fun HttpRequestBuilder.commonHeaders() {
        header("User-Agent", userAgent)
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
        header("Accept-Language", "en-US,en;q=0.9")
        header("Cache-Control", "max-age=0")
        header("Connection", "keep-alive")
        header("Upgrade-Insecure-Requests", "1")
    }

    override suspend fun scrape(onJobScraped: suspend (HouseSitJob) -> Boolean) {
        Log.d("MindaHomeScraper", "Starting scrape process")
        var page = 1
        var continueScraping = true

        try {
            while (continueScraping) {
                // Pagination structure provided by user:
                // Page 1: ...2,4?sort=-sort_date
                // Page 2: ...2,4-25?sort=-sort_date
                // Page 3: ...2,4-50?sort=-sort_date
                val offset = (page - 1) * 25
                val url = if (page == 1) {
                    "https://mindahome.com.au/house-sitting-positions-australia.2,4?sort=-sort_date"
                } else {
                    "https://mindahome.com.au/house-sitting-positions-australia.2,4-$offset?sort=-sort_date"
                }
                
                Log.d("MindaHomeScraper", "Requesting URL: $url")

                val response = client.get(url) {
                    commonHeaders()
                }

                val html = response.bodyAsText()
                val doc = Jsoup.parse(html)
                val listings = doc.select(".listing[data-listing-id]")
                
                Log.d("MindaHomeScraper", "Found ${listings.size} listings on page $page")

                if (listings.isEmpty()) {
                    Log.d("MindaHomeScraper", "No more listings found. Stopping.")
                    break
                }

                for (listing in listings) {
                    val job = parseListing(listing)
                    if (job != null) {
                        continueScraping = onJobScraped(job)
                        if (!continueScraping) {
                            Log.d("MindaHomeScraper", "Stop signaled by callback (likely job already exists)")
                            break
                        }
                    }
                }

                if (!continueScraping) break
                
                // Check for next page link to verify we can continue
                val hasNextPage = doc.select(".pagination a.next, .pagination a:contains(Next)").isNotEmpty() || 
                                 doc.select(".pagination a[href*='page=${page + 1}']").isNotEmpty()
                
                if (!hasNextPage && page > 1) { // Basic check, might need adjustment
                     // Log.d("MindaHomeScraper", "No next page link found. Stopping.")
                     // break
                }

                page++
                
                // Robots.txt compliance: Crawl-delay: 10
                Log.d("MindaHomeScraper", "Waiting 10 seconds before next page request...")
                delay(10000)
            }
            Log.d("MindaHomeScraper", "Scrape process completed successfully")
        } catch (e: Exception) {
            Log.e("MindaHomeScraper", "Scraping failed", e)
        }
    }

    private fun parseListing(element: Element): HouseSitJob? {
        return try {
            val id = element.attr("data-listing-id")
            if (id.isEmpty()) return null

            val headingLink = element.select(".listing-heading h3 a")
            val locationText = headingLink.text().trim() // e.g. "Cooroy, Queensland"
            val locationParts = locationText.split(",").map { it.trim() }
            val suburb = locationParts.getOrNull(0) ?: "Unknown"
            val rawState = locationParts.getOrNull(1) ?: "Unknown"
            
            val state = when (rawState.lowercase()) {
                "queensland" -> "QLD"
                "new south wales" -> "NSW"
                "victoria" -> "VIC"
                "south australia" -> "SA"
                "western australia" -> "WA"
                "tasmania" -> "TAS"
                "northern territory" -> "NT"
                "australian capital territory" -> "ACT"
                else -> rawState
            }

            val relativeUrl = headingLink.attr("href")
            val listingUrl = if (relativeUrl.startsWith("http")) relativeUrl else "$baseUrl$relativeUrl"

            val imageUrl = element.select(".listing-image img").attr("src").let {
                if (it.startsWith("http")) it else "$baseUrl$it"
            }

            val description = element.select(".listing-desc").text().trim().removeSuffix("Read more").trim()

            // Dates: <span>16 Jun 2026 <i></i> 23 Jun 2026</span>
            val dateSpan = element.select(".list-bookings span")
            val dateText = dateSpan.text().trim() // "16 Jun 2026 23 Jun 2026" or similar
            
            // Sometimes it's separated by a space or other characters. 
            // Let's try to extract two dates.
            val dates = Regex("""\d{1,2}\s+[A-Za-z]{3}\s+\d{4}""").findAll(dateText).map { it.value }.toList()
            
            val startDate = dates.getOrNull(0)?.let { parseDate(it) } ?: System.currentTimeMillis()
            val endDate = dates.getOrNull(1)?.let { parseDate(it) } ?: (startDate + 86400000)

            // Pets: <div class="listing-pet-care"><i class="icon-petcare"></i>Pet care for 1 dog. Sitters pets may be welcome</div>
            val petText = element.select(".listing-pet-care").text().trim()
            val animals = if (petText.isNotEmpty()) {
                val cleaned = petText
                    .replace("Pet care for ", "", ignoreCase = true)
                    .replace(Regex("Sitters pets.*", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .removeSuffix(".")
                    .trim()
                if (cleaned.isNotEmpty()) listOf(cleaned) else emptyList()
            } else emptyList()

            HouseSitJob(
                id = id,
                suburb = suburb,
                state = state,
                imageUrl = imageUrl,
                description = description,
                animals = animals,
                latitude = 0.0, // Will be geocoded by repository
                longitude = 0.0,
                startDate = startDate,
                endDate = endDate,
                listingUrl = listingUrl,
                locationDescriptor = locationText,
                source = "Mindahome"
            )
        } catch (e: Exception) {
            Log.e("MindaHomeScraper", "Error parsing listing", e)
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
