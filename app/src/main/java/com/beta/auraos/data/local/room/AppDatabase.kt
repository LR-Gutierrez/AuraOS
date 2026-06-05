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
        BranchEntity::class,
        MembershipEntity::class,
        VehicleEntryEntity::class,
        PendingNfcEntryEntity::class,
        PendingExitEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dashboardDao(): DashboardDao
    abstract fun branchDao(): BranchDao
    abstract fun membershipDao(): MembershipDao
    abstract fun vehicleEntryDao(): VehicleEntryDao
    abstract fun pendingNfcEntryDao(): PendingNfcEntryDao
    abstract fun pendingExitDao(): PendingExitDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memberships` (
                        `id` TEXT NOT NULL,
                        `memberName` TEXT NOT NULL,
                        `tier` TEXT NOT NULL,
                        `startDate` TEXT NOT NULL,
                        `endDate` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `branchId` TEXT NOT NULL,
                        `cardUuid` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_memberships_branchId` ON `memberships` (`branchId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_memberships_cardUuid` ON `memberships` (`cardUuid`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vehicle_entries` (
                        `id` TEXT NOT NULL,
                        `plate` TEXT NOT NULL,
                        `vehicleType` TEXT NOT NULL,
                        `isVip` INTEGER NOT NULL DEFAULT 0,
                        `membershipId` TEXT,
                        `branchId` TEXT NOT NULL,
                        `platePhotoUrl` TEXT,
                        `frontPhotoUrl` TEXT,
                        `rearPhotoUrl` TEXT,
                        `leftPhotoUrl` TEXT,
                        `rightPhotoUrl` TEXT,
                        `createdAt` TEXT NOT NULL,
                        `exitedAt` TEXT,
                        `duration` TEXT,
                        `fee` REAL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vehicle_entries_branchId` ON `vehicle_entries` (`branchId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vehicle_entries_plate` ON `vehicle_entries` (`plate`)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_nfc_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `cardUuid` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `memberName` TEXT,
                        `membershipId` TEXT,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        `retryCount` INTEGER NOT NULL DEFAULT 0,
                        `lastError` TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `pending_nfc_entries` ADD COLUMN `localEntryId` TEXT"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_exits` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `entryId` TEXT NOT NULL,
                        `exitTime` TEXT NOT NULL,
                        `duration` TEXT,
                        `fee` REAL NOT NULL DEFAULT 0.0,
                        `branchId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        `retryCount` INTEGER NOT NULL DEFAULT 0,
                        `lastError` TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `branches` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `memberships` ADD COLUMN `createdAt` TEXT")
                db.execSQL("ALTER TABLE `memberships` ADD COLUMN `updatedAt` TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "auraos_dashboard.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
