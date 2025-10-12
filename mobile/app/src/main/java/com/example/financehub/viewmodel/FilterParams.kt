package com.example.financehub.viewmodel

import java.util.Calendar

enum class DateMode { NONE, SINGLE_DAY, RANGE, PRESET }

enum class DatePreset { TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH }

data class FilterParams(
    val dateMode: DateMode = DateMode.NONE,
    val singleDay: Triple<Int, Int, Int>? = null, // day, month, year
    val rangeStart: Triple<Int, Int, Int>? = null,
    val rangeEnd: Triple<Int, Int, Int>? = null,
    val preset: DatePreset? = null,
    val requiredTagIds: Set<Int> = emptySet(),
    val removedTagIds: Set<Int> = emptySet(), // future use
    val version: Long = 0L
) {
    data class QueryDateRange(
        val startYear: Int?, val startMonth: Int?, val startDay: Int?,
        val endYear: Int?, val endMonth: Int?, val endDay: Int?
    )

    fun resolveDateRange(nowProvider: () -> Calendar = { Calendar.getInstance() }): QueryDateRange {
        return when (dateMode) {
            DateMode.NONE -> QueryDateRange(null, null, null, null, null, null)
            DateMode.SINGLE_DAY -> singleDay?.let { (d, m, y) ->
                QueryDateRange(y, m, d, y, m, d)
            } ?: QueryDateRange(null, null, null, null, null, null)
            DateMode.RANGE -> {
                val start = rangeStart
                val end = rangeEnd
                if (start != null && end != null) {
                    val startBeforeOrEqual = start.third < end.third ||
                            (start.third == end.third && (start.second < end.second ||
                                    (start.second == end.second && start.first <= end.first)))
                    val (s, e) = if (startBeforeOrEqual) start to end else end to start
                    QueryDateRange(s.third, s.second, s.first, e.third, e.second, e.first)
                } else QueryDateRange(null, null, null, null, null, null)
            }
            DateMode.PRESET -> preset?.let { p ->
                val (start, end) = computePresetRange(p, nowProvider())
                QueryDateRange(start.third, start.second, start.first, end.third, end.second, end.first)
            } ?: QueryDateRange(null, null, null, null, null, null)
        }
    }

    companion object {
        private fun computePresetRange(preset: DatePreset, cal: Calendar): Pair<Triple<Int,Int,Int>, Triple<Int,Int,Int>> {
            fun toTriple(c: Calendar) = Triple(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH)+1, c.get(Calendar.YEAR))
            return when(preset){
                DatePreset.TODAY -> {
                    val start = cal.clone() as Calendar
                    val end = cal.clone() as Calendar
                    toTriple(start) to toTriple(end)
                }
                DatePreset.YESTERDAY -> {
                    val start = cal.clone() as Calendar
                    start.add(Calendar.DAY_OF_YEAR, -1)
                    val end = start.clone() as Calendar
                    toTriple(start) to toTriple(end)
                }
                DatePreset.THIS_WEEK -> {
                    val start = cal.clone() as Calendar
                    val firstDow = start.firstDayOfWeek
                    while(start.get(Calendar.DAY_OF_WEEK) != firstDow){ start.add(Calendar.DAY_OF_YEAR, -1) }
                    val end = start.clone() as Calendar
                    end.add(Calendar.DAY_OF_YEAR, 6)
                    toTriple(start) to toTriple(end)
                }
                DatePreset.THIS_MONTH -> {
                    val start = cal.clone() as Calendar
                    start.set(Calendar.DAY_OF_MONTH, 1)
                    val end = start.clone() as Calendar
                    end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
                    toTriple(start) to toTriple(end)
                }
            }
        }
    }
}
