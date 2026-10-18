package com.beta.auraos.util

import android.content.Context
import com.beta.auraos.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Formatting shared by the redesigned screens, aligned with BCV Tracker: 12-hour times ("4:12 p. m."),
 * stay durations ("2 h 14 min"), money ("$4.50") and zero-padded counts ("08").
 */
object ParkingFormat {

    private fun utcParser() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun parseIso(iso: String?): Date? {
        if (iso.isNullOrBlank()) return null
        return try {
            utcParser().parse(iso.substringBefore(".").substringBefore("Z"))
        } catch (_: Exception) {
            null
        }
    }

    /** 12-hour clock as in BCV Tracker's ftime(): "4:12 p. m." in Spanish, "4:12 PM" in English. */
    fun hhmm(date: Date = Date()): String {
        val locale = Locale.getDefault()
        if (locale.language != "es") return SimpleDateFormat("h:mm a", locale).format(date)
        val hour = Calendar.getInstance().apply { time = date }.get(Calendar.HOUR_OF_DAY)
        return SimpleDateFormat("h:mm", locale).format(date) + if (hour < 12) " a. m." else " p. m."
    }

    /** "9 oct, 4:00 p. m." / "Oct 9, 4:00 PM" (BCV Tracker fdatetime()). */
    fun dateTime(date: Date): String = dateLabel(date, dayMonthPattern()) + ", " + hhmm(date)

    /** "9 oct" in Spanish, "Oct 9" in English. */
    fun dayMonthPattern(): String = if (Locale.getDefault().language == "es") "d MMM" else "MMM d"

    /** Hour-only axis label: "6 a. m." / "6 AM". */
    fun hourLabel(date: Date): String {
        val locale = Locale.getDefault()
        if (locale.language != "es") return SimpleDateFormat("h a", locale).format(date)
        val hour = Calendar.getInstance().apply { time = date }.get(Calendar.HOUR_OF_DAY)
        return SimpleDateFormat("h", locale).format(date) + if (hour < 12) " a. m." else " p. m."
    }

    fun hhmm(iso: String?): String = parseIso(iso)?.let { hhmm(it) } ?: "--:--"

    fun duration(fromIso: String?, to: Date = Date()): String {
        val from = parseIso(fromIso) ?: return "--"
        return durationMinutes(((to.time - from.time) / 60_000).coerceAtLeast(0))
    }

    fun durationMinutes(totalMinutes: Long): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "$h h $m min" else "$m min"
    }

    /** Locale-aware number with fixed decimals: "1.234,56" in Spanish, "1,234.56" in English. */
    fun number(n: Double, decimals: Int = 2): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(n)

    /** Signed change with a true minus, as BCV Tracker's pct(): "+1,23%" / "−0,50%". */
    fun percentChange(percent: Double): String =
        (if (percent >= 0) "+" else "−") + number(abs(percent)) + "%"

    fun money(amount: Double?, currency: String? = "USD"): String {
        if (amount == null) return "--"
        val value = number(amount)
        return when (currency?.uppercase()) {
            null, "", "USD" -> "$$value"
            else -> "${currency.uppercase()} $value"
        }
    }

    fun pad2(n: Int): String = n.toString().padStart(2, '0')

    fun vehicleType(context: Context, type: String): String = when (type) {
        "motorcycle" -> context.getString(R.string.vehicle_type_moto)
        "heavy" -> context.getString(R.string.vehicle_type_camion)
        "light" -> context.getString(R.string.vehicle_type_auto)
        else -> type.replaceFirstChar { it.uppercase() }
    }

    fun todayLabel(): String = dateLabel(Date(), "EEE d MMM")

    /**
     * Date in the device language. Java's Spanish short months and weekdays carry a trailing
     * dot ("oct."); the design system shows them bare, as BCV Tracker does.
     */
    fun dateLabel(date: Date, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(date).replace(".", "")

    /** Local-day bounds as UTC ISO strings, comparable with stored createdAt / exitedAt. */
    fun todayUtcRange(): Pair<String, String> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val from = utcParser().format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return from to utcParser().format(cal.time)
    }
}
