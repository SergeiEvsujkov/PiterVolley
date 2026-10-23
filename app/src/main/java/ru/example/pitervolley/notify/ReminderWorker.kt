package ru.example.pitervolley.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.example.pitervolley.data.Favorites
import ru.example.pitervolley.data.ReminderSettings
import ru.example.pitervolley.data.Repo
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Раз в сутки в 18:00 проверяет игры избранных команд на завтра и напоминает.
 * Сеть не нужна: работает по данным, уже загруженным в приложение.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        Favorites.init(context)
        ReminderSettings.init(context)
        runCatching { Repo.load(context) }
        GameNotifications.notifyTomorrow(context)
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-game-reminder"
        private const val HOUR = 18

        /** Включает ежедневную задачу, если напоминания вообще нужны, иначе снимает её. */
        fun sync(context: Context) {
            if (!ReminderSettings.state.value.anyEnabled) {
                cancel(context)
                return
            }
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(HOUR, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)

            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
