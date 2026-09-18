package com.example.financehub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financehub.data.repository.TimeAggregation
import com.example.financehub.viewmodel.ChartSeries
import java.time.format.DateTimeFormatter

/**
 * A multi-series line chart of expenses over time.
 *
 * All series arrive sharing one bucket axis (see AnalyticsViewModel.chartSeries), so points are
 * positioned by index rather than by calendar distance.
 *
 * @param series One line per selected tag / total, in palette order
 * @param availableSeries Every series that could be plotted, including the hidden ones - drives the legend
 * @param selectedSeries Labels currently switched on
 * @param onToggleSeries Called when a legend chip is tapped, to switch that line on or off
 * @param aggregation Current bucket size, shown in the in-card dropdown
 * @param onAggregationChange Called when the user picks a different bucket size
 * @param isCumulative Whether values are running totals
 * @param onCumulativeChange Called when the user flips the individual/cumulative toggle
 * @param onSelectSeries Called to open the series picker dialog
 * @param modifier Modifier for the composable
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseTimeChart(
    series: List<ChartSeries>,
    availableSeries: List<String>,
    selectedSeries: Set<String>,
    onToggleSeries: (String) -> Unit,
    aggregation: TimeAggregation,
    onAggregationChange: (TimeAggregation) -> Unit,
    isCumulative: Boolean,
    onCumulativeChange: (Boolean) -> Unit,
    onSelectSeries: () -> Unit,
    modifier: Modifier = Modifier
) {
    var aggregationExpanded by remember { mutableStateOf(false) }

    val visibleSeries = series.filter { it.points.isNotEmpty() }
    val axis = visibleSeries.firstOrNull()?.points?.map { it.date } ?: emptyList()
    val maxAmount = visibleSeries.maxOfOrNull { s -> s.points.maxOf { it.amount } } ?: 0

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Expense Trend",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onSelectSeries) {
                    Icon(Icons.Default.Tune, contentDescription = "Select chart series")
                }
            }

            // Controls: bucket size and individual/cumulative
            ExposedDropdownMenuBox(
                expanded = aggregationExpanded,
                onExpandedChange = { aggregationExpanded = !aggregationExpanded },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                OutlinedTextField(
                    value = aggregation.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Aggregation") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = aggregationExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = aggregationExpanded,
                    onDismissRequest = { aggregationExpanded = false }
                ) {
                    TimeAggregation.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                onAggregationChange(option)
                                aggregationExpanded = false
                            }
                        )
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                SegmentedButton(
                    selected = !isCumulative,
                    onClick = { onCumulativeChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Individual")
                }
                SegmentedButton(
                    selected = isCumulative,
                    onClick = { onCumulativeChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Cumulative")
                }
            }

            if (visibleSeries.isEmpty() || axis.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (availableSeries.isNotEmpty() && selectedSeries.isEmpty()) {
                            "All lines hidden - use the series picker"
                        } else {
                            "No expenses in this period"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LegendRow(
                    availableSeries = availableSeries,
                    selectedSeries = selectedSeries,
                    series = series,
                    onToggleSeries = onToggleSeries
                )
                return@Column
            }

            // Date range display
            val dateFormatter = DateTimeFormatter.ofPattern("MMM d")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = axis.first().format(dateFormatter),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = axis.last().format(dateFormatter),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(vertical = 8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // A single bucket has no horizontal span - draw it centred instead of dividing by zero
                    val bucketCount = axis.size
                    val horizontalStep =
                        if (bucketCount > 1) canvasWidth / (bucketCount - 1) else 0f
                    val verticalScale =
                        if (maxAmount > 0) canvasHeight / maxAmount.toFloat() else 0f

                    // Axes
                    drawLine(
                        color = Color.Gray,
                        start = Offset(0f, canvasHeight),
                        end = Offset(canvasWidth, canvasHeight),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = Color.Gray,
                        start = Offset(0f, 0f),
                        end = Offset(0f, canvasHeight),
                        strokeWidth = 1f
                    )

                    visibleSeries.forEach { chartSeries ->
                        val color = seriesColor(chartSeries.colorIndex)
                        val path = Path()

                        chartSeries.points.forEachIndexed { index, point ->
                            val x = if (bucketCount > 1) index * horizontalStep else canvasWidth / 2f
                            val y = canvasHeight - (point.amount * verticalScale)
                            val position = Offset(x, y)

                            if (index == 0) {
                                path.moveTo(position.x, position.y)
                            } else {
                                path.lineTo(position.x, position.y)
                            }

                            drawCircle(color = color, radius = 5f, center = position)
                        }

                        if (chartSeries.points.size > 1) {
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(width = 3f, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            // Y-axis range
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (maxAmount > 0) {
                    val formatter = java.text.NumberFormat.getCurrencyInstance()
                    Text(
                        text = formatter.format(maxAmount / 100.0),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LegendRow(
                availableSeries = availableSeries,
                selectedSeries = selectedSeries,
                series = series,
                onToggleSeries = onToggleSeries
            )
        }
    }
}

/**
 * Legend for the visible lines. Tapping an entry hides that line; bringing it back is done
 * through the series picker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendRow(
    availableSeries: List<String>,
    selectedSeries: Set<String>,
    series: List<ChartSeries>,
    onToggleSeries: (String) -> Unit
) {
    // Only lines that are switched on get a legend entry; hidden ones are managed via the picker
    val labels = availableSeries.ifEmpty { series.map { it.label } }
        .filter { selectedSeries.contains(it) }
    if (labels.isEmpty()) return

    val colorIndexByLabel = series.associate { it.label to it.colorIndex }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEach { label ->
            val isOn = selectedSeries.contains(label)
            val dotColor = colorIndexByLabel[label]
                ?.takeIf { isOn }
                ?.let { seriesColor(it) }
                ?: MaterialTheme.colorScheme.outlineVariant

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onToggleSeries(label) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    fontSize = 12.sp,
                    textDecoration = if (isOn) null else TextDecoration.LineThrough,
                    color = if (isOn) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    }
}
