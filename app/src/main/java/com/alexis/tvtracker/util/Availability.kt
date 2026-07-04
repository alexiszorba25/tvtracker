package com.alexis.tvtracker.util

import java.time.LocalDate

fun hasAired(date: String?): Boolean {
    if (date.isNullOrBlank()) return false
    return runCatching {
        !LocalDate.parse(date).isAfter(LocalDate.now())
    }.getOrDefault(false)
}

fun isReleased(date: String?): Boolean = hasAired(date)
