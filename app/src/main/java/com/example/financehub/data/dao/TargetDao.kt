package com.example.financehub.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.financehub.data.database.Target
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTarget(target: Target)

    @Query("SELECT * FROM targets WHERE month = :month AND year = :year AND tagID = :tagID LIMIT 1")
    suspend fun getTarget(month: Int, year: Int, tagID: Int): Target?

    @Query("SELECT * FROM targets WHERE year = :year AND month = :month")
    suspend fun getTargetsForMonthYear(month: Int, year: Int): List<Target>

    @Query("SELECT * FROM targets")
    suspend fun getAllTargets(): List<Target>

    @Query("SELECT * FROM targets WHERE (year > :currentYear) OR (year = :currentYear AND month >= :currentMonth)")
    suspend fun getAllTargetsFromCurrentMonth(currentMonth: Int, currentYear: Int): List<Target>

    @Query("DELETE FROM targets WHERE month = :month AND year = :year AND tagID = :tagID")
    suspend fun deleteTarget(month: Int, year: Int, tagID: Int)

    @Query("UPDATE targets SET amount = :newAmount WHERE month = :month AND year = :year AND tagID = :tagID")
    suspend fun updateTargetAmount(month: Int, year: Int, tagID: Int, newAmount: Int)

    @Query("UPDATE targets SET spent = :newSpent WHERE month = :month AND year = :year AND tagID = :tagID")
    suspend fun updateTargetSpent(month: Int, year: Int, tagID: Int, newSpent: Int)

    @Query("UPDATE targets SET spent = spent + :amount WHERE month = :month AND year = :year AND tagID = :tagID")
    suspend fun incrementSpentAmount(month: Int, year: Int, tagID: Int, amount: Int)

    @Query("SELECT COUNT(*) FROM targets WHERE month = :month AND year = :year AND spent > amount")
    fun getMissedTargetsCount(month: Int, year: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM targets WHERE month = :month AND year = :year")
    fun getTotalTargetsCount(month: Int, year: Int): Flow<Int>
}
