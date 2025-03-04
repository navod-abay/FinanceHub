package com.example.financehub.data.dao
import androidx.room.*
import com.example.financehub.data.database.Expense
import kotlinx.coroutines.flow.Flow


@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Query("SELECT * FROM expenses ORDER BY expenseID DESC")
    suspend fun getAllExpenses(): List<Expense>


    @Query("SELECT SUM(amount) FROM expenses WHERE year = :year AND month = :month")
    fun getTotalAmountForMonth(year: Int, month: Int): Flow<Int?>


}
