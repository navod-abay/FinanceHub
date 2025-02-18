package com.example.financehub.data.repository

import com.example.financehub.data.dao.ExpenseDao
import com.example.financehub.data.database.Expense

class ExpenseRepository(private val expenseDao: ExpenseDao) {
    suspend fun insertExpense(expense: Expense) {
        expenseDao.insertExpense(expense)
    }

    suspend fun getAllExpenses(): List<Expense> {
        return expenseDao.getAllExpenses()
    }
}
