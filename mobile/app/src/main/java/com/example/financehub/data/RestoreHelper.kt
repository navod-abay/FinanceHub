package com.example.financehub.data


import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.financehub.data.database.AppDatabase
import com.example.financehub.data.database.Expense
import com.example.financehub.data.database.Tags
import com.example.financehub.data.database.ExpenseTagsCrossRef
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object RestoreHelper {

    fun restoreFromBackup(
        context: Context,
        uri: Uri
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val expenseDao = db.expenseDao()
            val tagsDao = db.tagsDao()
            val expenseTagsCrossRefDao = db.expenseTagsCrossRefDao()

            // Open the backup database using SAF URI
            val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd == null) {
                println("❌ Could not open backup file.")
                return@launch
            }

            // Copy the backup file to a temporary file in cacheDir
            val tempBackupFile = File(context.cacheDir, "temp_backup.db")
            try {
                FileInputStream(pfd.fileDescriptor).use { inputStream ->
                    FileOutputStream(tempBackupFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: IOException) {
                println("❌ Failed to copy backup file: ${e.message}")
                pfd.close()
                return@launch
            }
            pfd.close()

            val oldDb = SQLiteDatabase.openDatabase(tempBackupFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

            // Gather expenses
            val expenseList = mutableListOf<Expense>()
            val expenseCursor = oldDb.rawQuery("SELECT * FROM expenses", null)
            while (expenseCursor.moveToNext()) {
                val expenseID = expenseCursor.getInt(0)
                val title = expenseCursor.getString(1)
                val amount = expenseCursor.getString(2)
                val year = expenseCursor.getString(3)
                val month = expenseCursor.getString(4)
                val date = expenseCursor.getString(5)
                expenseList.add(Expense(
                    expenseID = expenseID,
                    title = title,
                    amount = amount.toInt(),
                    year = year.toInt(),
                    month = month.toInt(),
                    date = date.toInt()
                ))
            }
            expenseCursor.close()

            // Gather tags
            val tagsList = mutableListOf<Tags>()
            val tagCursor = oldDb.rawQuery("SELECT * FROM tags", null)
            while (tagCursor.moveToNext()) {
                val tagID = tagCursor.getInt(0)
                val tag = tagCursor.getString(1)
                val monthlyAmount = tagCursor.getString(2)
                val currentMonth = tagCursor.getString(3)
                val currentYear = tagCursor.getString(4)
                val createdDay = tagCursor.getString(5)
                val createYear = tagCursor.getString(6)
                tagsList.add(Tags(
                    tagID = tagID,
                    tag = tag,
                    monthlyAmount = monthlyAmount.toInt(),
                    currentMonth = currentMonth.toInt(),
                    currentYear = currentYear.toInt(),
                    createdDay = createdDay.toInt(),
                    createdMonth = createYear.toInt(),
                    createdYear = createYear.toInt()
                ))
            }
            tagCursor.close()

            // Gather crossrefren
            val crossRefList = mutableListOf<ExpenseTagsCrossRef>()
            val crossRefCursor = oldDb.rawQuery("SELECT * FROM expense_tags", null)
            while (crossRefCursor.moveToNext()) {
                val expenseID = crossRefCursor.getInt(0)
                val tagID = crossRefCursor.getInt(1)
                crossRefList.add(ExpenseTagsCrossRef( expenseID = expenseID, tagID = tagID))
            }
            crossRefCursor.close()

            oldDb.close()
            tempBackupFile.delete()

            // Insert all in a transaction
            db.runInTransaction {
                CoroutineScope(Dispatchers.IO).launch {
                    expenseDao.insertAll(expenseList)
                    tagsDao.insertAll(tagsList)
                    expenseTagsCrossRefDao.insertAll(crossRefList)
                }
            }

            println("✅ Restore complete!")
        }
    }
}
