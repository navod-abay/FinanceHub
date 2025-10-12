package com.example.financehub.util

import com.example.financehub.viewmodel.DatePreset
import java.util.Calendar
import java.util.Date



fun getDateComponents(date: Date): Triple<Int, Int, Int> {
    val calendar = Calendar.getInstance().apply { time = date }
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.get(Calendar.MONTH) + 1// Months are one-based
    val year = calendar.get(Calendar.YEAR)
    return Triple(day, month, year)
}

fun getCurrentDate(): Triple<Int, Int, Int>{
    val date = Date()
    return getDateComponents(date)
}

fun getPresetDateRange(preset: DatePreset, baseCalendar: Calendar = Calendar.getInstance()): Pair<Triple<Int,Int,Int>, Triple<Int,Int,Int>> {
    fun Calendar.toTriple() = Triple(get(Calendar.DAY_OF_MONTH), get(Calendar.MONTH)+1, get(Calendar.YEAR))
    return when(preset){
        DatePreset.TODAY -> {
            val start = (baseCalendar.clone() as Calendar)
            val end = (baseCalendar.clone() as Calendar)
            start.toTriple() to end.toTriple()
        }
        DatePreset.YESTERDAY -> {
            val start = (baseCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            val end = (start.clone() as Calendar)
            start.toTriple() to end.toTriple()
        }
        DatePreset.THIS_WEEK -> {
            val start = (baseCalendar.clone() as Calendar)
            val firstDow = start.firstDayOfWeek
            while(start.get(Calendar.DAY_OF_WEEK) != firstDow){ start.add(Calendar.DAY_OF_YEAR, -1) }
            val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
            start.toTriple() to end.toTriple()
        }
        DatePreset.THIS_MONTH -> {
            val start = (baseCalendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val end = (start.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) }
            start.toTriple() to end.toTriple()
        }
    }
}

//fun normalizeRange(a: Triple<Int,Int,Int>?, b: Triple<Int,Int,Int>?): Pair<Triple<Int,Int,Int>?, Triple<Int,Int,Int>?> {
//    if(a==null || b==null) return a to b
//    val aKey = listOf(a.third, a.second, a.first) // y,m,d
//    val bKey = listOf(b.third, b.second, b.first)
//    return if(aKey <= bKey) a to b else b to a
//}
