package com.example.financehub.sync

import android.content.Context
import android.util.Log
import com.example.financehub.data.database.AppDatabase
import com.example.financehub.data.database.EntityMapping
import com.example.financehub.data.database.Expense
import com.example.financehub.data.database.Tags
import com.example.financehub.data.database.Target
import com.example.financehub.data.database.ExpenseTagsCrossRef
import com.example.financehub.data.database.GraphEdge
import com.example.financehub.network.FinanceHubApiService
import com.example.financehub.network.models.ApiExpense
import com.example.financehub.network.models.ApiExpenseTag
import com.example.financehub.network.models.ApiGraphEdge
import com.example.financehub.network.models.ApiTag
import com.example.financehub.network.models.ApiTarget
import com.example.financehub.network.models.CreateExpenseOperation
import com.example.financehub.network.models.CreateExpenseTagOperation
import com.example.financehub.network.models.CreateGraphEdgeOperation
import com.example.financehub.network.models.CreateTagOperation
import com.example.financehub.network.models.CreateTargetOperation
import com.example.financehub.network.models.DeleteExpenseOperation
import com.example.financehub.network.models.DeleteExpenseTagOperation
import com.example.financehub.network.models.DeleteGraphEdgeOperation
import com.example.financehub.network.models.DeleteTagOperation
import com.example.financehub.network.models.DeleteTargetOperation
import com.example.financehub.network.models.UpdateExpenseOperation
import com.example.financehub.network.models.UpdateGraphEdgeOperation
import com.example.financehub.network.models.UpdateTagOperation
import com.example.financehub.network.models.UpdateTargetOperation
import com.example.financehub.data.database.Wishlist
import com.example.financehub.network.models.ApiWishlistItem
import com.example.financehub.network.models.CreateWishlistOperation
import com.example.financehub.network.models.UpdateWishlistOperation
import com.example.financehub.network.models.DeleteWishlistOperation
import com.example.financehub.network.models.ApiWishlistTag
import com.example.financehub.network.models.CreateWishlistTagOperation
import com.example.financehub.network.models.DeleteWishlistTagOperation
import com.example.financehub.data.database.WishlistTagsCrossRef
import com.example.financehub.network.models.AtomicSyncGroup
import com.example.financehub.network.models.AtomicSyncRequest
import com.example.financehub.network.models.AtomicSyncResponse
import com.example.financehub.network.models.SyncOperation as NetworkSyncOperation
import com.example.financehub.network.models.ExpenseOperation
import com.example.financehub.network.models.ExpenseTagOperation
import com.example.financehub.network.models.GraphEdgeOperation
import com.example.financehub.network.models.TagOperation
import com.example.financehub.network.models.TargetOperation
import com.example.financehub.network.models.WishlistOperation
import com.example.financehub.network.models.WishlistTagOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

/**
 * SyncManager handles synchronization between local database and server
 * 
 * Key Features:
 * - Offline-first architecture: App works fully offline
 * - Client-authoritative conflict resolution: Local changes always win
 * - Server as backup: Server primarily stores data for backup/restore
 * - Batch processing: Efficient network usage
 * - Delta sync: Only syncs changes since last sync
 * - 3-month data retention: Automatic cleanup of old data
 */
