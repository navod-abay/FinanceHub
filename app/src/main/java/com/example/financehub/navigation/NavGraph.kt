package com.example.financehub.navigation


import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.financehub.ui.HomeScreen
import com.example.financehub.ui.AddExpense
import com.example.financehub.navigation.Screens
import com.example.financehub.viewmodel.ExpenseViewModel

@Composable
fun NavGraph(navController: NavHostController, viewModel: ExpenseViewModel) {
    NavHost(
        navController = navController,
        startDestination = Screens.HomeScreen.route
    ) {
        composable(route = "home") {
            HomeScreen(navController = navController)
        }
        composable(route = "details") {
            AddExpense(navController = navController, viewModel = viewModel)
        }
    }
}