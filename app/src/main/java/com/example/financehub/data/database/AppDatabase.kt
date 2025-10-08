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


@Database(entities = [Expense::class, Tags::class, ExpenseTagsCrossRef::class, Target::class, GraphEdge::class], version = 8)
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
                ).addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_9_8).build()
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
        private val MIGRATION_9_8 = object : Migration(9, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }
    }
}
