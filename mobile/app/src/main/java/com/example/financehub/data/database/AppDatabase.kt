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


@Database(entities = [Expense::class, Tags::class, ExpenseTagsCrossRef::class, Target::class, GraphEdge::class], version = 11)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun tagsDao(): TagsDao
    abstract fun expenseTagsCrossRefDao(): ExpenseTagsCrossRefDao
    abstract fun targetDao(): TargetDao
    abstract fun graphEdgeDAO(): GraphEdgeDAO
    abstract fun TagRefDao(): TagRefDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                ).addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQL query to update the 'month' column in the 'expenses' table
                db.execSQL("UPDATE expenses SET month = month + 1")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS targets (
                    amount INTEGER NOT NULL,
                     month INTEGER NOT NULL, 
                    year INTEGER NOT NULL, 
                    tagID INTEGER NOT NULL, 
                    spent INTEGER NOT NULL DEFAULT 0,
                     PRIMARY KEY(month, year, tagID), 
                     FOREIGN KEY(tagID) REFERENCES tags(tagID) ON DELETE CASCADE)""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_targets_tagID ON targets(tagID)")

            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS graph_edges (
                    fromTagId INTEGER NOT NULL,
                     toTagId INTEGER NOT NULL, 
                    weight INTEGER NOT NULL, 
                     PRIMARY KEY(fromTagId, toTagId))""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_targets_fromTagId ON graph_edges(fromTagId)")
                db.execSQL("INSERT INTO graph_edges (fromTagId, toTagId, weight)\n" +
                        "SELECT et1.tagId, et2.tagId, COUNT(*) AS weight_increment\n" +
                        "FROM expense_tags et1\n" +
                        "JOIN expense_tags et2 \n" +
                        "  ON et1.expenseID = et2.expenseID\n" +
                        " AND et1.tagID <> et2.tagID\n" +
                        "WHERE NOT EXISTS (SELECT 1 FROM graph_edges)\n"// Condition to check if graph_edges is empty
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add sync metadata columns to expenses table
                db.execSQL("ALTER TABLE expenses ADD COLUMN serverId TEXT")
                db.execSQL("ALTER TABLE expenses ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE expenses ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expenses ADD COLUMN syncOperation TEXT")
                db.execSQL("ALTER TABLE expenses ADD COLUMN createdAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
                db.execSQL("ALTER TABLE expenses ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")

                // Add sync metadata columns to tags table
                db.execSQL("ALTER TABLE tags ADD COLUMN serverId TEXT")
                db.execSQL("ALTER TABLE tags ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE tags ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tags ADD COLUMN syncOperation TEXT")
                db.execSQL("ALTER TABLE tags ADD COLUMN syncCreatedAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
                db.execSQL("ALTER TABLE tags ADD COLUMN syncUpdatedAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")

                // Add sync metadata columns to targets table
                db.execSQL("ALTER TABLE targets ADD COLUMN serverId TEXT")
                db.execSQL("ALTER TABLE targets ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE targets ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE targets ADD COLUMN syncOperation TEXT")
                db.execSQL("ALTER TABLE targets ADD COLUMN createdAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
                db.execSQL("ALTER TABLE targets ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")

                // Add sync metadata columns to expense_tags table
                db.execSQL("ALTER TABLE expense_tags ADD COLUMN serverId TEXT")
                db.execSQL("ALTER TABLE expense_tags ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE expense_tags ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expense_tags ADD COLUMN syncOperation TEXT")
                db.execSQL("ALTER TABLE expense_tags ADD COLUMN createdAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
                db.execSQL("ALTER TABLE expense_tags ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")

                // Add sync metadata columns to graph_edges table
                db.execSQL("ALTER TABLE graph_edges ADD COLUMN serverId TEXT")
                db.execSQL("ALTER TABLE graph_edges ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE graph_edges ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE graph_edges ADD COLUMN syncOperation TEXT")
                db.execSQL("ALTER TABLE graph_edges ADD COLUMN createdAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
                db.execSQL("ALTER TABLE graph_edges ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
            }
        }
        
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes needed - this migration is just to update the version number
                // to match the current schema with serverId fields already added in migration 8->9
            }
        }
    }
}
