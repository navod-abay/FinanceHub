package com.example.financehub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.navigation.compose.rememberNavController
import com.example.financehub.data.database.AppDatabase
import com.example.financehub.data.repository.ExpenseRepository
import com.example.financehub.navigation.NavGraph
import com.example.financehub.viewmodel.ExpenseViewModel

class MainActivity : ComponentActivity() {
    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { ExpenseRepository(database.expenseDao()) }
    private val viewModel by lazy { ExpenseViewModel(repository) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val navController = rememberNavController()
            NavGraph(navController = navController, viewModel)
        }
    }
}



