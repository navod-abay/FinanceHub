package com.example.financehub.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Network configuration for the FinanceHub API
 */
object NetworkConfig {
    
    // Server configuration - update IP address to match your server
    private const val BASE_URL = "http://locahost:8000/" // Replace with your server IP
    private const val API_BASE_URL = "${BASE_URL}api/v1/"
    
    // Timeouts
    private const val CONNECT_TIMEOUT = 10L // seconds
    private const val READ_TIMEOUT = 30L // seconds
    private const val WRITE_TIMEOUT = 30L // seconds
    
    /**
     * JSON configuration for serialization
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    
    /**
     * OkHttp client with logging and timeout configuration
     */
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Retrofit instance for API communication
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
    
    /**
     * Create API service instance
     */
    fun <T> createService(serviceClass: Class<T>): T {
        return retrofit.create(serviceClass)
    }
    
    /**
     * Get the base URL for reference
     */
    fun getBaseUrl(): String = BASE_URL
    
    /**
     * Get the API base URL for reference
     */
    fun getApiBaseUrl(): String = API_BASE_URL
}