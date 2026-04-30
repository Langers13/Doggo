package com.example.doggo.scraper

import android.util.Log
import com.example.doggo.data.HouseSitJob
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.jsoup.Jsoup
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class AHSScraper(
    private val jobParser: JobParser
) : Scraper {

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

    private fun HttpRequestBuilder.commonHeaders() {
        header("User-Agent", userAgent)
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
        header("Accept-Language", "en-US,en;q=0.9")
        header("Cache-Control", "max-age=0")
        header("Connection", "keep-alive")
        header("Upgrade-Insecure-Requests", "1")
    }

    override suspend fun scrape(onJobScraped: suspend (HouseSitJob) -> Boolean) {
        Log.d("AHSScraper", "Starting scrape process")
        val regions = listOf(2, 3, 5, 6, 7, 8, 9, 10, 47, 48, 49, 50)

        try {
            // Step 1: Establish session
            val homeResponse = client.get("https://www.aussiehousesitters.com.au/") {
                commonHeaders()
            }
            Log.d("AHSScraper", "Home response status: ${homeResponse.status}")

            for (region in regions) {
                var page = 1
                var continueScraping = true
                Log.d("AHSScraper", "Starting scrape for region: $region")

                while (continueScraping) {
                    val url = "https://www.aussiehousesitters.com.au/house-sitting-pet-sitting-jobs/search?region=$region&order=recent&page=$page"
                    Log.d("AHSScraper", "Requesting URL: $url")

                    val listResponse = client.get(url) {
                        commonHeaders()
                        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                        header("Referer", "https://www.aussiehousesitters.com.au/")
                    }
                    
                    val listHtml = listResponse.bodyAsText()
                    val listDoc = Jsoup.parse(listHtml)
                    val listings = listDoc.select(".search-listing")
                    Log.d("AHSScraper", "Found ${listings.size} listings in region $region, page $page")

                    if (listings.isEmpty()) {
                        Log.d("AHSScraper", "No more listings in region $region, page $page")
                        break
                    }

                    for (listing in listings) {
                        var extractedId = listing.previousElementSibling()?.let { prev ->
                            if (prev.tagName() == "a" && prev.attr("name").startsWith("jobad-")) {
                                prev.attr("name").removePrefix("jobad-")
                            } else null
                        }

                        if (extractedId.isNullOrEmpty()) {
                            extractedId = listing.attr("data-id").takeIf { it.isNotEmpty() }
                        }

                        val lat = listing.attr("data-lat").toDoubleOrNull()
                        val lng = listing.attr("data-lng").toDoubleOrNull()

                        val job = jobParser.parseJobElement(listing, lat, lng, extractedId, "AHS")

                        if (job != null) {
                            continueScraping = onJobScraped(job)
                            if (!continueScraping) {
                                Log.d("AHSScraper", "Existing job found or stop signaled")
                                break
                            }
                        }
                    }
                    if (!continueScraping) break
                    page++
                }
            }
            Log.d("AHSScraper", "Scrape process completed successfully")
        } catch (e: Exception) {
            Log.e("AHSScraper", "Scraping failed", e)
        }
    }
}
