package com.example.financehub.data.database
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.financehub.data.dao.ExpenseDao
import com.example.financehub.data.dao.ExpenseTagsCrossRefDao
import com.example.financehub.data.dao.TagsDao
import com.example.financehub.data.dao.TargetDao


@Database(entities = [Expense::class, Tags::class, ExpenseTagsCrossRef::class, Target::class], version = 7)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun tagsDao(): TagsDao
    abstract fun expenseTagsCrossRefDao(): ExpenseTagsCrossRefDao
    abstract fun targetDao(): TargetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                ).addMigrations(MIGRATION_5_6, MIGRATION_6_7).build()
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

        // In your AppDatabase.kt (within the companion object)

    }



}
