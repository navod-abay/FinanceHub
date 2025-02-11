package com.example.financehub.ui

import ExpenseDao
import ExpenseRepository
import ExpenseViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.example.financehub.navigation.Screens
import com.example.financehub.ui.components.NavBar

@Composable
fun AddExpense(navController: NavController, viewModel: ExpenseViewModel) {
    var title by remember { mutableStateOf("") }
    var amount by remember {mutableIntStateOf(0)}

    Scaffold (
        bottomBar = { NavBar(navController) },
    ) {
        innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),

                )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = amount.toString(),
                onValueChange = { amount = it.toInt() },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                viewModel.addExpense(title, amount.toDoubleOrNull() ?: 0.0)
                navController.navigate("home")
            }) {
                Text("Submit")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddExpensePreview() {
    AddExpense(navController = rememberNavController(), viewModel = ExpenseViewModel(ExpenseRepository(ExpenseDao())))
}
