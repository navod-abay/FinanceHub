package com.example.financehub.network.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Network models for API communication with the server
 * These correspond to the Pydantic models on the FastAPI server
 */

// Request models for operations
@Serializable
data class AddExpenseRequest(
    val expense: ExpenseCreateRequest,
    val existing_tags: List<String> = emptyList(), // List of tag server IDs
    val new_tags: List<String> = emptyList(),      // List of new tag names
    val device_timestamp: Long
)

@Serializable
data class UpdateExpenseRequest(
    val expense: ExpenseCreateRequest,
    val added_existing_tags: List<String> = emptyList(),
    val removed_tags: List<String> = emptyList(),
    val added_new_tags: List<String> = emptyList(),
    val device_timestamp: Long
)

@Serializable
data class DeleteExpenseRequest(
    val local_id: Int? = null,
    val server_id: String? = null,
    val device_timestamp: Long
)

@Serializable
data class AddTargetRequest(
    val target: TargetCreateRequest,
    val device_timestamp: Long
)

// Individual entity creation models
@Serializable
data class ExpenseCreateRequest(
    val title: String,
    val amount: Int,
    val year: Int,
    val month: Int,
    val date: Int,
    val local_id: Int? = null
)

@Serializable
data class TargetCreateRequest(
    val month: Int,
    val year: Int,
    val tag_id: String,
    val amount: Int,
    val spent: Int = 0
)

// Response models
@Serializable
data class OperationResponse(
    val success: Boolean,
    val message: String = "",
    val server_timestamp: Long,
    val affected_entities: Map<String, List<String>>? = null
)

@Serializable
data class AddExpenseResponse(
    val success: Boolean,
    val message: String = "",
    val server_timestamp: Long,
    val expense_id: String? = null,
    val created_tags: List<TagResponse>? = null,
    val affected_entities: Map<String, List<String>>? = null
)

// Entity response models
@Serializable
data class ExpenseResponse(
    val id: String,
    val local_id: Int? = null,
    val title: String,
    val amount: Int,
    val year: Int,
    val month: Int,
    val date: Int,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class TagResponse(
    val id: String,
    val local_id: Int? = null,
    val tag: String,
    val monthly_amount: Int = 0,
    val current_month: Int = 0,
    val current_year: Int = 0,
    val created_day: Int = 0,
    val created_month: Int = 0,
    val created_year: Int = 0,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class TargetResponse(
    val month: Int,
    val year: Int,
    val tag_id: String,
    val amount: Int,
    val spent: Int = 0,
    val created_at: String? = null,
    val updated_at: String? = null
)

// Sync models
@Serializable
data class SyncDeltaResponse(
    val expenses: List<ExpenseResponse> = emptyList(),
    val tags: List<TagResponse> = emptyList(),
    val targets: List<TargetResponse> = emptyList(),
    val graph_edges: List<GraphEdgeResponse> = emptyList(),
    val last_sync_timestamp: Long
)

@Serializable
data class GraphEdgeResponse(
    val from_tag_id: String,
    val to_tag_id: String,
    val weight: Int,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class SyncPushRequest(
    val expenses: List<Map<String, String>> = emptyList(),
    val tags: List<Map<String, String>> = emptyList(),
    val targets: List<Map<String, String>> = emptyList(),
    val device_timestamp: Long
)

@Serializable
data class SyncPushResponse(
    val success: Boolean,
    val processed_count: Int,
    val failed_items: List<Map<String, String>> = emptyList(),
    val server_timestamp: Long
)

// Query models
@Serializable
data class RecommendationRequest(
    val tag_id: String
)

@Serializable
data class RecommendationResponse(
    val tag_id: String,
    val tag_name: String,
    val score: Double
)

// Health check response
@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: Long,
    val version: String
)

// Statistics response
@Serializable
data class SummaryStatsResponse(
    val current_month_total: Int,
    val last_month_total: Int,
    val month_over_month_change: Int,
    val total_expenses: Int,
    val total_tags: Int,
    val active_targets: Int,
    val timestamp: Long
)


// Batch Sync Models

@Serializable
sealed interface ExpenseOperation

@Serializable
@SerialName("create_expense")
data class CreateExpenseBatchRequest(
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
data class UpdateExpenseBatchRequest(
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
data class DeleteExpenseBatchRequest(
    val serverId: String
) : ExpenseOperation

@Serializable
sealed interface TagOperation

@Serializable
@SerialName("create_tag")
data class CreateTagBatchRequest(
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
data class UpdateTagBatchRequest(
    val serverId: String,
    val name: String,
    val monthlyAmount: Int,
    val currentMonth: Int,
    val currentYear: Int
) : TagOperation

@Serializable
@SerialName("delete_tag")
data class DeleteTagBatchRequest(
    val serverId: String
) : TagOperation

@Serializable
sealed interface TargetOperation

@Serializable
@SerialName("create_target")
data class CreateTargetBatchRequest(
    val month: Int,
    val year: Int,
    val tagId: String,
    val amount: Int,
    val spent: Int,
    val clientId: String
) : TargetOperation

@Serializable
@SerialName("update_target")
data class UpdateTargetBatchRequest(
    val serverId: String,
    val amount: Int,
    val spent: Int
) : TargetOperation

@Serializable
@SerialName("delete_target")
data class DeleteTargetBatchRequest(
    val serverId: String
) : TargetOperation

@Serializable
sealed interface ExpenseTagOperation

@Serializable
@SerialName("create_expense_tag")
data class CreateExpenseTagBatchRequest(
    val expenseId: String,
    val tagId: String,
    val clientId: String
) : ExpenseTagOperation

@Serializable
@SerialName("delete_expense_tag")
data class DeleteExpenseTagBatchRequest(
    val serverId: String
) : ExpenseTagOperation

@Serializable
sealed interface GraphEdgeOperation

@Serializable
@SerialName("create_graph_edge")
data class CreateGraphEdgeBatchRequest(
    val fromTagId: String,
    val toTagId: String,
    val weight: Int,
    val clientId: String
) : GraphEdgeOperation

@Serializable
@SerialName("update_graph_edge")
data class UpdateGraphEdgeBatchRequest(
    val serverId: String,
    val weight: Int
) : GraphEdgeOperation

@Serializable
@SerialName("delete_graph_edge")
data class DeleteGraphEdgeBatchRequest(
    val serverId: String
) : GraphEdgeOperation

// Batch request wrappers
@Serializable
data class BatchSyncExpensesRequest(
    val operations: List<ExpenseOperation>
)

@Serializable
data class BatchSyncTagsRequest(
    val operations: List<TagOperation>
)

@Serializable
data class BatchSyncTargetsRequest(
    val operations: List<TargetOperation>
)

@Serializable
data class BatchSyncExpenseTagsRequest(
    val operations: List<ExpenseTagOperation>
)

@Serializable
data class BatchSyncGraphEdgesRequest(
    val operations: List<GraphEdgeOperation>
)

// Batch sync result type
@Serializable
data class SyncResultType(
    val success: Boolean,
    val clientId: String? = null,
    val serverId: String? = null,
    val error: String? = null
)

// Batch sync response
@Serializable
data class BatchSyncResponse(
    val results: List<SyncResultType>
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

// Updated data response for delta sync
@Serializable
data class UpdatedDataResponse(
    val expenses: List<ApiExpense> = emptyList(),
    val tags: List<ApiTag> = emptyList(),
    val targets: List<ApiTarget> = emptyList(),
    val expenseTags: List<ApiExpenseTag> = emptyList(),
    val graphEdges: List<ApiGraphEdge> = emptyList()
)
