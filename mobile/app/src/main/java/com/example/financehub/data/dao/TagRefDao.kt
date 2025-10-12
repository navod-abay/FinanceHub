package com.example.financehub.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.financehub.data.database.models.TagRef
import kotlinx.coroutines.flow.Flow

@Dao
interface TagRefDao {
    @Query("SELECT tagID, tag FROM TAGS")
    fun getAllTagRefs(): Flow<List<TagRef>>

    @Query("SELECT * FROM tags WHERE tag LIKE :query || '%'")
    fun getMatchingTags(query: String): Flow<List<TagRef>>
}