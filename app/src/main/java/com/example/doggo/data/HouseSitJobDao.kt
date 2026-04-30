package com.example.doggo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseSitJobDao {
    @Query("SELECT EXISTS(SELECT 1 FROM house_sit_jobs WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(jobs: List<HouseSitJob>)

    @Query("SELECT * FROM house_sit_jobs WHERE isArchived = 0 ORDER BY startDate ASC")
    fun getAllActiveJobs(): Flow<List<HouseSitJob>>

    @Query("SELECT * FROM house_sit_jobs WHERE isFavorited = 1 AND isArchived = 0")
    fun getFavoriteJobs(): Flow<List<HouseSitJob>>

    @Query("SELECT * FROM house_sit_jobs WHERE isArchived = 1")
    fun getArchivedJobs(): Flow<List<HouseSitJob>>

    @Update
    suspend fun updateJob(job: HouseSitJob)

    @Query("UPDATE house_sit_jobs SET isFavorited = :isFavorited WHERE id = :id")
    suspend fun updateFavoriteStatus(id: String, isFavorited: Boolean)

    @Query("UPDATE house_sit_jobs SET isArchived = :isArchived WHERE id = :id")
    suspend fun updateArchivedStatus(id: String, isArchived: Boolean)

    @Query("DELETE FROM house_sit_jobs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM house_sit_jobs WHERE startDate < :currentTime")
    suspend fun purgeExpiredJobs(currentTime: Long)

    @Query("DELETE FROM house_sit_jobs")
    suspend fun deleteAll()

    @Query("SELECT * FROM house_sit_jobs WHERE isArchived = 0 AND startDate >= :startRange AND (endDate <= :endRange OR :endRange = 0)")
    fun getJobsInRange(startRange: Long, endRange: Long): Flow<List<HouseSitJob>>
}
