package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.ui.ClickableRow
import ru.example.pitervolley.ui.DividerLine
import ru.example.pitervolley.ui.EmptyState
import ru.example.pitervolley.ui.PlayerAvatar
import ru.example.pitervolley.ui.ScreenScaffold
import ru.example.pitervolley.ui.SectionTitle
import ru.example.pitervolley.ui.TeamLogo

@Composable
fun CoachScreen(
    coachKey: String,
    onBack: () -> Unit,
    onOpenTeam: (String) -> Unit,
) {
    val key = runCatching { android.net.Uri.decode(coachKey) }.getOrDefault(coachKey)
    val entry = Repo.coaches().firstOrNull { Repo.coachNameKey(it.coach.name) == key }
    if (entry == null) {
        ScreenScaffold(title = "Тренер", onBack = onBack) { p ->
            Column(Modifier.padding(p)) { EmptyState("Тренер не найден") }
        }
        return
    }

    ScreenScaffold(title = entry.coach.name, subtitle = "Тренер", onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerAvatar(entry.coach.photo, entry.coach.name, 88)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.coach.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Тренер",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item { SectionTitle("Команды тренера · ${entry.teams.size}") }
            items(entry.teams, key = { "ct-${it.id}" }) { team ->
                ClickableRow({ onOpenTeam(team.id) }) {
                    TeamLogo(team, 36)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(team.name, style = MaterialTheme.typography.bodyLarge)
                        Repo.division(team.divisionId)?.let { division ->
                            Text(
                                "${division.competition} · ${division.subtitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge)
                }
                DividerLine()
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
