package ru.example.pitervolley.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Подписи про свежесть данных: когда собраны и сколько им времени. */
object TimeText {

    private val STAMP = DateTimeFormatter.ofPattern("dd.MM в HH:mm", Locale.forLanguageTag("ru-RU"))

    /** «Данные на 23.10 в 10:01» */
    fun dataStamp(iso: String?): String? {
        val instant = parse(iso) ?: return null
        return "Данные на ${STAMP.format(instant.atZone(ZoneId.systemDefault()))}"
    }

    /** «1 ч назад» */
    fun ageShort(iso: String?): String? {
        val instant = parse(iso) ?: return null
        return age(instant, withSuffix = true)
    }

    fun dataFreshness(iso: String?): String? {
        val instant = parse(iso) ?: return null
        val local = instant.atZone(ZoneId.systemDefault())
        return "Данные на ${STAMP.format(local)} · ${shortAge(instant)}"
    }

    private fun shortAge(instant: Instant): String {
        val minutes = Duration.between(instant, Instant.now()).toMinutes()
        return when {
            minutes < 1 -> "только что"
            minutes < 60 -> "$minutes мин"
            minutes < 60 * 24 -> "${minutes / 60} ч"
            else -> "${minutes / (60 * 24)} дн"
        }
    }

    fun age(instant: Instant, withSuffix: Boolean = true): String {
        val minutes = Duration.between(instant, Instant.now()).toMinutes()
        val suffix = if (withSuffix) " назад" else ""
        return when {
            minutes < 1 -> "только что"
            minutes < 60 -> "$minutes мин$suffix"
            minutes < 60 * 24 -> "${minutes / 60} ч$suffix"
            else -> "${minutes / (60 * 24)} дн$suffix"
        }
    }

    private fun parse(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return runCatching { Instant.parse(iso) }.getOrNull()
    }
}
