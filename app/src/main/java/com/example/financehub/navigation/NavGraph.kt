package com.example.financehub.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHost
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.financehub.ui.HomeScreen
import com.example.financehub.ui.AddExpense

@Composable
fun NavGraph(navController: NavController) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("details") { AddExpense(navController) }
    }
}