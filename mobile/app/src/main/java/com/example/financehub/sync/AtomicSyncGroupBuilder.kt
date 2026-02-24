package com.example.financehub.sync

import com.example.financehub.data.database.AppDatabase
import com.example.financehub.data.database.SyncGroup
import com.example.financehub.data.database.SyncGroupEntity
import kotlinx.coroutines.flow.first

/**
 * AtomicSyncGroupBuilder groups related entities for atomic synchronization.
 * 
 * Ensures that related entities (e.g., expense + tags + expense_tag relations) 
 * are synced together as an atomic group - they either all succeed or all fail.
 */
class AtomicSyncGroupBuilder(private val database: AppDatabase) {

    /**
     * Creates atomic sync groups for all pending entities.
     * Groups related entities together to maintain referential integrity.
     * 
     * @return List of group IDs that were created
     */
    suspend fun createPendingSyncGroups(): List<Long> {
        val createdGroupIds = mutableListOf<Long>()
        
        // Group expenses with their tags and expense_tag relations
        createdGroupIds.addAll(createExpenseGroups())
        
        // Group tags independently (no dependencies)
        createdGroupIds.addAll(createTagGroups())
        
        // Group targets independently
        createdGroupIds.addAll(createTargetGroups())
        
        // Group graph edges independently
        createdGroupIds.addAll(createGraphEdgeGroups())
        
        // Group wishlist items with their tags and wishlist_tag relations
        createdGroupIds.addAll(createWishlistGroups())
        
        return createdGroupIds
    }
    
    /**
     * Groups expenses with their associated tags and expense_tag relations.
     * Each expense and its relationships form one atomic group.
     */
    private suspend fun createExpenseGroups(): List<Long> {
        val groupIds = mutableListOf<Long>()
        val expenseDao = database.expenseDao()
        val expenseTagsDao = database.expenseTagsCrossRefDao()
        val tagsDao = database.tagsDao()
        
        // Get all pending expenses
        val pendingExpenses = expenseDao.getPendingSyncExpenses()
        
        for (expense in pendingExpenses) {
            // Create a sync group for this expense
            val syncGroup = SyncGroup(groupType = "EXPENSE_WITH_TAGS", status = "PENDING")
            val groupId = database.syncGroupDao().insert(syncGroup)
            
            val groupEntities = mutableListOf<SyncGroupEntity>()
            
            // Add expense to group
            groupEntities.add(
                SyncGroupEntity(
                    groupId = groupId,
                    entityType = "expense",
                    localId = expense.expenseID.toLong(),
                    operation = expense.syncOperation ?: "CREATE"
                )
            )
            
            // Add associated expense_tag relations
            val expenseTags = expenseTagsDao.getCrossRefsForExpense(expense.expenseID)
            for (expenseTag in expenseTags) {
                if (expenseTag.pendingSync) {
                    groupEntities.add(
                        SyncGroupEntity(
                            groupId = groupId,
                            entityType = "expense_tag",
                            localId = expenseTag.id,
                            operation = expenseTag.syncOperation ?: "CREATE"
                        )
                    )
                }
            }
            
            // Add associated tags (if they're pending)
            val expenseTagIds = expenseTags.map { it.tagID }
            for (tagId in expenseTagIds) {
                val tag = tagsDao.getTagById(tagId)
                if (tag?.pendingSync == true) {
                    // Check if already added
                    val alreadyAdded = groupEntities.any { 
                        it.entityType == "tag" && it.localId == tag.tagID.toLong() 
                    }
                    if (!alreadyAdded) {
                        groupEntities.add(
                            SyncGroupEntity(
                                groupId = groupId,
                                entityType = "tag",
                                localId = tag.tagID.toLong(),
                                operation = tag.syncOperation ?: "CREATE"
                            )
                        )
                    }
                }
            }
            
            // Save all entities in the group
            database.syncGroupEntityDao().insertAll(groupEntities)
            groupIds.add(groupId)
        }
        
        return groupIds
    }
    
    /**
     * Groups standalone tags (not part of expense groups).
     * Each tag is an independent group.
     */
    private suspend fun createTagGroups(): List<Long> {
        val groupIds = mutableListOf<Long>()
        val tagsDao = database.tagsDao()
        val expenseTagsDao = database.expenseTagsCrossRefDao()
        val syncGroupEntityDao = database.syncGroupEntityDao()
        
        // Get all pending tags
        val pendingTags = tagsDao.getPendingSyncTags()
        
        for (tag in pendingTags) {
            // Check if tag is already in an expense group
            val existingGroups = syncGroupEntityDao.getGroupsForEntity("tag", tag.tagID.toLong())
            if (existingGroups.isNotEmpty()) {
                continue // Skip, already grouped with expense
            }
            
            // Check if tag has pending expenses
            val expensesWithTag = expenseTagsDao.getExpensesForTag(tag.tagID)
            val hasPendingExpense = expensesWithTag.any { expenseTag ->
                val expense = database.expenseDao().getExpenseById(expenseTag.expenseID)
                expense?.pendingSync == true
            }
            
            if (hasPendingExpense) {
                continue // Skip, will be grouped with expense
            }
            
            // Create standalone tag group
            val syncGroup = SyncGroup(groupType = "TAG", status = "PENDING")
            val groupId = database.syncGroupDao().insert(syncGroup)
            
            val groupEntity = SyncGroupEntity(
                groupId = groupId,
                entityType = "tag",
                localId = tag.tagID.toLong(),
                operation = tag.syncOperation ?: "CREATE"
            )
            
            database.syncGroupEntityDao().insert(groupEntity)
            groupIds.add(groupId)
        }
        
        return groupIds
    }
    
