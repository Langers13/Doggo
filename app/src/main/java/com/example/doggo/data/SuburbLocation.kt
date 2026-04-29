package com.example.doggo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suburb_locations")
data class SuburbLocation(
    @PrimaryKey val id: String, // format: "suburb,state"
    val suburb: String,
    val state: String,
    val latitude: Double,
    val longitude: Double,
    val dateCached: Long = System.currentTimeMillis()
)
