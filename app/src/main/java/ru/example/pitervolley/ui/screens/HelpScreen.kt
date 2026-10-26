package ru.example.pitervolley.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.example.pitervolley.ui.ScreenScaffold
import ru.example.pitervolley.ui.SectionTitle

/** Короткая инструкция внутри приложения. */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "Как пользоваться", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionTitle("С чего начать")
            HelpCard(
                "На главной — ближайшие игры и игры избранных команд. " +
                    "Переключатель «Ближайшие / Прошедшие» меняет список. " +
                    "Нажмите на карточку игры, чтобы увидеть счёт, зал и судей.",
            )

            SectionTitle("Избранное")
            HelpCard(
                "Звёздочка на карточке команды или игрока добавляет её в избранное. " +
                    "Избранные команды и игроки появляются на главной, их игры попадают " +
                    "в отдельный список и в напоминания.",
            )

            SectionTitle("Календарь и дивизионы")
            HelpCard(
                "Календарь показывает все игры по дням или месяцем. " +
                    "Фильтр сверху оставляет только нужный дивизион. " +
                    "В разделе «Дивизионы» — таблица, игры и составы команд.",
            )

            SectionTitle("Если данные неверные")
            HelpCard(
                "На карточке игрока есть «Это разные люди» и «Связать с игроком» — " +
                    "если федерация записала одного человека дважды или объединила разных. " +
                    "Правки хранятся только на этом устройстве и на другие не передаются. " +
                    "Сбросить их можно в настройках, раздел «Ручная группировка».",
            )

            SectionTitle("Напоминания")
            HelpCard(
                "В настройках включите нужные источники: все игры, избранные команды, " +
                    "избранные игроки или отдельные дивизионы. Уведомление приходит " +
                    "за день до игры в 18:00. Для работы напоминаний разрешите уведомления.",
            )

            SectionTitle("Данные и обновление")
            HelpCard(
                "Расписание обновляется автоматически с сайта федерации и из таблицы назначений. " +
                    "Если интернета нет, приложение покажет последнюю сохранённую версию. " +
                    "Дата данных и источник видны в настройках.",
            )

            SectionTitle("Установка на телефон")
            HelpCard(
                "Android: откройте сайт в Chrome и выберите «Установить приложение» " +
                    "или «Добавить на главный экран».\n" +
                    "iPhone: откройте сайт в Safari, нажмите «Поделиться» → " +
                    "«На экран Домой».",
            )
        }
    }
}

@Composable
private fun HelpCard(text: String) {
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
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
