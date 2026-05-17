package com.example.financehub.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.financehub.data.database.Wishlist
import com.example.financehub.data.database.WishlistWithTags
import com.example.financehub.viewmodel.WishlistViewModel
import com.example.financehub.data.database.Tags
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.financehub.ui.components.NavBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    navController: NavController,
    viewModel: WishlistViewModel
) {
    val wishlistItems by viewModel.wishlistItems.collectAsStateWithLifecycle()
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
        },
        bottomBar = {NavBar(navController = navController)}
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
                HorizontalDivider()
            }
        }

        if (showDialog) {
            WishlistItemDialog(
                item = selectedItem,
                allTags = allTags,
                onDismiss = { showDialog = false },
                onSave = { name, minPrice, maxPrice, tagIds ->
                    if (selectedItem == null) {
                        viewModel.addWishlistItem(name, minPrice, maxPrice, tagIds)
                    } else {
                        viewModel.updateWishlistItem(selectedItem!!.wishlist.copy(
                            name = name,
                            minPrice = minPrice,
                            maxPrice = maxPrice
                        ), tagIds)
                    }
                    showDialog = false
                },
                viewModel = viewModel
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
            Text(text = "Price: LKR ${item.minPrice} - LKR ${item.maxPrice} | Tags: $tagsString", style = MaterialTheme.typography.bodyMedium)
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
    onSave: (String, Int, Int, List<Tags>) -> Unit,
    viewModel: WishlistViewModel
) {
    var name by remember { mutableStateOf(item?.wishlist?.name ?: "") }
    var minPriceStr by remember { mutableStateOf(item?.wishlist?.minPrice?.toString() ?: "") }
    var maxPriceStr by remember { mutableStateOf(item?.wishlist?.minPrice?.toString() ?: "") }
    // Multi-select state
    val initialTagIds = item?.tags ?: emptyList()
    var selectedTags by remember { mutableStateOf(initialTagIds.toSet()) }
    var tagQuery by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val suggestedTags by viewModel.matchingTags.collectAsState()


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
                    value = minPriceStr,
                    onValueChange = { minPriceStr = it },
                    label = { Text("Minimum Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = maxPriceStr,
                    onValueChange = { maxPriceStr = it },
                    label = { Text("Maximum Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Select Tags:", style = MaterialTheme.typography.labelMedium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(selectedTags.toList()) { tag ->
                        InputChip(
                            selected = true,
                            onClick = { },
                            label = { Text(tag.tag) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        selectedTags = selectedTags - tag
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove tag"
                                    )
                                }
                            }
                        )
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = expanded && suggestedTags.isNotEmpty(),
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = tagQuery,
                        onValueChange = {
                            tagQuery = it
                            viewModel.updateQuery(it)
                            expanded = true
                        },
                        label = { Text("Type or select tag") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded && suggestedTags.isNotEmpty(),
                        onDismissRequest = { expanded = false }
                    ) {
                        suggestedTags.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag.tag) },
                                onClick = {
                                    selectedTags = selectedTags + tag
                                    tagQuery = ""
                                    expanded = false
                                }
                            )
                        }
                    }
                }

            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minPrice = minPriceStr.toIntOrNull() ?: 0
                val maxPrice = maxPriceStr.toIntOrNull() ?: 0
                onSave(name, minPrice, maxPrice, selectedTags.toList())
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


