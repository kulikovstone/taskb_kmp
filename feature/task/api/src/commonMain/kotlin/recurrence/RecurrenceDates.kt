package com.mkulikov.taskb.feature.task.api.recurrence

import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Generates only the requested window; a series is never materialized as thousands of task rows. */
fun recurrenceDates(
    rule: TaskRecurrence,
    start: LocalDate,
    from: LocalDate,
    through: LocalDate,
    limit: Int = Int.MAX_VALUE,
): List<LocalDate> {
    val end = minOf(a = through, b = rule.endDate ?: through)
    if (end < start || end < from || limit <= 0) return emptyList()
    val lower = maxOf(a = start, b = from)
    val output = mutableListOf<LocalDate>()
    val count = rule.occurrenceCount?.toLong() ?: Long.MAX_VALUE
    when (rule.frequency) {
        RecurrenceFrequency.Daily -> {
            var index = ((lower.toEpochDays() - start.toEpochDays()) / rule.interval).coerceAtLeast(minimumValue = 0)
            while (index < count) {
                val day = start.plus(value = index * rule.interval, unit = DateTimeUnit.DAY)
                if (day > end) break
                if (day >= lower) output += day
                if (output.size >= limit) return output
                index++
            }
        }
        RecurrenceFrequency.Weekly -> {
            val anchor = start.minus(value = start.dayOfWeek.isoDayNumber - 1, unit = DateTimeUnit.DAY)
            val weekdays = rule.weekdays.sortedBy(selector = DayOfWeek::isoDayNumber)
            val firstWeekCount = weekdays.count { weekday ->
                anchor.plus(value = weekday.isoDayNumber - 1, unit = DateTimeUnit.DAY) >= start
            }
            val week = ((lower.toEpochDays() - anchor.toEpochDays()) / 7).coerceAtLeast(minimumValue = 0)
            var cycle = week / rule.interval
            while (true) {
                val monday = anchor.plus(value = cycle * rule.interval * 7L, unit = DateTimeUnit.DAY)
                if (monday > end) break
                var index = if (cycle == 0L) 0L else firstWeekCount + (cycle - 1) * weekdays.size
                for (weekday in weekdays) {
                    val day = monday.plus(value = weekday.isoDayNumber - 1, unit = DateTimeUnit.DAY)
                    if (day < start) continue
                    if (index++ >= count) return output
                    if (day in lower..end) output += day
                    if (output.size >= limit) return output
                }
                cycle++
            }
        }
        RecurrenceFrequency.Monthly,
        RecurrenceFrequency.Yearly -> {
            val step = rule.interval.toLong() * if (rule.frequency == RecurrenceFrequency.Yearly) 12 else 1
            val anchor = start.year.toLong() * 12 +
                (if (rule.frequency == RecurrenceFrequency.Yearly) rule.month else start.month.ordinal + 1) - 1
            fun date(cycle: Long): LocalDate {
                val month = anchor + cycle * step
                val year = (month / 12).toInt()
                val number = (month % 12 + 1).toInt()
                val day = minOf(a = rule.dayOfMonth, b = YearMonth(year = year, month = number).numberOfDays)
                return LocalDate(year = year, month = number, day = day)
            }
            val firstCycle = if (date(cycle = 0) < start) 1L else 0L
            val lowerMonth = lower.year.toLong() * 12 + lower.month.ordinal
            var cycle = maxOf(a = firstCycle, b = (lowerMonth - anchor) / step)
            while (cycle - firstCycle < count) {
                if (anchor + cycle * step > end.year.toLong() * 12 + end.month.ordinal) break
                val day = date(cycle = cycle)
                if (day > end) break
                if (day >= lower) output += day
                if (output.size >= limit) return output
                cycle++
            }
        }
    }
    return output
}

/** Zero-based ordinal in the complete rule, independent of the displayed window or exceptions. */
fun recurrenceOrdinal(rule: TaskRecurrence, start: LocalDate, date: LocalDate): Long? {
    if (recurrenceDates(rule = rule, start = start, from = date, through = date).isEmpty()) return null
    return when (rule.frequency) {
        RecurrenceFrequency.Daily -> (date.toEpochDays() - start.toEpochDays()) / rule.interval
        RecurrenceFrequency.Weekly -> {
            val anchor = start.minus(value = start.dayOfWeek.isoDayNumber - 1, unit = DateTimeUnit.DAY)
            val cycle = (date.toEpochDays() - anchor.toEpochDays()) / (7L * rule.interval)
            val first =
                rule.weekdays.count { weekday -> anchor.plus(
                    value = weekday.isoDayNumber - 1,
                    unit = DateTimeUnit.DAY,
                ) >= start }
            if (cycle == 0L) {
                rule.weekdays
                    .count { weekday -> anchor.plus(
                        value = weekday.isoDayNumber - 1,
                        unit = DateTimeUnit.DAY,
                    ) in start..<date }
                    .toLong()
            } else {
                first +
                    (cycle - 1) * rule.weekdays.size +
                    rule.weekdays.count { weekday -> weekday.isoDayNumber < date.dayOfWeek.isoDayNumber }
            }
        }
        RecurrenceFrequency.Monthly,
        RecurrenceFrequency.Yearly -> {
            val month = if (rule.frequency == RecurrenceFrequency.Yearly) rule.month else start.month.ordinal + 1
            val anchor = start.year.toLong() * 12 + month - 1
            val firstDate = LocalDate(
                year = start.year,
                month = month,
                day = minOf(a = rule.dayOfMonth, b = YearMonth(year = start.year, month = month).numberOfDays),
            )
            val step = rule.interval.toLong() * if (rule.frequency == RecurrenceFrequency.Yearly) 12 else 1
            (date.year.toLong() * 12 + date.month.ordinal - anchor) / step - if (firstDate < start) 1 else 0
        }
    }
}

fun nextRecurrenceDates(
    rule: TaskRecurrence,
    start: LocalDate,
    from: LocalDate = start,
    count: Int = 3,
): List<LocalDate> {
    if (count <= 0) return emptyList()
    val days = when (rule.frequency) {
        RecurrenceFrequency.Daily -> 1L
        RecurrenceFrequency.Weekly -> 7L
        RecurrenceFrequency.Monthly -> 31L
        RecurrenceFrequency.Yearly -> 366L
    }
    val until = (maxOf(a = start, b = from).toEpochDays() + (count.toLong() + 1) * rule.interval * days).coerceAtMost(
        maximumValue = LocalDate(year = 999_999, month = 12, day = 31).toEpochDays()
    )
    return recurrenceDates(
        rule = rule,
        start = start,
        from = from,
        through = LocalDate.fromEpochDays(epochDays = until),
        limit = count,
    )
}
