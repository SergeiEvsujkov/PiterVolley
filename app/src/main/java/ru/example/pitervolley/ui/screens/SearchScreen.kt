package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.ui.ClickableRow
import ru.example.pitervolley.ui.DividerLine
import ru.example.pitervolley.ui.EmptyState
import ru.example.pitervolley.ui.PlayerAvatar
import ru.example.pitervolley.ui.ScreenScaffold
import ru.example.pitervolley.ui.TeamLogo

private const val MIN_QUERY = 2

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenTeam: (String) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onOpenDivision: (Int) -> Unit,
    initialQuery: String = "",
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val q = query.trim().lowercase()

    val teams = remember(q) {
        if (q.length < MIN_QUERY) emptyList()
        else Repo.data.teams.filter { it.name.lowercase().contains(q) }.take(30)
    }
    val players = remember(q) {
        if (q.length < MIN_QUERY) emptyList()
        else Repo.data.players.filter { it.name.lowercase().contains(q) }.take(30)
    }
    val divisions = remember(q) {
        if (q.length < MIN_QUERY) emptyList()
        else Repo.data.divisions.filter { it.title.lowercase().contains(q) }.take(10)
    }

    ScreenScaffold(title = "Поиск", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Команда, игрок или дивизион") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                q.length < MIN_QUERY -> EmptyState("Введите хотя бы $MIN_QUERY символа")
                teams.isEmpty() && players.isEmpty() && divisions.isEmpty() -> EmptyState("Ничего не найдено")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (teams.isNotEmpty()) {
                        item { Header("Команды · ${teams.size}") }
                        items(teams, key = { "t-${it.id}" }) { team ->
                            ClickableRow({ onOpenTeam(team.id) }) {
                                TeamLogo(team, 36)
                                Spacer(Modifier.padding(start = 12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(team.name, style = MaterialTheme.typography.bodyLarge)
                                    Repo.division(team.divisionId)?.let {
                                        Text(
                                            "${it.competition} · ${it.subtitle}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text("›", style = MaterialTheme.typography.titleLarge)
                            }
                            DividerLine()
                        }
                    }
                    if (players.isNotEmpty()) {
                        item { Header("Игроки · ${players.size}") }
                        items(players, key = { "p-${it.id}" }) { player ->
                            ClickableRow({ onOpenPlayer(player.id) }) {
                                PlayerAvatar(player.photo, player.name, 36)
                                Spacer(Modifier.padding(start = 12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(player.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        listOfNotNull(
                                            player.birthYear?.let { "$it г.р." },
                                            Repo.team(player.teamId)?.name,
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("›", style = MaterialTheme.typography.titleLarge)
                            }
                            DividerLine()
                        }
                    }
                    if (divisions.isNotEmpty()) {
                        item { Header("Дивизионы · ${divisions.size}") }
                        items(divisions, key = { "d-${it.id}" }) { division ->
                            ClickableRow({ onOpenDivision(division.id) }) {
                                Column(Modifier.weight(1f)) {
                                    Text(division.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        division.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("›", style = MaterialTheme.typography.titleLarge)
                            }
                            DividerLine()
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}
