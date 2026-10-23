package ru.example.pitervolley.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.example.pitervolley.R
import ru.example.pitervolley.data.Favorites
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.data.today

/** Local reminders about tomorrow's games of favourite teams. */
object GameNotifications {

    private const val CHANNEL_ID = "games"
    private const val NOTIFICATION_ID = 1001
    private const val PREFS = "reminder"
    private const val KEY_DAY = "last_notified_day"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Матчи",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Напоминания об играх избранных команд" },
        )
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Posts one digest notification for tomorrow's games, if any favourite team plays. */
    fun notifyTomorrow(context: Context) {
        if (!canNotify(context)) return
        val favorites = Favorites.trackedTeamIds()
        if (favorites.isEmpty()) return
        if (!Repo.isLoaded) return

        val tomorrow = today().plusDays(1).toString()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_DAY, null) == tomorrow) return

        val games = Repo.data.games
            .filter { it.date == tomorrow && (it.homeId in favorites || it.awayId in favorites) }
            .sortedBy { it.time }
        if (games.isEmpty()) return

        ensureChannel(context)

        val title = if (games.size == 1) "Завтра игра вашей команды" else "Завтра ${games.size} игры ваших команд"
        val body = games.take(6).joinToString("\n") { g ->
            "${g.time}  ${g.home} — ${g.away}" + if (g.venue.isNotBlank()) "\n${g.venue}" else ""
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle(title)
            .setContentText(games.first().let { "${it.time} ${it.home} — ${it.away}" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }.onSuccess {
            prefs.edit().putString(KEY_DAY, tomorrow).apply()
        }
    }
}
