package ru.example.pitervolley.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class Dataset(
    val schema: Int = 1,
    val generatedAt: String = "",
    val season: String = "",
    val divisions: List<Division> = emptyList(),
    val teams: List<Team> = emptyList(),
    val players: List<Player> = emptyList(),
    val games: List<Game> = emptyList(),
    val slots: List<Slot> = emptyList(),
)

@Serializable
data class Division(
    val id: Int,
    val title: String = "",
    val competition: String = "",
    val gender: String = "",
    val birthYear: Int? = null,
    val teamLabel: Int? = null,
) {
    val genderRu: String
        get() = when (gender) {
            "boys" -> "Юноши"
            "girls" -> "Девушки"
            "men" -> "Мужчины"
            "women" -> "Женщины"
            else -> ""
        }

    /** "Юноши · 2010 г.р." or "Мужчины · 1-я команда" */
    val subtitle: String
        get() = listOfNotNull(
            genderRu.takeIf { it.isNotEmpty() },
            birthYear?.let { "$it г.р." } ?: teamLabel?.let { "$it-я команда" },
        ).joinToString(" · ")
}

@Serializable
data class Team(
    val id: String,
    val name: String = "",
    val divisionId: Int = 0,
    val logo: String? = null,
)

@Serializable
data class Player(
    val id: String,
    val name: String = "",
    val birthYear: Int? = null,
    val photo: String? = null,
    val teamId: String = "",
    val divisionId: Int = 0,
)

@Serializable
data class Game(
    val id: String,
    val divisionId: Int = 0,
    val round: Int? = null,
    var date: String? = null,
    var time: String = "",
    var venue: String = "",
    val homeId: String = "",
    val awayId: String = "",
    val home: String = "",
    val away: String = "",
    val score: String = "",
    val sets: List<List<Int>> = emptyList(),
    var slotNum: Int? = null,
    var dateSource: String? = null,
) {
    val hasResult: Boolean get() = score.isNotBlank()
    val homeSets: Int get() = sets.count { it.size > 1 && it[0] > it[1] }
    val awaySets: Int get() = sets.count { it.size > 1 && it[1] > it[0] }
    val localDate: LocalDate? get() = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** "08.11, сб" */
    val dateShort: String get() = formatDate(date, withYear = false)
    val dateFull: String get() = formatDate(date, withYear = true)
}

@Serializable
data class Slot(
    val num: Int? = null,
    val date: String = "",
    val time: String = "",
    val group: String = "",
    val home: String = "",
    val away: String = "",
    val venue: String = "",
    val games: Int = 1,
    val refs: List<String> = emptyList(),
    val ages: List<Int> = emptyList(),
    val comment: String = "",
    val gameIds: List<String>? = null,
    val divisionId: Int? = null,
)

// ------------------------------------------------------------------ date helpers

private val RU = Locale.forLanguageTag("ru-RU")
private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

fun parseIsoDate(s: String?): LocalDate? =
    if (s.isNullOrBlank()) null else runCatching { LocalDate.parse(s, ISO) }.getOrNull()

fun formatDate(iso: String?, withYear: Boolean): String {
    val d = parseIsoDate(iso) ?: return "дата не назначена"
    val day = d.format(DateTimeFormatter.ofPattern("dd.MM", RU))
    val wd = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, RU)
    return if (withYear) "$day.${d.year}, $wd" else "$day, $wd"
}

fun today(): LocalDate = LocalDate.now()
