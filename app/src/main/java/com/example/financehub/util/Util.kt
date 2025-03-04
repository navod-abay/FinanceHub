package com.example.financehub.util

import java.util.Calendar
import java.util.Date



fun getDateComponents(date: Date): Triple<Int, Int, Int> {
    val calendar = Calendar.getInstance().apply { time = date }
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.get(Calendar.MONTH) // Months are zero-based
    val year = calendar.get(Calendar.YEAR)
    return Triple(day, month, year)
}

fun getCurrentDate(): Triple<Int, Int, Int>{
    val date = Date()
    return getDateComponents(date)
}
