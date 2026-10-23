package com.sandbox.autoclick

class RuleInterpreter {
    private val pattern = Regex(
        "SI\\s+pixel\\((\\d+),\\s*(\\d+)\\)\\s*==\\s*(#[A-Fa-f0-9]{6})\\s+ENTONCES\\s+click\\((\\d+),\\s*(\\d+)\\)(?:\\s+ESPERAR\\s+(\\d+))?",
        RegexOption.IGNORE_CASE
    )

    fun parseLine(line: String): CommandRule? {
        val m = pattern.matchEntire(line.trim()) ?: return null
        return try {
            CommandRule(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(),
                m.groupValues[3].uppercase(), m.groupValues[4].toFloat(),
                m.groupValues[5].toFloat(),
                m.groupValues[6].takeIf { it.isNotBlank() }?.toLong() ?: 500L
            )
        } catch (_: NumberFormatException) { null }
    }

    fun parseAll(text: String): List<CommandRule> {
        val lines = text.lines().filter { it.isNotBlank() }
        require(lines.isNotEmpty()) { "Escribe al menos una regla." }
        return lines.mapIndexed { index, line ->
            parseLine(line) ?: throw IllegalArgumentException("Regla no válida en línea ${index + 1}.")
        }
    }
}
