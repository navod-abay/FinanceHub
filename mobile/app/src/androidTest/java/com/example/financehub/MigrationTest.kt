package com.example.financehub

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.financehub.data.database.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun validateCurrentSchema() {
        // Create the database with the current version (2)
        // This validates that the current schema matches the compiled code
        helper.createDatabase(TEST_DB, 2).apply {
            close()
        }
    }

    /*
     * To test a migration from version 1 to 2:
     * 1. Ensure schema json for version 1 exists in schemas/ folder.
     * 2. Uncomment the test below.
     * 3. Provide the migration logic (e.g. AppDatabase.MIGRATION_1_2) to verify it works.
     */
    /*
    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        var db = helper.createDatabase(TEST_DB, 1).apply {
            // Insert data manually using SQL queries to simulate existing data
            execSQL("INSERT INTO expenses (title, amount, year, month, date) VALUES ('Test', 100, 2023, 10, 1)")
            close()
        }

        // Run migration and validate
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        // Query to validate data is still there
        val cursor = db.query("SELECT * FROM expenses WHERE title = 'Test'")
        assert(cursor.moveToFirst())
        assert(cursor.getInt(cursor.getColumnIndex("amount")) == 100)
    }
    */
}
