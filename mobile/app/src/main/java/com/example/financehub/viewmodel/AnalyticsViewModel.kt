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
import com.example.financehub.data.repository.TagTimeSeries
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlin.time.Duration.Companion.milliseconds

/**
 * Data class representing a point in time with an expense amount
 */


/**
 * One line on the expense time chart.
 * @param colorIndex position in the shared chart palette
 */
data class ChartSeries(
    val label: String,
    val points: List<TimeSeriesPoint>,
    val colorIndex: Int
)


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
        TimeAggregation.DAILY
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


    // ---- Expense time chart state ----

    /** null = follow the automatic default (Total + top tags + Other) */
    private val _chartSeriesSelection = MutableStateFlow<Set<String>?>(null)
    val chartSeriesSelection: StateFlow<Set<String>?> = _chartSeriesSelection

    private val _isCumulative = MutableStateFlow(false)
    val isCumulative: StateFlow<Boolean> = _isCumulative

    /**
     * Raw chart input: the overall total series plus one series per tag, both restricted to the
     * expenses the date/tag filters select. Kept un-aggregated so the aggregation dropdown can
     * re-bucket without re-querying.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val chartSource: StateFlow<Pair<List<TimeSeriesPoint>, List<TagTimeSeries>>> =
        combine(startDate, endDate, selectedTags) { start, end, tags ->
            Triple(start, end, tags)
        }.flatMapLatest { (start, end, tags) ->
            combine(
                repository.getExpensesOverTime(start, end, tags),
                repository.getExpensesByTagOverTime(start, end, tags)
            ) { total, byTag -> total to byTag }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList<TimeSeriesPoint>() to emptyList()
        )

    /** Everything the user may plot: Total first, then tags by how often they occur, then Other. */
    val availableChartSeries: StateFlow<List<String>> = chartSource
        .map { (_, byTag) ->
            val tags = byTag.sortedByDescending { it.expenseCount }.map { it.label }
            buildList {
                add(TOTAL_SERIES)
                addAll(tags)
                if (tags.isNotEmpty()) add(OTHER_SERIES)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(TOTAL_SERIES))

    /** The render model for [com.example.financehub.ui.ExpenseTimeChart]. */
    val chartSeries: StateFlow<List<ChartSeries>> = combine(
        chartSource,
        selectedTimePeriod,
        _chartSeriesSelection,
        _isCumulative
    ) { (total, byTag), aggregation, selection, cumulative ->
        buildChartSeries(total, byTag, aggregation, selection, cumulative)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildChartSeries(
        total: List<TimeSeriesPoint>,
        byTag: List<TagTimeSeries>,
        aggregation: TimeAggregation,
        selection: Set<String>?,
        cumulative: Boolean
    ): List<ChartSeries> {
        if (total.isEmpty() && byTag.isEmpty()) return emptyList()

        val orderedTags = byTag.sortedByDescending { it.expenseCount }
        val available = buildList {
            add(TOTAL_SERIES)
            addAll(orderedTags.map { it.label })
            if (orderedTags.isNotEmpty()) add(OTHER_SERIES)
        }
        // An explicit empty selection means the user hid every line - respect it rather than
        // springing the whole default set back. "Reset" is how the default comes back.
        val selected = selection ?: defaultSeriesSelection(available)
        if (selected.isEmpty()) return emptyList()

        val raw = mutableListOf<Pair<String, List<TimeSeriesPoint>>>()
        if (selected.contains(TOTAL_SERIES)) raw.add(TOTAL_SERIES to total)
        orderedTags.filter { selected.contains(it.label) }.forEach { raw.add(it.label to it.points) }

        if (selected.contains(OTHER_SERIES)) {
            val rolledUp = orderedTags.filterNot { selected.contains(it.label) }
            if (rolledUp.isNotEmpty()) {
                val merged = mutableMapOf<LocalDate, Int>()
                rolledUp.forEach { series ->
                    series.points.forEach { point ->
                        merged[point.date] = (merged[point.date] ?: 0) + point.amount
                    }
                }
                raw.add(OTHER_SERIES to merged.map { (date, amount) -> TimeSeriesPoint(date, amount) })
            }
        }

        // Bucket every series the same way, then put them on a shared x axis so the lines line up
        val aggregated = raw.map { (label, points) ->
            label to repository.aggregateTimeSeriesData(points, aggregation)
        }
        val axis = aggregated.flatMap { (_, points) -> points.map { it.date } }.distinct().sorted()
        if (axis.isEmpty()) return emptyList()

        return aggregated.mapIndexed { index, (label, points) ->
            val byDate = points.associateBy { it.date }
            val normalized = axis.map { date -> TimeSeriesPoint(date, byDate[date]?.amount ?: 0) }
            ChartSeries(
                label = label,
                points = if (cumulative) repository.accumulate(normalized) else normalized,
                colorIndex = index
            )
        }
    }

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

    fun setChartSeriesSelection(labels: Set<String>) {
        _chartSeriesSelection.value = labels
    }

    fun toggleChartSeries(label: String) {
        val current = _chartSeriesSelection.value ?: defaultSeriesSelection(availableChartSeries.value)
        _chartSeriesSelection.value = if (current.contains(label)) current - label else current + label
    }

    fun resetChartSeriesSelection() {
        _chartSeriesSelection.value = null
    }

    fun setCumulative(cumulative: Boolean) {
        _isCumulative.value = cumulative
    }

    companion object {
        const val TOTAL_SERIES = "Total"
        const val OTHER_SERIES = "Other"

        /** Total + the 7 most commonly occurring tags + Other = 9 series. */
        private const val MAX_TAG_SERIES = 7

        /**
         * @param available series labels, already ordered: Total first, then tags by occurrence desc, then Other
         */
        fun defaultSeriesSelection(available: List<String>): Set<String> {
            val tags = available.filter { it != TOTAL_SERIES && it != OTHER_SERIES }
            val top = tags.take(MAX_TAG_SERIES)
            val selection = mutableSetOf(TOTAL_SERIES)
            selection.addAll(top)
            // Only offer Other when there are tags it would actually roll up
            if (tags.size > top.size) selection.add(OTHER_SERIES)
            return selection
        }
    }
}