package com.example.financehub.data.database

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class WishlistWithTags(
    @Embedded val wishlist: Wishlist,
    @Relation(
        parentColumn = "id",
        entityColumn = "tagID",
        associateBy = Junction(
            value = WishlistTagsCrossRef::class,
            parentColumn = "wishlistId",
            entityColumn = "tagID"
        )
    )
    val tags: List<Tags>
)
