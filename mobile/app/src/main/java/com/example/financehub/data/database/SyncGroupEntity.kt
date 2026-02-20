package com.example.financehub.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Junction table linking sync groups to entities.
 * Each entity in a group must be synced together atomically.
 */
@Entity(
    tableName = "sync_group_entities",
    foreignKeys = [
        ForeignKey(
            entity = SyncGroup::class,
            parentColumns = ["groupId"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["entityType", "localId"])
    ]
)
data class SyncGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val entityType: String,                      // "expense", "tag", "target", "expense_tag", etc.
    val localId: Long,                           // Local database ID
    val operation: String                        // "CREATE", "UPDATE", "DELETE"
)
