package com.example.financehub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.navigation.NavController
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.example.financehub.R
import com.example.financehub.navigation.Screens

@Composable
fun HomeScreen(navController: NavController) {
    Scaffold (
        bottomBar = { BottomButtons(navController)},
    ) {
        innerPadding ->
        Column (
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Greeting(navController)
            Spacer(Modifier.height(16.dp))
            TotalExpenditure(navController)
            Spacer(Modifier.height(16.dp))
            SnapshotGrid(navController)
            Spacer(Modifier.height(16.dp))
            RecentTransactions(navController)
        }
    }
    // BottomButtons(navController)

}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(navController = rememberNavController())
}

@Composable
fun RecentTransactions(navController: NavController) {

}

@Composable
fun Greeting(navController: NavController) {
    Row (
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
        ) {
        Spacer(Modifier.width(16.dp))
        Column  {
            Text("Hello", style = MaterialTheme.typography.headlineLarge)
            Text("Navod")
        }
        Image(
            painter = painterResource(id = R.drawable.frame),
            contentDescription = stringResource(id = R.string.frame_description)
        )

    }
}


@Composable
fun SnapshotGrid(navController: NavController) {
    Column {
        Row {

        }
    }
    Column {
        Row {

        }
    }
}

@Composable
fun TotalExpenditure(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "Total Expenditure",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Rs. 0",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun BottomButtons(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    navController.navigate(Screens.AddExpense.route)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                ),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text(
                    text = "Add Expense",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }

            Button(
                onClick = {

                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Green
                ),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            ) {
                Text(
                    text = "Add Income",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
        }
    }
}