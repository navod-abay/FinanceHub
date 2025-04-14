package com.example.financehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavController
import androidx.compose.ui.unit.dp
import com.example.financehub.ui.components.NavBar
import com.example.financehub.viewmodel.ExpenseViewModel

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.financehub.util.getDateComponents
import java.util.Date

@Composable
fun AddExpense(navController: NavController, viewModel: ExpenseViewModel) {
    Scaffold (
        bottomBar = { NavBar(navController) },
    ) {
        _ ->
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
           ExpenseForm(viewModel, navController)
        }
    }
}



@Composable
fun ExpenseForm(
    viewModel: ExpenseViewModel,
    navController: NavController
) {
    val date = Date() // Current date
    val (day, month, year) = getDateComponents(date)
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dayState by remember { mutableIntStateOf(day)}
    var monthState by remember { mutableIntStateOf(month)}
    var yearState by remember { mutableIntStateOf(year)}
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var showTagDialog by remember { mutableStateOf(false) }
    var newTagText by remember { mutableStateOf("") }

    // Predefined tags (you can modify this list)
    val suggestedTags by viewModel.matchingTags.collectAsState()
    val lastNameFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Name field
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Expense Name") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = {

                }
            ),
            singleLine = true,
        )

        // Amount field
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth().focusRequester(lastNameFocusRequester),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    // Clear focus when "Done" is pressed
                    focusManager.clearFocus()
                }
            )
        )

        Row (
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ){
            OutlinedTextField(
                value = dayState.toString(),
                onValueChange = { dayState = it.toInt() },
                label = { Text("Day") },
                modifier = Modifier.weight(1f).focusRequester(lastNameFocusRequester),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        // Clear focus when "Done" is pressed
                        focusManager.clearFocus()
                    }
                )
            )
            OutlinedTextField(
                    value = monthState.toString() + 1,
            onValueChange = { monthState = it.toInt() - 1 },
            label = { Text("Month") },
            modifier = Modifier.weight(1f).focusRequester(lastNameFocusRequester),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    // Clear focus when "Done" is pressed
                    focusManager.clearFocus()
                }
            )
            )
            OutlinedTextField(
            value = yearState.toString(),
            onValueChange = { yearState = it.toInt() },
            label = { Text("Amount") },
            modifier = Modifier.weight(1f).focusRequester(lastNameFocusRequester),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    // Clear focus when "Done" is pressed
                    focusManager.clearFocus()
                }
            )
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
                    label = { Text(tag) },
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

            item {
                AssistChip(
                    onClick = { showTagDialog = true },
                    label = { Text("Add Tag") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add tag"
                        )
                    }
                )
            }
        }

        // Submit button
        Button(
            onClick = {
                if (name.isNotBlank() && amount.isNotBlank()) {

                        viewModel.addExpense(
                            title = name,
                            amount = amount.toInt() ,
                            tags = selectedTags,
                            day = dayState,
                            month = monthState,
                            year = yearState
                        )
                        navController.navigate("home")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Expense")
        }
    }

    // Tag selection dialog
    if (showTagDialog) {
        AlertDialog(
            onDismissRequest = {
                showTagDialog = false
                newTagText = ""
            },
            title = { Text("Add Tag") },
            text = {
                val lastNameFocusRequester = remember { FocusRequester() }
                val focusManager = LocalFocusManager.current
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Custom tag input
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = {
                            newTagText = it
                            viewModel.updateQuery(it)
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
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Predefined tags
                    Text(
                        text = "Suggested Tags",
                        style = MaterialTheme.typography.titleSmall
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestedTags.forEach { tag ->
                            FilterChip(

                                selected = tag.tag in selectedTags,
                                onClick = {
                                    newTagText = ""
                                    selectedTags = if (selectedTags.contains(tag.tag)) {
                                        selectedTags - tag.tag
                                    } else {
                                        selectedTags + tag.tag
                                    }
                                },
                                label = { Text(tag.tag) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTagText.isNotBlank()) {
                            selectedTags = selectedTags + newTagText
                            newTagText = ""
                        }
                        showTagDialog = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTagDialog = false
                        newTagText = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        measurables.forEach { measurable ->
            val placeable = measurable.measure(constraints)
            if (currentRowWidth + placeable.width > constraints.maxWidth) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        val height = rows.sumOf { row -> row.maxOf { it.height } }

        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                row.forEach { placeable ->
                    placeable.place(x, y)
                    x += placeable.width
                }
                y += row.maxOf { it.height }
            }
        }
    }
}