package com.example.financehub.ui

import androidx.compose.ui.graphics.Color

/**
 * Shared color palette for all charts (pie chart slices, time chart series).
 * Colors are assigned by index and cycle once the list is exhausted.
 */
val ChartPalette = listOf(
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

fun seriesColor(index: Int): Color = ChartPalette[index % ChartPalette.size]
