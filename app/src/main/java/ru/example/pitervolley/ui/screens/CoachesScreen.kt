package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.ui.ClickableRow
import ru.example.pitervolley.ui.DividerLine
import ru.example.pitervolley.ui.EmptyState
import ru.example.pitervolley.ui.PlayerAvatar
import ru.example.pitervolley.ui.ScreenScaffold

@Composable
fun CoachesScreen(
    onBack: () -> Unit,
    onOpenTeam: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val coaches = remember(q) {
        val all = Repo.coaches()
        if (q.length < 2) all else all.filter { it.coach.name.lowercase().contains(q) }
    }

    ScreenScaffold(title = "Тренеры", subtitle = "Тренеры команд", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Фамилия тренера") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (coaches.isEmpty()) {
                EmptyState("Ничего не найдено")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(coaches, key = { it.coach.id }) { entry ->
                        ClickableRow({
                            entry.teams.firstOrNull()?.let { onOpenTeam(it.id) }
                        }) {
                            PlayerAvatar(entry.coach.photo, entry.coach.name, 40)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.coach.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    entry.teams.joinToString(", ") { team ->
                                        val division = Repo.division(team.divisionId)
                                        if (division != null) "${team.name} · ${division.subtitle}" else team.name
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("›", style = MaterialTheme.typography.titleLarge)
                        }
                        DividerLine()
                    }
                }
            }
        }
    }
}
