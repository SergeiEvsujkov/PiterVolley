package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.ui.DividerLine
import ru.example.pitervolley.ui.EmptyState
import ru.example.pitervolley.ui.ScreenScaffold

@Composable
fun GameScreen(
    divisionId: Int,
    gameId: String,
    onBack: () -> Unit,
    onOpenTeam: (String) -> Unit,
    onOpenDivision: (Int) -> Unit,
) {
    val game = Repo.game(divisionId, gameId)
    if (game == null) {
        ScreenScaffold(title = "Игра", onBack = onBack) { p ->
            Column(Modifier.padding(p)) { EmptyState("Игра не найдена") }
        }
        return
    }
    val division = Repo.division(game.divisionId)

    ScreenScaffold(
        title = "${game.home} — ${game.away}",
        subtitle = listOfNotNull(division?.competition, division?.subtitle).joinToString(" · "),
        onBack = onBack,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = game.dateFull,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (game.time.isNotBlank()) {
                        Text(game.time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (game.venue.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(game.venue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (game.refs.isNotEmpty() || game.comment.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (game.refs.isNotEmpty()) {
                            Text(
                                "Судьи",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(game.refs.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (game.comment.isNotBlank()) {
                            if (game.refs.isNotEmpty()) Spacer(Modifier.height(10.dp))
                            Text(
                                "Комментарий",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(game.comment, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = game.home,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenTeam(game.homeId) },
                )
                Text(
                    text = game.score.ifBlank { "—" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(96.dp),
                )
                Text(
                    text = game.away,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenTeam(game.awayId) },
                )
            }

            if (game.sets.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Партии", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                game.sets.forEachIndexed { index, set ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Партия ${index + 1}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${set.getOrElse(0) { 0 }} : ${set.getOrElse(1) { 0 }}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    DividerLine()
                }
            }

            Spacer(Modifier.height(20.dp))
            if (division != null) {
                Card(
                    onClick = { onOpenDivision(division.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Дивизион", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text("${division.competition} · ${division.subtitle}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (game.corrected && !Repo.isPastGame(game)) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("!", fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFFA95A12))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Внимание данная игра была скорректирована.",
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color(0xFFA95A12),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = when {
                    game.hasResult -> "Результат опубликован федерацией."
                    game.localDate == null -> "Дата игры ещё не назначена."
                    else -> "Результат пока не опубликован."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
