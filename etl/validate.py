# -*- coding: utf-8 -*-
"""Проверки перед публикацией data.json.

Запуск в CI: python etl/validate.py
Код возврата 1 — публиковать нельзя.
"""
import argparse
import datetime
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)


def load(path):
    if not os.path.exists(path):
        return None
    with io.open(path, encoding="utf-8") as f:
        return json.load(f)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default=os.path.join(HERE, "out", "data.json"))
    parser.add_argument("--previous", default=os.path.join(ROOT, "docs", "data.json"))
    parser.add_argument("--allow-offline", action="store_true", help="разрешить проверку локальной сборки из кеша")
    args = parser.parse_args()

    errors = []
    notes = []

    data = load(args.data)
    if data is None:
        print("validate: файл данных не найден:", args.data)
        return 1

    # 1. Сборка из кеша не публикуется
    if data.get("offline") and not args.allow_offline:
        errors.append("данные собраны из кеша (--offline) — публиковать нельзя")

    # 2. Нужны оба источника
    sources = data.get("sources") or {}
    if not sources.get("site"):
        errors.append("сайт pitervolley.ru не собрался (sources.site = false)")
    if not sources.get("sheet"):
        errors.append("таблица расписания не собралась (sources.sheet = false)")

    # 3. Дата сборки — сегодняшняя
    generated = (data.get("generatedAt") or "")[:10]
    today = datetime.datetime.now(datetime.timezone.utc).date().isoformat()
    if generated != today:
        errors.append("generatedAt = %r, ожидался %s" % (generated, today))

    # 4. Сравнение с опубликованной версией: результаты и игры не должны исчезать
    previous = load(args.previous)
    if previous:
        cur_results = sum(1 for g in data.get("games", []) if g.get("score"))
        prev_results = sum(1 for g in previous.get("games", []) if g.get("score"))
        cur_games = len(data.get("games", []))
        prev_games = len(previous.get("games", []))
        notes.append("игр: %d -> %d, результатов: %d -> %d" % (prev_games, cur_games, prev_results, cur_results))
        if cur_results < prev_results:
            errors.append("результатов стало меньше: %d -> %d" % (prev_results, cur_results))
        if cur_games < prev_games:
            errors.append("игр стало меньше: %d -> %d" % (prev_games, cur_games))

    print("validate:", "OK" if not errors else "FAIL")
    for note in notes:
        print("  ", note)
    for error in errors:
        print("   ОШИБКА:", error)
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())
