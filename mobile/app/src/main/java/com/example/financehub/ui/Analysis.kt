package com.example.financehub.ui

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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseAnalyticsScreen(
    navController: NavController,
    viewModel: AnalyticsViewModel
) {

    val selectedTags by viewModel.selectedTags.collectAsState()
    val currentDateRangePreset by viewModel.currentDateRangePreset.collectAsState()
    val availableChartSeries by viewModel.availableChartSeries.collectAsState()
    val chartSeriesSelection by viewModel.chartSeriesSelection.collectAsState()
    // null means "no explicit choice yet", so fall back to the automatic default for display
    val effectiveChartSeries = chartSeriesSelection
        ?: AnalyticsViewModel.defaultSeriesSelection(availableChartSeries)

    var showDateFilterDialog by remember { mutableStateOf(false) }
    var showTagFilterDialog by remember { mutableStateOf(false) }
    var showChartSeriesDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analysis)) },
                actions = {
                    IconButton(onClick = { showDateFilterDialog = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Date Filter")
                    }
                    IconButton(onClick = { showTagFilterDialog = true }) {
                        Icon(Icons.Default.Label, contentDescription = "Tag Filter")
                    }
                }
            )
        },
        bottomBar = { NavBar(navController) }
    ) { padding ->
        AnalysisContent(
            selectedTags = selectedTags,
            padding = padding,
            viewModel = viewModel,
            availableChartSeries = availableChartSeries,
            selectedChartSeries = effectiveChartSeries,
            onSelectSeries = { showChartSeriesDialog = true }
        )
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

    // Chart Series Dialog - picks which lines the time chart draws
    if (showChartSeriesDialog) {
        ChartSeriesDialog(
            availableSeries = availableChartSeries,
            selectedSeries = effectiveChartSeries,
            onToggle = { viewModel.toggleChartSeries(it) },
            onReset = { viewModel.resetChartSeriesSelection() },
            onDismiss = { showChartSeriesDialog = false }
        )
    }
}

@Composable
fun AnalysisContent(
    selectedTags: List<String>,
    padding: PaddingValues,
    viewModel: AnalyticsViewModel,
    availableChartSeries: List<String>,
    selectedChartSeries: Set<String>,
    onSelectSeries: () -> Unit
) {
    val expenseAnalytics by viewModel.expensesAnalytics.collectAsState()
    val expensesByTag by viewModel.expensesByTag.collectAsState()
    val dateRangeDisplay by viewModel.dateRangeDisplay.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val chartSeries by viewModel.chartSeries.collectAsState()
    val aggregation by viewModel.selectedTimePeriod.collectAsState()
    val isCumulative by viewModel.isCumulative.collectAsState()

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
                TagBreakdownCard(
                    sortedTagAmounts = expensesByTag.entries.sortedByDescending { it.value }
                        .map { it.key to it.value },
                    totalAmount = expenseAnalytics.totalAmount,
                    showAll = showAllTags,
                    onToggleShowAll = { showAllTags = !showAllTags },
                    currencyFormatter = currencyFormatter
                )
            }
        }

        if (expensesByTag.isNotEmpty()) {
            item {
                ExpensePieChart(
                    tagAmounts = expensesByTag,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        // The time chart renders its own empty state, so it is not gated on the tag breakdown
        item {
            ExpenseTimeChart(
                series = chartSeries,
                availableSeries = availableChartSeries,
                selectedSeries = selectedChartSeries,
                onToggleSeries = { viewModel.toggleChartSeries(it) },
                aggregation = aggregation,
                onAggregationChange = { viewModel.setSelectedTimePeriod(it) },
                isCumulative = isCumulative,
                onCumulativeChange = { viewModel.setCumulative(it) },
                onSelectSeries = onSelectSeries,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (expensesByTag.isEmpty()) {
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

/** All tag totals in one card; only the top [collapsedCount] are listed until expanded. */
@Composable
fun TagBreakdownCard(
    sortedTagAmounts: List<Pair<String, Int>>,
    totalAmount: Int,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    currencyFormatter: NumberFormat,
    collapsedCount: Int = 7
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Expenses by Tag",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val shown = if (showAll) sortedTagAmounts else sortedTagAmounts.take(collapsedCount)
            shown.forEachIndexed { index, (tag, amount) ->
                if (index > 0) HorizontalDivider()
                TagExpenseItem(
                    tagName = tag,
                    amount = currencyFormatter.format(amount),
                    percentage = if (totalAmount > 0) (amount.toFloat() / totalAmount) * 100 else 0f
                )
            }
            if (sortedTagAmounts.size > collapsedCount) {
                TextButton(
                    onClick = onToggleShowAll,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showAll) "Show Less" else "Show More")
                }
            }
        }
    }
}

@Composable
fun TagExpenseItem(tagName: String, amount: String, percentage: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
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
                            SelectableRow(
                                label = tag.tag,
                                checked = selectedTags.contains(tag.tag),
                                onToggle = { onTagToggle(tag.tag) }
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

/** A checkbox + label row shared by the tag filter and chart series dialogs. */
@Composable
private fun SelectableRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            modifier = Modifier.clickable { onToggle() }
        )
    }
}

/**
 * Picks which lines the expense time chart draws. Independent of the tag filter, which decides
 * which expenses are analysed in the first place. Total and Other have their own checkboxes;
 * tags are found through the search box and shown as removable chips once selected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartSeriesDialog(
    availableSeries: List<String>,
    selectedSeries: Set<String>,
    onToggle: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val tagSeries = availableSeries.filter {
        it != AnalyticsViewModel.TOTAL_SERIES && it != AnalyticsViewModel.OTHER_SERIES
    }
    val selectedTagSeries = tagSeries.filter { selectedSeries.contains(it) }
    var query by remember { mutableStateOf("") }
    val suggestions = if (query.isBlank()) emptyList() else tagSeries.filter {
        !selectedSeries.contains(it) && it.contains(query.trim(), ignoreCase = true)
    }.take(6)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chart Series") },
        text = {
            Column {
                Text(
                    "Choose what the trend chart plots.",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SelectableRow(
                    label = "Total",
                    checked = selectedSeries.contains(AnalyticsViewModel.TOTAL_SERIES),
                    onToggle = { onToggle(AnalyticsViewModel.TOTAL_SERIES) }
                )
                SelectableRow(
                    label = "Other (unselected tags combined)",
                    checked = selectedSeries.contains(AnalyticsViewModel.OTHER_SERIES),
                    onToggle = { onToggle(AnalyticsViewModel.OTHER_SERIES) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (tagSeries.isEmpty()) {
                    Text("No tags in the selected period")
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search tags") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true
                    )
                    suggestions.forEach { tag ->
                        Text(
                            text = tag,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onToggle(tag)
                                    query = ""
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        )
                    }
                    if (query.isNotBlank() && suggestions.isEmpty()) {
                        Text(
                            "No matching tags",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    if (selectedTagSeries.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedTagSeries.forEach { tag ->
                                InputChip(
                                    selected = true,
                                    onClick = { onToggle(tag) },
                                    label = { Text(tag) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove $tag",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("Reset")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
