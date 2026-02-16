package com.example.financehub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.financehub.data.database.Wishlist
import com.example.financehub.data.database.WishlistWithTags
import com.example.financehub.viewmodel.WishlistViewModel
import com.example.financehub.data.database.Tags
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    navController: NavController,
    viewModel: WishlistViewModel
) {
    val wishlistItems by viewModel.wishlistItems.collectAsState()
    val allTags by viewModel.allTags.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    // selectedItem is now WishlistWithTags? Or just Wishlist for edit?
    // ViewModel exposed WishlistWithTags.
    var selectedItem by remember { mutableStateOf<WishlistWithTags?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Wish List") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                selectedItem = null
                showDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            items(wishlistItems) { itemWithTags ->
                WishlistItemRow(
                    item = itemWithTags.wishlist,
                    tags = itemWithTags.tags,
                    onEdit = {
                        selectedItem = itemWithTags
                        showDialog = true
                    },
                    onDelete = { viewModel.deleteWishlistItem(itemWithTags.wishlist) }
                )
                Divider()
            }
        }

        if (showDialog) {
            WishlistItemDialog(
                item = selectedItem,
                allTags = allTags,
                onDismiss = { showDialog = false },
                onSave = { name, price, tagIds ->
                    if (selectedItem == null) {
                        viewModel.addWishlistItem(name, price, tagIds)
                    } else {
                        viewModel.updateWishlistItem(selectedItem!!.wishlist.copy(
                            name = name,
                            expectedPrice = price
                        ), tagIds)
                    }
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun WishlistItemRow(
    item: Wishlist,
    tags: List<Tags>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tagsString = if (tags.isEmpty()) "No Tag" else tags.joinToString(", ") { it.tag }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "Price: $${item.expectedPrice} | Tags: $tagsString", style = MaterialTheme.typography.bodyMedium)
        }
        Row {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistItemDialog(
    item: WishlistWithTags?,
    allTags: List<Tags>,
    onDismiss: () -> Unit,
    onSave: (String, Int, List<Int>) -> Unit
) {
    var name by remember { mutableStateOf(item?.wishlist?.name ?: "") }
    var priceStr by remember { mutableStateOf(item?.wishlist?.expectedPrice?.toString() ?: "") }
    // Multi-select state
    val initialTagIds = item?.tags?.map { it.tagID } ?: emptyList()
    var selectedTagIds by remember { mutableStateOf(initialTagIds.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add Item" else "Edit Item") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Expected Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Select Tags:", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.height(150.dp)) {
                    items(allTags) { tag ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTagIds = if (selectedTagIds.contains(tag.tagID)) {
                                        selectedTagIds - tag.tagID
                                    } else {
                                        selectedTagIds + tag.tagID
                                    }
                                }
                        ) {
                            Checkbox(
                                checked = selectedTagIds.contains(tag.tagID),
                                onCheckedChange = { checked ->
                                    selectedTagIds = if (checked) {
                                        selectedTagIds + tag.tagID
                                    } else {
                                        selectedTagIds - tag.tagID
                                    }
                                }
                            )
                            Text(text = tag.tag)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val price = priceStr.toIntOrNull() ?: 0
                onSave(name, price, selectedTagIds.toList())
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

