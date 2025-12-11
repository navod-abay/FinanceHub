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


@Database(entities = [Expense::class, Tags::class, ExpenseTagsCrossRef::class, Target::class, GraphEdge::class], version = 10)
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
                ).addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build()
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
                // Step 1: Migrate expense_tags table
                // Create new table with id as primary key
                db.execSQL("""CREATE TABLE IF NOT EXISTS expense_tags_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    expenseID INTEGER NOT NULL,
                    tagID INTEGER NOT NULL,
                    serverId TEXT,
                    lastSyncedAt INTEGER,
                    pendingSync INTEGER NOT NULL DEFAULT 0,
                    syncOperation TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(expenseID) REFERENCES expenses(expenseID) ON DELETE CASCADE,
                    FOREIGN KEY(tagID) REFERENCES tags(tagID) ON DELETE CASCADE
                )""")
                
                // Create unique index on the old composite key
                db.execSQL("CREATE UNIQUE INDEX index_expense_tags_composite ON expense_tags_new(expenseID, tagID)")
                
                // Copy data with generated IDs
                db.execSQL("""INSERT INTO expense_tags_new (id, expenseID, tagID, serverId, lastSyncedAt, pendingSync, syncOperation, createdAt, updatedAt)
                    SELECT 
                        CASE WHEN serverId IS NOT NULL THEN serverId ELSE hex(randomblob(16)) END,
                        expenseID, tagID, serverId, lastSyncedAt, pendingSync, syncOperation, createdAt, updatedAt
                    FROM expense_tags""")
                
                // Drop old table and rename new one
                db.execSQL("DROP TABLE expense_tags")
                db.execSQL("ALTER TABLE expense_tags_new RENAME TO expense_tags")
                
                // Step 2: Migrate targets table
                db.execSQL("""CREATE TABLE IF NOT EXISTS targets_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    month INTEGER NOT NULL,
                    year INTEGER NOT NULL,
                    tagID INTEGER NOT NULL,
                    amount INTEGER NOT NULL,
                    spent INTEGER NOT NULL,
                    serverId TEXT,
                    lastSyncedAt INTEGER,
                    pendingSync INTEGER NOT NULL DEFAULT 0,
                    syncOperation TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(tagID) REFERENCES tags(tagID) ON DELETE CASCADE
                )""")
                
                db.execSQL("CREATE UNIQUE INDEX index_targets_composite ON targets_new(month, year, tagID)")
                db.execSQL("CREATE INDEX index_targets_tagID ON targets_new(tagID)")
                
                db.execSQL("""INSERT INTO targets_new (id, month, year, tagID, amount, spent, serverId, lastSyncedAt, pendingSync, syncOperation, createdAt, updatedAt)
                    SELECT 
                        CASE WHEN serverId IS NOT NULL THEN serverId ELSE hex(randomblob(16)) END,
                        month, year, tagID, amount, spent, serverId, lastSyncedAt, pendingSync, syncOperation, createdAt, updatedAt
                    FROM targets""")
                
                db.execSQL("DROP TABLE targets")
                db.execSQL("ALTER TABLE targets_new RENAME TO targets")
                
                // Step 3: Migrate graph_edges table
                db.execSQL("""CREATE TABLE IF NOT EXISTS graph_edges_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    fromTagId INTEGER NOT NULL,
                    toTagId INTEGER NOT NULL,
                    weight INTEGER NOT NULL,
                    serverId TEXT,
                    lastSyncedAt INTEGER,
                    pendingSync INTEGER NOT NULL DEFAULT 0,
                    syncOperation TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )""")
                
                db.execSQL("CREATE UNIQUE INDEX index_graph_edges_composite ON graph_edges_new(fromTagId, toTagId)")
                db.execSQL("CREATE INDEX index_graph_edges_fromTagId ON graph_edges_new(fromTagId)")
                
                db.execSQL("""INSERT INTO graph_edges_new (id, fromTagId, toTagId, weight, serverId, lastSyncedAt, pendingSync, syncOperation, createdAt, updatedAt)
                    SELECT 
                        CASE WHEN serverId IS NOT NULL THEN serverId ELSE hex(randomblob(16)) END,
                        fromTagId, toTagId, weight, serverId, lastSyncedAt, pendingSync, syncOperation, createdAt, updatedAt
                    FROM graph_edges""")
                
                db.execSQL("DROP TABLE graph_edges")
                db.execSQL("ALTER TABLE graph_edges_new RENAME TO graph_edges")
            }
        }
        
        private val MIGRATION_9_8 = object : Migration(9, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }
    }
}
