package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.ui.ClickableRow
import ru.example.pitervolley.ui.ScreenScaffold

@Composable
fun DivisionsScreen(onOpenDivision: (Int) -> Unit) {
    ScreenScaffold(title = "Дивизионы", subtitle = "Сезон ${Repo.data.season}") { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            Repo.divisionsByCompetition().forEach { (competition, divisions) ->
                item(key = "c-$competition") {
                    Text(
                        text = competition,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                    )
                }
                items(divisions, key = { "d-${it.id}" }) { division ->
                    ClickableRow({ onOpenDivision(division.id) }) {
                        Column(Modifier.weight(1f)) {
                            Text(division.subtitle, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "${Repo.teamsOf(division.id).size} команд · ${Repo.gamesOf(division.id).size} игр",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
