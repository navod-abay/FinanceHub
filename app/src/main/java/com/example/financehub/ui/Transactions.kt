package com.example.financehub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import com.example.financehub.viewmodel.TransactionsViewModel
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Transactions(navController: NavController, viewModel: TransactionsViewModel) {
    Scaffold (
        bottomBar = { NavBar(navController) }
    ){

        _ ->  Column {
        var searchQuery by remember { mutableStateOf("") }
        var selectedDate by remember { mutableStateOf<Calendar?>(null) }
        val transactionsFlow = viewModel.pagedExpenses


        val filteredTransactionsFlow = remember(searchQuery, selectedDate) {
            transactionsFlow.map { pagingData ->
                pagingData.filter { transaction ->
                    val matchesSearch = searchQuery.isEmpty() ||
                            transaction.expense.title.contains(searchQuery, ignoreCase = true)

                    val matchesDate = selectedDate?.let { date ->
                        transaction.expense.year == selectedDate!!.get(Calendar.YEAR) &&
                                transaction.expense.month == selectedDate!!.get(Calendar.MONTH) + 1 && // Calendar months are 0-based
                                transaction.expense.date == selectedDate!!.get(Calendar.DAY_OF_MONTH)
                    } ?: true

                    matchesSearch && matchesDate
                }
            }
        }
            TopAppBar(
                modifier = Modifier.fillMaxWidth(),
                title = { Text("Transaction History") }
            )
            SearchWithDateFilter(searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it }, selectedDate = selectedDate, onDateSelected = {selectedDate = it })
            TransactionsList(navController, viewModel, filteredTransactionsFlow.collectAsLazyPagingItems())
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchWithDateFilter(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedDate: Calendar?,
    onDateSelected: (Calendar?) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Column(modifier = Modifier.fillMaxWidth()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search transactions") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        // Date Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = "Calendar",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Filter by date")
            }

            selectedDate?.let { date ->
                Spacer(modifier = Modifier.weight(1f))

                FilterChip(
                    selected = true,
                    onClick = { onDateSelected(null) },
                    label = { Text(dateFormatter.format(date.time)) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear date filter",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val initialCalendar = selectedDate ?: Calendar.getInstance()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialCalendar.timeInMillis
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = millis
                        onDateSelected(calendar)
                    }
                    showDatePicker = false
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
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

