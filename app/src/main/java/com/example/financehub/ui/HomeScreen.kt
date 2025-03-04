package com.example.financehub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.navigation.NavController
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.financehub.R
import com.example.financehub.ui.components.NavBar
import com.example.financehub.viewmodel.HomeScreenViewModel

@Composable
fun HomeScreen(navController: NavController, viewModel: HomeScreenViewModel) {
    Scaffold (
        bottomBar = { NavBar(navController) },
    ) {
        innerPadding ->
        Column (
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding.calculateTopPadding()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Greeting(navController)
            Spacer(Modifier.height(16.dp))
            TotalExpenditure(navController, viewModel = viewModel)
            Spacer(Modifier.height(16.dp))
            SnapshotGrid(navController)
            Spacer(Modifier.height(16.dp))
            RecentTransactions(navController)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    // HomeScreen(navController = rememberNavController())
}

@Composable
fun RecentTransactions(navController: NavController) {

}

@Composable
fun Greeting(navController: NavController) {
    Row (
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
        ) {
        // Spacer(Modifier.width(16.dp))
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
    val cardData = listOf(
        "Title 2" to "$150.00",
        "Title 3" to "$200.00",
        "Title 4" to "$100.00",
        "Title 1" to "$120.00",
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2), // 2 columns
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(cardData) { (title, amount) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = amount, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        
    }
}

@Composable
fun TotalExpenditure(navController: NavController, viewModel: HomeScreenViewModel) {
    val monthlyTotal by viewModel.monthlyTotal.collectAsState()
    val topTag by viewModel.topTagForMonth.collectAsState()
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column (horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Total Expenditure",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = monthlyTotal.toString(),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

