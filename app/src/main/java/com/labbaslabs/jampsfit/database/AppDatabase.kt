package com.labbaslabs.jampsfit.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [HealthEntry::class, UnknownPacket::class, SeenNotification::class], version = 6, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jampsfit_database"
                )
                    .addMigrations(MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
