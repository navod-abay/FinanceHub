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
    val spent: Int = 0,
    
    // Sync metadata
    @ColumnInfo(name = "serverId")
    val serverId: String? = null,                    // Server's UUID for this target
    @ColumnInfo(name = "lastSyncedAt")
    val lastSyncedAt: Long? = null,                 // When this item was last synced successfully
    @ColumnInfo(name = "pendingSync")
    val pendingSync: Boolean = false,               // True if local changes need to be pushed to server
    @ColumnInfo(name = "syncOperation")
    val syncOperation: String? = null,              // "CREATE", "UPDATE", "DELETE" - what operation is pending
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(), // When created locally
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis()  // When last modified locally
)
