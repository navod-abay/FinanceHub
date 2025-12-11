package com.example.financehub.network

import com.example.financehub.network.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API service interface for com.example.financehub.FinanceHub server communication
 */
interface FinanceHubApiService {
    
    // Health check
    @GET("../../health")
    suspend fun healthCheck(): Response<HealthResponse>
    
    // Core Operations
    @POST("operations/add-expense")
    suspend fun addExpense(@Body request: AddExpenseRequest): Response<AddExpenseResponse>
    
    @PUT("operations/update-expense/{expense_id}")
    suspend fun updateExpense(
        @Path("expense_id") expenseId: String,
        @Body request: UpdateExpenseRequest
    ): Response<OperationResponse>
    
    @HTTP(method = "DELETE", path = "operations/delete-expense/{expense_id}", hasBody = true)
    suspend fun deleteExpense(
        @Path("expense_id") expenseId: String,
        @Body request: DeleteExpenseRequest
    ): Response<OperationResponse>
    
    @POST("operations/add-target")
    suspend fun addTarget(@Body request: AddTargetRequest): Response<OperationResponse>
    
    // Synchronization endpoints
    @GET("sync/delta")
    suspend fun getSyncDelta(@Query("since") since: Long? = null): Response<SyncDeltaResponse>
    
    @POST("sync/push")
    suspend fun pushSyncData(@Body request: SyncPushRequest): Response<SyncPushResponse>
    
    @POST("sync/full")
    suspend fun fullSync(): Response<SyncDeltaResponse>
    
    // Query endpoints
    @GET("expenses")
    suspend fun getExpenses(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("since") since: Long? = null,
        @Query("tag_ids") tagIds: String? = null
    ): Response<List<ExpenseResponse>>
    
    @GET("expenses/{expense_id}")
    suspend fun getExpense(@Path("expense_id") expenseId: String): Response<ExpenseResponse>
    
    @GET("tags")
    suspend fun getTags(
        @Query("limit") limit: Int = 100,
        @Query("search") search: String? = null
    ): Response<List<TagResponse>>
    
    @GET("tags/{tag_id}")
    suspend fun getTag(@Path("tag_id") tagId: String): Response<TagResponse>
    
    @GET("targets")
    suspend fun getTargets(
        @Query("month") month: Int? = null,
        @Query("year") year: Int? = null,
        @Query("tag_id") tagId: String? = null
    ): Response<List<TargetResponse>>
    
    @POST("recommendations")
    suspend fun getRecommendations(@Body request: RecommendationRequest): Response<List<RecommendationResponse>>
    
    @GET("stats/summary")
    suspend fun getSummaryStats(): Response<SummaryStatsResponse>

    // Batch Synchronization Endpoints
    @POST("sync/batch/expenses")
    suspend fun batchSyncExpenses(@Body request: BatchSyncExpensesRequest): Response<BatchSyncResponse>

    @POST("sync/batch/tags")
    suspend fun batchSyncTags(@Body request: BatchSyncTagsRequest): Response<BatchSyncResponse>

    @POST("sync/batch/targets")
    suspend fun batchSyncTargets(@Body request: BatchSyncTargetsRequest): Response<BatchSyncResponse>

    @POST("sync/batch/expense-tags")
    suspend fun batchSyncExpenseTags(@Body request: BatchSyncExpenseTagsRequest): Response<BatchSyncResponse>

    @POST("sync/batch/graph-edges")
    suspend fun batchSyncGraphEdges(@Body request: BatchSyncGraphEdgesRequest): Response<BatchSyncResponse>

    // Delta Sync Endpoint
    @GET("sync/updated-data")
    suspend fun getUpdatedData(@Query("since") lastSyncTimestamp: Long): Response<UpdatedDataResponse>
}

/**
 * Factory for creating the API service
 */
object ApiServiceFactory {
    
    val apiService: FinanceHubApiService by lazy {
        NetworkConfig.createService(FinanceHubApiService::class.java)
    }

}