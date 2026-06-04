package com.beta.auraos.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DashboardEntity::class,
        ExpiringMembershipEntity::class,
        VipArrivalEntity::class,
        BranchEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dashboardDao(): DashboardDao
    abstract fun branchDao(): BranchDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `branches` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `address` TEXT,
                        `motorcycleCapacity` INTEGER NOT NULL DEFAULT 0,
                        `lightVehicleCapacity` INTEGER NOT NULL DEFAULT 0,
                        `heavyVehicleCapacity` INTEGER NOT NULL DEFAULT 0,
                        `motorcycleRate` REAL NOT NULL DEFAULT 0.0,
                        `lightVehicleRate` REAL NOT NULL DEFAULT 0.0,
                        `heavyVehicleRate` REAL NOT NULL DEFAULT 0.0,
                        `motorcycleOvernightRate` REAL NOT NULL DEFAULT 0.0,
                        `lightVehicleOvernightRate` REAL NOT NULL DEFAULT 0.0,
                        `heavyVehicleOvernightRate` REAL NOT NULL DEFAULT 0.0,
                        `openTimeWeekday` TEXT NOT NULL DEFAULT '07:00',
                        `closeTimeWeekday` TEXT NOT NULL DEFAULT '23:00',
                        `openTimeWeekend` TEXT NOT NULL DEFAULT '09:00',
                        `closeTimeWeekend` TEXT NOT NULL DEFAULT '22:00',
                        `currency` TEXT NOT NULL DEFAULT 'USD',
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "auraos_dashboard.db"
                ).addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
