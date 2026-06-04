package com.beta.auraos.data.audit

import com.beta.auraos.data.model.Branch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

object FeeCalculator {

    fun computeFee(createdAt: String, vehicleType: String, branch: Branch?): Double? {
        if (branch == null) return null
        val days = elapsedDays(createdAt)
        val nights = max(0, days - 1)
        val rate = when (vehicleType) {
            "motorcycle" -> branch.motorcycleRate
            "light" -> branch.lightVehicleRate
            "heavy" -> branch.heavyVehicleRate
            else -> return null
        }
        val overnightRate = when (vehicleType) {
            "motorcycle" -> branch.motorcycleOvernightRate
            "light" -> branch.lightVehicleOvernightRate
            "heavy" -> branch.heavyVehicleOvernightRate
            else -> return null
        }
        return (days * rate) + (nights * overnightRate)
    }

    fun elapsedDays(createdAt: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val created = sdf.parse(createdAt.substringBefore(".").substringBefore("Z")) ?: return 1
            val diffMs = Date().time - created.time
            val days = diffMs / (1000 * 60 * 60 * 24)
            max(1, days.toInt() + 1)
        } catch (e: Exception) {
            1
        }
    }

    fun computeDuration(createdAt: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val created = sdf.parse(createdAt.substringBefore(".").substringBefore("Z")) ?: return "--"
            val diffMs = Date().time - created.time
            val hours = diffMs / (1000 * 60 * 60)
            val minutes = (diffMs % (1000 * 60 * 60)) / (1000 * 60)
            "${hours}h ${minutes}m"
        } catch (e: Exception) {
            "--"
        }
    }
}
