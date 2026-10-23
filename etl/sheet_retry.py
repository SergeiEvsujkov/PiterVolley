# -*- coding: utf-8 -*-
"""Догоняет таблицу расписания, если её не было при основной сборке.

Запускается по расписанию каждые 10 минут: пока в опубликованных данных
`sources.sheet = false`, пробует загрузить таблицу (три попытки) и, если
получилось, дописывает даты, залы и судей к уже опубликованным играм.
Данные сайта при этом не перезаписываются.
"""
import argparse
import datetime
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import etl  # noqa: E402


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default=os.path.join(os.path.dirname(HERE), "docs", "data.json"))
    args = parser.parse_args()

    path = args.data
    if not os.path.exists(path):
        print("нет файла данных:", path)
        return 0

    with io.open(path, encoding="utf-8") as f:
        data = json.load(f)

    sources = data.get("sources") or {}
    if sources.get("sheet"):
        print("таблица уже собрана — нечего догонять")
        return 0

    # нужны свежие данные таблицы, а не кеш
    etl.REFRESH = True
    slots = etl.load_sheet()
    if not slots:
        print("таблица всё ещё недоступна — повторим через 10 минут")
        return 0

    games = data.get("games") or []
    divisions = data.get("divisions") or []
    matched, filled = etl.match_slots(slots, games, divisions)

    data["slots"] = slots
    data["sources"] = dict(sources, sheet=True)
    data["sheetUpdatedAt"] = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")
    counts = data.get("counts") or {}
    counts.update({
        "slots": len(slots),
        "slotsMatched": matched,
        "datesFromSheet": filled,
        "gamesWithoutDate": sum(1 for g in games if not g.get("date")),
    })
    data["counts"] = counts

    with io.open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))

    print("таблица догнала: слотов %d, сопоставлено %d, дат из таблицы %d" % (len(slots), matched, filled))
    return 0


if __name__ == "__main__":
    sys.exit(main())
