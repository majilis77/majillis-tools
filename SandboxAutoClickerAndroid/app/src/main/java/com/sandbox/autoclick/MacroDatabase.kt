package com.sandbox.autoclick

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MacroEntity::class], version = 1, exportSchema = false)
abstract class MacroDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao
    companion object {
        @Volatile private var INSTANCE: MacroDatabase? = null
        fun get(context: Context): MacroDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, MacroDatabase::class.java, "sandbox-macros.db").build().also { INSTANCE = it }
        }
    }
}
