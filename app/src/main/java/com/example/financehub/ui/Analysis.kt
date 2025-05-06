package com.example.financehub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.financehub.ui.components.NavBar
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import com.example.financehub.viewmodel.AnalyticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseAnalyticsScreen(
    navController: NavController,
    viewModel: AnalyticsViewModel
) {
    val startDate by viewModel.startDate.collectAsState()
    val endDate by viewModel.endDate.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val filteredExpenses by viewModel.filteredExpenses.collectAsState()
    val totalAmount by viewModel.totalAmount.collectAsState()
    val monthlyAverage by viewModel.monthlyAverage.collectAsState()
    val expensesByTag by viewModel.expensesByTag.collectAsState()
    val dateRangeDisplay by viewModel.dateRangeDisplay.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentDateRangePreset by viewModel.currentDateRangePreset.collectAsState()

    // State for showing filter dialog
    var showDateFilterDialog by remember { mutableStateOf(false) }
    var showTagFilterDialog by remember { mutableStateOf(false) }

    // Format numbers for display
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expense Analytics") },
                actions = {
                    // Filter icon for date filter
                    IconButton(onClick = { showDateFilterDialog = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Date Filter")
                    }
                    // Tags filter icon
                    IconButton(onClick = { showTagFilterDialog = true }) {
                        Icon(Icons.Default.Label, contentDescription = "Tag Filter")
                    }
                }
            )
        },
        bottomBar = { NavBar(navController) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Loading indicator
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Current filter display
            item {
                FilterInfoCard(dateRangeDisplay, selectedTags)
            }

            // Summary card with total and average
            item {
                SummaryCard(
                    totalAmount = currencyFormatter.format(totalAmount ),
                    monthlyAverage = currencyFormatter.format(monthlyAverage),
                    expenseCount = filteredExpenses.size
                )
            }

            // Breakdown by tag
            if (expensesByTag.isNotEmpty()) {
                item {
                    Text(
                        "Expenses by Tag",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(expensesByTag.entries.sortedByDescending { it.value }) { (tag, amount) ->
                    TagExpenseItem(
                        tagName = tag,
                        amount = currencyFormatter.format(amount / 100.0),
                        percentage = if (totalAmount > 0) (amount.toFloat() / totalAmount) * 100 else 0f
                    )
                }
            }

            // Placeholder for future graph
            if (expensesByTag.isNotEmpty()) {
                item {
                    ExpensePieChart(
                        tagAmounts = expensesByTag,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                // Placeholder when no data is available
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.BarChart,
                                    contentDescription = "Charts",
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No data available for visualization")
                            }
                        }
                    }
                }
            }
        }
    }

    // Date Filter Dialog
    if (showDateFilterDialog) {
        DateFilterDialog(
            currentPreset = currentDateRangePreset,
            onPresetSelected = { preset ->
                viewModel.setDateRangePreset(preset)
                showDateFilterDialog = false
            },
            onCustomDateSelected = { start, end ->
                viewModel.setCustomDateRange(start, end)
                showDateFilterDialog = false
            },
            onDismiss = { showDateFilterDialog = false }
        )
    }

    // Tag Filter Dialog
    if (showTagFilterDialog) {
        TagFilterDialog(
            availableTags = availableTags.map { it.tag },
            selectedTags = selectedTags,
            onTagToggle = { viewModel.toggleTag(it) },
            onClearAll = { viewModel.clearSelectedTags() },
            onSelectAll = { viewModel.selectAllTags() },
            onDismiss = { showTagFilterDialog = false }
        )
    }
}

