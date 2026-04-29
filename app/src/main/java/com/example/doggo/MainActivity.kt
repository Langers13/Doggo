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
import com.example.doggo.network.GeocodingService
import com.example.doggo.scraper.AHSScraper
import com.example.doggo.scraper.JobParser
import com.example.doggo.ui.screens.ListScreen
import com.example.doggo.ui.screens.MapScreen
import com.example.doggo.ui.screens.WebViewScreen
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
    data class WebView(val url: String, val title: String) : DoggoNavKey
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = AppDatabase.getDatabase(this)
        val jobParser = JobParser()
        val scraper = AHSScraper(jobParser)
        val geocodingService = GeocodingService()
        val repository = HouseSitRepository(
            database.houseSitJobDao(),
            database.suburbLocationDao(),
            scraper,
            geocodingService
        )
        
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
                
                // Initialize the backstack as a mutable list of keys
                val backStack = rememberNavBackStack(DoggoNavKey.List as DoggoNavKey)
                val currentKey = backStack.lastOrNull()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentKey is DoggoNavKey.List,
                                onClick = { 
                                    // Reset to List as the root
                                    if (currentKey !is DoggoNavKey.List) {
                                        backStack.clear()
                                        backStack.add(DoggoNavKey.List)
                                    }
                                },
                                icon = { Icon(Icons.Default.List, contentDescription = "List") },
                                label = { Text("List") }
                            )
                            NavigationBarItem(
                                selected = currentKey is DoggoNavKey.Map,
                                onClick = { 
                                    // Reset to Map as the root
                                    if (currentKey !is DoggoNavKey.Map) {
                                        backStack.clear()
                                        backStack.add(DoggoNavKey.Map)
                                    }
                                },
                                icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                                label = { Text("Map") }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavDisplay(
                        backStack = backStack,
                        modifier = Modifier.padding(innerPadding),
                        entryProvider = { key ->
                            NavEntry(
                                key = key,
                                content = {
                                    when (key) {
                                    is DoggoNavKey.List -> ListScreen(viewModel, onJobClick = { job ->
                                        backStack.add(DoggoNavKey.WebView(job.listingUrl, job.suburb))
                                    })
                                    is DoggoNavKey.Map -> MapScreen(viewModel, onJobClick = { job ->
                                        backStack.add(DoggoNavKey.WebView(job.listingUrl, job.suburb))
                                    })
                                    is DoggoNavKey.WebView -> {
                                        WebViewScreen(
                                            url = key.url,
                                            title = key.title,
                                            onBack = { if (backStack.size > 1) backStack.removeLast() }
                                        )
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
