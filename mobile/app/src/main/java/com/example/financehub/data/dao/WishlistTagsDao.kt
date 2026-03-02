package com.example.financehub.data.dao

import androidx.room.*
import com.example.financehub.data.database.WishlistTagsCrossRef
import com.example.financehub.data.database.Tags
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistTagsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWishlistTag(crossRef: WishlistTagsCrossRef)

    @Delete
    suspend fun deleteWishlistTag(crossRef: WishlistTagsCrossRef)
    
    @Query("DELETE FROM wishlist_tags WHERE wishlistId = :wishlistId AND tagID = :tagId")
    suspend fun deleteWishlistTagByIds(wishlistId: Int, tagId: Int)

    @Query("SELECT * FROM wishlist_tags WHERE pendingSync = 1")
    suspend fun getPendingSyncWishlistTags(): List<WishlistTagsCrossRef>

    @Query("SELECT * FROM wishlist_tags WHERE id = :id")
    suspend fun getWishlistTagBySyncId(id: String): WishlistTagsCrossRef?

    @Query("UPDATE wishlist_tags SET pendingSync = 1, syncOperation = :operation, updatedAt = :timestamp WHERE wishlistId = :wishlistId AND tagID = :tagId")
    suspend fun markForSync(wishlistId: Int, tagId: Int, operation: String, timestamp: Long)

    @Query("UPDATE wishlist_tags SET serverId = :serverId, lastSyncedAt = :lastSyncedAt, pendingSync = :pendingSync, syncOperation = :syncOperation WHERE id = :id")
    suspend fun updateSyncMetadata(id: String, serverId: String?, lastSyncedAt: Long?, pendingSync: Boolean, syncOperation: String?)
    
    @Query("SELECT * FROM wishlist_tags WHERE serverId = :serverId LIMIT 1")
    suspend fun getWishlistTagByServerId(serverId: String): WishlistTagsCrossRef?
    
    @Query("SELECT * FROM wishlist_tags WHERE wishlistId = :wishlistId AND tagID = :tagId LIMIT 1")
    suspend fun getWishlistTag(wishlistId: Int, tagId: Int): WishlistTagsCrossRef?
    
    // Helper to get tags for a wishlist item
    @Transaction
    @Query("SELECT * FROM tags INNER JOIN wishlist_tags ON tags.tagID = wishlist_tags.tagID WHERE wishlist_tags.wishlistId = :wishlistId")
    fun getTagsForWishlist(wishlistId: Int): Flow<List<Tags>>
    
    @Query("SELECT * FROM wishlist_tags WHERE wishlistId = :wishlistId")
    suspend fun getWishlistTagsByWishlistId(wishlistId: Int): List<WishlistTagsCrossRef>
}
