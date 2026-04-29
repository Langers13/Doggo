package com.example.doggo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.doggo.data.AppDatabase
import com.example.doggo.repository.HouseSitRepository
import com.example.doggo.scraper.AHSScraper
import com.example.doggo.scraper.JobParser
import com.example.doggo.ui.screens.JobDetailScreen
import com.example.doggo.ui.screens.ListScreen
import com.example.doggo.ui.screens.MapScreen
import com.example.doggo.ui.theme.DoggoTheme
import com.example.doggo.viewmodel.DoggoViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope

@Serializable
sealed interface DoggoNavKey : NavKey {
    @Serializable
    data object List : DoggoNavKey
    @Serializable
    data object Map : DoggoNavKey
    @Serializable
    data class Detail(val jobId: String) : DoggoNavKey
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = AppDatabase.getDatabase(this)
        val jobParser = JobParser()
        val scraper = AHSScraper(jobParser)
        val repository = HouseSitRepository(database.houseSitJobDao(), scraper)
        
        // One-time cleanup for any bad data during development
        GlobalScope.launch(Dispatchers.IO) {
            database.houseSitJobDao().deleteById("")
        }
        
        val viewModelFactory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DoggoViewModel(repository) as T
            }
        }

        setContent {
            DoggoTheme {
                val viewModel: DoggoViewModel = viewModel(factory = viewModelFactory)
                val currentNavKey = remember { mutableStateOf<DoggoNavKey>(DoggoNavKey.List) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentNavKey.value is DoggoNavKey.List,
                                onClick = { currentNavKey.value = DoggoNavKey.List },
                                icon = { Icon(Icons.Default.List, contentDescription = "List") },
                                label = { Text("List") }
                            )
                            NavigationBarItem(
                                selected = currentNavKey.value is DoggoNavKey.Map,
                                onClick = { currentNavKey.value = DoggoNavKey.Map },
                                icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                                label = { Text("Map") }
                            )
                        }
                    }
                ) { innerPadding ->
                    val backStack = rememberNavBackStack(currentNavKey.value)
                    NavDisplay(
                        backStack = backStack,
                        modifier = Modifier.padding(innerPadding),
                        entryProvider = { key ->
                            NavEntry(
                                key = key,
                                content = {
                                    when (key) {
                                    is DoggoNavKey.List -> ListScreen(viewModel, onJobClick = { job ->
                                        // Internal navigation is handled inside ListScreen, 
                                        // but we could also add to backStack if we want a separate detail screen
                                    })
                                    is DoggoNavKey.Map -> MapScreen(viewModel, onJobClick = { job ->
                                        // On map, we might want to navigate to the detail screen explicitly
                                        backStack.add(DoggoNavKey.Detail(job.id))
                                    })
                                        is DoggoNavKey.Detail -> {
                                            val job = viewModel.jobs.collectAsState().value.find { it.id == key.jobId }
                                            if (job != null) {
                                                JobDetailScreen(
                                                    job = job,
                                                    onFavoriteToggle = { viewModel.toggleFavorite(job) },
                                                    onArchive = { viewModel.archiveJob(job) },
                                                    onBack = { if (backStack.size > 1) backStack.removeLast() }
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
