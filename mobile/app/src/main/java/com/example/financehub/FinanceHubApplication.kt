package com.example.financehub

import android.app.Application
import androidx.work.Configuration
import com.example.financehub.sync.SyncWorkerFactory

class FinanceHubApplication: Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(SyncWorkerFactory(this))
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

}