package com.example.financehub.data.dao

import androidx.room.*
import com.example.financehub.data.database.SyncGroup

/**
 * DAO for SyncGroup operations.
 * Manages atomic sync groups and their lifecycle.
 */
@Dao
interface SyncGroupDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(syncGroup: SyncGroup): Long
    
    @Update
    suspend fun update(syncGroup: SyncGroup)
    
    @Query("SELECT * FROM sync_groups WHERE groupId = :groupId")
    suspend fun getById(groupId: Long): SyncGroup?
    
    @Query("SELECT * FROM sync_groups WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: String): List<SyncGroup>
    
    @Query("SELECT * FROM sync_groups WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingGroups(): List<SyncGroup>
    
    @Query("SELECT * FROM sync_groups WHERE status = 'FAILED' ORDER BY createdAt ASC")
    suspend fun getFailedGroups(): List<SyncGroup>
    
    @Query("UPDATE sync_groups SET status = :status, syncedAt = :syncedAt, errorMessage = :errorMessage WHERE groupId = :groupId")
    suspend fun updateStatus(groupId: Long, status: String, syncedAt: Long?, errorMessage: String?)
    
    @Query("DELETE FROM sync_groups WHERE status = 'SUCCESS' AND syncedAt < :cutoffTime")
    suspend fun deleteSuccessfulBefore(cutoffTime: Long)
    
    @Delete
    suspend fun delete(syncGroup: SyncGroup)
}
