package ru.example.pitervolley.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.example.pitervolley.ui.ScreenScaffold
import ru.example.pitervolley.ui.SectionTitle
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

private const val REPO = "SergeiEvsujkov/PiterVolley"
private const val NOTICE_PATH = "docs/notice.json"
private const val ADMIN_PIN = "9630"
private const val PREFS = "notice_admin"
private const val KEY_TOKEN = "token"

/**
 * Публикация объявления для веба: пишет docs/notice.json в репозиторий.
 * Токен GitHub (contents: write) хранится только на этом устройстве.
 */
@Composable
fun NoticeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var token by rememberSaveable { mutableStateOf(prefs.getString(KEY_TOKEN, "").orEmpty()) }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var signature by rememberSaveable { mutableStateOf("С уважением, Сергей Евсюков.") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(title = "Объявление для веба", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionTitle("Публикация")
            Text(
                "Объявление появится в вебе при первом запуске у каждого пользователя. " +
                    "Показывается один раз и закрывается кнопкой.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = token,
                onValueChange = { token = it; prefs.edit().putString(KEY_TOKEN, it).apply() },
                singleLine = true,
                label = { Text("Токен GitHub (contents: write)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Заголовок") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Текст") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = signature,
                onValueChange = { signature = it },
                singleLine = true,
                label = { Text("Подпись") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = from,
                onValueChange = { from = it },
                singleLine = true,
                label = { Text("Показывать с (ГГГГ-ММ-ДД, можно пусто)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                singleLine = true,
                label = { Text("Показывать по (ГГГГ-ММ-ДД, можно пусто)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                singleLine = true,
                label = { Text("Пароль администратора") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                enabled = !busy,
                onClick = {
                    if (pin != ADMIN_PIN) {
                        message = "Неверный пароль администратора"
                        return@FilledTonalButton
                    }
                    if (token.isBlank()) {
                        message = "Нужен токен GitHub"
                        return@FilledTonalButton
                    }
                    if (title.isBlank()) {
                        message = "Нужен заголовок"
                        return@FilledTonalButton
                    }
                    busy = true
                    message = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            publishNotice(token, title, text, signature, from, to)
                        }
                        busy = false
                        if (result.first) {
                            android.widget.Toast.makeText(
                                context,
                                "Объявление опубликовано",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                            onBack()
                        } else {
                            message = result.second
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(if (busy) "Публикуем…" else "Опубликовать в вебе")
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Объявление обновляется на сайте в течение минуты после публикации.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

private fun publishNotice(
    token: String,
    title: String,
    text: String,
    signature: String,
    from: String,
    to: String,
): Pair<Boolean, String> {
    val api = "https://api.github.com/repos/$REPO/contents/$NOTICE_PATH"
    val notice = JSONObject().apply {
        put("id", "n-" + System.currentTimeMillis())
        put("title", title)
        put("text", text)
        put("signature", signature)
        put("from", from.trim())
        put("to", to.trim())
        put("active", true)
    }
    val content = Base64.encodeToString(notice.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    val sha = try {
        val response = request("GET", api + "?ref=main", token, null)
        if (response.first == 200) JSONObject(response.second).optString("sha") else ""
    } catch (error: Exception) {
        ""
    }

    val body = JSONObject().apply {
        put("message", "notice: " + title.take(60))
        put("content", content)
        put("branch", "main")
        if (sha.isNotBlank()) put("sha", sha)
    }
    return try {
        val response = request("PUT", api, token, body.toString())
        if (response.first in 200..299) {
            true to "Опубликовано"
        } else {
            false to "Ошибка GitHub: ${response.first} ${response.second.take(160)}"
        }
    } catch (error: Exception) {
        false to "Ошибка: ${error.message}"
    }
}

private fun request(method: String, url: String, token: String, body: String?): Pair<Int, String> {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = 20_000
    connection.readTimeout = 30_000
    connection.setRequestProperty("Authorization", "Bearer $token")
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("User-Agent", "VolleyPiter")
    if (body != null) {
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    }
    val code = connection.responseCode
    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    connection.disconnect()
    return code to text
}