@Composable
fun FilterInfoCard(dateRangeDisplay: String, selectedTags: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = "Date Range",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Period: $dateRangeDisplay",
                    fontWeight = FontWeight.Medium
                )
            }

            if (selectedTags.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Label,
                        contentDescription = "Tags",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "Filtered by tags:",
                            fontWeight = FontWeight.Medium
                        )
                        Text(selectedTags.joinToString(", "))
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(totalAmount: String, monthlyAverage: String, expenseCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Summary",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Expenses", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(totalAmount, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Number of Expenses", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$expenseCount", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Monthly Average", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(monthlyAverage, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun TagExpenseItem(tagName: String, amount: String, percentage: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Label,
                    contentDescription = tagName,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(tagName)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(amount, fontWeight = FontWeight.Bold)
                Text(
                    String.format("%.1f%%", percentage),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFilterDialog(
    currentPreset: AnalyticsViewModel.DateRangePreset,
    onPresetSelected: (AnalyticsViewModel.DateRangePreset) -> Unit,
    onCustomDateSelected: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var showCustomDatePicker by remember { mutableStateOf(false) }
    var tempStartDate by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var tempEndDate by remember { mutableStateOf(LocalDate.now()) }
    var selectingStartDate by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Date Range") },
        text = {
            Column {
                if (showCustomDatePicker) {
                    // Custom date selection UI
                    Text("Select ${if (selectingStartDate) "Start" else "End"} Date")
                    // Here you would add a DatePicker composable
                    // Since Jetpack Compose doesn't have a built-in date picker yet,
                    // you would need to use a third-party library or create your own

                    // For this example, we'll use a placeholder
                    Text(
                        "Start: ${tempStartDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Text(
                        "End: ${tempEndDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { selectingStartDate = true },
                            enabled = !selectingStartDate
                        ) {
                            Text("Edit Start")
                        }
                        Button(
                            onClick = { selectingStartDate = false },
                            enabled = selectingStartDate
                        ) {
                            Text("Edit End")
                        }
                    }
                } else {
                    // Date range presets
                    PresetOption(
                        "Current Month",
                        isSelected = currentPreset == AnalyticsViewModel.DateRangePreset.CURRENT_MONTH,
                        onClick = { onPresetSelected(AnalyticsViewModel.DateRangePreset.CURRENT_MONTH) }
                    )
                    PresetOption(
                        "Previous Month",
                        isSelected = currentPreset == AnalyticsViewModel.DateRangePreset.PREVIOUS_MONTH,
                        onClick = { onPresetSelected(AnalyticsViewModel.DateRangePreset.PREVIOUS_MONTH) }
                    )
                    PresetOption(
                        "Last 3 Months",
                        isSelected = currentPreset == AnalyticsViewModel.DateRangePreset.LAST_3_MONTHS,
                        onClick = { onPresetSelected(AnalyticsViewModel.DateRangePreset.LAST_3_MONTHS) }
                    )
                    PresetOption(
                        "Last 6 Months",
                        isSelected = currentPreset == AnalyticsViewModel.DateRangePreset.LAST_6_MONTHS,
                        onClick = { onPresetSelected(AnalyticsViewModel.DateRangePreset.LAST_6_MONTHS) }
                    )
                    PresetOption(
                        "Current Year",
                        isSelected = currentPreset == AnalyticsViewModel.DateRangePreset.CURRENT_YEAR,
                        onClick = { onPresetSelected(AnalyticsViewModel.DateRangePreset.CURRENT_YEAR) }
                    )
                    PresetOption(
                        "Previous Year",
                        isSelected = currentPreset == AnalyticsViewModel.DateRangePreset.PREVIOUS_YEAR,
                        onClick = { onPresetSelected(AnalyticsViewModel.DateRangePreset.PREVIOUS_YEAR) }
                    )
                    PresetOption(
                        "Custom Range",
                        isSelected = currentPreset == AnalyticsViewModel.DateRangePreset.CUSTOM,
                        onClick = { showCustomDatePicker = true }
                    )
                }
            }
        },
        confirmButton = {
            if (showCustomDatePicker) {
                Button(
                    onClick = {
                        onCustomDateSelected(tempStartDate, tempEndDate)
                    }
                ) {
                    Text("Apply")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (showCustomDatePicker) {
                TextButton(
                    onClick = { showCustomDatePicker = false }
                ) {
                    Text("Back")
                }
            }
        }
    )
}

@Composable
fun PresetOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterDialog(
    availableTags: List<String>,
    selectedTags: List<String>,
    onTagToggle: (String) -> Unit,
    onClearAll: () -> Unit,
    onSelectAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by Tags") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onClearAll) {
                        Text("Clear All")
                    }
                    TextButton(onClick = onSelectAll) {
                        Text("Select All")
                    }
                }

                if (availableTags.isEmpty()) {
                    Text("No tags available")
                } else {
                    availableTags.sorted().forEach { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedTags.contains(tag),
                                onCheckedChange = { onTagToggle(tag) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tag,
                                modifier = Modifier.clickable { onTagToggle(tag) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}