package com.beta.auraos.data.local

import android.content.Context
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.local.room.BranchEntity
import com.beta.auraos.data.local.room.MembershipEntity
import com.beta.auraos.data.local.room.VehicleEntryEntity
import com.beta.auraos.data.model.LoggedInUser
import com.beta.auraos.util.PasswordHasher
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class SeedDataInitializer(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val database: AppDatabase
) {
    private val prefs = context.getSharedPreferences("seed_prefs", Context.MODE_PRIVATE)

    suspend fun seedIfNeeded() {
        if (prefs.getBoolean(SEEDED_KEY, false)) return
        seedUser()
        seedBranch()
        seedVehicleEntries()
        seedMemberships()
        prefs.edit().putBoolean(SEEDED_KEY, true).apply()
    }

    private fun seedUser() {
        val user = LoggedInUser(
            userId = SEED_USER_ID,
            displayName = "Administrador",
            email = "admin@smartpark.com",
            avatarUrl = null
        )
        sessionManager.saveLocalUserProfile(user)
        sessionManager.saveLocalPasswordHash(PasswordHasher.hash("admin123"))
        sessionManager.saveCredentials("admin@smartpark.com", "admin123")
    }

    private suspend fun seedBranch() {
        val now = nowUtc()
        val branch = BranchEntity(
            id = SEED_BRANCH_ID,
            name = "SmartPark Centro",
            address = "Av. Principal 123, Centro",
            motorcycleCapacity = 20,
            lightVehicleCapacity = 50,
            heavyVehicleCapacity = 20,
            motorcycleRate = 3.0,
            lightVehicleRate = 5.0,
            heavyVehicleRate = 7.0,
            motorcycleOvernightRate = 5.0,
            lightVehicleOvernightRate = 10.0,
            heavyVehicleOvernightRate = 15.0,
            openTimeWeekday = "07:00",
            closeTimeWeekday = "23:00",
            openTimeWeekend = "09:00",
            closeTimeWeekend = "22:00",
            currency = "USD",
            createdAt = now,
            updatedAt = now
        )
        database.branchDao().insert(branch)
        sessionManager.saveSelectedBranchId(SEED_BRANCH_ID)
    }

    private suspend fun seedVehicleEntries() {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun calcOffset(hoursBack: Int, minutesBack: Int = 0): String {
            val cal = now.clone() as Calendar
            cal.add(Calendar.HOUR_OF_DAY, -hoursBack)
            cal.add(Calendar.MINUTE, -minutesBack)
            return fmt.format(cal.time)
        }

        val entries = listOf(
            VehicleEntryEntity(
                id = UUID.randomUUID().toString(),
                plate = "ABC-1234",
                vehicleType = "light",
                isVip = false,
                branchId = SEED_BRANCH_ID,
                createdAt = calcOffset(2),
                lastModifiedAt = System.currentTimeMillis()
            ),
            VehicleEntryEntity(
                id = UUID.randomUUID().toString(),
                plate = "XYZ-7890",
                vehicleType = "heavy",
                isVip = false,
                branchId = SEED_BRANCH_ID,
                createdAt = calcOffset(0, 30),
                lastModifiedAt = System.currentTimeMillis()
            ),
            VehicleEntryEntity(
                id = UUID.randomUUID().toString(),
                plate = "VIP-001",
                vehicleType = "light",
                isVip = true,
                branchId = SEED_BRANCH_ID,
                createdAt = calcOffset(1),
                lastModifiedAt = System.currentTimeMillis()
            )
        )
        database.vehicleEntryDao().insertAll(entries)
    }

    private suspend fun seedMemberships() {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val cal = now.clone() as Calendar
        cal.add(Calendar.MONTH, -1)
        val juanStart = dateFmt.format(cal.time)
        val juanEnd = dateFmt.format(Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.MONTH, 1)
        }.time)

        val mariaStart = dateFmt.format(now.time)
        val mariaEnd = dateFmt.format(Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.MONTH, 1)
        }.time)

        val memberships = listOf(
            MembershipEntity(
                id = "seed-mem-1",
                memberName = "Juan Pérez",
                tier = "Elite",
                startDate = juanStart,
                endDate = juanEnd,
                isActive = true,
                branchId = SEED_BRANCH_ID,
                lastModifiedAt = System.currentTimeMillis()
            ),
            MembershipEntity(
                id = "seed-mem-2",
                memberName = "María García",
                tier = "Premium",
                startDate = mariaStart,
                endDate = mariaEnd,
                isActive = true,
                branchId = SEED_BRANCH_ID,
                lastModifiedAt = System.currentTimeMillis()
            ),
            MembershipEntity(
                id = "seed-mem-3",
                memberName = "Carlos López",
                tier = "PREMIUM",
                startDate = "2026-05-01",
                endDate = "2026-05-15",
                isActive = false,
                branchId = SEED_BRANCH_ID,
                lastModifiedAt = System.currentTimeMillis()
            )
        )
        database.membershipDao().insertAll(memberships)
    }

    private fun nowUtc(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    companion object {
        private const val SEEDED_KEY = "is_seeded"
        private const val SEED_USER_ID = "seed-admin"
        private const val SEED_BRANCH_ID = "seed-branch-1"
    }
}
