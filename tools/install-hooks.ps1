# Подключить хуки репозитория (один раз после клонирования)
# Запуск: powershell -ExecutionPolicy Bypass -File tools/install-hooks.ps1

$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

git config core.hooksPath .githooks

# на Unix-подобных системах хуку нужен бит выполнения
if (Test-Path ".githooks/commit-msg") {
    git update-index --chmod=+x .githooks/commit-msg 2>$null
}

Write-Host "Готово: core.hooksPath = .githooks"
Write-Host "Теперь docs/data.json нельзя закоммитить без сообщения, начинающегося с 'data:'."
