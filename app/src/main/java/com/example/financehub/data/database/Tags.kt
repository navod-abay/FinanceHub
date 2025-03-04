package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["tag"], unique = true)]
)
data class Tags(
    @PrimaryKey(autoGenerate = true) val tagID: Int = 0,
    val tag: String = "",
    val monthlyAmount: Int = 0,
    val currentMonth: Int = 0,
    val currentYear: Int = 0,
    val createdDay: Int = 0,
    val createdMonth: Int = 0,
    val createdYear: Int = 0

)


data class TagWithAmount(val tag: String, val monthlyAmount: Int)