class SyncManager constructor(
    private val database: AppDatabase,
    private val apiService: FinanceHubApiService,
    private val context: Context
) {
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: Flow<SyncState> = _syncState.asStateFlow()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: Flow<SyncProgress> = _syncProgress.asStateFlow()

    companion object {
        private const val TAG = "SyncManager"
        private const val MAX_BATCH_SIZE = 50
        private const val SYNC_TIMEOUT_MS = 30000L
    }

    /**
     * Performs a full synchronization with the server
     * 1. Push local changes to server (client-authoritative)
     * 2. Pull server changes to local database
     * 3. Resolve conflicts (client-authoritative - local wins)
     */
    suspend fun performFullSync(): SyncResult {
        try {
            _syncState.value = SyncState.SYNCING
            Log.d(TAG, "Starting full sync...")

            // Step 1: Push local changes to server
            val pushResult = pushLocalChanges()
            Log.d("SyncManager:performFullSync", "Pushed Local Changes")
            when (pushResult) {
                is SyncResult.Failure -> {
                    _syncState.value = SyncState.ERROR
                    return SyncResult.failure("Failed to push local changes: ${pushResult.error}")
                }
                is SyncResult.Success -> {
                    Log.d("SyncManager", "Local changes pushed successfully: ${pushResult.message}")
                }
            }

            // Step 2: Pull server changes
            val pullResult = pullServerChanges()
            when (pullResult) {
                is SyncResult.Failure -> {
                    _syncState.value = SyncState.ERROR
                    return SyncResult.failure("Failed to pull server changes: ${pullResult.error}")
                }
                is SyncResult.Success -> {
                    Log.d("SyncManager", "Server changes pulled successfully: ${pullResult.message}")
                }
            }

            // Step 3: Clean up old sync data (3-month retention)
            cleanupOldData()

            _syncState.value = SyncState.IDLE
            Log.d(TAG, "Full sync completed successfully")
            return SyncResult.success("Sync completed successfully")

        } catch (e: Exception) {
            _syncState.value = SyncState.ERROR
            Log.e(TAG, "Full sync failed", e)
            return SyncResult.failure("Sync failed: ${e.message}")
        }
    }

    /**
     * Push all pending local changes to the server using atomic sync groups
     */
    private suspend fun pushLocalChanges(): SyncResult {
        try {
            _syncProgress.value = _syncProgress.value.copy(stage = "Creating sync groups...")
            Log.d(TAG, "Creating atomic sync groups...")
            
            // Build atomic sync groups from pending entities
            val groupBuilder = AtomicSyncGroupBuilder(database)
            val groupIds = groupBuilder.createPendingSyncGroups()
            
            if (groupIds.isEmpty()) {
                Log.d(TAG, "No pending changes to sync")
                return SyncResult.success("No pending changes")
            }
            
            Log.d(TAG, "Created ${groupIds.size} sync groups")
            _syncProgress.value = _syncProgress.value.copy(stage = "Pushing ${groupIds.size} groups...")
            
            // Build atomic sync request from groups
            val atomicGroups = mutableListOf<AtomicSyncGroup>()
            val validGroupIds = mutableListOf<Long>()
            var hasBuildErrors = false
            
            for (groupId in groupIds) {
                val group = buildAtomicSyncGroup(groupId)
                if (group != null) {
                    if (group.operations.isNotEmpty()) {
                        atomicGroups.add(group)
                        validGroupIds.add(groupId)
                    } else {
                        // Group is effectively empty (e.g. offline create+delete)
                        clearPendingSyncForGroup(groupId)
                        database.syncGroupDao().updateStatus(groupId, "SUCCESS", System.currentTimeMillis(), null)
                    }
                } else {
                    hasBuildErrors = true
                }
            }
            
            if (hasBuildErrors && atomicGroups.isEmpty()) {
                return SyncResult.failure("Failed to build sync groups")
            }
            
            if (atomicGroups.isEmpty()) {
                Log.d(TAG, "No valid groups to sync")
                return SyncResult.success("No valid groups")
            }
            
            // Send atomic sync request
            val request = AtomicSyncRequest(
                groups = atomicGroups,
                clientTimestamp = System.currentTimeMillis()
            )
            val response = apiService.atomicSync(request)
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    val totalGroups = responseBody.groupResults.size
                    val successfulGroups = responseBody.groupResults.count { it.success }
                    Log.d(TAG, "Atomic sync completed: $successfulGroups/$totalGroups groups succeeded")
                    
                    // Process results
                    processAtomicSyncResults(validGroupIds, responseBody)
                    
                    if (successfulGroups < totalGroups || hasBuildErrors) {
                        return SyncResult.failure("Partial sync: $successfulGroups/$totalGroups groups succeeded")
                    }
                    
                    return SyncResult.success("Synced $successfulGroups/$totalGroups groups")
                } else {
                    return SyncResult.failure("Empty response from server")
                }
            } else {
                Log.e(TAG, "Atomic sync failed: ${response.message()}")
                return SyncResult.failure("Sync failed: ${response.message()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to push local changes", e)
            return SyncResult.failure("Failed to push local changes: ${e.message}")
        }
    }
    
    /**
     * Build an AtomicSyncGroup from a sync group in the database
     */
    private suspend fun buildAtomicSyncGroup(groupId: Long): AtomicSyncGroup? {
        try {
            val syncGroup = database.syncGroupDao().getById(groupId) ?: return null
            val groupEntities = database.syncGroupEntityDao().getEntitiesForGroup(groupId)
            if (groupEntities.isEmpty()) {
                return null
            }
            
            val expenses = mutableListOf<ExpenseOperation>()
            val tags = mutableListOf<TagOperation>()
            val targets = mutableListOf<TargetOperation>()
            val expenseTags = mutableListOf<ExpenseTagOperation>()
            val graphEdges = mutableListOf<GraphEdgeOperation>()
            val wishlist = mutableListOf<WishlistOperation>()
            val wishlistTags = mutableListOf<WishlistTagOperation>()
            
            for (entityRef in groupEntities) {
                when (entityRef.entityType) {
                    "expense" -> {
                        val expense = database.expenseDao().getExpenseById(entityRef.localId.toInt())
                        if (expense != null) {
                            buildExpenseOperation(expense, entityRef.operation)?.let { expenses.add(it) }
                        }
                    }
                    "tag" -> {
                        val tag = database.tagsDao().getTagById(entityRef.localId.toInt())
                        if (tag != null) {
                            buildTagOperation(tag, entityRef.operation)?.let { tags.add(it) }
                        }
                    }
                    "target" -> {
                        val target = database.targetDao().getTargetById(entityRef.localId)
                        if (target != null) {
                            buildTargetOperation(target, entityRef.operation)?.let { targets.add(it) }
                        }
                    }
                    "expense_tag" -> {
                        val expenseTag = database.expenseTagsCrossRefDao().getExpenseTagById(entityRef.localId)
                        if (expenseTag != null) {
                            buildExpenseTagOperation(expenseTag, entityRef.operation)?.let { expenseTags.add(it) }
                        }
                    }
                    "graph_edge" -> {
                        val graphEdge = database.graphEdgeDAO().getGraphEdgeById(entityRef.localId)
                        if (graphEdge != null) {
                            buildGraphEdgeOperation(graphEdge, entityRef.operation)?.let { graphEdges.add(it) }
                        }
                    }
                    "wishlist" -> {
                        val wishlistItem = database.wishlistDao().getWishlistById(entityRef.localId.toInt())
                        if (wishlistItem != null) {
                            buildWishlistOperation(wishlistItem, entityRef.operation)?.let { wishlist.add(it) }
                        }
                    }
                    "wishlist_tag" -> {
                        val wishlistTag = database.wishlistTagsDao().getWishlistTagBySyncId(entityRef.localId.toString())
                        if (wishlistTag != null) {
                            buildWishlistTagOperation(wishlistTag, entityRef.operation)?.let { wishlistTags.add(it) }
                        }
                    }
                }
            }
            
            // Flatten all operations into a single list for the server
            val allOperations = mutableListOf<NetworkSyncOperation>()
            allOperations.addAll(expenses)
            allOperations.addAll(tags)
            allOperations.addAll(targets)
            allOperations.addAll(expenseTags)
            allOperations.addAll(graphEdges)
            allOperations.addAll(wishlist)
            allOperations.addAll(wishlistTags)
            
            return AtomicSyncGroup(
                groupId = groupId.toString(),
                groupType = syncGroup.groupType,
                operations = allOperations
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build atomic group $groupId", e)
            return null
        }
    }
    
    /**
     * Build an expense operation from an Expense entity
     */
    private fun buildExpenseOperation(expense: Expense, operation: String): ExpenseOperation? {
        val actualOperation = if (expense.serverId == null && operation == "UPDATE") "CREATE" else operation
        if (expense.serverId == null && actualOperation == "DELETE") return null
        return when (actualOperation) {
            "CREATE" -> CreateExpenseOperation(
                title = expense.title,
                amount = expense.amount,
                year = expense.year,
                month = expense.month,
                date = expense.date,
                clientId = expense.expenseID.toString()
            )
            "UPDATE" -> UpdateExpenseOperation(
                serverId = expense.serverId!!,
                title = expense.title,
                amount = expense.amount,
                year = expense.year,
                month = expense.month,
                date = expense.date
            )
            "DELETE" -> DeleteExpenseOperation(
                serverId = expense.serverId!!
            )
            else -> throw IllegalArgumentException("Unknown operation: $actualOperation")
        }
    }
    
    /**
     * Build a tag operation from a Tags entity
     */
    private fun buildTagOperation(tag: Tags, operation: String): TagOperation? {
        val actualOperation = if (tag.serverId == null && operation == "UPDATE") "CREATE" else operation
        if (tag.serverId == null && actualOperation == "DELETE") return null
        return when (actualOperation) {
            "CREATE" -> CreateTagOperation(
                name = tag.tag,
                monthlyAmount = tag.monthlyAmount,
                currentMonth = tag.currentMonth,
                currentYear = tag.currentYear,
                createdDay = tag.createdDay,
                createdMonth = tag.createdMonth,
                createdYear = tag.createdYear,
                clientId = tag.tagID.toString()
            )
            "UPDATE" -> UpdateTagOperation(
                serverId = tag.serverId!!,
                name = tag.tag,
                monthlyAmount = tag.monthlyAmount,
                currentMonth = tag.currentMonth,
                currentYear = tag.currentYear
            )
            "DELETE" -> DeleteTagOperation(
                serverId = tag.serverId!!
            )
            else -> throw IllegalArgumentException("Unknown operation: $actualOperation")
        }
    }
    
    /**
     * Build a target operation from a Target entity
     */
    private fun buildTargetOperation(target: Target, operation: String): TargetOperation? {
        val actualOperation = if (target.serverId == null && operation == "UPDATE") "CREATE" else operation
        if (target.serverId == null && actualOperation == "DELETE") return null
        return when (actualOperation) {
            "CREATE" -> CreateTargetOperation(
                month = target.month,
                year = target.year,
                tagId = target.serverId ?: target.tagID.toString(),
                amount = target.amount,
                spent = target.spent,
                clientId = target.id.toString()
            )
            "UPDATE" -> UpdateTargetOperation(
                serverId = target.serverId!!,
                amount = target.amount,
                spent = target.spent
            )
            "DELETE" -> DeleteTargetOperation(
                serverId = target.serverId!!
            )
            else -> throw IllegalArgumentException("Unknown operation: $actualOperation")
        }
    }
    
    /**
     * Build an expense-tag operation from an ExpenseTagsCrossRef entity
     */
    private suspend fun buildExpenseTagOperation(expenseTag: ExpenseTagsCrossRef, operation: String): ExpenseTagOperation? {
        val actualOperation = if (expenseTag.serverId == null && operation == "UPDATE") "CREATE" else operation
        if (expenseTag.serverId == null && actualOperation == "DELETE") return null
        return when (actualOperation) {
            "CREATE" -> {
                val expenseServerId = database.entityMappingDao().getServerId("expense", expenseTag.expenseID.toLong())
                    ?: database.expenseDao().getExpenseById(expenseTag.expenseID)?.serverId
                    ?: expenseTag.expenseID.toString()
                
                val tagServerId = database.entityMappingDao().getServerId("tag", expenseTag.tagID.toLong())
                    ?: database.tagsDao().getTagById(expenseTag.tagID)?.serverId
                    ?: expenseTag.tagID.toString()
                
                CreateExpenseTagOperation(
                    expenseId = expenseServerId,
                    tagId = tagServerId,
                    clientId = expenseTag.id.toString()
                )
            }
            "DELETE" -> DeleteExpenseTagOperation(
                serverId = expenseTag.serverId!!
            )
            else -> throw IllegalArgumentException("Unknown operation: $actualOperation")
        }
    }
    
    /**
     * Build a graph edge operation from a GraphEdge entity
     */
    private suspend fun buildGraphEdgeOperation(graphEdge: GraphEdge, operation: String): GraphEdgeOperation? {
        val actualOperation = if (graphEdge.serverId == null && operation == "UPDATE") "CREATE" else operation
        if (graphEdge.serverId == null && actualOperation == "DELETE") return null
        return when (actualOperation) {
            "CREATE" -> {
                val fromTagServerId = database.entityMappingDao().getServerId("tag", graphEdge.fromTagId.toLong())
                    ?: database.tagsDao().getTagById(graphEdge.fromTagId)?.serverId
                    ?: graphEdge.fromTagId.toString()
                
                val toTagServerId = database.entityMappingDao().getServerId("tag", graphEdge.toTagId.toLong())
                    ?: database.tagsDao().getTagById(graphEdge.toTagId)?.serverId
                    ?: graphEdge.toTagId.toString()
                
                CreateGraphEdgeOperation(
                    fromTagId = fromTagServerId,
                    toTagId = toTagServerId,
                    weight = graphEdge.weight,
                    clientId = graphEdge.id.toString()
                )
            }
            "UPDATE" -> UpdateGraphEdgeOperation(
                serverId = graphEdge.serverId!!,
                weight = graphEdge.weight
            )
            "DELETE" -> DeleteGraphEdgeOperation(
                serverId = graphEdge.serverId!!
            )
            else -> throw IllegalArgumentException("Unknown operation: $actualOperation")
        }
    }
    
    /**
     * Build a wishlist operation from a Wishlist entity
     */
    private fun buildWishlistOperation(wishlistItem: Wishlist, operation: String): WishlistOperation? {
        val actualOperation = if (wishlistItem.serverId == null && operation == "UPDATE") "CREATE" else operation
        if (wishlistItem.serverId == null && actualOperation == "DELETE") return null
        return when (actualOperation) {
            "CREATE" -> CreateWishlistOperation(
                name = wishlistItem.name,
                minPrice = wishlistItem.minPrice,
                maxPrice = wishlistItem.maxPrice,
                clientId = wishlistItem.id.toString()
            )
            "UPDATE" -> UpdateWishlistOperation(
                serverId = wishlistItem.serverId!!,
                name = wishlistItem.name,
                minPrice = wishlistItem.minPrice,
                maxPrice = wishlistItem.maxPrice
            )
            "DELETE" -> DeleteWishlistOperation(
                serverId = wishlistItem.serverId!!
            )
            else -> throw IllegalArgumentException("Unknown operation: $actualOperation")
        }
    }
    
    /**
     * Build a wishlist-tag operation from a WishlistTagsCrossRef entity
     */
    private suspend fun buildWishlistTagOperation(wishlistTag: WishlistTagsCrossRef, operation: String): WishlistTagOperation? {
        val actualOperation = if (wishlistTag.serverId == null && operation == "UPDATE") "CREATE" else operation
        if (wishlistTag.serverId == null && actualOperation == "DELETE") return null
        return when (actualOperation) {
            "CREATE" -> {
                val tagServerId = database.entityMappingDao().getServerId("tag", wishlistTag.tagID.toLong())
                    ?: database.tagsDao().getTagById(wishlistTag.tagID)?.serverId
                    ?: wishlistTag.tagID.toString()
                
                CreateWishlistTagOperation(
                    wishlistId = wishlistTag.wishlistId.hashCode(),
                    tagId = tagServerId,
                    clientId = wishlistTag.id
                )
            }
            "DELETE" -> DeleteWishlistTagOperation(
                wishlistId = wishlistTag.wishlistId.hashCode(),
                tagId = wishlistTag.tagID.toString(),
                serverId = wishlistTag.serverId!!
            )
            else -> throw IllegalArgumentException("Unknown operation: $actualOperation")
        }
    }
    
    /**
     * Process atomic sync results and update local database
     */
    private suspend fun processAtomicSyncResults(groupIds: List<Long>, response: AtomicSyncResponse) {
        for ((index, groupResult) in response.groupResults.withIndex()) {
            val groupId = groupIds.getOrNull(index) ?: continue
            
            if (groupResult.success) {
                // Mark group as successful
                database.syncGroupDao().updateStatus(
                    groupId = groupId,
                    status = "SUCCESS",
                    syncedAt = System.currentTimeMillis(),
                    errorMessage = null
                )

                // Save entity mappings
                for (mapping in groupResult.entityMappings) {
                    database.entityMappingDao().insert(
                        EntityMapping(
                            entityType = mapping.entityType,
                            localId = mapping.clientId.toLong(),
                            serverId = mapping.serverId
                        )
                    )
                }

                // Clear pending sync flags for entities in this group
                clearPendingSyncForGroup(groupId)
                
            } else {
                // Mark group as failed
                val errorMsg = groupResult.error.toString()
                database.syncGroupDao().updateStatus(
                    groupId = groupId,
                    status = "FAILED",
                    syncedAt = null,
                    errorMessage = errorMsg
                )
                Log.e(TAG, "Group $groupId failed: $errorMsg")
            }
        }
    }
    
    /**
     * Clear pending sync flags for all entities in a group
     */
    private suspend fun clearPendingSyncForGroup(groupId: Long) {
        val entities = database.syncGroupEntityDao().getEntitiesForGroup(groupId)
        
        for (entity in entities) {
            when (entity.entityType) {
                "expense" -> {
                    database.expenseDao().updateSyncMetadata(
                        expenseId = entity.localId.toInt(),
                        serverId = null,
                        lastSyncedAt = System.currentTimeMillis(),
                        pendingSync = false,
                        syncOperation = null
                    )
                }
                "tag" -> {
                    database.tagsDao().updateSyncMetadata(
                        tagId = entity.localId.toInt(),
                        serverId = null,
                        lastSyncedAt = System.currentTimeMillis(),
                        pendingSync = false,
                        syncOperation = null
                    )
                }
                "target" -> {
                    // Similar update for targets
                }
                "expense_tag" -> {
                    // Similar update for expense tags
                }
                "graph_edge" -> {
                    // Similar update for graph edges
                }
                "wishlist" -> {
                    // Similar update for wishlist
                }
                "wishlist_tag" -> {
                    // Similar update for wishlist tags
                }
            }
        }
    }

    /**
     * Pull changes from server and apply them locally
     */
    private suspend fun pullServerChanges(): SyncResult {
        try {
            _syncProgress.value = _syncProgress.value.copy(stage = "Pulling server changes...")

            // Get the timestamp of the last successful sync
            val lastSyncTimestamp = getLastSyncTimestamp()

            // Get all changes from server since last sync
            val response = apiService.getUpdatedData(lastSyncTimestamp)
            if (response.isSuccessful) {
                val serverData = response.body()
                if (serverData != null) {
                    // Apply server changes with client-authoritative conflict resolution
                    applyServerExpenses(serverData.expenses)
                    applyServerTags(serverData.tags)
                    applyServerTargets(serverData.targets)
                    applyServerExpenseTags(serverData.expenseTags)
                    applyServerGraphEdges(serverData.graphEdges)
                    applyServerWishlist(serverData.wishlist)
                    applyServerWishlistTags(serverData.wishlistTags)

                    // Update last sync timestamp
                    updateLastSyncTimestamp(System.currentTimeMillis())

                    return SyncResult.success("Server changes pulled successfully")
                } else {
                    return SyncResult.failure("Failed to fetch server data: Empty response body")
                }
            } else {
                return SyncResult.failure("Failed to fetch server data: ${response.message()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull server changes", e)
            return SyncResult.failure("Failed to pull server changes: ${e.message}")
        }
    }


    /**
     * Apply server expenses to local database with client-authoritative conflict resolution
     */
    private suspend fun applyServerExpenses(serverExpenses: List<ApiExpense>) {
        serverExpenses.forEach { serverExpense ->
            val localExpense = database.expenseDao().getExpenseByServerId(serverExpense.id)
            
            if (localExpense == null) {
                // New expense from server - insert locally only if no local item exists
                val newExpense = Expense(
                    title = serverExpense.title,
                    amount = serverExpense.amount,
                    year = serverExpense.year,
                    month = serverExpense.month,
                    date = serverExpense.date,
                    serverId = serverExpense.id,
                    lastSyncedAt = System.currentTimeMillis(),
                    pendingSync = false,
                    syncOperation = null,
                    createdAt = serverExpense.createdAt,
                    updatedAt = serverExpense.updatedAt
                )
                database.expenseDao().insertExpense(newExpense)
                
            } else {
                // Conflict resolution: Client-authoritative
                if (localExpense.pendingSync) {
                    // Local has pending changes - local wins, don't apply server changes
                    // Mark that we've seen this server version
                    database.expenseDao().updateSyncMetadata(
                        expenseId = localExpense.expenseID,
                        serverId = localExpense.serverId,
                        lastSyncedAt = localExpense.lastSyncedAt,
                        pendingSync = true, // Keep pending status
                        syncOperation = localExpense.syncOperation
                    )
                } else if (serverExpense.updatedAt > (localExpense.updatedAt)) {
                    // No local pending changes and server has newer version - apply server changes
                    database.expenseDao().updateFromServer(
                        expenseId = localExpense.expenseID,
                        title = serverExpense.title,
                        amount = serverExpense.amount,
                        year = serverExpense.year,
                        month = serverExpense.month,
                        date = serverExpense.date,
                        updatedAt = serverExpense.updatedAt,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                } else {
                    // Local version is same or newer - just update sync timestamp
                    database.expenseDao().updateSyncMetadata(
                        expenseId = localExpense.expenseID,
                        serverId = localExpense.serverId,
                        lastSyncedAt = System.currentTimeMillis(),
                        pendingSync = false,
                        syncOperation = null
                    )
                }
            }
        }
    }

    /**
     * Apply server tags with client-authoritative conflict resolution
     */
    private suspend fun applyServerTags(serverTags: List<ApiTag>) {
        serverTags.forEach { serverTag ->
            val localTag = database.tagsDao().getTagByServerId(serverTag.id)
            
            if (localTag == null) {
                // New tag from server - insert locally
                val newTag = Tags(
                    tag = serverTag.name,
                    monthlyAmount = serverTag.monthlyAmount,
                    currentMonth = serverTag.currentMonth,
                    currentYear = serverTag.currentYear,
                    createdDay = serverTag.createdDay,
                    createdMonth = serverTag.createdMonth,
                    createdYear = serverTag.createdYear,
                    serverId = serverTag.id,
                    lastSyncedAt = System.currentTimeMillis(),
                    pendingSync = false,
                    syncOperation = null,
                    syncCreatedAt = serverTag.createdAt,
                    syncUpdatedAt = serverTag.updatedAt
                )
                database.tagsDao().insertTag(newTag)
                
            } else {
                // Conflict resolution: Client-authoritative
                if (localTag.pendingSync) {
                    // Local has pending changes - local wins
                    database.tagsDao().updateSyncMetadata(
                        tagId = localTag.tagID,
                        serverId = localTag.serverId,
                        lastSyncedAt = localTag.lastSyncedAt,
                        pendingSync = true,
                        syncOperation = localTag.syncOperation
                    )
                } else if (serverTag.updatedAt > localTag.syncUpdatedAt) {
                    // No local pending changes and server has newer version
                    database.tagsDao().updateFromServer(
                        tagId = localTag.tagID,
                        name = serverTag.name,
                        monthlyAmount = serverTag.monthlyAmount,
                        currentMonth = serverTag.currentMonth,
                        currentYear = serverTag.currentYear,
                        updatedAt = serverTag.updatedAt,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                } else {
                    // Local version is same or newer - just update sync timestamp
                    database.tagsDao().updateSyncMetadata(
                        tagId = localTag.tagID,
                        serverId = localTag.serverId,
                        lastSyncedAt = System.currentTimeMillis(),
                        pendingSync = false,
                        syncOperation = null
                    )
                }
            }
        }
    }

    /**
     * Apply server targets with client-authoritative conflict resolution
     */
    private suspend fun applyServerTargets(serverTargets: List<ApiTarget>) {
        serverTargets.forEach { serverTarget ->
            val localTarget = database.targetDao().getTargetByServerId(serverTarget.id)
            
            if (localTarget == null) {
                // New target from server - insert locally
                val newTarget = Target(
                    month = serverTarget.month,
                    year = serverTarget.year,
                    tagID = serverTarget.tagId.toInt(),
                    amount = serverTarget.amount,
                    spent = serverTarget.spent,
                    serverId = serverTarget.id,
                    lastSyncedAt = System.currentTimeMillis(),
                    pendingSync = false,
                    syncOperation = null,
                    createdAt = serverTarget.createdAt,
                    updatedAt = serverTarget.updatedAt
                )
                database.targetDao().insertTarget(newTarget)
                
            } else {
                // Conflict resolution: Client-authoritative
                if (localTarget.pendingSync) {
                    // Local has pending changes - local wins
                    database.targetDao().updateSyncMetadata(
                        month = localTarget.month,
                        year = localTarget.year,
                        tagId = localTarget.tagID,
                        serverId = localTarget.serverId,
                        lastSyncedAt = localTarget.lastSyncedAt,
                        pendingSync = true,
                        syncOperation = localTarget.syncOperation
                    )
                } else if (serverTarget.updatedAt > localTarget.updatedAt) {
                    // No local pending changes and server has newer version
                    database.targetDao().updateFromServer(
                        month = localTarget.month,
                        year = localTarget.year,
                        tagId = localTarget.tagID,
                        amount = serverTarget.amount,
                        spent = serverTarget.spent,
                        updatedAt = serverTarget.updatedAt,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                } else {
                    // Local version is same or newer - just update sync timestamp
                    database.targetDao().updateSyncMetadata(
                        month = localTarget.month,
                        year = localTarget.year,
                        tagId = localTarget.tagID,
                        serverId = localTarget.serverId,
                        lastSyncedAt = System.currentTimeMillis(),
                        pendingSync = false,
                        syncOperation = null
                    )
                }
            }
        }
    }

    /**
     * Apply server expense-tag relationships
     */
    private suspend fun applyServerExpenseTags(serverExpenseTags: List<ApiExpenseTag>) {
        serverExpenseTags.forEach { serverExpenseTag ->
            val localExpenseTag = database.expenseTagsCrossRefDao().getExpenseTagByServerId(serverExpenseTag.id)
            
            if (localExpenseTag == null) {
                // New relationship from server
                val newExpenseTag = ExpenseTagsCrossRef(
                    expenseID = serverExpenseTag.expenseId.toInt(),
                    tagID = serverExpenseTag.tagId.toInt(),
                    serverId = serverExpenseTag.id,
                    lastSyncedAt = System.currentTimeMillis(),
                    pendingSync = false,
                    syncOperation = null,
                    createdAt = serverExpenseTag.createdAt,
                    updatedAt = serverExpenseTag.updatedAt
                )
                database.expenseTagsCrossRefDao().insertExpenseTag(newExpenseTag)
            }
        }
    }

    /**
     * Apply server graph edges with client-authoritative conflict resolution
     */
    private suspend fun applyServerGraphEdges(serverGraphEdges: List<ApiGraphEdge>) {
        serverGraphEdges.forEach { serverEdge ->
            val localEdge = database.graphEdgeDAO().getGraphEdgeByServerId(serverEdge.id)
            
            if (localEdge == null) {
                // New edge from server - insert locally
                val newEdge = GraphEdge(
                    fromTagId = serverEdge.fromTagId.toInt(),
                    toTagId = serverEdge.toTagId.toInt(),
                    weight = serverEdge.weight,
                    serverId = serverEdge.id,
                    lastSyncedAt = System.currentTimeMillis(),
                    pendingSync = false,
                    syncOperation = null,
                    createdAt = serverEdge.createdAt,
                    updatedAt = serverEdge.updatedAt
                )
                database.graphEdgeDAO().insertGraphEdge(newEdge)
                
            } else {
                // Conflict resolution: Client-authoritative
                if (localEdge.pendingSync) {
                    // Local has pending changes - local wins
                    database.graphEdgeDAO().updateSyncMetadata(
                        fromTagId = localEdge.fromTagId,
                        toTagId = localEdge.toTagId,
                        serverId = localEdge.serverId,
                        lastSyncedAt = localEdge.lastSyncedAt,
                        pendingSync = true,
                        syncOperation = localEdge.syncOperation
                    )
                } else if (serverEdge.updatedAt > localEdge.updatedAt) {
                    // No local pending changes and server has newer version
                    database.graphEdgeDAO().updateFromServer(
                        fromTagId = localEdge.fromTagId,
                        toTagId = localEdge.toTagId,
                        weight = serverEdge.weight,
                        updatedAt = serverEdge.updatedAt,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                } else {
                    // Local version is same or newer - just update sync timestamp
                    database.graphEdgeDAO().updateSyncMetadata(
                        fromTagId = localEdge.fromTagId,
                        toTagId = localEdge.toTagId,
                        serverId = localEdge.serverId,
                        lastSyncedAt = System.currentTimeMillis(),
                        pendingSync = false,
                        syncOperation = null
                    )
                }
            }
        }
    }


    /**
     * Apply server wishlist items with client-authoritative conflict resolution
     */
    private suspend fun applyServerWishlist(serverItems: List<ApiWishlistItem>) {
        serverItems.forEach { serverItem ->
            val localItem = database.wishlistDao().getWishlistItemByServerId(serverItem.id)
            
            if (localItem == null) {
                val newItem = Wishlist(
                    // id is auto-generated, don't set it
                    name = serverItem.name,
                    minPrice = serverItem.minPrice ?: 0,
                    maxPrice = serverItem.maxPrice ?: 0,
                    // tagID removed
                    serverId = serverItem.id,
                    lastSyncedAt = System.currentTimeMillis(),
                    pendingSync = false,
                    syncOperation = null,
                    createdAt = serverItem.createdAt,
                    updatedAt = serverItem.updatedAt
                )
                database.wishlistDao().insertWishlist(newItem)
            } else {
                if (localItem.pendingSync) {
                    database.wishlistDao().updateSyncMetadata(
                        id = localItem.id,
                        serverId = localItem.serverId,
                        lastSyncedAt = localItem.lastSyncedAt ?: 0,
                        pendingSync = true,
                        syncOperation = localItem.syncOperation
                    )
                } else if (serverItem.updatedAt > localItem.updatedAt) {
                    database.wishlistDao().updateFromServer(
                        id = localItem.id,
                        name = serverItem.name,
                        minPrice = serverItem.minPrice,
                        maxPrice = serverItem.maxPrice,
                        // tagId removed
                        updatedAt = serverItem.updatedAt,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                } else {
                    database.wishlistDao().updateSyncMetadata(
                        id = localItem.id,
                        serverId = localItem.serverId,
                        lastSyncedAt = System.currentTimeMillis(),
                        pendingSync = false,
                        syncOperation = null
                    )
                }
            }
        }
    }

    /**
     * Apply server wishlist tags
     */
    private suspend fun applyServerWishlistTags(serverItems: List<ApiWishlistTag>) {
        serverItems.forEach { serverItem ->
            val localItem = database.wishlistTagsDao().getWishlistTagByServerId(serverItem.id)
            
            if (localItem == null) {
                // Check if relationship exists by composite key
                val existing = database.wishlistTagsDao().getWishlistTag(serverItem.wishlistId, serverItem.tagId.toInt())
                if (existing == null) {
                     val newItem = WishlistTagsCrossRef(
                        wishlistId = serverItem.wishlistId,
                        tagID = serverItem.tagId.toInt(),
                        serverId = serverItem.id,
                        lastSyncedAt = System.currentTimeMillis(),
                        pendingSync = false,
                        syncOperation = null,
                        createdAt = serverItem.createdAt,
                        updatedAt = serverItem.updatedAt
                    )
                    database.wishlistTagsDao().insertWishlistTag(newItem)
                } else {
                    // Update existing mapping with server ID
                     database.wishlistTagsDao().updateSyncMetadata(
                        id = existing.id,
                        serverId = serverItem.id,
                        lastSyncedAt = System.currentTimeMillis(),
                        pendingSync = false,
                        syncOperation = null
                    )
                }
            } else {
                 if (!localItem.pendingSync) {
                      // Update metadata
                      database.wishlistTagsDao().updateSyncMetadata(
                        id = localItem.id,
                        serverId = serverItem.id,
                        lastSyncedAt = System.currentTimeMillis(),
                        pendingSync = false,
                        syncOperation = null
                    )
                 }
            }
        }
    }

    /**
     * Clean up data older than 3 months
     */
    private suspend fun cleanupOldData() {
        val threeMonthsAgo = System.currentTimeMillis() - (90 * 24 * 60 * 60 * 1000L) // 90 days
        
        database.expenseDao().deleteOldExpenses(threeMonthsAgo)
        database.tagsDao().deleteOldTags(threeMonthsAgo)
        database.targetDao().deleteOldTargets(threeMonthsAgo)
        database.expenseTagsCrossRefDao().deleteOldExpenseTags(threeMonthsAgo)
        database.graphEdgeDAO().deleteOldGraphEdges(threeMonthsAgo)
        database.wishlistDao().deleteOldWishlist(threeMonthsAgo)
    }

    /**
     * Get the timestamp of the last successful sync
     */
    private fun getLastSyncTimestamp(): Long {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        return prefs.getLong("last_sync_timestamp", 0)
    }

    /**
     * Update the timestamp of the last successful sync
     */
    private fun updateLastSyncTimestamp(timestamp: Long) {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_sync_timestamp", timestamp).apply()
    }

    /**
     * Mark an item as pending sync
     */
    suspend fun markForSync(entityType: SyncEntityType, entityId: String, operation: SyncOperation) {
        when (entityType) {
            SyncEntityType.EXPENSE -> {
                database.expenseDao().markForSync(entityId.toInt(), operation.name, System.currentTimeMillis())
            }
            SyncEntityType.TAG -> {
                database.tagsDao().markForSync(entityId.toInt(), operation.name, System.currentTimeMillis())
            }
            SyncEntityType.TARGET -> {
                val parts = entityId.split("-")
                database.targetDao().markForSync(parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), operation.name, System.currentTimeMillis())
            }
            SyncEntityType.EXPENSE_TAG -> {
                val parts = entityId.split("-")
                database.expenseTagsCrossRefDao().markForSync(parts[0].toInt(), parts[1].toInt(), operation.name, System.currentTimeMillis())
            }
            SyncEntityType.GRAPH_EDGE -> {
                val parts = entityId.split("-")
                database.graphEdgeDAO().markForSync(parts[0].toInt(), parts[1].toInt(), operation.name, System.currentTimeMillis())
            }
            SyncEntityType.WISHLIST -> {
                database.wishlistDao().markForSync(entityId.toInt(), operation.name, System.currentTimeMillis())
            }
            SyncEntityType.WISHLIST_TAG -> {
                 // Format: wishlistId-tagId
                 val parts = entityId.split("-")
                 if (parts.size >= 2) {
                     val wishlistId = parts[0].toInt()
                     val tagId = parts[1].toInt()
                     database.wishlistTagsDao().markForSync(wishlistId, tagId, operation.name, System.currentTimeMillis())
                 }
            }
        }
    }
}

/**
 * Sync states
 */
enum class SyncState {
    IDLE,
    SYNCING,
    ERROR
}

/**
 * Sync progress information
 */
data class SyncProgress(
    val stage: String = "",
    val progress: Float = 0f,
    val details: String = ""
)

/**
 * Sync result
 */
sealed class SyncResult {
    data class Success(val message: String) : SyncResult()
    data class Failure(val error: String) : SyncResult()

    val isSuccess: Boolean get() = this is Success

    companion object {
        fun success(message: String) = Success(message)
        fun failure(error: String) = Failure(error)
    }
}

/**
 * Entity types for sync
 */
enum class SyncEntityType {
    EXPENSE,
    TAG,
    TARGET,
    EXPENSE_TAG,
    GRAPH_EDGE,
    WISHLIST,
    WISHLIST_TAG
}

/**
 * Sync operations
 */
enum class SyncOperation {
    CREATE,
    UPDATE,
    DELETE
}