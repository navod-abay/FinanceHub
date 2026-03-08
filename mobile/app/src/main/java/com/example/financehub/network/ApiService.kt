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


    // Atomic Sync Endpoint
    @POST("sync/atomic")
    suspend fun atomicSync(@Body request: AtomicSyncRequest): Response<AtomicSyncResponse>

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