package com.example.financehub.sync

import android.content.Context
import android.util.Log
import com.example.financehub.data.database.AppDatabase
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
import com.example.financehub.network.models.BatchSyncExpenseTagsRequest
import com.example.financehub.network.models.BatchSyncExpensesRequest
import com.example.financehub.network.models.BatchSyncGraphEdgesRequest
import com.example.financehub.network.models.BatchSyncTagsRequest
import com.example.financehub.network.models.BatchSyncTargetsRequest
import com.example.financehub.network.models.CreateExpenseBatchRequest
import com.example.financehub.network.models.CreateExpenseTagBatchRequest
import com.example.financehub.network.models.CreateGraphEdgeBatchRequest
import com.example.financehub.network.models.CreateTagBatchRequest
import com.example.financehub.network.models.CreateTargetBatchRequest
import com.example.financehub.network.models.DeleteExpenseBatchRequest
import com.example.financehub.network.models.DeleteExpenseTagBatchRequest
import com.example.financehub.network.models.DeleteGraphEdgeBatchRequest
import com.example.financehub.network.models.DeleteTagBatchRequest
import com.example.financehub.network.models.DeleteTargetBatchRequest
import com.example.financehub.network.models.SyncResultType
import com.example.financehub.network.models.UpdateExpenseBatchRequest
import com.example.financehub.network.models.UpdateGraphEdgeBatchRequest
import com.example.financehub.network.models.UpdateTagBatchRequest
import com.example.financehub.network.models.UpdateTargetBatchRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class SyncManager @Inject constructor(
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
            when (pushResult) {
                is SyncResult.Failure -> {
                    _syncState.value = SyncState.ERROR
                    // The compiler smart-casts pushResult to Failure, so you can safely access .error
                    return SyncResult.failure("Failed to push local changes: ${pushResult.error}")
                }
                is SyncResult.Success -> {
                    // This block is for the success case.
                    // You can log the success message or simply do nothing and proceed.
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
     * Push all pending local changes to the server
     */
    private suspend fun pushLocalChanges(): SyncResult {
        try {
            _syncProgress.value = _syncProgress.value.copy(stage = "Pushing local changes...")

            // Push expenses
            val pendingExpenses = database.expenseDao().getPendingSyncExpenses()
            pushExpensesToServer(pendingExpenses)

            // Push tags
            val pendingTags = database.tagsDao().getPendingSyncTags()
            pushTagsToServer(pendingTags)

            // Push targets
            val pendingTargets = database.targetDao().getPendingSyncTargets()
            pushTargetsToServer(pendingTargets)

            // Push expense-tag relationships
            val pendingExpenseTags = database.expenseTagsCrossRefDao().getPendingSyncExpenseTags()
            pushExpenseTagsToServer(pendingExpenseTags)

            // Push graph edges
            val pendingGraphEdges = database.graphEdgeDAO().getPendingSyncGraphEdges()
            pushGraphEdgesToServer(pendingGraphEdges)

            return SyncResult.success("Local changes pushed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to push local changes", e)
            return SyncResult.failure("Failed to push local changes: ${e.message}")
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
     * Push expenses to server in batches
     */
    private suspend fun pushExpensesToServer(expenses: List<Expense>) {
        expenses.chunked(MAX_BATCH_SIZE).forEach { batch ->
            val operations = batch.map { expense ->
                when (expense.syncOperation) {
                    "CREATE" -> CreateExpenseBatchRequest(
                        title = expense.title,
                        amount = expense.amount,
                        year = expense.year,
                        month = expense.month,
                        date = expense.date,
                        clientId = expense.expenseID.toString()
                    )
                    "UPDATE" -> UpdateExpenseBatchRequest(
                        serverId = expense.serverId!!,
                        title = expense.title,
                        amount = expense.amount,
                        year = expense.year,
                        month = expense.month,
                        date = expense.date
                    )
                    "DELETE" -> DeleteExpenseBatchRequest(
                        serverId = expense.serverId!!
                    )
                    else -> null
                }
            }.filterNotNull()

            // Send batch to server
            val response = apiService.batchSyncExpenses(BatchSyncExpensesRequest(operations))
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    // Update local records with server IDs and sync status
                    responseBody.results.forEach { syncResult: SyncResultType ->
                        if (syncResult.success) {
                            val localExpense = batch.find { it.expenseID.toString() == syncResult.clientId }
                            localExpense?.let { expense ->
                                database.expenseDao().updateSyncMetadata(
                                    expenseId = expense.expenseID,
                                    serverId = syncResult.serverId,
                                    lastSyncedAt = System.currentTimeMillis(),
                                    pendingSync = false,
                                    syncOperation = null
                                )
                            }
                        }
                    }
                }
            } else {
                Log.e(TAG, "Failed to sync expense batch: ${response.message()}")
            }
        }
    }

    /**
     * Push tags to server in batches
     */
    private suspend fun pushTagsToServer(tags: List<Tags>) {
        tags.chunked(MAX_BATCH_SIZE).forEach { batch ->
            val operations = batch.map { tag ->
                when (tag.syncOperation) {
                    "CREATE" -> CreateTagBatchRequest(
                        name = tag.tag,
                        monthlyAmount = tag.monthlyAmount,
                        currentMonth = tag.currentMonth,
                        currentYear = tag.currentYear,
                        createdDay = tag.createdDay,
                        createdMonth = tag.createdMonth,
                        createdYear = tag.createdYear,
                        clientId = tag.tagID.toString()
                    )
                    "UPDATE" -> UpdateTagBatchRequest(
                        serverId = tag.serverId!!,
                        name = tag.tag,
                        monthlyAmount = tag.monthlyAmount,
                        currentMonth = tag.currentMonth,
                        currentYear = tag.currentYear
                    )
                    "DELETE" -> DeleteTagBatchRequest(
                        serverId = tag.serverId!!
                    )
                    else -> null
                }
            }.filterNotNull()

            // Send batch to server
            val response = apiService.batchSyncTags(BatchSyncTagsRequest(operations))
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    responseBody.results.forEach { syncResult: SyncResultType ->
                        if (syncResult.success) {
                            val localTag = batch.find { it.tagID.toString() == syncResult.clientId }
                            localTag?.let { tag ->
                                database.tagsDao().updateSyncMetadata(
                                    tagId = tag.tagID,
                                    serverId = syncResult.serverId,
                                    lastSyncedAt = System.currentTimeMillis(),
                                    pendingSync = false,
                                    syncOperation = null
                                )
                            }
                        }
                    }
                }
            } else {
                Log.e(TAG, "Failed to sync tag batch: ${response.message()}")
            }
        }
    }

    /**
     * Push targets to server in batches
     */
    private suspend fun pushTargetsToServer(targets: List<Target>) {
        targets.chunked(MAX_BATCH_SIZE).forEach { batch ->
            val operations = batch.map { target ->
                when (target.syncOperation) {
                    "CREATE" -> CreateTargetBatchRequest(
                        month = target.month,
                        year = target.year,
                        tagId = target.tagID.toString(),
                        amount = target.amount,
                        spent = target.spent,
                        clientId = "${target.month}-${target.year}-${target.tagID}"
                    )
                    "UPDATE" -> UpdateTargetBatchRequest(
                        serverId = target.serverId!!,
                        amount = target.amount,
                        spent = target.spent
                    )
                    "DELETE" -> DeleteTargetBatchRequest(
                        serverId = target.serverId!!
                    )
                    else -> null
                }
            }.filterNotNull()

            // Send batch to server
            val response = apiService.batchSyncTargets(BatchSyncTargetsRequest(operations))
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    responseBody.results.forEach { syncResult: SyncResultType ->
                        if (syncResult.success) {
                            val localTarget = batch.find { 
                                "${it.month}-${it.year}-${it.tagID}" == syncResult.clientId 
                            }
                            localTarget?.let { target ->
                                database.targetDao().updateSyncMetadata(
                                    month = target.month,
                                    year = target.year,
                                    tagId = target.tagID,
                                    serverId = syncResult.serverId,
                                    lastSyncedAt = System.currentTimeMillis(),
                                    pendingSync = false,
                                    syncOperation = null
                                )
                            }
                        }
                    }
                }
            } else {
                Log.e(TAG, "Failed to sync target batch: ${response.message()}")
            }
        }
    }

    /**
     * Push expense-tag relationships to server
     */
    private suspend fun pushExpenseTagsToServer(expenseTags: List<ExpenseTagsCrossRef>) {
        expenseTags.chunked(MAX_BATCH_SIZE).forEach { batch ->
            val operations = batch.map { expenseTag ->
                when (expenseTag.syncOperation) {
                    "CREATE" -> CreateExpenseTagBatchRequest(
                        expenseId = expenseTag.expenseID.toString(),
                        tagId = expenseTag.tagID.toString(),
                        clientId = "${expenseTag.expenseID}-${expenseTag.tagID}"
                    )
                    "DELETE" -> DeleteExpenseTagBatchRequest(
                        serverId = expenseTag.serverId!!
                    )
                    else -> null
                }
            }.filterNotNull()

            // Send batch to server
            val response = apiService.batchSyncExpenseTags(BatchSyncExpenseTagsRequest(operations))
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    responseBody.results.forEach { syncResult: SyncResultType ->
                        if (syncResult.success) {
                            val localExpenseTag = batch.find { 
                                "${it.expenseID}-${it.tagID}" == syncResult.clientId 
                            }
                            localExpenseTag?.let { expenseTag ->
                                database.expenseTagsCrossRefDao().updateSyncMetadata(
                                    expenseId = expenseTag.expenseID,
                                    tagId = expenseTag.tagID,
                                    serverId = syncResult.serverId,
                                    lastSyncedAt = System.currentTimeMillis(),
                                    pendingSync = false,
                                    syncOperation = null
                                )
                            }
                        }
                    }
                }
            } else {
                Log.e(TAG, "Failed to sync expense-tag batch: ${response.message()}")
            }
        }
    }

    /**
     * Push graph edges to server
     */
    private suspend fun pushGraphEdgesToServer(graphEdges: List<GraphEdge>) {
        graphEdges.chunked(MAX_BATCH_SIZE).forEach { batch ->
            val operations = batch.map { edge ->
                when (edge.syncOperation) {
                    "CREATE" -> CreateGraphEdgeBatchRequest(
                        fromTagId = edge.fromTagId.toString(),
                        toTagId = edge.toTagId.toString(),
                        weight = edge.weight,
                        clientId = "${edge.fromTagId}-${edge.toTagId}"
                    )
                    "UPDATE" -> UpdateGraphEdgeBatchRequest(
                        serverId = edge.serverId!!,
                        weight = edge.weight
                    )
                    "DELETE" -> DeleteGraphEdgeBatchRequest(
                        serverId = edge.serverId!!
                    )
                    else -> null
                }
            }.filterNotNull()

            // Send batch to server
            val response = apiService.batchSyncGraphEdges(BatchSyncGraphEdgesRequest(operations))
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    responseBody.results.forEach { syncResult: SyncResultType ->
                        if (syncResult.success) {
                            val localEdge = batch.find { 
                                "${it.fromTagId}-${it.toTagId}" == syncResult.clientId 
                            }
                            localEdge?.let { edge ->
                                database.graphEdgeDAO().updateSyncMetadata(
                                    fromTagId = edge.fromTagId,
                                    toTagId = edge.toTagId,
                                    serverId = syncResult.serverId,
                                    lastSyncedAt = System.currentTimeMillis(),
                                    pendingSync = false,
                                    syncOperation = null
                                )
                            }
                        }
                    }
                }
            } else {
                Log.e(TAG, "Failed to sync graph edge batch: ${response.message()}")
            }
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
     * Clean up data older than 3 months
     */
    private suspend fun cleanupOldData() {
        val threeMonthsAgo = System.currentTimeMillis() - (90 * 24 * 60 * 60 * 1000L) // 90 days
        
        database.expenseDao().deleteOldExpenses(threeMonthsAgo)
        database.tagsDao().deleteOldTags(threeMonthsAgo)
        database.targetDao().deleteOldTargets(threeMonthsAgo)
        database.expenseTagsCrossRefDao().deleteOldExpenseTags(threeMonthsAgo)
        database.graphEdgeDAO().deleteOldGraphEdges(threeMonthsAgo)
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
    GRAPH_EDGE
}

/**
 * Sync operations
 */
enum class SyncOperation {
    CREATE,
    UPDATE,
    DELETE
}