    /**
     * Groups targets independently.
     * Each target is an independent group.
     */
    private suspend fun createTargetGroups(): List<Long> {
        val groupIds = mutableListOf<Long>()
        val targetDao = database.targetDao()
        
        val pendingTargets = targetDao.getPendingSyncTargets()
        
        for (target in pendingTargets) {
            val syncGroup = SyncGroup(groupType = "TARGET", status = "PENDING")
            val groupId = database.syncGroupDao().insert(syncGroup)
            
            val groupEntity = SyncGroupEntity(
                groupId = groupId,
                entityType = "target",
                localId = target.id,
                operation = target.syncOperation ?: "CREATE"
            )
            
            database.syncGroupEntityDao().insert(groupEntity)
            groupIds.add(groupId)
        }
        
        return groupIds
    }
    
    /**
     * Groups graph edges independently.
     * Each edge is an independent group.
     */
    private suspend fun createGraphEdgeGroups(): List<Long> {
        val groupIds = mutableListOf<Long>()
        val graphEdgeDao = database.graphEdgeDAO()
        
        val pendingEdges = graphEdgeDao.getPendingSyncEdges()
        
        for (edge in pendingEdges) {
            val syncGroup = SyncGroup(groupType = "GRAPH_EDGE", status = "PENDING")
            val groupId = database.syncGroupDao().insert(syncGroup)
            
            val groupEntity = SyncGroupEntity(
                groupId = groupId,
                entityType = "graph_edge",
                localId = edge.id,
                operation = edge.syncOperation ?: "CREATE"
            )
            
            database.syncGroupEntityDao().insert(groupEntity)
            groupIds.add(groupId)
        }
        
        return groupIds
    }
    
    /**
     * Groups wishlist items with their tags and wishlist_tag relations.
     * Each wishlist item and its relationships form one atomic group.
     */
    private suspend fun createWishlistGroups(): List<Long> {
        val groupIds = mutableListOf<Long>()
        val wishlistDao = database.wishlistDao()
        val wishlistTagsDao = database.wishlistTagsDao()
        val tagsDao = database.tagsDao()
        
        val pendingWishlist = wishlistDao.getPendingSyncWishlist()
        
        for (wishlistItem in pendingWishlist) {
            val syncGroup = SyncGroup(groupType = "WISHLIST_WITH_TAGS", status = "PENDING")
            val groupId = database.syncGroupDao().insert(syncGroup)
            
            val groupEntities = mutableListOf<SyncGroupEntity>()
            
            // Add wishlist item to group
            groupEntities.add(
                SyncGroupEntity(
                    groupId = groupId,
                    entityType = "wishlist",
                    localId = wishlistItem.id.hashCode().toLong(), // Convert String ID to Long
                    operation = wishlistItem.syncOperation ?: "CREATE"
                )
            )
            
            // Add associated wishlist_tag relations
            val wishlistTags = wishlistTagsDao.getTagsForWishlist(wishlistItem.id).first()
            for (wishlistTag in wishlistTags) {
                if (wishlistTag.pendingSync) {
                    groupEntities.add(
                        SyncGroupEntity(
                            groupId = groupId,
                            entityType = "wishlist_tag",
                            localId = wishlistTag.tagID.hashCode().toLong(),
                            operation = wishlistTag.syncOperation ?: "CREATE"
                        )
                    )
                }
            }
            
            // Add associated tags (if they're pending)
            for (wishlistTag in wishlistTags) {
                val tag = tagsDao.getTagById(wishlistTag.tagID)
                if (tag?.pendingSync == true) {
                    val alreadyAdded = groupEntities.any { 
                        it.entityType == "tag" && it.localId == tag.tagID.toLong() 
                    }
                    if (!alreadyAdded) {
                        groupEntities.add(
                            SyncGroupEntity(
                                groupId = groupId,
                                entityType = "tag",
                                localId = tag.tagID.toLong(),
                                operation = tag.syncOperation ?: "CREATE"
                            )
                        )
                    }
                }
            }
            
            database.syncGroupEntityDao().insertAll(groupEntities)
            groupIds.add(groupId)
        }
        
        return groupIds
    }
}
