package com.example.financehub.data.dao

import androidx.room.*
import com.example.financehub.data.database.Wishlist
import com.example.financehub.data.database.WishlistWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist WHERE pendingSync = 0 AND syncOperation IS NULL ORDER BY createdAt DESC")
    fun getAllWishlistItems(): Flow<List<WishlistWithTags>>
    
    @Query("SELECT * FROM wishlist WHERE id = :id")
    suspend fun getWishlistItemById(id: Int): Wishlist?
    
    @Query("SELECT * FROM wishlist WHERE serverId = :serverId LIMIT 1")
    suspend fun getWishlistItemByServerId(serverId: String): Wishlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWishlist(item: Wishlist): Long

    @Update
    suspend fun updateWishlist(item: Wishlist)

    @Delete
    suspend fun deleteWishlist(item: Wishlist)
    
    @Query("UPDATE wishlist SET pendingSync = 1, syncOperation = :operation, updatedAt = :timestamp WHERE id = :id")
    suspend fun markForSync(id: Int, operation: String, timestamp: Long)
    
    @Query("SELECT * FROM wishlist WHERE pendingSync = 1")
    suspend fun getPendingSyncWishlist(): List<Wishlist>
    
    @Query("UPDATE wishlist SET serverId = :serverId, lastSyncedAt = :lastSyncedAt, pendingSync = :pendingSync, syncOperation = :syncOperation WHERE id = :id")
    suspend fun updateSyncMetadata(id: Int, serverId: String?, lastSyncedAt: Long, pendingSync: Boolean, syncOperation: String?)
    
    @Query("UPDATE wishlist SET name = :name, minPrice = :minPrice, maxPrice = :maxPrice, updatedAt = :updatedAt, lastSyncedAt = :lastSyncedAt WHERE id = :id")
    suspend fun updateFromServer(id: Int, name: String, minPrice: Int, maxPrice: Int, updatedAt: Long, lastSyncedAt: Long)
    
    @Query("DELETE FROM wishlist WHERE updatedAt < :timestamp")
    suspend fun deleteOldWishlist(timestamp: Long)
}
