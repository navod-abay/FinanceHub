package com.example.financehub.data.datastore

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class PersistentAnalyticData (
    val operationalExpenseMonthlyAvgAllTime: Float,
    val monthlyAverageAllTime  : Float,
    val numMonths : Int
)

class AnalyticsDatastore(private val context: Context) {
    private object Keys {
        val ROLLING_AVERAGE = floatPreferencesKey("monthly_rolling_average")
        val MONTH_COUNT = intPreferencesKey("number_of_months")
        val OPERATIONAL_EXPENSE_AVG = floatPreferencesKey("operational_expense_avg")
    }

    val analyticDataFlow: Flow<PersistentAnalyticData> = context.dataStore.data.map { prefs ->
        PersistentAnalyticData (
            monthlyAverageAllTime = prefs[Keys.ROLLING_AVERAGE] ?: 0.0f,
            numMonths = prefs[Keys.MONTH_COUNT] ?: 0,
            operationalExpenseMonthlyAvgAllTime = prefs[Keys.OPERATIONAL_EXPENSE_AVG] ?: 0.0f,
        )
    }

    suspend fun     updateAnalyticData(newAverage: Float, newMonthCount: Int, newOperationalExpenseAvg: Float) {
        context.dataStore.edit {prefs ->
            prefs[Keys.ROLLING_AVERAGE] = newAverage
            prefs[Keys.MONTH_COUNT] = newMonthCount
            prefs[Keys.OPERATIONAL_EXPENSE_AVG] = newOperationalExpenseAvg
        }
    }
}