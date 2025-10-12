package com.example.financehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.financehub.viewmodel.ExpenseViewModel
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetForm(viewModel: ExpenseViewModel, navController: NavController?) {
    val suggestedTags by viewModel.matchingTags.collectAsState()
    val month = viewModel.monthState.value
    val year = viewModel.yearState.value
    val selectedTag = viewModel.selectedTag.value
    val amount = viewModel.targetAmount.value
    val error = viewModel.targetError.value
    var tagQuery by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        OutlinedTextField(
            value = month,
            onValueChange = { viewModel.monthState.value = it },
            label = { Text("Month") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            singleLine = true
        )
        OutlinedTextField(
            value = year,
            onValueChange = { viewModel.yearState.value = it },
            label = { Text("Year") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            singleLine = true
        )
        Text("Tag", style = MaterialTheme.typography.titleMedium)
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
                            viewModel.selectedTag.value = tag
                            tagQuery = tag.tag
                            expanded = false
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = amount,
            onValueChange = { viewModel.targetAmount.value = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            singleLine = true
        )
        Button(
            onClick = {
                viewModel.addTarget()
                // Clear the form after submission
                viewModel.monthState.value = ""
                viewModel.yearState.value = ""
                viewModel.selectedTag.value = null
                viewModel.targetAmount.value = ""
                tagQuery = ""
                expanded = false
                navController?.navigate("home")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Target")
        }
    }
}
