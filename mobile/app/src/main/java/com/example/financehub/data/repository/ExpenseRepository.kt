package com.example.financehub.data.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.Transaction
import com.example.financehub.data.dao.ExpenseDao
import com.example.financehub.data.dao.ExpenseTagsCrossRefDao
import com.example.financehub.data.dao.GraphEdgeDAO
import com.example.financehub.data.dao.TagRefDao
import com.example.financehub.data.dao.TagsDao
import com.example.financehub.data.dao.TargetDao
import com.example.financehub.data.database.Expense
import com.example.financehub.data.database.ExpenseTagsCrossRef
import com.example.financehub.data.database.ExpenseWithTags
import com.example.financehub.data.database.GraphEdge
import com.example.financehub.data.database.TagWithAmount
import com.example.financehub.data.database.Tags
import com.example.financehub.data.database.Target
import com.example.financehub.data.database.models.TagRef
import com.example.financehub.viewmodel.TargetWithTag
import com.example.financehub.viewmodel.FilterParams
import com.example.financehub.sync.SyncManager
import com.example.financehub.sync.SyncEntityType
import com.example.financehub.sync.SyncOperation
import com.example.financehub.sync.SyncResult
import com.example.financehub.sync.ConnectivityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private val targetDao: TargetDao,
    private val graphEdgeDAO: GraphEdgeDAO,
    private val tagRefDao: TagRefDao,
    private val syncManager: SyncManager
) : ExpenseRepositoryInterface{

    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private val _hasPendingSync = MutableStateFlow(false)
    val hasPendingSync: StateFlow<Boolean> = _hasPendingSync.asStateFlow()

    init {
        repositoryScope.launch {
            val hasPending = hasPendingSync()
            _hasPendingSync.value = hasPending
            // Sync the count with ConnectivityState
            ConnectivityState.updatePendingSyncCount(if (hasPending) 1 else 0)
        }
    }

    @Transaction
    suspend fun insertExpense(expense: Expense, tags: Set<TagRef>, newTags: List<String>){
        // Insert expense with sync metadata
        val expenseWithSync = expense.copy(
            pendingSync = true,
            syncOperation = "CREATE",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val expenseID = expenseDao.insertExpense(expenseWithSync).toInt()
        
        // Mark expense for sync
        syncManager.markForSync(SyncEntityType.EXPENSE, expenseID.toString(), SyncOperation.CREATE)
        _hasPendingSync.value = true
        ConnectivityState.updatePendingSyncCount(1)
        
        tags.forEach { tag ->
            addExistingTagforExpense(expenseID, expenseWithSync, tag)
        }
        newTags.forEach { tag ->
            addNewTagforExpense(expenseID, expenseWithSync, tag)
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
                Log.d(
                    "ExpenseRepository",
                    "Getting total for month: $currentMonth, year: $currentYear, total: $total"
                )
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
    suspend fun updateExpense(expense: Expense, addedExistingTags: Set<TagRef>, removedTags: Set<TagRef>, addedNewTags: List<String>) {
        // Update expense with sync metadata
        val expenseWithSync = expense.copy(
            pendingSync = true,
            syncOperation = "UPDATE",
            updatedAt = System.currentTimeMillis()
        )
        expenseDao.updateExpense(expenseWithSync)
        
        // Mark expense for sync
        syncManager.markForSync(SyncEntityType.EXPENSE, expense.expenseID.toString(), SyncOperation.UPDATE)
        _hasPendingSync.value = true
        ConnectivityState.updatePendingSyncCount(1)

        removedTags.forEach { tagRef ->
            removeTagFromExpense(expense.expenseID, expense.amount, tagRef)
        }
        addedExistingTags.forEach { tagRef ->
            addExistingTagforExpense(expense.expenseID, expenseWithSync, tagRef)
        }
        addedNewTags.forEach { tagText ->
            addNewTagforExpense(expense.expenseID, expenseWithSync, tagText)
        }
    }

    private suspend fun removeTagFromExpense(expenseID: Int, amount: Int, tag: TagRef) {
        // Mark expense-tag relationship for deletion sync
        syncManager.markForSync(SyncEntityType.EXPENSE_TAG, "$expenseID-${tag.tagID}", SyncOperation.DELETE)
        _hasPendingSync.value = true
        ConnectivityState.updatePendingSyncCount(1)
        
        expenseTagsCrossRefDao.deleteExpenseTagsCrossRef(
            ExpenseTagsCrossRef(
                expenseID,
                tag.tagID
            )
        )
        
        // Update tag amount and mark for sync
        tagDao.decrementAmount(tag.tagID, amount)
        syncManager.markForSync(SyncEntityType.TAG, tag.tagID.toString(), SyncOperation.UPDATE)
    }

    private suspend fun addNewTagforExpense(expenseID: Int, expense: Expense, tag: String) {
        // Create tag with sync metadata
        val newTag = Tags(
            tag = tag,
            monthlyAmount = expense.amount,
            currentMonth = expense.month,
            currentYear = expense.year,
            createdDay = expense.date,
            createdMonth = expense.month,
            createdYear = expense.year,
            pendingSync = true,
            syncOperation = "CREATE",
            syncCreatedAt = System.currentTimeMillis(),
            syncUpdatedAt = System.currentTimeMillis()
        )
        val tagID = tagDao.insertTag(newTag).toInt()
        
        // Mark tag for sync
        syncManager.markForSync(SyncEntityType.TAG, tagID.toString(), SyncOperation.CREATE)
        _hasPendingSync.value = true
        ConnectivityState.updatePendingSyncCount(1)
        
        // Create expense-tag relationship with sync metadata
        val expenseTagRef = ExpenseTagsCrossRef(
            expenseID = expenseID,
            tagID = tagID,
            pendingSync = true,
            syncOperation = "CREATE",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        expenseTagsCrossRefDao.insertExpenseTagsCrossRef(expenseTagRef)
        
        // Mark expense-tag relationship for sync
        syncManager.markForSync(SyncEntityType.EXPENSE_TAG, "$expenseID-$tagID", SyncOperation.CREATE)
    }

    private suspend fun addExistingTagforExpense(expenseID: Int, expense: Expense, tag: TagRef) {
        // Update tag amount and mark for sync
        tagDao.incrementAmount(tag.tagID, expense.amount, expense.month, expense.year)
        syncManager.markForSync(SyncEntityType.TAG, tag.tagID.toString(), SyncOperation.UPDATE)
        _hasPendingSync.value = true
        ConnectivityState.updatePendingSyncCount(1)
        
        // Update target if exists and mark for sync
        targetDao.getTarget(expense.month, expense.year, tag.tagID)?.let { target ->
            targetDao.incrementSpentAmount(expense.month, expense.year, tag.tagID, expense.amount)
            syncManager.markForSync(SyncEntityType.TARGET, "${expense.month}-${expense.year}-${tag.tagID}", SyncOperation.UPDATE)
        }
        
        // Create expense-tag relationship with sync metadata
        val expenseTagRef = ExpenseTagsCrossRef(
            expenseID = expenseID,
            tagID = tag.tagID,
            pendingSync = true,
            syncOperation = "CREATE",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        expenseTagsCrossRefDao.insertExpenseTagsCrossRef(expenseTagRef)
        
        // Mark expense-tag relationship for sync
        syncManager.markForSync(SyncEntityType.EXPENSE_TAG, "$expenseID-${tag.tagID}", SyncOperation.CREATE)
    }

    fun getMatchingTags(query: String): Flow<List<TagRef>> {
        return tagRefDao.getMatchingTags(query)
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
        val months =
            if (startDate.year == endDate.year && startDate.monthValue == endDate.monthValue) {
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
    override fun getAllTags(): Flow<List<Tags>> {
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
                    // Add the amount to each tag's total, but ignore tags in tagNames
                    expenseWithTags.tags.forEach { tag ->
                        if (!tagNames.contains(tag.tag)) {
                            val currentAmount = tagAmountMap[tag.tag] ?: 0
                            tagAmountMap[tag.tag] = currentAmount + amount
                        }
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

    // Add a new target to the database with sync support
    suspend fun addTarget(month: Int, year: Int, tag: TagRef, amount: Int) {
        val tagID = tag.tagID
        // Sum existing expenses for this tag in given month/year to initialize spent
        val existingSpent = expenseDao.getSumForTagMonthYear(tagID, month, year)
        val target = Target(
            month = month,
            year = year,
            tagID = tagID,
            amount = amount,
            spent = existingSpent,
            pendingSync = true,
            syncOperation = "CREATE",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        targetDao.insertTarget(target)
        
        // Mark target for sync
        syncManager.markForSync(SyncEntityType.TARGET, "$month-$year-$tagID", SyncOperation.CREATE)
        _hasPendingSync.value = true
        ConnectivityState.updatePendingSyncCount(1)
    }

    fun getAllTargetsWithTagsFromCurrentMonth(): Flow<List<TargetWithTag>> {
        return flow {
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
            val currentYear = calendar.get(Calendar.YEAR)
            val allTags = tagDao.getAllTags().first()
            val allTargets = targetDao.getAllTargetsFromCurrentMonth(currentMonth, currentYear)
            val targetsWithTags = allTargets.mapNotNull { target ->
                val tag = allTags.find { it.tagID == target.tagID }
                if (tag != null) TargetWithTag(tag, target) else null
            }
            emit(targetsWithTags)
        }
    }

    fun getMonthlyTargetsStats(): Flow<Pair<Int, Int>> {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH) + 1
        val year = calendar.get(Calendar.YEAR)
        return combine(
            targetDao.getMissedTargetsCount(month, year),
            targetDao.getTotalTargetsCount(month, year)
        ) { missed, total -> missed to total }
    }


    suspend fun deleteTarget(target: Target) {
        // Mark target for deletion sync
        syncManager.markForSync(SyncEntityType.TARGET, "${target.month}-${target.year}-${target.tagID}", SyncOperation.DELETE)
        _hasPendingSync.value = true
        ConnectivityState.updatePendingSyncCount(1)
        
        targetDao.deleteTarget(target.month, target.year, target.tagID)
    }

    suspend fun updateTargetAmount(target: Target, newAmount: Int) {
        // Mark target for update sync
        syncManager.markForSync(SyncEntityType.TARGET, "${target.month}-${target.year}-${target.tagID}", SyncOperation.UPDATE)
        _hasPendingSync.value = true
        ConnectivityState.updatePendingSyncCount(1)
        
        targetDao.updateTargetAmount(target.month, target.year, target.tagID, newAmount)
    }

    fun getPagedFilteredExpenses(params: FilterParams): Flow<PagingData<ExpenseWithTags>> {
        val range = params.resolveDateRange()
        val tagIds =
            if (params.requiredTagIds.isEmpty()) listOf(-1) else params.requiredTagIds.map { it }
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 5
            )
        ) {
            expenseDao.getPagedExpensesFiltered(
                range.startYear, range.startMonth, range.startDay,
                range.endYear, range.endMonth, range.endDay,
                tagIds,
                params.requiredTagIds.size
            )
        }.flow
    }

    override suspend fun getAllGraphEdges(): Flow<List<GraphEdge>> {
        return graphEdgeDAO.getAllGraphEdges()
    }

    override suspend fun getAllTagRefs(): Flow<List<TagRef>> {
        return tagRefDao.getAllTagRefs()
    }

    // Additional sync-aware methods
    
    /**
     * Delete an expense and mark for sync
     */
    suspend fun deleteExpense(expense: Expense) {
        // Mark expense for deletion sync
        syncManager.markForSync(SyncEntityType.EXPENSE, expense.expenseID.toString(), SyncOperation.DELETE)
        _hasPendingSync.value = true
        ConnectivityState.updatePendingSyncCount(1)
        
        // Get associated tags to update their amounts
        val tags = expenseTagsCrossRefDao.getTagsForExpense(expense.expenseID)
        tags.forEach { tag ->
            // Decrement tag amount
            tagDao.decrementAmount(tag.tagID, expense.amount)
            syncManager.markForSync(SyncEntityType.TAG, tag.tagID.toString(), SyncOperation.UPDATE)
            
            // Update target spent amount if exists
            targetDao.getTarget(expense.month, expense.year, tag.tagID)?.let { target ->
                val newSpent = maxOf(0, target.spent - expense.amount)
                targetDao.updateTargetSpent(expense.month, expense.year, tag.tagID, newSpent)
                syncManager.markForSync(SyncEntityType.TARGET, "${expense.month}-${expense.year}-${tag.tagID}", SyncOperation.UPDATE)
            }
        }
        
        // Delete expense-tag relationships and mark for sync
        expenseTagsCrossRefDao.deleteExpenseTagCrossRefs(expense.expenseID)
        tags.forEach { tag ->
            syncManager.markForSync(SyncEntityType.EXPENSE_TAG, "${expense.expenseID}-${tag.tagID}", SyncOperation.DELETE)
        }
        
        // Note: We don't actually delete the expense from local DB immediately
        // Instead, we mark it as deleted and let sync handle it
        expenseDao.markForSync(expense.expenseID, "DELETE", System.currentTimeMillis())
    }

    /**
     * Get sync state for the repository
     */
    override fun getSyncState(): Flow<SyncRepositoryState> {
        return syncManager.syncState.map { syncState ->
            when (syncState) {
                com.example.financehub.sync.SyncState.IDLE -> SyncRepositoryState.IDLE
                com.example.financehub.sync.SyncState.SYNCING -> SyncRepositoryState.SYNCING
                com.example.financehub.sync.SyncState.ERROR -> SyncRepositoryState.ERROR
            }
        }
    }

    /**
     * Get sync progress
     */
    override fun getSyncProgress() = syncManager.syncProgress

    /**
     * Trigger manual sync
     */
    override suspend fun triggerSync(): SyncRepositoryResult {
        val result = syncManager.performFullSync()
        if (result is SyncResult.Success) {
            _hasPendingSync.value = false
            ConnectivityState.updatePendingSyncCount(0)
        }
        return when (result) {
            is SyncResult.Success -> SyncRepositoryResult.Success(result.message)
            is SyncResult.Failure -> SyncRepositoryResult.Error(result.error)
        }
    }

    /**
     * Check if there are pending sync operations
     */
    override suspend fun hasPendingSync(): Boolean {
        val pendingExpenses = expenseDao.getPendingSyncExpenses()
        val pendingTags = tagDao.getPendingSyncTags()
        val pendingTargets = targetDao.getPendingSyncTargets()
        val pendingExpenseTags = expenseTagsCrossRefDao.getPendingSyncExpenseTags()
        val pendingGraphEdges = graphEdgeDAO.getPendingSyncGraphEdges()
        
        return pendingExpenses.isNotEmpty() || 
               pendingTags.isNotEmpty() || 
               pendingTargets.isNotEmpty() || 
               pendingExpenseTags.isNotEmpty() || 
               pendingGraphEdges.isNotEmpty()
    }
}
