package com.example.doggo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

@Entity(tableName = "house_sit_jobs")
data class HouseSitJob(
    @PrimaryKey val id: String,
    val suburb: String,
    val state: String,
    val imageUrl: String,
    val description: String,
    val animals: List<String>,
    val latitude: Double,
    val longitude: Double,
    val startDate: Long,
    val endDate: Long,
    val listingUrl: String = "",
    val source: String = "",
    val isFavorited: Boolean = false,
    val isArchived: Boolean = false,
    val dateScraped: Long = System.currentTimeMillis()
) : ClusterItem {
    override fun getPosition(): LatLng = LatLng(latitude, longitude)
    override fun getTitle(): String = suburb
    override fun getSnippet(): String = animals.joinToString(", ")
    override fun getZIndex(): Float? = 0f
}
