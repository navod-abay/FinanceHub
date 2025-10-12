package com.example.financehub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import com.example.financehub.data.database.ExpenseWithTags
import com.example.financehub.navigation.Screens
import com.example.financehub.ui.components.NavBar
import com.example.financehub.viewmodel.*
import kotlinx.coroutines.flow.map
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Transactions(navController: NavController, viewModel: TransactionsViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }

    val filterParams by viewModel.filterParams.collectAsState()
    val allTags by viewModel.allTags.collectAsState(initial = emptyList())

    val transactionsFlow = viewModel.pagedExpenses
    val filteredTransactionsFlow = remember(searchQuery) {
        transactionsFlow.map { pagingData ->
            pagingData.filter { tx ->
                searchQuery.isBlank() || tx.expense.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val lazyPagingItems = filteredTransactionsFlow.collectAsLazyPagingItems()

    val isEmpty = lazyPagingItems.loadState.refresh is LoadState.NotLoading && lazyPagingItems.itemCount == 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction History") },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
                    }
                }
            )
        },
        bottomBar = { NavBar(navController) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchBar(searchQuery) { searchQuery = it }
            ActiveFiltersChipsRow(
                filterParams = filterParams,
                allTags = allTags,
                onClearDate = { viewModel.clearDateFilter() },
                onRemoveTag = { viewModel.toggleTagId(it) },
                onClearAll = {
                    viewModel.clearDateFilter(); viewModel.clearAllTags()
                }
            )
            if (isEmpty) {
                EmptyState(
                    hasFilters = filterParams.dateMode != DateMode.NONE || filterParams.requiredTagIds.isNotEmpty(),
                    onClearFilters = { viewModel.clearDateFilter(); viewModel.clearAllTags() }
                )
            } else {
                TransactionsList(navController, viewModel, lazyPagingItems)
            }
        }
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            onDismiss = { showFilterSheet = false },
            filterParams = filterParams,
            viewModel = viewModel
        )
    }
}

@Composable
private fun SearchBar(value: String, onChange: (String)->Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search transactions") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotBlank()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, null) }
        },
        singleLine = true
    )
}

@Composable
private fun ActiveFiltersChipsRow(
    filterParams: FilterParams,
    allTags: List<com.example.financehub.data.database.Tags>,
    onClearDate: ()->Unit,
    onRemoveTag: (Int)->Unit,
    onClearAll: ()->Unit
){
    val hasDate = filterParams.dateMode != DateMode.NONE
    val selectedTagIds = filterParams.requiredTagIds
    val hasTags = selectedTagIds.isNotEmpty()
    if(!hasDate && !hasTags) return
    val tagNameById = remember(allTags) { allTags.associateBy({it.tagID},{it.tag}) }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if(hasDate){
                item {
                    InputChip(selected = true, onClick = { onClearDate() }, label = { Text(dateLabel(filterParams)) }, trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) })
                }
            }
            items(selectedTagIds.toList(), key = { it }) { id ->
                val name = tagNameById[id] ?: id.toString()
                InputChip(selected = true, onClick = { onRemoveTag(id) }, label = { Text(name) }, trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) })
            }
            item {
                TextButton(onClick = onClearAll) { Text("Clear All") }
            }
        }
    }
}

private fun dateLabel(p: FilterParams): String = when(p.dateMode){
    DateMode.NONE -> ""
    DateMode.SINGLE_DAY -> p.singleDay?.let { (d,m,y)-> "$d/$m/$y" } ?: "Day"
    DateMode.RANGE -> {
        val s = p.rangeStart; val e = p.rangeEnd
        if(s!=null && e!=null) "${s.first}/${s.second}/${s.third} - ${e.first}/${e.second}/${e.third}" else "Range"
    }
    DateMode.PRESET -> p.preset?.let { presetLabel(it) } ?: "Preset"
}

