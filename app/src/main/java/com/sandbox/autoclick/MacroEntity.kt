package com.sandbox.autoclick

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rulesText: String,
    val scheduledHour: Int = -1,
    val scheduledMinute: Int = -1
)
