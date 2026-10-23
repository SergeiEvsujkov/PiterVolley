package ru.example.pitervolley.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

@Serializable
data class RemindersBackup(
    val allGames: Boolean = false,
    val favoriteTeams: Boolean = true,
    val favoritePlayers: Boolean = true,
    val divisionsOn: Boolean = false,
    val divisionIds: List<Int> = emptyList(),
)

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: String = "",
    val favoriteTeams: List<String> = emptyList(),
    val favoritePersons: List<String> = emptyList(),
    val splitPlayerIds: List<String> = emptyList(),
    val merges: Map<String, String> = emptyMap(),
    val reminders: RemindersBackup = RemindersBackup(),
)

/** Выгрузка локальных настроек (избранное, ручные правки, напоминания) в JSON. */
object Backup {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun build(): String {
        val reminders = ReminderSettings.state.value
        val data = BackupData(
            exportedAt = Instant.now().toString(),
            favoriteTeams = Favorites.teams.value.sorted(),
            favoritePersons = Favorites.personKeys().sorted(),
            splitPlayerIds = PersonOverrides.splitIds().sorted(),
            merges = PersonOverrides.merges(),
            reminders = RemindersBackup(
                allGames = reminders.allGames,
                favoriteTeams = reminders.favoriteTeams,
                favoritePlayers = reminders.favoritePlayers,
                divisionsOn = reminders.divisionsOn,
                divisionIds = reminders.divisionIds.sorted(),
            ),
        )
        return json.encodeToString(BackupData.serializer(), data)
    }

    fun suggestedFileName(): String = "pitervolley-backup-${LocalDate.now()}.json"
}
