package com.example.doggo.repository

import com.example.doggo.data.HouseSitJob
import com.example.doggo.data.HouseSitJobDao
import com.example.doggo.scraper.Scraper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class HouseSitRepository(
    private val dao: HouseSitJobDao,
    private val scraper: Scraper
) {
    val allActiveJobs: Flow<List<HouseSitJob>> = dao.getAllActiveJobs()
    val favoriteJobs: Flow<List<HouseSitJob>> = dao.getFavoriteJobs()
    val archivedJobs: Flow<List<HouseSitJob>> = dao.getArchivedJobs()

    suspend fun refreshJobs() {
        withContext(Dispatchers.IO) {
            // 1. Purge expired jobs
            dao.purgeExpiredJobs(System.currentTimeMillis())

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
                    Log.d("HouseSitRepository", "Inserting new job: ${job.id} (${job.suburb})")
                    dao.insertAll(listOf(job))
                    true // Continue
                }
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
