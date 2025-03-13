package com.example.financehub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.financehub.data.database.Tags
import com.example.financehub.ui.components.NavBar
import com.example.financehub.viewmodel.TagsViewModel

@Composable
fun TagsScreen(viewModel: TagsViewModel, navController: NavController) {
    Scaffold (
        bottomBar = {NavBar(navController)}
    ){
        _ ->
            Column {
                Text("Tags")
                TagsList(viewModel, navController)
            }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsList(viewModel: TagsViewModel, navController: NavController) {
    val lazyPagingItems = viewModel.pagedTags.collectAsLazyPagingItems()

    Column {
        TopAppBar(
            title = { Text("Tags") }
        )
        LazyColumn {
            items(
                count = lazyPagingItems.itemCount,
                key = { index -> lazyPagingItems[index]?.tagID ?: index }
            ) {
                index ->
                val item = lazyPagingItems[index]
                if (item != null) {
                    TagCard(item)
                } else {
                    TagsItemPlaceholder()
                }
            }

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
                            message = error.error.localizedMessage ?: "Error loading tags",
                            onRetryClick = { lazyPagingItems.retry() }
                        )
                    }
                }
                lazyPagingItems.loadState.append is LoadState.Error -> {
                    val error = lazyPagingItems.loadState.append as LoadState.Error
                    item {
                        ErrorItem(
                            message = error.error.localizedMessage ?: "Error loading more tags",
                            onRetryClick = { lazyPagingItems.retry() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TagsItemPlaceholder() {
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
fun TagCard(Tags: Tags) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Tags.tag,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$${Tags.monthlyAmount}",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

}
