package com.example.financehub.data.database
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.financehub.data.dao.ExpenseDao
import com.example.financehub.data.dao.ExpenseTagsCrossRefDao
import com.example.financehub.data.dao.GraphEdgeDAO
import com.example.financehub.data.dao.TagRefDao
import com.example.financehub.data.dao.TagsDao
import com.example.financehub.data.dao.TargetDao
import com.example.financehub.data.dao.WishlistDao
import com.example.financehub.data.dao.WishlistTagsDao
import com.example.financehub.data.dao.SyncGroupDao
import com.example.financehub.data.dao.SyncGroupEntityDao
import com.example.financehub.data.dao.EntityMappingDao
import com.example.financehub.data.database.models.TagRef

@Database(
    entities = [
        Expense::class,
        Tags::class,
        ExpenseTagsCrossRef::class,
        Target::class,
        GraphEdge::class,
        Wishlist::class,
        WishlistTagsCrossRef::class,
        SyncGroup::class,
        SyncGroupEntity::class,
        EntityMapping::class
    ],
    views = [TagRef::class],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun tagsDao(): TagsDao
    abstract fun expenseTagsCrossRefDao(): ExpenseTagsCrossRefDao
    abstract fun targetDao(): TargetDao
    abstract fun graphEdgeDAO(): GraphEdgeDAO
    abstract fun TagRefDao(): TagRefDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun wishlistTagsDao(): WishlistTagsDao
    abstract fun syncGroupDao(): SyncGroupDao
    abstract fun syncGroupEntityDao(): SyncGroupEntityDao
    abstract fun entityMappingDao(): EntityMappingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                                context.applicationContext,
                                AppDatabase::class.java,
                                "expense_database"
                            ).fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
