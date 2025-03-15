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
import java.util.Calendar

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
}
