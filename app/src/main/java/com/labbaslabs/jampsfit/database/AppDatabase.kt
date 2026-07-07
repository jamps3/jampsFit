package com.labbaslabs.jampsfit.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HealthEntry::class,
        UnknownPacket::class,
        SeenNotification::class,
        CandyEntity::class,
        MealEntity::class,
        EventEntity::class,
        FestivalEntity::class,
        FoodEntity::class
    ],
    version = 12,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao
    abstract fun eventDao(): EventDao
    abstract fun foodDao(): FoodDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Attempt to create tables that might be missing if they were added in this version
                db.execSQL("CREATE TABLE IF NOT EXISTS `unknown_packets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `message` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `seen_notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `content_hash` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_seen_notifications_content_hash` ON `seen_notifications` (`content_hash`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER,
                        `startSteps` INTEGER,
                        `startActivityCount` INTEGER,
                        `startDistance` INTEGER,
                        `startCalories` INTEGER,
                        `durationSeconds` INTEGER NOT NULL,
                        `stepDelta` INTEGER NOT NULL,
                        `activityDelta` INTEGER NOT NULL,
                        `distanceDelta` INTEGER NOT NULL,
                        `calorieDelta` INTEGER NOT NULL,
                        `heartRateSamples` INTEGER NOT NULL,
                        `averageBpm` INTEGER,
                        `minBpm` INTEGER,
                        `maxBpm` INTEGER,
                        `estimatedWorkoutCalories` INTEGER NOT NULL,
                        `lastUpdatedTime` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_endTime` ON `events` (`endTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_type_startTime` ON `events` (`type`, `startTime`)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `foods` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `unitLabel` TEXT NOT NULL,
                        `kcalPerUnit` INTEGER NOT NULL,
                        `defaultAmount` REAL NOT NULL,
                        `stepSize` REAL NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `availableAmount` REAL,
                        `isCustom` INTEGER NOT NULL,
                        `onShoppingList` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_foods_source` ON `foods` (`source`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_foods_role` ON `foods` (`role`)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `festivals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `imageUri` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE `events` ADD COLUMN `festivalId` INTEGER")
                db.execSQL(
                    """
                    INSERT INTO `festivals` (`name`, `imageUri`, `createdAt`, `updatedAt`)
                    SELECT 'Event', NULL, COALESCE(MIN(`startTime`), strftime('%s','now') * 1000), COALESCE(MAX(`lastUpdatedTime`), strftime('%s','now') * 1000)
                    FROM `events`
                    """.trimIndent()
                )
                db.execSQL("UPDATE `events` SET `festivalId` = (SELECT `id` FROM `festivals` ORDER BY `createdAt` DESC LIMIT 1) WHERE `festivalId` IS NULL")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `candies` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `festivalId` INTEGER,
                        `name` TEXT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER NOT NULL,
                        `size` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_candies_festivalId` ON `candies` (`festivalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_candies_startTime` ON `candies` (`startTime`)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `meals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `festivalId` INTEGER,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `calories` INTEGER NOT NULL,
                        `details` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_festivalId` ON `meals` (`festivalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_createdAt` ON `meals` (`createdAt`)")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `festivals` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE `festivals`
                    SET `isActive` = 1
                    WHERE `id` = (SELECT `id` FROM `festivals` ORDER BY `updatedAt` DESC LIMIT 1)
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jampsfit_database"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
