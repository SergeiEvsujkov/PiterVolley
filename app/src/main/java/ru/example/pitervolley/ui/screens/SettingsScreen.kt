package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.example.pitervolley.data.PersonOverrides
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.ui.ScreenScaffold
import ru.example.pitervolley.ui.SectionTitle

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    val splitCount = remember(version) { PersonOverrides.splitCount() }
    val mergeCount = remember(version) { PersonOverrides.mergeCount() }
    val persons = remember(version) { Repo.personCount() }

    ScreenScaffold(title = "Настройки", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SectionTitle("Данные")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Игроки", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Сейчас в базе $persons уникальных игроков. Приложение считает их по ФИО и году рождения.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle("Ручная группировка игроков")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Зачем это нужно",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Иногда федерация пишет одно и то же имя по-разному или, наоборот, " +
                            "склеивает разных детей. На карточке игрока есть кнопки " +
                            "«Это разные люди» и «Связать с игроком» — они исправляют такие случаи " +
                            "вручную, и от этого зависят счётчик игроков, избранное и список игр.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Сейчас правок: разделений $splitCount, объединений $mergeCount",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.FilledTonalButton(
                        enabled = splitCount > 0 || mergeCount > 0,
                        onClick = {
                            PersonOverrides.clear()
                            Repo.rebuildPersons()
                            version++
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Сбросить все правки")
                    }
                    Text(
                        "Сброс вернёт автоматическую группировку по ФИО и году рождения. " +
                            "Избранное, дивизионы и расписание не изменятся — поменяется только то, " +
                            "какие записи считаются одним человеком.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
