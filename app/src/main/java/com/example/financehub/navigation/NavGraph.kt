package com.example.financehub.navigation


import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.financehub.data.database.AppDatabase
import com.example.financehub.data.repository.ExpenseRepository
import com.example.financehub.ui.HomeScreen
import com.example.financehub.ui.AddExpense
import com.example.financehub.ui.EditExpenseScreen
import com.example.financehub.ui.TagsScreen
import com.example.financehub.ui.Transactions
import com.example.financehub.viewmodel.ExpenseViewModel
import com.example.financehub.viewmodel.HomeScreenViewModel
import com.example.financehub.viewmodel.TagsViewModel
import com.example.financehub.viewmodel.TransactionsViewModel

@Composable
fun NavGraph(navController: NavHostController) {
    val applicationContext = LocalContext.current.applicationContext
    val database by lazy { AppDatabase.getDatabase(applicationContext) }
    val repository by lazy { ExpenseRepository(database.expenseDao(), database.tagsDao(), database.expenseTagsCrossRefDao()) }
    val expenseViewModel by lazy { ExpenseViewModel(repository) }
    val homeScreenViewModel by lazy { HomeScreenViewModel(repository) }
    val transactionsViewModel by lazy { TransactionsViewModel(repository) }
    val tagsViewModel by lazy { TagsViewModel(repository) }

    NavHost(
        navController = navController,
        startDestination = Screens.HomeScreen.route
    ) {
        composable(route = "home") {
            HomeScreen(navController = navController, viewModel = homeScreenViewModel)
        }
        composable(route = "addexpense") {
            AddExpense(navController = navController, viewModel = expenseViewModel)
        }
        composable(route = "transactions") {
            Transactions(navController = navController, viewModel = transactionsViewModel)
        }
        composable(route = "tags") {
            TagsScreen(navController = navController, viewModel = tagsViewModel)
        }
        composable(route = Screens.EditExpense.route) {
            EditExpenseScreen(navController = navController, viewModel = transactionsViewModel)
        }
    }
}