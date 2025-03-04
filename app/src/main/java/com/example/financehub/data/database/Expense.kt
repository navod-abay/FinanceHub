package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val expenseID: Int = 0,
    val title: String,
    val amount: Int,
    val year: Int,
    val month: Int,
    val date: Int
)
