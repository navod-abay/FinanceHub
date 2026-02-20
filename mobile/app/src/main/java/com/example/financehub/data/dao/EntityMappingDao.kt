package com.example.financehub.data.dao

import androidx.room.*
import com.example.financehub.data.database.EntityMapping

/**
 * DAO for EntityMapping operations.
 * Manages mappings between local IDs and server UUIDs.
 */
@Dao
interface EntityMappingDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entityMapping: EntityMapping): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entityMappings: List<EntityMapping>)
    
    @Query("SELECT * FROM entity_mappings WHERE entityType = :entityType AND localId = :localId")
    suspend fun getMapping(entityType: String, localId: Long): EntityMapping?
    
    @Query("SELECT serverId FROM entity_mappings WHERE entityType = :entityType AND localId = :localId")
    suspend fun getServerId(entityType: String, localId: Long): String?
    
    @Query("SELECT localId FROM entity_mappings WHERE entityType = :entityType AND serverId = :serverId")
    suspend fun getLocalId(entityType: String, serverId: String): Long?
    
    @Query("SELECT * FROM entity_mappings WHERE entityType = :entityType")
    suspend fun getMappingsForType(entityType: String): List<EntityMapping>
    
    @Query("DELETE FROM entity_mappings WHERE entityType = :entityType AND localId = :localId")
    suspend fun deleteMapping(entityType: String, localId: Long)
    
    @Delete
    suspend fun delete(entityMapping: EntityMapping)
}
