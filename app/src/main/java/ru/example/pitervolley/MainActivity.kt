package ru.example.pitervolley

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.example.pitervolley.data.Favorites
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.data.SiteSync
import ru.example.pitervolley.notify.GameNotifications
import ru.example.pitervolley.ui.PiterVolleyApp
import ru.example.pitervolley.ui.theme.PiterVolleyTheme
import ru.example.pitervolley.work.SyncWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = applicationContext
        setContent {
            PiterVolleyTheme {
                var ready by remember { mutableStateOf(Repo.isLoaded) }
                var error by remember { mutableStateOf<String?>(null) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }

                LaunchedEffect(Unit) {
                    Favorites.init(app)
                    SyncWorker.schedule(app)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    if (!Repo.isLoaded) {
                        try {
                            withContext(Dispatchers.IO) { Repo.load(app) }
                            ready = true
                        } catch (t: Throwable) {
                            error = t.message ?: t.javaClass.simpleName
                        }
                    }

                    if (error == null) {
                        runCatching { SiteSync.refresh(app, force = false) }
                        runCatching { GameNotifications.notifyTomorrow(app) }
                    }
                }

                when {
                    ready -> PiterVolleyApp(
                        startRoute = intent.getStringExtra("route"),
                        startQuery = intent.getStringExtra("q"),
                    )
                    error != null -> LoadingState("Не удалось загрузить данные: $error")
                    else -> LoadingState(null)
                }
            }
        }
    }
}

@Composable
private fun LoadingState(error: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (error == null) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Загружаем данные федерации…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
