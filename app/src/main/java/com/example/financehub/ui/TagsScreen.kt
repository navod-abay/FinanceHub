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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.financehub.data.database.Tags
import com.example.financehub.data.database.Target
import com.example.financehub.ui.components.NavBar
import com.example.financehub.viewmodel.TagsViewModel
import com.example.financehub.viewmodel.TargetWithTag
import com.example.financehub.viewmodel.TargetsViewModel

@Composable
fun TagsScreen(viewModel: TagsViewModel, navController: NavController, targetsViewModel: TargetsViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Tags", "Targets")
    Scaffold (
        bottomBar = {NavBar(navController)}
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> TagsList(viewModel, navController)
                1 -> TargetsList(targetsViewModel)
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsList(viewModel: TargetsViewModel) {
    val targetsWithTags by viewModel.targetsWithTags.collectAsState()
    Column {
        TopAppBar(
            title = { Text("Targets") }
        )
        LazyColumn {
            items(
                count = targetsWithTags.size,
                key = { index ->
                    val item = targetsWithTags[index]
                    "${item.target.month}_${item.target.year}_${item.target.tagID}"
                }
            ) { index ->
                val item = targetsWithTags[index]
                TargetCard(
                    item = item,
                    onDelete = { viewModel.deleteTarget(item.target) },
                    onEdit = { newAmount -> viewModel.updateTargetAmount(item.target, newAmount) }
                )
            }
        }
    }
}

@Composable
fun TargetCard(
    item: TargetWithTag,
    onDelete: (() -> Unit)? = null,
    onEdit: ((Int) -> Unit)? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    var editAmount by remember { mutableStateOf(item.target.amount.toString()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = item.tag.tag, modifier = Modifier.weight(1f))
                if (isEditing) {
                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it },
                        label = { Text("Target") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    IconButton(onClick = {
                        onEdit?.invoke(editAmount.toIntOrNull() ?: item.target.amount)
                        isEditing = false
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                    IconButton(onClick = { isEditing = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                } else {
                    Text(text = "Target: $${item.target.amount}", modifier = Modifier.weight(1f))
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Target")
                    }
                }
                IconButton(onClick = { onDelete?.invoke() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Target")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Month: ${item.target.month}")
                Text(text = "Year: ${item.target.year}")
                Text(text = "Spent: $${item.target.spent}")
            }
        }
    }
}
