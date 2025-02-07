package com.example.financehub.navigation

sealed class Screens(val route: String) {
    object HomeScreen : Screens("home")
    object AddExpense : Screens("addexpense")
}