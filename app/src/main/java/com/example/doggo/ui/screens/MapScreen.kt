package com.example.doggo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Close
import com.example.doggo.viewmodel.DoggoViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

import com.example.doggo.data.HouseSitJob
import com.google.maps.android.compose.clustering.Clustering
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MapScreen(
    viewModel: DoggoViewModel,
    onJobClick: (HouseSitJob) -> Unit,
    modifier: Modifier = Modifier
) {
    val jobs by viewModel.jobs.collectAsState()
    var selectedJob by remember { mutableStateOf<HouseSitJob?>(null) }
    
    // Default to Sydney area
    val sydney = LatLng(-33.8688, 151.2093)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(sydney, 10f)
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = true)
        ) {
            Clustering(
                items = jobs.filter { it.latitude != 0.0 && it.longitude != 0.0 },
                onClusterItemClick = {
                    selectedJob = it
                    false
                }
            )
        }

        selectedJob?.let { job ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = job.suburb, style = MaterialTheme.typography.titleLarge)
                            val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                            Text(
                                text = "${dateFormat.format(Date(job.startDate))} - ${dateFormat.format(Date(job.endDate))}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { selectedJob = null }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onJobClick(job) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Details")
                    }
                }
            }
        }
    }
}
