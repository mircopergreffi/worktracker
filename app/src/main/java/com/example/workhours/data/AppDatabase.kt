package com.example.workhours.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Entry::class, Client::class, Project::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 -> v2: adds clients, projects and the two id columns on entries (old rows keep NULL). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `clients` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `client_id` INTEGER NOT NULL, `name` TEXT NOT NULL)")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `client_id` INTEGER")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `project_id` INTEGER")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "workhours.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
