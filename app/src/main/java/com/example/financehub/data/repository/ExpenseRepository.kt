package com.example.financehub.data.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.Transaction
import com.example.financehub.data.dao.ExpenseDao
import com.example.financehub.data.dao.ExpenseTagsCrossRefDao
import com.example.financehub.data.dao.TagsDao
import com.example.financehub.data.database.Expense
import com.example.financehub.data.database.ExpenseTagsCrossRef
import com.example.financehub.data.database.ExpenseWithTags
import com.example.financehub.data.database.TagWithAmount
import com.example.financehub.data.database.Tags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.Calendar

data class TimeSeriesPoint(
    val date: LocalDate,
    val amount: Int
)

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val tagDao: TagsDao,
    private val expenseTagsCrossRefDao: ExpenseTagsCrossRefDao,
) {
    @Transaction
    suspend fun insertExpense(expense: Expense, tags: Set<String>) {
        val expenseID = expenseDao.insertExpense(expense).toInt()
        tags.forEach { tag ->
           addTagforExpense(expenseID, expense.amount, tag)
        }

    }

    fun getPagedExpenses(): Flow<PagingData<Expense>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,          // Number of items per page
                enablePlaceholders = false,
                prefetchDistance = 5     // Start loading when 5 items from the end
            )
        ) {
            expenseDao.getPagedExpenses()
        }.flow
    }

    fun getCurrentMonthTotal(currentMonth: Int, currentYear: Int): Flow<Int> {
        return expenseDao.getTotalAmountForMonth(currentYear, currentMonth)
            .map { total ->
                if (total == null) {
                    Log.d("ExpenseRepository", "Total is null")
                    0
                } else {
                    total
                }
            }.map { total ->
                Log.d("ExpenseRepository", "Getting total for month: $currentMonth, year: $currentYear, total: $total")
                total
            }
    }
    fun getPagedExpensesWithTags(): Flow<PagingData<ExpenseWithTags>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 5
            )
        ) {
            expenseDao.getPagedExpensesWithTags()
        }.flow
    }

    fun getTopTagForMonth(currentMonth: Int, currentYear: Int): Flow<TagWithAmount> {
        return tagDao.getTopTagForMonth(currentMonth, currentYear).map {
            it ?: TagWithAmount("No tag", 0)
        }
    }

    fun getPagedTags(): Flow<PagingData<Tags>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 5
            )
        ) {
            tagDao.getPagedTags()
        }.flow
    }

    // Inside ExpenseRepository implementation
    suspend fun updateExpense(expense: Expense, addedTags: Set<String>, removedTags:Set<String>) {
        expenseDao.updateExpense(expense)

        removedTags.forEach { tagText ->
            removeTagFromExpense(expense.expenseID, expense.amount, tagText)
        }
        addedTags.forEach { tagText ->
            addTagforExpense(expense.expenseID, expense.amount, tagText)
        }
    }

    private suspend fun removeTagFromExpense(expenseID: Int, amount: Int, tagString: String) {
        val tag = tagDao.getTagbyTag(tagString)
        if (tag != null) {
            expenseTagsCrossRefDao.deleteExpenseTagsCrossRef(ExpenseTagsCrossRef(expenseID, tag.tagID))
            tagDao.decrementAmount(tag.tagID, amount)
        }
    }

    private suspend fun addTagforExpense(expenseID: Int, amount: Int, tag: String) {

            var tagID = tagDao.getTagIDbyTag(tag)
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH) // Calendar months are 0-based
            val currentYear = calendar.get(Calendar.YEAR)
            if (tagID == null) {
                val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
                tagID = tagDao.insertTag(
                    Tags(
                        tag = tag,
                        monthlyAmount = amount,
                        currentMonth = currentMonth,
                        currentYear = currentYear,
                        createdDay = currentDay,
                        createdMonth = currentMonth,
                        createdYear = currentYear
                    )
                ).toInt()
            } else {
                tagDao.incrementAmount(tagID, amount, currentMonth, currentYear)
            }
            expenseTagsCrossRefDao.insertExpenseTagsCrossRef(
                ExpenseTagsCrossRef(
                    expenseID = expenseID,
                    tagID = tagID
                )
            )

    }

    fun getMatchingTags(query: String): Flow<List<Tags>> {
        return tagDao.getMatchingTags(query)
    }

    /**
     * Filter expenses by date range and optional tag list
     * @param startDate The start date for filtering (inclusive)
     * @param endDate The end date for filtering (inclusive)
     * @param tagNames List of tag names to filter by (empty list means all tags)
     * @return Flow of filtered expenses with their tags
     */
    fun getFilteredExpenses(
        startDate: LocalDate,
        endDate: LocalDate,
        tagNames: List<String> = emptyList()
    ): Flow<List<ExpenseWithTags>> {
        // Convert dates to year, month, day components
        val startYear = startDate.year
        val startMonth = startDate.monthValue
        val startDay = startDate.dayOfMonth

        val endYear = endDate.year
        val endMonth = endDate.monthValue
        val endDay = endDate.dayOfMonth

        // If no tags are specified, get all expenses in date range
        if (tagNames.isEmpty()) {
            return expenseDao.getExpensesInDateRange(
                startYear, startMonth, startDay,
                endYear, endMonth, endDay
            )
        }

        // If tags are specified, filter by both date range and tags
        return expenseDao.getExpensesWithTagsInDateRange(
            startYear, startMonth, startDay,
            endYear, endMonth, endDay
        ).map { expenses ->
            // Filter expenses that have at least one of the specified tags
            expenses.filter { expenseWithTags ->
                expenseWithTags.tags.any { tag ->
                    tagNames.contains(tag.tag)
                }
            }
        }
    }

    /**
     * Get current month expenses
     * @param tagNames Optional list of tag names to filter by
     * @return Flow of current month expenses
     */
    fun getCurrentMonthExpenses(tagNames: List<String> = emptyList()): Flow<List<ExpenseWithTags>> {
        val today = LocalDate.now()
        val firstDayOfMonth = today.withDayOfMonth(1)
        return getFilteredExpenses(firstDayOfMonth, today, tagNames)
    }

    /**
     * Calculate total amount from filtered expenses
     * @param expenses List of expenses to calculate total from
     * @return Total amount
     */
    fun calculateTotalAmount(expenses: List<ExpenseWithTags>): Int {
        return expenses.sumOf { it.expense.amount }
    }

    /**
     * Calculate average monthly amount from filtered expenses
     * @param expenses List of expenses to calculate from
     * @param startDate Start date of the filter period
     * @param endDate End date of the filter period
     * @return Average monthly amount
     */
    fun calculateMonthlyAverage(
        expenses: List<ExpenseWithTags>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Double {
        if (expenses.isEmpty()) return 0.0

        val totalAmount = calculateTotalAmount(expenses)

        // Calculate number of months (including partial months)
        val months = if (startDate.year == endDate.year && startDate.monthValue == endDate.monthValue) {
            1.0 // Same month
        } else {
            val yearDiff = endDate.year - startDate.year
            val monthDiff = endDate.monthValue - startDate.monthValue
            (yearDiff * 12 + monthDiff + 1).toDouble()
        }

        return totalAmount / months
    }

    /**
     * Get all available tags in the database
     * @return Flow of all tags
     */
    fun getAllTags(): Flow<List<Tags>> {
        return tagDao.getAllTags()
    }

    /**
     * Get expenses grouped by tag for the filtered period
     * @param startDate The start date for filtering
     * @param endDate The end date for filtering
     * @param tagNames Optional list of tags to filter by
     * @return Flow of tag to amount pairs
     */
    fun getExpensesByTag(
        startDate: LocalDate,
        endDate: LocalDate,
        tagNames: List<String> = emptyList()
    ): Flow<Map<String, Int>> {
        return getFilteredExpenses(startDate, endDate, tagNames).map { expenses ->
            // Create a map of tag name to total amount
            val tagAmountMap = mutableMapOf<String, Int>()

            expenses.forEach { expenseWithTags ->
                val amount = expenseWithTags.expense.amount

                // If the expense has no tags, add it to an "Untagged" category
                if (expenseWithTags.tags.isEmpty()) {
                    val currentAmount = tagAmountMap["Untagged"] ?: 0
                    tagAmountMap["Untagged"] = currentAmount + amount
                } else {
                    // Add the amount to each tag's total
                    expenseWithTags.tags.forEach { tag ->
                        val currentAmount = tagAmountMap[tag.tag] ?: 0
                        tagAmountMap[tag.tag] = currentAmount + amount
                    }
                }
            }

            tagAmountMap
        }
    }

    /**
     * Get expenses over time for time series visualization
     * @param startDate The start date for filtering
     * @param endDate The end date for filtering
     * @param tagNames Optional list of tags to filter by
     * @return Flow of time series data points
     */
    fun getExpensesOverTime(
        startDate: LocalDate,
        endDate: LocalDate,
        tagNames: List<String> = emptyList()
    ): Flow<List<TimeSeriesPoint>> {
        return getFilteredExpenses(startDate, endDate, tagNames).map { expenses ->
            // Group expenses by date
            val dateMap = mutableMapOf<LocalDate, Int>()

            expenses.forEach { expenseWithTags ->
                val expense = expenseWithTags.expense
                val date = LocalDate.of(expense.year, expense.month, expense.date)
                val amount = expense.amount

                // If any tag filter is specified, check if this expense has at least one of the specified tags
                if (tagNames.isEmpty() || expenseWithTags.tags.any { tag -> tagNames.contains(tag.tag) } ||
                    (expenseWithTags.tags.isEmpty() && tagNames.contains("Untagged"))) {
                    val currentAmount = dateMap[date] ?: 0
                    dateMap[date] = currentAmount + amount
                }
            }

            // Convert to list of time series points
            dateMap.map { (date, amount) ->
                TimeSeriesPoint(date, amount)
            }
        }
    }

    /**
     * Aggregate expenses by time period (daily, weekly, monthly)
     * @param timeSeriesData The raw time series data
     * @param aggregation The aggregation period (DAILY, WEEKLY, MONTHLY)
     * @return Aggregated time series data
     */
    fun aggregateTimeSeriesData(
        timeSeriesData: List<TimeSeriesPoint>,
        aggregation: TimeAggregation
    ): List<TimeSeriesPoint> {
        if (timeSeriesData.isEmpty()) return emptyList()

        return when (aggregation) {
            TimeAggregation.DAILY -> timeSeriesData
            TimeAggregation.WEEKLY -> {
                // Group by week
                timeSeriesData.groupBy { point ->
                    // Get the first day of the week
                    point.date.minusDays(point.date.dayOfWeek.value.toLong() - 1)
                }.map { (weekStart, points) ->
                    TimeSeriesPoint(
                        date = weekStart,
                        amount = points.sumOf { it.amount }
                    )
                }
            }
            TimeAggregation.MONTHLY -> {
                // Group by month
                timeSeriesData.groupBy { point ->
                    // Get the first day of the month
                    point.date.withDayOfMonth(1)
                }.map { (monthStart, points) ->
                    TimeSeriesPoint(
                        date = monthStart,
                        amount = points.sumOf { it.amount }
                    )
                }
            }
        }
    }

    /**
     * Aggregation periods for time series data
     */
    enum class TimeAggregation {
        DAILY, WEEKLY, MONTHLY
    }

}
