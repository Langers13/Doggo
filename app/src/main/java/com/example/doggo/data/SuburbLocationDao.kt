package com.example.doggo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SuburbLocationDao {
    @Query("SELECT * FROM suburb_locations WHERE id = :id")
    suspend fun getLocation(id: String): SuburbLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: SuburbLocation)
}
