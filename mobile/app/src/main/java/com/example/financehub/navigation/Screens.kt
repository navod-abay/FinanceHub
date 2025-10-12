package com.example.financehub.navigation

sealed class Screens(val route: String) {
    object HomeScreen : Screens("home")
    object AddExpense : Screens("addexpense")
    object Transactions : Screens("transactions")
    object Tags : Screens("tags")
    object EditExpense: Screens("editexpense")
    object Analysis: Screens("analysis")
}