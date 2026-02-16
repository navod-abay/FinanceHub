package com.example.financehub.data.repository

import com.example.financehub.data.dao.WishlistDao
import com.example.financehub.data.dao.WishlistTagsDao
import com.example.financehub.data.database.Wishlist
import com.example.financehub.data.database.WishlistTagsCrossRef
import com.example.financehub.data.database.WishlistWithTags
import com.example.financehub.sync.SyncManager
import com.example.financehub.sync.SyncEntityType
import com.example.financehub.sync.SyncOperation
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class WishlistRepository(
    private val wishlistDao: WishlistDao,
    private val wishlistTagsDao: WishlistTagsDao,
    private val syncManager: SyncManager
) {
    val allWishlistItems: Flow<List<WishlistWithTags>> = wishlistDao.getAllWishlistItems()

    suspend fun addWishlistItem(name: String, expectedPrice: Int, tagIds: List<Int>) {
        val wishlistId = UUID.randomUUID().toString()
        val newItem = Wishlist(
            id = wishlistId,
            name = name,
            expectedPrice = expectedPrice,
            // tagID removed
            pendingSync = true,
            syncOperation = "CREATE",
            updatedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        wishlistDao.insertWishlist(newItem)
        
        // Add tags
        tagIds.forEach { tagId ->
            val crossRef = WishlistTagsCrossRef(
                wishlistId = wishlistId,
                tagID = tagId,
                pendingSync = true,
                syncOperation = "CREATE",
                updatedAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis()
            )
            wishlistTagsDao.insertWishlistTag(crossRef)
        }
        
        // Trigger sync? Handled by SyncManager periodic or manual.
    }

    suspend fun updateWishlistItem(item: Wishlist, tagIds: List<Int>) {
        val updatedItem = item.copy(
            pendingSync = true,
            syncOperation = "UPDATE",
            updatedAt = System.currentTimeMillis()
        )
        wishlistDao.updateWishlist(updatedItem)
        
        // Update tags - Diffing Strategy
        val currentTags = wishlistTagsDao.getWishlistTagsByWishlistId(item.id)
        val currentTagIds = currentTags.map { it.tagID }.toSet()
        val newTagIds = tagIds.toSet()
        
        // Tags to add
        val toAdd = newTagIds - currentTagIds
        toAdd.forEach { tagId ->
            val crossRef = WishlistTagsCrossRef(
                wishlistId = item.id,
                tagID = tagId,
                pendingSync = true,
                syncOperation = "CREATE",
                updatedAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis()
            )
            wishlistTagsDao.insertWishlistTag(crossRef)
        }
        
        // Tags to remove
        val toRemove = currentTagIds - newTagIds
        toRemove.forEach { tagId ->
            val crossRef = currentTags.find { it.tagID == tagId }
            crossRef?.let { ref ->
                if (ref.serverId != null) {
                    // Mark for sync delete if synced
                    wishlistTagsDao.markForSync(ref.wishlistId, ref.tagID, "DELETE", System.currentTimeMillis())
                } else {
                    // Delete locally if not synced
                    wishlistTagsDao.deleteWishlistTag(ref)
                }
            }
        }
    }

    suspend fun deleteWishlistItem(item: Wishlist) {
        if (item.serverId != null) {
            // If server synced, mark as deleted
             wishlistDao.markForSync(item.id, "DELETE", System.currentTimeMillis())
             // For local deletion, we might keep it to sync delete, or if using soft delete
             // The SyncManager handles DELETE op by sending DELETE request.
             // But locally we probably want to assume it's gone from UI?
             // Since getAllWishlistItems filters pendingSync=0? No.
             // Usually we need soft delete locally or handle "deleted" status.
             // The Wishlist entity doesn't have "deleted" flag except generic deleted_at.
             // Wait, the DAO says deleteWishlist(item).
             // If we delete local row, we lose serverId.
             // So we should soft delete if we want to sync delete.
             // But existing system: SyncManager uses "getPendingSync...".
             // If it's a DELETE op, it expects the item to exist with syncOperation="DELETE"?
             // Yes.
             // So we must NOT delete from DB until synced.
             // But we want it gone from UI.
             // DAO `getAllWishlistItems` query: `SELECT * FROM wishlist WHERE pendingSync = 0 AND syncOperation IS NULL` ??
             // Actually, usually we filter out delete ops.
             // Let's check DAO query in `WishlistDao.kt` I created:
             // `SELECT * FROM wishlist WHERE pendingSync = 0 AND syncOperation IS NULL ORDER BY createdAt DESC`
             // Wait, if pendingSync=1 (CREATE/UPDATE), it won't be shown?
             // That's a BUG in my DAO! Pending sync Create/Update items SHOULD be shown.
             // Only DELETE operation items should be hidden, or maybe I should check `syncOperation != 'DELETE'`.
             // I need to fix DAO query.
        } else {
            // Local only, just delete
            wishlistDao.deleteWishlist(item)
        }
    }
}
