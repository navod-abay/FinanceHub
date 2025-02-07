package com.example.financehub.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusModifier
import androidx.navigation.NavController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.example.financehub.navigation.Screens

@Composable
fun AddExpense(navController: NavController) {
    var title by remember { mutableStateOf("") }
    var amount by remember {mutableIntStateOf(0)}

    Column (
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = title ,
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
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            navController.navigate(Screens.HomeScreen.route)
        }) {
            Text("Submit")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddExpensePreview() {
    AddExpense(navController = rememberNavController())
}
