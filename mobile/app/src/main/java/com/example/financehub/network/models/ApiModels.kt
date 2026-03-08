package com.example.financehub.network.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// Health check response
@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: Long,
    val version: String
)


// Batch Sync Models

/**
 * Base sealed interface for all sync operations.
 * Server expects a flat list of operations with type discriminator.
 */
@Serializable
sealed interface SyncOperation

@Serializable
sealed interface ExpenseOperation : SyncOperation

@Serializable
@SerialName("create_expense")
data class CreateExpenseOperation(
    val title: String,
    val amount: Int,
    val year: Int,
    val month: Int,
    val date: Int,
    val clientId: String
) : ExpenseOperation

// For batch update expense (different from UpdateExpenseRequest used in regular operations)
@Serializable
@SerialName("update_expense")
data class UpdateExpenseOperation(
    val serverId: String,
    val title: String,
    val amount: Int,
    val year: Int,
    val month: Int,
    val date: Int
) : ExpenseOperation

// For batch delete expense (different from DeleteExpenseRequest used in regular operations)
@Serializable
@SerialName("delete_expense")
data class DeleteExpenseOperation(
    val serverId: String
) : ExpenseOperation

@Serializable
sealed interface TagOperation : SyncOperation

@Serializable
@SerialName("create_tag")
data class CreateTagOperation(
    val name: String,
    val monthlyAmount: Int,
    val currentMonth: Int,
    val currentYear: Int,
    val createdDay: Int,
    val createdMonth: Int,
    val createdYear: Int,
    val clientId: String
) : TagOperation

@Serializable
@SerialName("update_tag")
data class UpdateTagOperation(
    val serverId: String,
    val name: String,
    val monthlyAmount: Int,
    val currentMonth: Int,
    val currentYear: Int
) : TagOperation

@Serializable
@SerialName("delete_tag")
data class DeleteTagOperation(
    val serverId: String
) : TagOperation

@Serializable
sealed interface TargetOperation : SyncOperation

@Serializable
@SerialName("create_target")
data class CreateTargetOperation(
    val month: Int,
    val year: Int,
    val tagId: String,
    val amount: Int,
    val spent: Int,
    val clientId: String
) : TargetOperation

@Serializable
@SerialName("update_target")
data class UpdateTargetOperation(
    val serverId: String,
    val amount: Int,
    val spent: Int
) : TargetOperation

@Serializable
@SerialName("delete_target")
data class DeleteTargetOperation(
    val serverId: String
) : TargetOperation

@Serializable
sealed interface ExpenseTagOperation : SyncOperation

@Serializable
@SerialName("create_expense_tag")
data class CreateExpenseTagOperation(
    val expenseId: String,
    val tagId: String,
    val clientId: String
) : ExpenseTagOperation

@Serializable
@SerialName("delete_expense_tag")
data class DeleteExpenseTagOperation(
    val serverId: String
) : ExpenseTagOperation

@Serializable
sealed interface GraphEdgeOperation : SyncOperation

@Serializable
@SerialName("create_graph_edge")
data class CreateGraphEdgeOperation(
    val fromTagId: String,
    val toTagId: String,
    val weight: Int,
    val clientId: String
) : GraphEdgeOperation

@Serializable
@SerialName("update_graph_edge")
data class UpdateGraphEdgeOperation(
    val serverId: String,
    val weight: Int
) : GraphEdgeOperation

@Serializable
@SerialName("delete_graph_edge")
data class DeleteGraphEdgeOperation(
    val serverId: String
) : GraphEdgeOperation

@Serializable
sealed interface WishlistTagOperation : SyncOperation {
    val wishlistId: Int
    val tagId: String
}

@Serializable
@SerialName("create_wishlist_tag")
data class CreateWishlistTagOperation(
    override val wishlistId: Int,
    override val tagId: String,
    val clientId: String
) : WishlistTagOperation

@Serializable
@SerialName("delete_wishlist_tag")
data class DeleteWishlistTagOperation(
    override val wishlistId: Int,
    override val tagId: String,
    val serverId: String? = null
) : WishlistTagOperation


