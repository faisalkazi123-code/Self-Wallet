package com.selfwallet.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()

    fun toDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun startOf(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun monthRange(ym: YearMonth): LongRange = startOf(ym.atDay(1)) until startOf(ym.plusMonths(1).atDay(1))
    fun yearRange(year: Int): LongRange = startOf(LocalDate.of(year, 1, 1)) until startOf(LocalDate.of(year + 1, 1, 1))

    private val dayHeader = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val dayHeaderYear = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    private val full = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun dayLabel(date: LocalDate): String {
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> if (date.year == today.year) date.format(dayHeader) else date.format(dayHeaderYear)
        }
    }

    fun monthLabel(ym: YearMonth): String = ym.format(monthFmt)
    fun shortMonthLabel(ym: YearMonth): String = ym.format(shortMonth)
    fun full(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(full)
    fun time(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(time)
}
