package com.example.doggo.scraper

import com.example.doggo.data.HouseSitJob
import org.jsoup.nodes.Element
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class JobParser {
    
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)

    fun parseJobElement(listing: Element, externalLat: Double?, externalLng: Double?, externalId: String?, source: String): HouseSitJob? {
        return try {
            // 1. Job ID and URL Extraction
            val linkElement = listing.select("h3 a")
            val relativeUrl = linkElement.attr("href")
            val listingUrl = if (relativeUrl.startsWith("http")) relativeUrl else "https://www.aussiehousesitters.com.au$relativeUrl"
            val favButton = listing.select("button.favourites-button").attr("onclick")
            
            // Priority for ID: 1. Passed in (from anchor tag), 2. URL regex, 3. Button script regex
            val id = externalId ?: 
                     Regex("/(\\d+)/").find(relativeUrl)?.groupValues?.getOrNull(1) ?:
                     Regex("'(\\d+)'").find(favButton)?.groupValues?.getOrNull(1) ?:
                     ""
            
            if (id.isEmpty()) {
                Log.w("JobParser", "ID extraction failed. Link: $relativeUrl, FavButton: $favButton")
            } else {
                Log.d("JobParser", "Parsed Job ID: $id for ${linkElement.first()?.ownText()}")
            }

            // 2. Location
            val suburb = linkElement.first()?.ownText()?.trim() ?: "Unknown"
            val locationDescriptor = listing.select("h3 a span").text().trim()
            val state = locationDescriptor.split(" ").firstOrNull()?.trim() ?: "Unknown"

            // 3. Image
            var imageUrl = ""
            val photoLink = listing.select(".photo-link")
            val style = photoLink.attr("style")
            
            // Try background-image first
            imageUrl = Regex("url\\((.*?)\\)").find(style)?.groupValues?.getOrNull(1)?.removeSurrounding("'")?.removeSurrounding("\"") ?: ""
            
            // Fallback to <img> tag inside photo-link
            if (imageUrl.isEmpty()) {
                imageUrl = photoLink.select("img").attr("src").ifEmpty { 
                    photoLink.select("img").attr("data-src") 
                }
            }

            // Fallback to any <img> in the listing
            if (imageUrl.isEmpty()) {
                imageUrl = listing.select("img").firstOrNull { it.attr("src").contains("houses") }?.attr("src") ?: ""
            }

            imageUrl = imageUrl.let {
                when {
                    it.isEmpty() -> ""
                    it.startsWith("http") -> it
                    it.startsWith("//") -> "https:$it"
                    it.startsWith("/") -> "https://www.aussiehousesitters.com.au$it"
                    else -> "https://www.aussiehousesitters.com.au/$it"
                }
            }
            Log.d("JobParser", "Captured Image URL: '$imageUrl'")
            
            // 4. Description
            val tag = listing.select(".listing-tag").text().trim()
            val intro = listing.select(".listing-intro").text().trim().removeSuffix(" View")
            val description = if (tag.isNotEmpty()) "$tag - $intro" else intro
            
            // 5. Dates
            val dateText = listing.select("ul.icon-list li").firstOrNull { it.select(".icon").text().contains("date") }?.text()?.replace("date_range", "")?.trim() 
                           ?: listing.select("ul.icon-list li").firstOrNull()?.text() ?: ""
            
            val dateParts = dateText.split(" - ")
            val startDate = dateParts.getOrNull(0)?.let { parseDate(it) } ?: System.currentTimeMillis()
            val endDate = dateParts.getOrNull(1)?.let { parseDate(it) } ?: (startDate + 86400000)

            // 6. Pets
            val animals = listing.select("ul.listing-pets li").map { 
                val type = it.select("img").attr("alt")
                val count = it.select("span").text()
                if (count.isNotEmpty()) "$count $type" else type
            }.filter { it.isNotBlank() }

            HouseSitJob(
                id = id,
                suburb = suburb,
                state = state,
                imageUrl = imageUrl,
                description = description,
                animals = animals,
                latitude = externalLat ?: 0.0,
                longitude = externalLng ?: 0.0,
                startDate = startDate,
                endDate = endDate,
                listingUrl = listingUrl,
                locationDescriptor = locationDescriptor,
                source = source
            )
        } catch (e: Exception) {
            Log.e("JobParser", "Error parsing element", e)
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