@Serializable
sealed interface WishlistOperation : SyncOperation

@Serializable
@SerialName("create_wishlist")
data class CreateWishlistOperation(
    val name: String,
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val clientId: String
    // tagId removed
) : WishlistOperation

@Serializable
@SerialName("update_wishlist")
data class UpdateWishlistOperation(
    val serverId: String,
    val name: String? = null,
    val minPrice: Int? = null,
    val maxPrice: Int? = null
    // tagId removed
) : WishlistOperation

@Serializable
@SerialName("delete_wishlist")
data class DeleteWishlistOperation(
    val serverId: String
) : WishlistOperation

// Batch sync result type
@Serializable
data class SyncResultType(
    val success: Boolean,
    val clientId: String? = null,
    val serverId: String? = null,
    val error: String? = null
)


// API entity models (what server returns)
@Serializable
data class ApiExpense(
    val id: String,
    val title: String,
    val amount: Int,
    val year: Int,
    val month: Int,
    val date: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ApiTag(
    val id: String,
    val name: String,
    val monthlyAmount: Int,
    val currentMonth: Int,
    val currentYear: Int,
    val createdDay: Int,
    val createdMonth: Int,
    val createdYear: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ApiTarget(
    val id: String,
    val month: Int,
    val year: Int,
    val tagId: String,
    val amount: Int,
    val spent: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ApiExpenseTag(
    val id: String,
    val expenseId: String,
    val tagId: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ApiGraphEdge(
    val id: String,
    val fromTagId: String,
    val toTagId: String,
    val weight: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ApiWishlistItem(
    val id: String,
    val name: String,
    val minPrice: Int,
    val maxPrice: Int,
    // tagId removed
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ApiWishlistTag(
    val id: String,
    val wishlistId: Int,
    val tagId: String,
    val createdAt: Long,
    val updatedAt: Long
)

// Updated data response for delta sync
@Serializable
data class UpdatedDataResponse(
    val expenses: List<ApiExpense> = emptyList(),
    val tags: List<ApiTag> = emptyList(),
    val targets: List<ApiTarget> = emptyList(),
    val expenseTags: List<ApiExpenseTag> = emptyList(),
    val graphEdges: List<ApiGraphEdge> = emptyList(),
    val wishlist: List<ApiWishlistItem> = emptyList(),
    val wishlistTags: List<ApiWishlistTag> = emptyList()
)

// Atomic Sync Models

/**
 * Entity mapping from client ID to server ID.
 * Returned when a CREATE operation succeeds.
 */
@Serializable
data class EntityMappingResponse(
    @SerialName("entityType") val entityType: String,
    @SerialName("clientId") val clientId: String,
    @SerialName("serverId") val serverId: String
)

/**
 * Result of processing one atomic group.
 * A group either fully succeeds or fully fails.
 */


@Serializable
data class AtomicGroupResult(
    @SerialName("groupId") val groupId: String,
    val success: Boolean,
    val error: String? = null,
    @SerialName("entityMappings") val entityMappings: List<EntityMappingResponse> = emptyList(),
    @SerialName("rolledBack") val rolledBack: Boolean = false
)

/**
 * Response from atomic sync endpoint.
 * Contains results for all groups.
 */
@Serializable
data class AtomicSyncResponse(
    @SerialName("groupResults") val groupResults: List<AtomicGroupResult>,
    @SerialName("serverTimestamp") val serverTimestamp: Long
)

/**
 * A single atomic sync group containing related operations.
 * All operations in a group are processed atomically.
 * Operations are sent as a flat list with type discriminators.
 */
@Serializable
data class AtomicSyncGroup(
    @SerialName("groupId") val groupId: String,
    @SerialName("groupType") val groupType: String,
    val operations: List<SyncOperation>
)

/**
 * Request for atomic sync.
 * Contains multiple groups that are processed independently.
 */
@Serializable
data class AtomicSyncRequest(
    val groups: List<AtomicSyncGroup>,
    @SerialName("clientTimestamp") val clientTimestamp: Long,
    @SerialName("deviceId") val deviceId: String? = null
)