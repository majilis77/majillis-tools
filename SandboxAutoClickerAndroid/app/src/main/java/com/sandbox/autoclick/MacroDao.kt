package com.sandbox.autoclick

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros ORDER BY name")
    suspend fun getAll(): List<MacroEntity>

    @Query("SELECT * FROM macros WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MacroEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(macro: MacroEntity): Long

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun deleteById(id: Long)
}
