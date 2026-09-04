package com.example.financehub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.database.models.TagRef
import com.example.financehub.data.repository.ExpenseRepository
import com.example.financehub.data.repository.ExpensesAnalytics
import com.example.financehub.data.repository.TimeAggregation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import com.example.financehub.data.repository.TimeSeriesPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlin.time.Duration.Companion.milliseconds

/**
 * Data class representing a point in time with an expense amount
 */


data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


class AnalyticsViewModel(
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

    private val _selectedTimePeriod = MutableStateFlow<TimeAggregation>(
        TimeAggregation.CALENDAR_MONTH
    )
    val selectedTimePeriod: StateFlow<TimeAggregation> = _selectedTimePeriod

    // All available tags
    val availableTags: StateFlow<List<TagRef>> = repository.getMatchingTags("").stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val expensesAnalytics: StateFlow<ExpensesAnalytics> =
        combine(startDate, endDate, selectedTags) { start, end, tags ->
            Triple(start, end, tags)
        }.flatMapLatest { (start, end, tags) ->
            repository.getExpensesAnalysisDetails(start, end, tags)
                .onStart {
                    _isLoading.value = true
                }
                .onEach {
                    _isLoading.value = false
                }

        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExpensesAnalytics())

    @OptIn(ExperimentalCoroutinesApi::class)
    val expensesByTag: StateFlow<Map<String, Int>> =
        combine(startDate, endDate, selectedTags) { start, end, tags ->
            Triple(start, end, tags)
        }.flatMapLatest { (start, end, tags) ->
            repository.getExpensesByTag(start, end, tags)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private var _tagSearchText = MutableStateFlow<String>("")
    val tagSearchText = _tagSearchText.asStateFlow()
    fun updateTagSearchText(newQuery: String) {
        _tagSearchText.value = newQuery
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val matchingTags: StateFlow<List<TagRef>> = tagSearchText
        .debounce(300.milliseconds) // Wait for user to stop typing
        .flatMapLatest { query ->
            if (query.isBlank()) availableTags else repository.getMatchingTags(query)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


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
    val timeSeriesData: StateFlow<List<TimeSeriesPoint>> = combine(
        startDate,
        endDate,
        selectedTags,
        selectedTimePeriod
    ) { start, end, tags, aggregation ->
        Quadruple(start, end, tags, aggregation)
    }.flatMapLatest { (start, end, tags, aggregation) ->
        repository.getExpensesOverTime(start, end, tags)
            .map { timeSeriesData ->
                repository.aggregateTimeSeriesData(
                    timeSeriesData,
                    aggregation
                )
            }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )


    init {
        // Set default to current month
        setDateRangePreset(DateRangePreset.CURRENT_MONTH)

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
        _selectedTags.value = availableTags.value.map { it.tag }
    }

    fun setSelectedTimePeriod(timePeriod: TimeAggregation) {
        _selectedTimePeriod.value = timePeriod
    }
}