package com.example.financehub.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.financehub.data.database.models.TagRef

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val expenseID: Int = 0,
    val title: String,
    val amount: Int,
    val year: Int,
    val month: Int,
    val date: Int
)


data class ExpenseWithTags(
    @Embedded val expense: Expense,
    @Relation(
        parentColumn = "expenseID",
        entityColumn = "tagID",
        associateBy = Junction(
            value = ExpenseTagsCrossRef::class,
            parentColumn = "expenseID",
            entityColumn = "tagID"
        )
    )
    val tags: List<Tags>
)