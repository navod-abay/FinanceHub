package com.example.financehub.data.dao


import androidx.room.*
import com.example.financehub.data.database.ExpenseTagsCrossRef

@Dao
interface ExpenseTagsCrossRefDao {
    @Insert
    suspend fun insertExpenseTagsCrossRef(ref: ExpenseTagsCrossRef)
}