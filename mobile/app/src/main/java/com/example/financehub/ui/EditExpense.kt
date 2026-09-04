package com.example.financehub.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.financehub.data.database.ExpenseWithTags
import com.example.financehub.data.database.models.TagRef
import com.example.financehub.viewmodel.TransactionsViewModel

@Composable
fun EditExpenseScreen(
    viewModel: TransactionsViewModel,
    navController: NavController
) {
    val selectedExpense by viewModel.selectedExpense.collectAsState()

    selectedExpense?.let { expenseWithTags ->
        ExpenseEditForm(
            expenseWithTags = expenseWithTags,
            viewModel = viewModel,
            navController = navController
        )
    } ?: run {
        // Show error or loading state
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No expense selected for editing")
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditForm(
    expenseWithTags: ExpenseWithTags,
    viewModel: TransactionsViewModel,
    navController: NavController
) {
    val expense = expenseWithTags.expense

    var title by remember { mutableStateOf(expense.title) }
    var amount by remember { mutableStateOf(expense.amount.toString()) }
    var year by remember { mutableStateOf(expense.year.toString()) }
    var month by remember { mutableStateOf(expense.month.toString()) }
    var date by remember { mutableStateOf(expense.date.toString()) }
    val newlyAddedTags  = viewModel.newlyAddedTags
    val suggestedTags by viewModel.matchingTags.collectAsState()
    val reccommendedTags by viewModel.reccommendedTags.collectAsState()

    val focusManager = LocalFocusManager.current

    // Extract tag strings from the Tags objects
    val initialTags = expenseWithTags.tags.map { it -> TagRef(it.tagID, it.tag) }.toSet()
    val selectedTags by viewModel.selectedTags.collectAsState()

    var showTagDialog by remember { mutableStateOf(false) }
    val newTagText by viewModel.newTagText.collectAsState()

    // Predefined tags (you can modify this list)
    val predefinedTags = remember {
        listOf("Food", "Transport", "Entertainment", "Utilities", "Shopping")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Transaction") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Expense Name") },
                modifier = Modifier.fillMaxWidth()
            )

            // Amount field
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Date fields - Row with 3 fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Day") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = month,
                    onValueChange = { month = it },
                    label = { Text("Month") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Year") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            // Tags section
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleMedium
            )

            // Selected tags
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
                                    viewModel.deselectTag(tag)
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
                items(newlyAddedTags) { tag ->
                    InputChip(
                        selected = true,
                        onClick = { },
                        label = { Text(tag) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    newlyAddedTags.remove(tag)
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
                items(suggestedTags) { tag ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.clickSuggestedTag(tag)},
                        label = { Text(tag.tag) },
                    )
                }
                items(reccommendedTags) { tag ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.clickRecommendedTag(tag)},
                        label = { Text(tag.tag) },
                    )
                }

            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Custom tag input
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = {
                        viewModel.updateNewTagText(it)
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences, // Capitalize first letter
                        imeAction = ImeAction.Done // Show "Done" on keyboard
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            showTagDialog = false
                        }
                    ),
                    label = { Text("New Tag") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if(newTagText.isNotEmpty()) {
                            IconButton(onClick = {viewModel.onNewTagTextAddition()}) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Add New Tag"
                                )
                            }
                        }
                    }
                )

            }




            // Submit and Cancel buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        navController.navigateUp()
                        viewModel.clearSelectedExpense()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        if (title.isNotBlank() && amount.isNotBlank() &&
                            year.isNotBlank() && month.isNotBlank() && date.isNotBlank()
                        ) {
                            try {
                                viewModel.updateExpense(
                                    expenseId = expense.expenseID,
                                    title = title,
                                    amount = amount.toInt(),
                                    year = year.toInt(),
                                    month = month.toInt(),
                                    date = date.toInt(),
                                    newTags = selectedTags,
                                    oldTags = initialTags,
                                    newlyAddedTags = newlyAddedTags.toList()
                                )
                                navController.navigateUp()
                                viewModel.clearSelectedExpense()
                            } catch (e: NumberFormatException) {
                                // Handle invalid number format
                                // You might want to show a snackbar or dialog here
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Update Expense")
                }
            }
        }
    }

    // Tag selection dialog

}