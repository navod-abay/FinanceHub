package com.example.financehub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.financehub.data.database.ExpenseWithTags
import com.example.financehub.viewmodel.TransactionsViewModel

@Composable
fun Transactions(navController: NavController, viewModel: TransactionsViewModel) {
    Scaffold {
        _ ->  Column {
            Text("Transactions")
            TransactionsList(navController, viewModel)
    }

    }
}


@Preview
@Composable
fun TransactionsPreview() {
    // Transactions(navController = rememberNavController())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsList(navController: NavController, viewModel: TransactionsViewModel) {
    val lazyPagingItems = viewModel.pagedExpenses.collectAsLazyPagingItems()

    Column {
        TopAppBar(
            title = { Text("Transaction History") }
        )

        LazyColumn {
            items(
                count = lazyPagingItems.itemCount,
                key = { index -> lazyPagingItems[index]?.expense?.expenseID ?: index }
            ) { index ->
                val item = lazyPagingItems[index]
                if (item != null) {
                    TransactionItem(item)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionItem(item: ExpenseWithTags) {
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
                Text(
                    text = "$${item.expense.amount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
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