private fun presetLabel(p: DatePreset) = when(p){
    DatePreset.TODAY -> "Today"
    DatePreset.YESTERDAY -> "Yesterday"
    DatePreset.THIS_WEEK -> "This Week"
    DatePreset.THIS_MONTH -> "This Month"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterBottomSheet(
    onDismiss: ()->Unit,
    filterParams: FilterParams,
    viewModel: TransactionsViewModel
){
    val matchingTags by viewModel.matchingTags.collectAsState(initial = emptyList())
    var tagSearch by remember { mutableStateOf("") }
    var showSingleDayPicker by remember { mutableStateOf(false) }
    var showRangeStartPicker by remember { mutableStateOf(false) }
    var showRangeEndPicker by remember { mutableStateOf(false) }

    LaunchedEffect(tagSearch){ viewModel.updateTagSearch(tagSearch) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Filters", style = MaterialTheme.typography.titleLarge)
            // Date Section
            DateSection(filterParams, onMode = { mode ->
                when(mode){
                    DateMode.NONE -> viewModel.clearDateFilter()
                    DateMode.SINGLE_DAY -> { viewModel.setSingleDay(filterParams.singleDay?.first ?: Calendar.getInstance().get(Calendar.DAY_OF_MONTH), filterParams.singleDay?.second ?: (Calendar.getInstance().get(Calendar.MONTH)+1), filterParams.singleDay?.third ?: Calendar.getInstance().get(Calendar.YEAR)); showSingleDayPicker = true }
                    DateMode.RANGE -> { viewModel.setRange(filterParams.rangeStart, filterParams.rangeEnd); showRangeStartPicker = true }
                    DateMode.PRESET -> { /* handled by presets row */ }
                }
            }, onPreset = { viewModel.selectPreset(it) }, openSingleDay = { showSingleDayPicker = true }, openRangeStart = { showRangeStartPicker = true }, openRangeEnd = { showRangeEndPicker = true })

            // Tag Section
            Text("Tags", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = tagSearch,
                onValueChange = { tagSearch = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search tags") },
                singleLine = true,
                trailingIcon = { if(tagSearch.isNotBlank()) IconButton({ tagSearch = "" }) { Icon(Icons.Default.Close,null) } }
            )
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                matchingTags.forEach { tag ->
                    FilterChip(
                        selected = filterParams.requiredTagIds.contains(tag.tagID),
                        onClick = { viewModel.toggleTagId(tag.tagID) },
                        label = { Text(tag.tag) }
                    )
                }
            }
            if(filterParams.requiredTagIds.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearAllTags() }) { Text("Clear Selected Tags") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    // Date pickers
    if(showSingleDayPicker){
        val initialMillis = Calendar.getInstance().apply {
            filterParams.singleDay?.let { set(Calendar.YEAR, it.third); set(Calendar.MONTH, it.second-1); set(Calendar.DAY_OF_MONTH, it.first) }
        }.timeInMillis
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(onDismissRequest = { showSingleDayPicker = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val cal = Calendar.getInstance(); cal.timeInMillis = millis
                    viewModel.setSingleDay(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH)+1, cal.get(Calendar.YEAR))
                }
                showSingleDayPicker = false
            }) { Text("OK") }
        }, dismissButton = { TextButton({ showSingleDayPicker = false }) { Text("Cancel") } }) { DatePicker(state = state) }
    }
    if(showRangeStartPicker){
        val state = rememberDatePickerState()
        DatePickerDialog(onDismissRequest = { showRangeStartPicker = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val cal = Calendar.getInstance(); cal.timeInMillis = millis
                    viewModel.setRange(Triple(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH)+1, cal.get(Calendar.YEAR)), filterParams.rangeEnd)
                    showRangeStartPicker = false
                    showRangeEndPicker = true
                }
            }) { Text("Next") }
        }, dismissButton = { TextButton({ showRangeStartPicker = false }) { Text("Cancel") } }) { DatePicker(state = state) }
    }
    if(showRangeEndPicker){
        val state = rememberDatePickerState()
        DatePickerDialog(onDismissRequest = { showRangeEndPicker = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val cal = Calendar.getInstance(); cal.timeInMillis = millis
                    viewModel.setRange(filterParams.rangeStart, Triple(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH)+1, cal.get(Calendar.YEAR)))
                }
                showRangeEndPicker = false
            }) { Text("OK") }
        }, dismissButton = { TextButton({ showRangeEndPicker = false }) { Text("Cancel") } }) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateSection(
    filterParams: FilterParams,
    onMode: (DateMode)->Unit,
    onPreset: (DatePreset)->Unit,
    openSingleDay: ()->Unit,
    openRangeStart: ()->Unit,
    openRangeEnd: ()->Unit
){
    Text("Date", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = { onMode(DateMode.SINGLE_DAY) }, label = { Text("Single Day") }, leadingIcon = null)
        AssistChip(onClick = { onMode(DateMode.RANGE) }, label = { Text("Range") })
        AssistChip(onClick = { onMode(DateMode.NONE) }, label = { Text("Clear") })
    }
    if(filterParams.dateMode == DateMode.SINGLE_DAY){
        TextButton(onClick = openSingleDay) { Text(filterParams.singleDay?.let { (d,m,y)-> "Change: $d/$m/$y" } ?: "Pick Date") }
    }
    if(filterParams.dateMode == DateMode.RANGE){
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = openRangeStart) { Text(filterParams.rangeStart?.let { (d,m,y)-> "Start: $d/$m/$y" } ?: "Start Date") }
            TextButton(onClick = openRangeEnd) { Text(filterParams.rangeEnd?.let { (d,m,y)-> "End: $d/$m/$y" } ?: "End Date") }
        }
    }
    // Presets always visible
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        listOf(DatePreset.TODAY, DatePreset.YESTERDAY, DatePreset.THIS_WEEK, DatePreset.THIS_MONTH).forEach { p ->
            FilterChip(selected = filterParams.preset == p && filterParams.dateMode == DateMode.PRESET, onClick = { onPreset(p) }, label = { Text(presetLabel(p)) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsList(navController: NavController, viewModel: TransactionsViewModel, lazyPagingItems: LazyPagingItems<ExpenseWithTags>) {


    Column {


        LazyColumn {
            items(
                count = lazyPagingItems.itemCount,
                key = { index -> lazyPagingItems[index]?.expense?.expenseID ?: index }
            ) { index ->
                val item = lazyPagingItems[index]
                if (item != null) {
                    TransactionItem(
                        item,
                        onEditClick = {
                            viewModel.selectExpenseForEdit(it)
                            navController.navigate(Screens.EditExpense.route)
                        }
                        )
                } else {
                    // Show placeholder or loading indicator
                    TransactionItemPlaceholder()
                }
            }

            // Add loading state handling

                when {
                    lazyPagingItems.loadState.refresh is LoadState.Loading -> {
                        item { LoadingItem(modifier = Modifier.fillParentMaxSize()) }
                    }
                    lazyPagingItems.loadState.append is LoadState.Loading -> {
                        item { LoadingItem() }
                    }
                    lazyPagingItems.loadState.refresh is LoadState.Error -> {
                        val error = lazyPagingItems.loadState.refresh as LoadState.Error
                        item {
                            ErrorItem(
                                message = error.error.localizedMessage ?: "Error loading transactions",
                                onRetryClick = { lazyPagingItems.retry() }
                            )
                        }
                    }
                    lazyPagingItems.loadState.append is LoadState.Error -> {
                        val error = lazyPagingItems.loadState.append as LoadState.Error
                        item {
                            ErrorItem(
                                message = error.error.localizedMessage ?: "Error loading more transactions",
                                onRetryClick = { lazyPagingItems.retry() }
                            )
                        }
                    }
                }
            }
        }

}

@Composable
private fun EmptyState(hasFilters: Boolean, onClearFilters: ()->Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (hasFilters) "No transactions match filters" else "No transactions found")
            if (hasFilters) {
                TextButton(onClick = onClearFilters) { Text("Clear Filters") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionItem(
    item: ExpenseWithTags,
    onEditClick: (ExpenseWithTags) -> Unit
    ) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.expense.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Row {
                    IconButton(
                        onClick = { onEditClick(item) },
                    ) {
                        Icon (
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Transaction"
                        )
                    }
                    Text(
                        text = "${item.expense.amount} Rs.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${item.expense.date}/${item.expense.month}/${item.expense.year}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (item.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item.tags.forEach { tag ->
                        TagChip(tag.tag)
                    }
                }
            }
        }
    }
}

// 8. Tag Chip Composable
@Composable
fun TagChip(tagName: String) {
    Surface(
        modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = tagName,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun TransactionItemPlaceholder() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        color = Color.Gray.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(16.dp)
                    .background(
                        color = Color.Gray.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

@Composable
fun LoadingItem(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorItem(message: String, onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRetryClick) {
            Text("Retry")
        }
    }
}
