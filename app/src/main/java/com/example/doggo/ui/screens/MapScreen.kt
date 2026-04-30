package com.example.doggo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.doggo.data.HouseSitJob
import com.example.doggo.viewmodel.DoggoViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.maps.android.compose.clustering.Clustering
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalLocale

@Composable
fun MapScreen(
    viewModel: DoggoViewModel,
    onJobClick: (HouseSitJob) -> Unit,
    modifier: Modifier = Modifier
) {
    val jobs by viewModel.jobs.collectAsState()
    val geocodedJobs = remember(jobs) { jobs.filter { it.latitude != 0.0 && it.longitude != 0.0 } }
    var selectedJob by remember { mutableStateOf<HouseSitJob?>(null) }
    
    // Default to a broad view of Australia if no jobs, otherwise center on the first job
    val initialCenter = geocodedJobs.firstOrNull()?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(-25.2744, 133.7751)
    val initialZoom = if (geocodedJobs.isEmpty()) 4f else 8f
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialCenter, initialZoom)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (geocodedJobs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No jobs with locations found.", style = MaterialTheme.typography.bodyLarge)
                    Text("Try refreshing the list to geocode them.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = true, mapToolbarEnabled = false)
        ) {
            Clustering(
                items = geocodedJobs,
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
                elevation = CardDefaults.cardElevation(8.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = job.suburb,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = job.state,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            val dateFormat = SimpleDateFormat("dd MMM", LocalLocale.current.platformLocale)
                            Text(
                                text = "${dateFormat.format(Date(job.startDate))} - ${dateFormat.format(Date(job.endDate))}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        IconButton(onClick = { selectedJob = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Pets, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = job.animals.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { onJobClick(job) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Open Listing")
                    }
                }
            }
        }
    }
}
