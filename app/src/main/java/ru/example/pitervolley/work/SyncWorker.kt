package ru.example.pitervolley.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.data.Sync
import ru.example.pitervolley.notify.GameNotifications
import java.util.concurrent.TimeUnit

/** Refreshes the dataset and reminds about tomorrow's games. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { Repo.load(applicationContext) }
        val result = Sync.refresh(applicationContext)
        runCatching { GameNotifications.notifyTomorrow(applicationContext) }
        return if (result.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "piter-volley-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
