package com.example.doggo.repository

import com.example.doggo.data.HouseSitJob
import com.example.doggo.data.HouseSitJobDao
import com.example.doggo.data.SuburbLocation
import com.example.doggo.data.SuburbLocationDao
import com.example.doggo.network.GeocodingService
import com.example.doggo.scraper.Scraper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class HouseSitRepository(
    private val dao: HouseSitJobDao,
    private val suburbDao: SuburbLocationDao,
    private val scraper: Scraper,
    private val geocodingService: GeocodingService
) {
    val allActiveJobs: Flow<List<HouseSitJob>> = dao.getAllActiveJobs()
    val favoriteJobs: Flow<List<HouseSitJob>> = dao.getFavoriteJobs()
    val archivedJobs: Flow<List<HouseSitJob>> = dao.getArchivedJobs()

    suspend fun clearAllJobs() {
        withContext(Dispatchers.IO) {
            dao.deleteAll()
        }
    }

    suspend fun refreshJobs(): Int {
        return withContext(Dispatchers.IO) {
            // 1. Purge expired jobs
            dao.purgeExpiredJobs(System.currentTimeMillis())

            var count = 0
            // 2. Start scraping
            scraper.scrape { job ->
                if (job.id.isEmpty()) {
                    Log.w("HouseSitRepository", "Skipping job with empty ID: ${job.suburb}")
                    return@scrape true // Skip this one but continue scraping
                }

                // Check if job already exists in DB
                val alreadyExists = dao.exists(job.id)
                if (alreadyExists) {
                    Log.d("HouseSitRepository", "Job ${job.id} (${job.suburb}) already exists. Stopping scrape.")
                    false // Stop
                } else {
                    // 3. Geocode-on-scrape logic
                    val geocodedJob = if (job.latitude == 0.0 || job.longitude == 0.0) {
                        geocodeJob(job)
                    } else job

                    Log.d("HouseSitRepository", "Inserting new job: ${geocodedJob.id} (${geocodedJob.suburb})")
                    dao.insertAll(listOf(geocodedJob))
                    count++
                    true // Continue
                }
            }
            count
        }
    }

    private suspend fun geocodeJob(job: HouseSitJob): HouseSitJob {
        val cacheId = "${job.suburb.lowercase()},${job.state.lowercase()}"
        val cachedLocation = suburbDao.getLocation(cacheId)

        return if (cachedLocation != null) {
            Log.d("HouseSitRepository", "Cache hit for $cacheId")
            job.copy(latitude = cachedLocation.latitude, longitude = cachedLocation.longitude)
        } else {
            Log.d("HouseSitRepository", "Cache miss for $cacheId, calling API")
            val coords = geocodingService.getCoordinates(job.suburb, job.state)
            if (coords != null) {
                suburbDao.insertLocation(
                    SuburbLocation(
                        id = cacheId,
                        suburb = job.suburb,
                        state = job.state,
                        latitude = coords.first,
                        longitude = coords.second
                    )
                )
                job.copy(latitude = coords.first, longitude = coords.second)
            } else {
                job // Return as is if geocoding fails
            }
        }
    }

    suspend fun updateFavorite(id: String, isFavorited: Boolean) {
        dao.updateFavoriteStatus(id, isFavorited)
    }

    suspend fun updateArchived(id: String, isArchived: Boolean) {
        dao.updateArchivedStatus(id, isArchived)
    }

    fun getFilteredJobs(startDate: Long, endDate: Long): Flow<List<HouseSitJob>> {
        return dao.getJobsInRange(startDate, endDate)
    }
}
