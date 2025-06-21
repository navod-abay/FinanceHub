package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index

@Entity(
    tableName = "targets",
    primaryKeys = ["month", "year", "tagID"],
    foreignKeys = [
        ForeignKey(
            entity = Tags::class,
            parentColumns = ["tagID"],
            childColumns = ["tagID"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tagID"])]
)
data class Target(
    @ColumnInfo(name = "month")
    val month: Int = 0,
    @ColumnInfo(name = "year")
    val year: Int = 0,
    @ColumnInfo(name = "tagID")
    val tagID: Int = 0,
    @ColumnInfo(name = "amount")
    val amount: Int = 0,
    @ColumnInfo(name = "spent")
    val spent: Int = 0
)
