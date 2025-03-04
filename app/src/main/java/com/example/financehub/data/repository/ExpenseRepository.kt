package com.example.financehub.data.repository

import android.util.Log
import androidx.room.Transaction
import com.example.financehub.data.dao.ExpenseDao
import com.example.financehub.data.dao.ExpenseTagsCrossRefDao
import com.example.financehub.data.dao.TagsDao
import com.example.financehub.data.database.Expense
import com.example.financehub.data.database.ExpenseTagsCrossRef
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
            run {
                var tagID = tagDao.getTagIDbyTag(tag)
                val calendar = Calendar.getInstance()
                val currentMonth = calendar.get(Calendar.MONTH) // Calendar months are 0-based
                val currentYear = calendar.get(Calendar.YEAR)
                if (tagID == null) {
                    val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
                    tagID = tagDao.insertTag(
                        Tags(
                            tag = tag,
                            monthlyAmount = expense.amount,
                            currentMonth = currentMonth,
                            currentYear = currentYear,
                            createdDay = currentDay,
                            createdMonth = currentMonth,
                            createdYear = currentYear
                        )
                    ).toInt()
                } else {
                    tagDao.incrementAmount(tagID, expense.amount, currentMonth, currentYear)
                }
                expenseTagsCrossRefDao.insertExpenseTagsCrossRef(
                    ExpenseTagsCrossRef(
                        expenseID = expenseID,
                        tagID = tagID
                    )
                )
            }
        }

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

    suspend fun getAllExpenses(): List<Expense> {
        return expenseDao.getAllExpenses()
    }

    fun getTopTagForMonth(currentMonth: Int, currentYear: Int): Flow<TagWithAmount> {
        return tagDao.getTopTagForMonth(currentMonth, currentYear).map {
            it ?: TagWithAmount("No tag", 0)
        }
    }
}
