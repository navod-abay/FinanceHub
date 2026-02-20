package com.example.financehub.data.dao

import androidx.room.*
import com.example.financehub.data.database.SyncGroupEntity

/**
 * DAO for SyncGroupEntity operations.
 * Manages the junction table between sync groups and entities.
 */
@Dao
interface SyncGroupEntityDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(syncGroupEntity: SyncGroupEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(syncGroupEntities: List<SyncGroupEntity>)
    
    @Query("SELECT * FROM sync_group_entities WHERE groupId = :groupId")
    suspend fun getEntitiesForGroup(groupId: Long): List<SyncGroupEntity>
    
    @Query("SELECT * FROM sync_group_entities WHERE entityType = :entityType AND localId = :localId")
    suspend fun getGroupsForEntity(entityType: String, localId: Long): List<SyncGroupEntity>
    
    @Query("DELETE FROM sync_group_entities WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: Long)
    
    @Delete
    suspend fun delete(syncGroupEntity: SyncGroupEntity)
}
