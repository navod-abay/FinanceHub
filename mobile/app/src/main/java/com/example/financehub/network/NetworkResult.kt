package com.example.financehub.network

import retrofit2.Response
import java.io.IOException

/**
 * A generic wrapper for network operations that handles success/failure states
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val exception: Exception) : NetworkResult<Nothing>()
    data class Loading(val message: String = "Loading...") : NetworkResult<Nothing>()
}

/**
 * Extension function to safely execute network calls and wrap them in NetworkResult
 */
suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): NetworkResult<T> {
    return try {
        val response = apiCall()
        if (response.isSuccessful) {
            response.body()?.let { body ->
                NetworkResult.Success(body)
            } ?: NetworkResult.Error(Exception("Empty response body"))
        } else {
            NetworkResult.Error(
                Exception("HTTP ${response.code()}: ${response.message()}")
            )
        }
    } catch (e: IOException) {
        NetworkResult.Error(Exception("Network error: ${e.message}", e))
    } catch (e: Exception) {
        NetworkResult.Error(Exception("Unexpected error: ${e.message}", e))
    }
}

/**
 * Extension function for handling NetworkResult in a more functional way
 */
inline fun <T> NetworkResult<T>.onSuccess(action: (T) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Success) action(data)
    return this
}

inline fun <T> NetworkResult<T>.onError(action: (Exception) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Error) action(exception)
    return this
}

inline fun <T> NetworkResult<T>.onLoading(action: (String) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Loading) action(message)
    return this
}