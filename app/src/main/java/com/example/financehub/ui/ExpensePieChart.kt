package com.example.financehub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * A composable function that displays expense data in a pie chart
 * @param tagAmounts Map of tag names to expense amounts
 * @param modifier Modifier for the composable
 */
@Composable
fun ExpensePieChart(
    tagAmounts: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    if (tagAmounts.isEmpty()) return

    // Calculate total amount
    val totalAmount = tagAmounts.values.sum()

    // Define a list of colors to use for the pie chart slices
    val colors = listOf(
        Color(0xFF6200EE), // Purple
        Color(0xFF3700B3), // Dark Purple
        Color(0xFF03DAC5), // Teal
        Color(0xFF018786), // Dark Teal
        Color(0xFFFF0266), // Pink
        Color(0xFFB00020), // Red
        Color(0xFFF86734), // Orange
        Color(0xFF4CAF50), // Green
        Color(0xFF2196F3), // Blue
        Color(0xFFFFEB3B)  // Yellow
    )

    // Calculate the percentages and assign colors
    val pieData = tagAmounts.entries
        .sortedByDescending { it.value }
        .mapIndexed { index, entry ->
            val percentage = entry.value.toFloat() / totalAmount
            Triple(entry.key, entry.value, colors[index % colors.size])
        }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Expense Distribution",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Pie chart
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val radius = min(canvasWidth, canvasHeight) / 2f
                    val center = Offset(canvasWidth / 2f, canvasHeight / 2f)

                    var startAngle = 0f

                    pieData.forEach { (_, amount, color) ->
                        val sweepAngle = (amount.toFloat() / totalAmount) * 360f

                        // Draw slice
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2)
                        )

                        // Draw outline
                        drawArc(
                            color = Color.White,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = 2f)
                        )

                        startAngle += sweepAngle
                    }
                }
            }

            // Legend
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pieData) { (tag, amount, color) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 8.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(color = color)
                            }
                        }

                        Text(
                            text = tag,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = String.format("%.1f%%", (amount.toFloat() / totalAmount) * 100),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}