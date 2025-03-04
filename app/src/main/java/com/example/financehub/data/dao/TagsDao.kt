package com.example.financehub.data.dao

import androidx.room.*
import com.example.financehub.data.database.TagWithAmount
import com.example.financehub.data.database.Tags
import kotlinx.coroutines.flow.Flow

@Dao
interface TagsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tags): Long

    @Query("SELECT tagID FROM tags WHERE tag=:tag")
    suspend fun getTagIDbyTag(tag: String): Int?

    @Query("UPDATE tags SET monthlyAmount = monthlyAmount + :amount, currentMonth = currentMonth, currentYear = currentYear  WHERE tagID = :tagID")
    suspend fun incrementAmount(tagID: Int, amount: Int, currentMonth: Int, currentYear: Int) {
    }

    @Query("SELECT * FROM tags WHERE currentMonth = :currentMonth AND currentYear = :currentYear ORDER BY monthlyAmount DESC LIMIT 1")
    fun getTopTagForMonth(currentMonth: Int, currentYear: Int): Flow<TagWithAmount?>

}