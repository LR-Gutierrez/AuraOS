package com.beta.auraos.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val isoDateTimeParser by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

private val isoDateParser by lazy {
    SimpleDateFormat("yyyy-MM-dd", Locale.US)
}

private val dateTimeFormatter by lazy {
    SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US)
}

private val dateFormatter by lazy {
    SimpleDateFormat("dd/MM/yyyy", Locale.US)
}

fun formatDateTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    val clean = isoString.substringBefore(".").substringBefore("Z")
    return try {
        val date = isoDateTimeParser.parse(clean)
        date?.let { dateTimeFormatter.format(it) } ?: clean
    } catch (_: Exception) {
        clean
    }
}

fun formatDate(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    return try {
        val date = isoDateParser.parse(isoString)
        date?.let { dateFormatter.format(it) } ?: isoString
    } catch (_: Exception) {
        isoString
    }
}
