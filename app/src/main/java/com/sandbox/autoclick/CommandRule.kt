package com.sandbox.autoclick

data class CommandRule(
    val testX: Int,
    val testY: Int,
    val colorHex: String,
    val actionX: Float,
    val actionY: Float,
    val waitMs: Long
)
