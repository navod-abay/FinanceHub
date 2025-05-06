package com.example.financehub.data.dao
import androidx.paging.PagingSource
import androidx.room.*
import com.example.financehub.data.database.Expense
import com.example.financehub.data.database.ExpenseWithTags
import kotlinx.coroutines.flow.Flow


@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Query("SELECT * FROM expenses ORDER BY expenseID DESC")
    suspend fun getAllExpenses(): List<Expense>


    @Query("SELECT SUM(amount) FROM expenses WHERE year = :year AND month = :month")
    fun getTotalAmountForMonth(year: Int, month: Int): Flow<Int?>

    @Query("SELECT * FROM expenses ORDER BY year DESC, month DESC, date DESC")
    fun getPagedExpenses(): PagingSource<Int, Expense>

    @Transaction
    @Query("SELECT * FROM expenses ORDER BY year DESC, month DESC, date DESC")
    fun getPagedExpensesWithTags(): PagingSource<Int, ExpenseWithTags>

    @Update
    suspend fun updateExpense(expense: Expense)

    @Transaction
    @Query("""
        SELECT * FROM expenses 
        WHERE (year > :startYear OR (year = :startYear AND month > :startMonth) OR (year = :startYear AND month = :startMonth AND date >= :startDay))
        AND (year < :endYear OR (year = :endYear AND month < :endMonth) OR (year = :endYear AND month = :endMonth AND date <= :endDay))
        ORDER BY year DESC, month DESC, date DESC
    """)
    fun getExpensesWithTagsInDateRange(
        startYear: Int, startMonth: Int, startDay: Int,
        endYear: Int, endMonth: Int, endDay: Int
    ): Flow<List<ExpenseWithTags>>

    @Query("""
        SELECT * FROM expenses 
        WHERE (year > :startYear OR (year = :startYear AND month > :startMonth) OR (year = :startYear AND month = :startMonth AND date >= :startDay))
        AND (year < :endYear OR (year = :endYear AND month < :endMonth) OR (year = :endYear AND month = :endMonth AND date <= :endDay))
        ORDER BY year DESC, month DESC, date DESC
    """)
    fun getExpensesInDateRange(
        startYear: Int, startMonth: Int, startDay: Int,
        endYear: Int, endMonth: Int, endDay: Int
    ): Flow<List<ExpenseWithTags>>



}
