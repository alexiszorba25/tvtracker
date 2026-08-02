package com.alexis.tvtracker.util

import java.time.LocalDate

fun hasAired(date: String?): Boolean {
    if (date.isNullOrBlank()) return false
    return runCatching {
        !LocalDate.parse(date).isAfter(LocalDate.now())
    }.getOrDefault(false)
}

fun airedWithinLastDays(date: String?, days: Long = 7): Boolean {
    if (date.isNullOrBlank()) return false
    return runCatching {
        val airedAt = LocalDate.parse(date)
        val today = LocalDate.now()
        !airedAt.isAfter(today) && !airedAt.isBefore(today.minusDays(days))
    }.getOrDefault(false)
}

fun isReleased(date: String?): Boolean = hasAired(date)
