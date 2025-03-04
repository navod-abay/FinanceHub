package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "expense_tags",
    primaryKeys = ["expenseID", "tagID"],
    foreignKeys = [
        ForeignKey(entity = Expense::class, parentColumns = ["expenseID"], childColumns = ["expenseID"]),
        ForeignKey(entity = Tags::class, parentColumns = ["tagID"], childColumns = ["tagID"])
    ]
)
data class ExpenseTagsCrossRef(
    val expenseID: Int,
    val tagID: Int
)
