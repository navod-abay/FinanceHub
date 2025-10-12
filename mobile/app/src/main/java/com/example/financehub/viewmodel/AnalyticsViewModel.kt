package com.example.financehub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.database.ExpenseWithTags
import com.example.financehub.data.database.Tags
import com.example.financehub.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import com.example.financehub.data.repository.TimeSeriesPoint

/**
 * Data class representing a point in time with an expense amount
 */


data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)



class AnalyticsViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    // Date filter state
    private val _startDate = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val startDate: StateFlow<LocalDate> = _startDate

    private val _endDate = MutableStateFlow(LocalDate.now())
    val endDate: StateFlow<LocalDate> = _endDate

    // Selected tags for filtering
    private val _selectedTags = MutableStateFlow<List<String>>(emptyList())
    val selectedTags: StateFlow<List<String>> = _selectedTags

    // All available tags
    private val _availableTags = MutableStateFlow<List<Tags>>(emptyList())
    val availableTags: StateFlow<List<Tags>> = _availableTags

    // Filtered expenses
    private val _filteredExpenses = MutableStateFlow<List<ExpenseWithTags>>(emptyList())
    val filteredExpenses: StateFlow<List<ExpenseWithTags>> = _filteredExpenses

    // Analytics data
    private val _totalAmount = MutableStateFlow(0)
    val totalAmount: StateFlow<Int> = _totalAmount

    private val _monthlyAverage = MutableStateFlow(0.0)
    val monthlyAverage: StateFlow<Double> = _monthlyAverage

    private val _expensesByTag = MutableStateFlow<Map<String, Int>>(emptyMap())
    val expensesByTag: StateFlow<Map<String, Int>> = _expensesByTag

    // Date range presets
    enum class DateRangePreset {
        CURRENT_MONTH,
        PREVIOUS_MONTH,
        CURRENT_YEAR,
        PREVIOUS_YEAR,
        LAST_3_MONTHS,
        LAST_6_MONTHS,
        CUSTOM
    }

    private val _currentDateRangePreset = MutableStateFlow(DateRangePreset.CURRENT_MONTH)
    val currentDateRangePreset: StateFlow<DateRangePreset> = _currentDateRangePreset

    // UI state for date range display
    private val _dateRangeDisplay = MutableStateFlow("")
    val dateRangeDisplay: StateFlow<String> = _dateRangeDisplay

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Time series data
    private val _timeSeriesData = MutableStateFlow<List<TimeSeriesPoint>>(emptyList())
    val timeSeriesData: StateFlow<List<TimeSeriesPoint>> = _timeSeriesData

    // Time aggregation
    private val _timeAggregation = MutableStateFlow(ExpenseRepository.TimeAggregation.DAILY)
    val timeAggregation: StateFlow<ExpenseRepository.TimeAggregation> = _timeAggregation

    init {
        // Load initial data
        viewModelScope.launch {
            repository.getAllTags().collect { tags ->
                _availableTags.value = tags
            }
        }

        // Set default to current month
        setDateRangePreset(DateRangePreset.CURRENT_MONTH)

        // Collect filtered expenses and update analytics when filters change
        viewModelScope.launch {
            combine(startDate, endDate, selectedTags) { start, end, tags ->
                Triple(start, end, tags)
            }.collectLatest { (start, end, tags) ->
                _isLoading.value = true
                repository.getFilteredExpenses(start, end, tags).collect { expenses ->
                    _filteredExpenses.value = expenses
                    _totalAmount.value = repository.calculateTotalAmount(expenses)
                    _monthlyAverage.value = repository.calculateMonthlyAverage(expenses, start, end)
                    _isLoading.value = false
                }
            }
        }

        // Collect expenses by tag
        viewModelScope.launch {
            combine(startDate, endDate, selectedTags) { start, end, tags ->
                Triple(start, end, tags)
            }.collectLatest { (start, end, tags) ->
                repository.getExpensesByTag(start, end, tags).collect { tagAmounts ->
                    _expensesByTag.value = tagAmounts
                }
            }
        }

        // Collect time series data
        viewModelScope.launch {
            combine(startDate, endDate, selectedTags, timeAggregation) { start, end, tags, aggregation ->
                Quadruple(start, end, tags, aggregation)
            }.collectLatest { (start, end, tags, aggregation) ->
                repository.getExpensesOverTime(start, end, tags).collect { rawData:List<TimeSeriesPoint> ->
                    _timeSeriesData.value = repository.aggregateTimeSeriesData(rawData, aggregation)
                }
            }
        }

        // Update date range display
        viewModelScope.launch {
            combine(startDate, endDate, currentDateRangePreset) { start, end, preset ->
                Triple(start, end, preset)
            }.collect { (start, end, preset) ->
                val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
                _dateRangeDisplay.value = when (preset) {
                    DateRangePreset.CURRENT_MONTH -> "Current Month"
                    DateRangePreset.PREVIOUS_MONTH -> "Previous Month"
                    DateRangePreset.CURRENT_YEAR -> "Current Year"
                    DateRangePreset.PREVIOUS_YEAR -> "Previous Year"
                    DateRangePreset.LAST_3_MONTHS -> "Last 3 Months"
                    DateRangePreset.LAST_6_MONTHS -> "Last 6 Months"
                    DateRangePreset.CUSTOM -> "${start.format(formatter)} - ${end.format(formatter)}"
                }
            }
        }
    }

    fun setDateRangePreset(preset: DateRangePreset) {
        val today = LocalDate.now()
        val currentMonth = YearMonth.now()
        val previousMonth = currentMonth.minusMonths(1)

        val (newStart, newEnd) = when (preset) {
            DateRangePreset.CURRENT_MONTH -> Pair(
                currentMonth.atDay(1),
                currentMonth.atEndOfMonth()
            )
            DateRangePreset.PREVIOUS_MONTH -> Pair(
                previousMonth.atDay(1),
                previousMonth.atEndOfMonth()
            )
            DateRangePreset.CURRENT_YEAR -> Pair(
                LocalDate.of(today.year, 1, 1),
                LocalDate.of(today.year, 12, 31)
            )
            DateRangePreset.PREVIOUS_YEAR -> Pair(
                LocalDate.of(today.year - 1, 1, 1),
                LocalDate.of(today.year - 1, 12, 31)
            )
            DateRangePreset.LAST_3_MONTHS -> Pair(
                today.minusMonths(3).withDayOfMonth(1),
                today
            )
            DateRangePreset.LAST_6_MONTHS -> Pair(
                today.minusMonths(6).withDayOfMonth(1),
                today
            )
            DateRangePreset.CUSTOM -> return // Don't change dates for custom
        }

        _startDate.value = newStart
        _endDate.value = newEnd
        _currentDateRangePreset.value = preset
    }

    fun setCustomDateRange(start: LocalDate, end: LocalDate) {
        _startDate.value = start
        _endDate.value = end
        _currentDateRangePreset.value = DateRangePreset.CUSTOM
    }

    fun toggleTag(tagName: String) {
        val currentTags = _selectedTags.value.toMutableList()
        if (currentTags.contains(tagName)) {
            currentTags.remove(tagName)
        } else {
            currentTags.add(tagName)
        }
        _selectedTags.value = currentTags
    }

    fun clearSelectedTags() {
        _selectedTags.value = emptyList()
    }

    fun selectAllTags() {
        _selectedTags.value = _availableTags.value.map { it.tag }
    }
}