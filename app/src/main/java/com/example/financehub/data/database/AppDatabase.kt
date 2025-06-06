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


@Database(entities = [Expense::class, Tags::class, ExpenseTagsCrossRef::class], version = 6)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun tagsDao(): TagsDao
    abstract fun expenseTagsCrossRefDao(): ExpenseTagsCrossRefDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                ).addMigrations(MIGRATION_5_6).build()
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

        // In your AppDatabase.kt (within the companion object)

    }



}
