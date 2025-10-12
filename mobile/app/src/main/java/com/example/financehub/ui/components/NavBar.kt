package com.example.financehub.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.financehub.R
import com.example.financehub.navigation.Screens

data class BottomNavItem(
    val name: String,
    val route: String,
    val icon: ImageVector,
)

@Composable
fun NavBar(navController: NavController) {
    val items = listOf(
        BottomNavItem(
            name = stringResource(id = R.string.home),
            route = Screens.HomeScreen.route,
            icon = Icons.Filled.Home
        ),
        BottomNavItem(
            name = stringResource(id = R.string.add_expense),
            route = Screens.AddExpense.route,
            icon = Icons.Filled.Add
        ),
        BottomNavItem(
            name = stringResource(id = R.string.transactions),
            route = Screens.Transactions.route,
            icon = Icons.Filled.Payments
        ),
        BottomNavItem(
            name = stringResource(id = R.string.tags),
            route = Screens.Tags.route,
            icon = Icons.Filled.Tag
        ),
        BottomNavItem(
            name = stringResource(id = R.string.analysis),
            route = Screens.Analysis.route,
            icon = Icons.Filled.BarChart

        )
    )
    NavigationBar(
        containerColor = Color.White
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(imageVector = item.icon, contentDescription = item.name) },
                label = { Text(text = item.name) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        navController.graph.startDestinationRoute?.let { route ->
                            popUpTo(route) {
                                saveState = true
                            }
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NavBarPreview() {
    NavBar(navController = rememberNavController())
}