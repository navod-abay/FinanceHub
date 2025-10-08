package com.example.financehub.data.database.models

import androidx.room.DatabaseView


@DatabaseView("SELECT tagID, tag FROM TAGS")
data class TagRef (
    val tagID: Int = 0,
    val tag: String = ""
)