package com.example.financehub.data.dao


import androidx.room.*
import com.example.financehub.data.database.ExpenseTagsCrossRef
import com.example.financehub.data.database.Tags

@Dao
interface ExpenseTagsCrossRefDao {
    @Insert
    suspend fun insertExpenseTagsCrossRef(ref: ExpenseTagsCrossRef)

    @Query("DELETE FROM expense_tags WHERE expenseID = :expenseId")
    suspend fun deleteExpenseTagCrossRefs(expenseId: Int)

    @Query("SELECT * FROM tags INNER JOIN expense_tags ON tags.tagID = expense_tags.tagID WHERE expense_tags.expenseID = :expenseID")
    suspend fun getTagsForExpense(expenseID: Int): List<Tags>
}