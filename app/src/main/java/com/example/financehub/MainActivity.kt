package com.example.financehub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.navigation.compose.rememberNavController
import com.example.financehub.navigation.NavGraph

class MainActivity : ComponentActivity() {
    val database = AppDatabase.getDatabase(this)
    val repository = ExpenseRepository(database.expenseDao())
    val viewModel = ExpenseViewModel(repository)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            NavGraph(navController = navController, viewModel)
        }
    }
}



