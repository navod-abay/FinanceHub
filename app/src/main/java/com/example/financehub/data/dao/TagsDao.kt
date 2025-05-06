package com.example.financehub.data.dao

import androidx.paging.PagingSource
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

    @Query("UPDATE tags SET monthlyAmount = monthlyAmount + :amount, currentMonth = :currentMonth, currentYear = :currentYear  WHERE tagID = :tagID")
    suspend fun incrementAmount(tagID: Int, amount: Int, currentMonth: Int, currentYear: Int)

    @Query("SELECT * FROM tags WHERE currentMonth = :currentMonth AND currentYear = :currentYear ORDER BY monthlyAmount DESC LIMIT 1")
    fun getTopTagForMonth(currentMonth: Int, currentYear: Int): Flow<TagWithAmount?>

    @Query("SELECT * FROM tags ORDER BY monthlyAmount DESC")
    fun getPagedTags(): PagingSource<Int, Tags>

    @Query("SELECT tagID FROM tags WHERE tag = :tagName LIMIT 1")
    suspend fun getTagIdByName(tagName: String): Int?

    @Query("SELECT * FROM tags WHERE tag = :tag LIMIT 1")
    suspend fun getTagbyTag(tag: String): Tags?

    @Query("UPDATE tags SET monthlyAmount = monthlyAmount - :amount WHERE tagID = :tagID")
    suspend fun decrementAmount(tagID: Int, amount: Int)

    @Query("SELECT * FROM tags WHERE tag LIKE :query || '%'")
    fun getMatchingTags(query: String): Flow<List<Tags>>

    @Query("SELECT * FROM tags ORDER BY tag ASC")
    fun getAllTags(): Flow<List<Tags>>

    @Update
    suspend fun updateTag(tag: Tags)



}