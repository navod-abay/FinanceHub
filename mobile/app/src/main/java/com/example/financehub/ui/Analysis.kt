package com.example.financehub.ui

import ExpenseTimeChart
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.financehub.ui.components.NavBar
import java.text.NumberFormat
import java.time.LocalDate
import java.util.*
import com.example.financehub.viewmodel.AnalyticsViewModel
import kotlin.collections.component1
import kotlin.collections.component2
import com.example.financehub.R
import com.example.financehub.data.repository.TimeAggregation


enum class AnalysisType(
    @StringRes val title: Int
) {
    INTRA_TIME_PERIOD(
        title = R.string.intra_time_period
    ),
    INTER_TIME_PERIOD(
        title = R.string.inter_time_period
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseAnalyticsScreen(
    navController: NavController,
    viewModel: AnalyticsViewModel
) {

    var topMenuExpanded by remember { mutableStateOf(false) }
    var analysisType by remember { mutableStateOf(AnalysisType.INTRA_TIME_PERIOD) }

    // Intra Analysis ViewModel States
    val selectedTags by viewModel.selectedTags.collectAsState()
    val currentDateRangePreset by viewModel.currentDateRangePreset.collectAsState()

    // Intra Analysis UI States
    var showDateFilterDialog by remember { mutableStateOf(false) }
    var showTagFilterDialog by remember { mutableStateOf(false) }


    // Inter Analysis UI States
    var showTimePeriodDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(analysisType.title)) },
                actions = {
                    IconButton(onClick = { topMenuExpanded = true }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = topMenuExpanded,
                        onDismissRequest = { topMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Inter Time Period Analysis") },
                            onClick = { analysisType = AnalysisType.INTER_TIME_PERIOD }
                        )
                        DropdownMenuItem(
                            text = { Text("Intra Time Period Analysis") },
                            onClick = { analysisType = AnalysisType.INTRA_TIME_PERIOD }
                        )
                    }
                    IconButton(onClick = { showDateFilterDialog = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Date Filter")
                    }
                    when (analysisType) {
                        AnalysisType.INTRA_TIME_PERIOD -> {
                            // Filter icon for date filter

                            // Tags filter icon
                            IconButton(onClick = { showTagFilterDialog = true }) {
                                Icon(Icons.Default.Label, contentDescription = "Tag Filter")
                            }
                        }

                        AnalysisType.INTER_TIME_PERIOD -> {
                            IconButton(onClick = { showTimePeriodDialog = true }) {
                                Icon(
                                    Icons.Default.CalendarViewMonth,
                                    contentDescription = "Time Period Filter"
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = { NavBar(navController) }
    ) { padding ->
        when (analysisType) {
            AnalysisType.INTRA_TIME_PERIOD -> {
                IntraAnalysisScreen(selectedTags, padding, viewModel)
            }

            AnalysisType.INTER_TIME_PERIOD -> {
                InterAnalysisScreen(viewModel, padding)
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
            selectedTags = selectedTags,
            onTagToggle = { viewModel.toggleTag(it) },
            onClearAll = { viewModel.clearSelectedTags() },
            onSelectAll = { viewModel.selectAllTags() },
            onDismiss = { showTagFilterDialog = false },
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterAnalysisScreen(viewModel: AnalyticsViewModel, padding: PaddingValues) {
    var timeWindowExpanded by remember { mutableStateOf(false) }
    val selectedTimeWindow by viewModel.selectedTimePeriod.collectAsState()
    val timeSeriesData by viewModel.timeSeriesData.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InterAnalysisSummaryCard(
            totalBuckets = timeSeriesData.size,
            numberOfExpenses = timeSeriesData.sumOf { it.amount }
        )
        ExposedDropdownMenuBox(
            expanded = timeWindowExpanded,
            onExpandedChange = { timeWindowExpanded = !timeWindowExpanded }
        ) {
            OutlinedTextField(
                value = selectedTimeWindow.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeWindowExpanded)
                },
                modifier = Modifier.menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = timeWindowExpanded,
                onDismissRequest = { timeWindowExpanded = false }
            ) {
                TimeAggregation.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name) },
                        onClick = {
                            viewModel.setSelectedTimePeriod(option)
                            timeWindowExpanded = false
                        }
                    )
                }
            }
        }

        ExpenseTimeChart(
            timeData = timeSeriesData,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
fun IntraAnalysisScreen(
    selectedTags: List<String>,
    padding: PaddingValues,
    viewModel: AnalyticsViewModel
) {
    val expenseAnalytics by viewModel.expensesAnalytics.collectAsState()
    val expensesByTag by viewModel.expensesByTag.collectAsState()
    val dateRangeDisplay by viewModel.dateRangeDisplay.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    var showAllTags by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
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
                totalAmount = currencyFormatter.format(expenseAnalytics.totalAmount),
                monthlyAverage = currencyFormatter.format(expenseAnalytics.monthlyAverage),
                expenseCount = expenseAnalytics.expensesList.size
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
            val sortedExpensesWithTags = expensesByTag.entries.sortedByDescending { it.value }
            if (showAllTags) {
                items(sortedExpensesWithTags) { (tag, amount) ->
                    TagExpenseItem(
                        tagName = tag,
                        amount = currencyFormatter.format(amount),
                        percentage = if (expenseAnalytics.totalAmount > 0) (amount.toFloat() / expenseAnalytics.totalAmount) * 100 else 0f
                    )
                }
                item {
                    TextButton(
                        onClick = { showAllTags = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show Less")
                    }
                }
            } else {
                val topTags =
                    if (sortedExpensesWithTags.size > 7) sortedExpensesWithTags.take(7) else sortedExpensesWithTags
                items(topTags) { (tag, amount) ->
                    TagExpenseItem(
                        tagName = tag,
                        amount = currencyFormatter.format(amount),
                        percentage = if (expenseAnalytics.totalAmount > 0) (amount.toFloat() / expenseAnalytics.totalAmount) * 100 else 0f
                    )
                }
                item {
                    TextButton(
                        onClick = { showAllTags = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show More")
                    }
                }
            }
        }

        // Placeholder for future graph
        if (expensesByTag.isNotEmpty()) {
            item {
                ExpensePieChart(
                    tagAmounts = expensesByTag,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                ExpenseTimeChart(
                    timeData = viewModel.timeSeriesData.collectAsState().value,
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

    if (showCustomDatePicker) {
        DateRangePickerModal(
            onDateRangeSelected = { range ->
                val start = range.first?.let {
                    java.time.Instant.ofEpochMilli(it)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                } ?: LocalDate.now().withDayOfMonth(1)
                val end = range.second?.let {
                    java.time.Instant.ofEpochMilli(it)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                } ?: LocalDate.now()
                onCustomDateSelected(start, end)
                showCustomDatePicker = false
            },
            onDismiss = { showCustomDatePicker = false }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Select Date Range") },
            text = {
                Column {
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
            },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }
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
    selectedTags: List<String>,
    onTagToggle: (String) -> Unit,
    onClearAll: () -> Unit,
    onSelectAll: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: AnalyticsViewModel
) {
    val matchingTags by viewModel.matchingTags.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by Tags") },
        text = {
            val tagSearchText by viewModel.tagSearchText.collectAsState()
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
                OutlinedTextField(
                    value = tagSearchText,
                    onValueChange = { viewModel.updateTagSearchText(it) },
                    label = { Text("Search Tag") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true
                )
                if (matchingTags.isEmpty()) {
                    Text("No tags available")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(matchingTags.toList()) { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedTags.contains(tag.tag),
                                    onCheckedChange = { onTagToggle(tag.tag) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tag.tag,
                                    modifier = Modifier.clickable { onTagToggle(tag.tag) }
                                )
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerModal(
    onDateRangeSelected: (Pair<Long?, Long?>) -> Unit,
    onDismiss: () -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDateRangeSelected(
                        Pair(
                            dateRangePickerState.selectedStartDateMillis,
                            dateRangePickerState.selectedEndDateMillis
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = {
                Text(
                    text = "Select date range"
                )
            },
            showModeToggle = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .padding(16.dp)
        )
    }
}

@Composable
fun InterAnalysisSummaryCard(totalBuckets: Int, numberOfExpenses: Int) {
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
                    Text("Number of Buckets", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$totalBuckets", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Number of Expenses", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$numberOfExpenses", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Monthly Average", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("0", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }
    }
}
