package com.example.financehub.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.financehub.data.database.AppDatabase
import com.example.financehub.network.ApiServiceFactory

class SyncWorkerFactory(
    private val context: Context
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {

        return if (workerClassName == SyncWorker::class.java.name) {
            val database = AppDatabase.getDatabase(appContext)
            val apiService = ApiServiceFactory.apiService
            val syncManager = SyncManager(database, apiService, context)
            SyncWorker(appContext, workerParameters, syncManager)
        } else {
            null
        }
    }
}