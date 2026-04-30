package com.example.doggo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.doggo.viewmodel.DoggoViewModel

import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue

import com.example.doggo.data.HouseSitJob
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
fun FilterRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ListScreen(
    viewModel: DoggoViewModel,
    onJobClick: (HouseSitJob) -> Unit,
    modifier: Modifier = Modifier
) {
    val jobs by viewModel.jobs.collectAsState()
    val availableSources by viewModel.sources.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val lastScrapeCount by viewModel.lastScrapeCount.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(lastScrapeCount) {
        lastScrapeCount?.let { count ->
            snackbarHostState.showSnackbar("Added $count new jobs")
        }
    }
    
    var showFilters by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setFilter(filterState.copy(
                        startDate = dateRangePickerState.selectedStartDateMillis,
                        endDate = dateRangePickerState.selectedEndDateMillis
                    ))
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text("Select Date Range", modifier = Modifier.padding(16.dp)) },
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
            ) {
                Text("Filters", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                FilterSection("Options") {
                    FilterRow("Only Favorites", filterState.onlyFavorites) {
                        viewModel.setFilter(filterState.copy(onlyFavorites = it))
                    }
                    FilterRow("Include Archived", filterState.includeArchived) {
                        viewModel.setFilter(filterState.copy(includeArchived = it))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                FilterSection("States") {
                    val states = listOf("NSW", "QLD")
                    Row {
                        states.forEach { state ->
                            FilterChip(
                                selected = filterState.selectedStates.contains(state),
                                onClick = {
                                    val newStates = if (filterState.selectedStates.contains(state)) {
                                        filterState.selectedStates - state
                                    } else {
                                        filterState.selectedStates + state
                                    }
                                    viewModel.setFilter(filterState.copy(selectedStates = newStates))
                                },
                                label = { Text(state) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }

                if (availableSources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    FilterSection("Sources") {
                        Row {
                            availableSources.forEach { source ->
                                FilterChip(
                                    selected = filterState.selectedSources.contains(source),
                                    onClick = {
                                        val newSources = if (filterState.selectedSources.contains(source)) {
                                            filterState.selectedSources - source
                                        } else {
                                            filterState.selectedSources + source
                                        }
                                        viewModel.setFilter(filterState.copy(selectedSources = newSources))
                                    },
                                    label = { Text(source) },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                FilterSection("Date Range") {
                    Button(onClick = { showDatePicker = true }) {
                        Text(if (filterState.startDate != null && filterState.endDate != null) "Change Range" else "Select Range")
                    }
                    if (filterState.startDate != null) {
                        TextButton(onClick = {
                            viewModel.setFilter(filterState.copy(startDate = null, endDate = null))
                        }) {
                            Text("Clear Dates")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Doggo Listings") },
                actions = {
                    IconButton(onClick = { viewModel.clearAllData() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Purge")
                    }
                    IconButton(onClick = { viewModel.refresh() }, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        if (filterState.selectedStates.isNotEmpty() || filterState.onlyFavorites || filterState.startDate != null || filterState.selectedSources.isNotEmpty()) {
                            Badge(modifier = Modifier.size(8.dp))
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (jobs.isEmpty() && !isRefreshing) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No jobs found.", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Search for Jobs")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(jobs) { job ->
                    JobCard(
                        job = job,
                        onFavoriteToggle = { viewModel.toggleFavorite(job) },
                        onArchive = { viewModel.archiveJob(job) },
                        onClick = { 
                            onJobClick(job)
                        }
                    )
                }
            }
        }
    }
}
