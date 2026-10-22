# PiterVolley

Android-приложение о Первенстве и Чемпионате Санкт-Петербурга по волейболу:
календарь игр, результаты по партиям, турнирные таблицы, команды и игроки.

Данные собираются из открытых источников: [pitervolley.ru](https://pitervolley.ru)
(таблицы, результаты, составы) и Google-таблицы расписания федерации.

## Структура

```
app/                 Android-приложение (Kotlin, Compose, без Room: данные в памяти)
etl/                 сборщик данных: etl.py, validate.py, README.md
etl/out/data.json    результат ETL (не коммитится)
docs/data.json       опубликованный датасет для приложения (GitHub Pages)
.github/workflows/   крон-задача: пересобирает данные каждые 30 минут
```

## Как работают обновления

1. GitHub Actions каждые 30 минут запускает `etl/etl.py --refresh`.
2. Скрипт скачивает 18 страниц дивизионов, ~170 страниц команд и Google-таблицу,
   нормализует и сопоставляет их.
3. Результат копируется в `docs/data.json` и коммитится.
4. GitHub Pages отдаёт файл по адресу `https://<user>.github.io/<repo>/data.json`.
5. Приложение при старте (и раз в 6 часов через WorkManager) скачивает свежий JSON,
   кэширует его в `filesDir` и подменяет данные; при отсутствии сети работает копия из APK.

Адрес задаётся в `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "DATA_URL", "\"https://<user>.github.io/<repo>/data.json\"")
```

Пустая строка отключает синхронизацию — приложение остаётся на данных из APK.

## Сборка

```bash
export JAVA_HOME="/path/to/Android Studio/jbr"
./gradlew :app:assembleDebug
# или релиз
./gradlew :app:assembleRelease
```

Минимальные требования: JDK 17+, Android SDK 37, Gradle 9.8.2 (wrapper).

## Настройка Pages

1. Создать репозиторий на GitHub и запушить этот проект.
2. Settings → Pages → Source: **Deploy from a branch**, ветка `main`, папка `/docs`.
3. В `app/build.gradle.kts` заменить `DATA_URL` на адрес вида
   `https://<user>.github.io/<repo>/data.json` и пересобрать APK.
4. Workflow `ETL` можно запустить вручную: Actions → ETL → Run workflow.

## Ограничения

- `robots.txt` pitervolley.ru закрывает URL с `?` — для регулярного обхода нужно
  разрешение федерации; до его получения данные собираются из открытых страниц.
- Составы заполнены не у всех команд, статистики игроков источники не публикуют.
- Часть игр без даты: они есть и на сайте, и в таблице без расписания.
