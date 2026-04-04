package com.example.financehub.data.database
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    ],
    views = [TagRef::class],
    version = 3
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                                context.applicationContext,
                                AppDatabase::class.java,
                                "expense_database"
                            ).addMigrations(MIGRATION_2_TO_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_2_TO_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop the table you no longer need
                database.execSQL("DROP TABLE IF EXISTS entity_mappings")
            }
        }
    }
}
