#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
VolleyPiter ETL: pitervolley.ru + Google Sheet -> normalized JSON.

Usage:
    python etl.py            # parse cached pages, download what is missing
    python etl.py --refresh  # ignore cache, re-download everything
    python etl.py --offline  # build from cache only
"""
import io
import json
import os
import re
import sys
import time
import difflib
import datetime
import subprocess
import tempfile
import shutil
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
OUT = os.path.join(HERE, "out")
SITE = "https://pitervolley.ru"
SHEET_ID = os.environ.get("VP_SHEET_ID", "1qVhAPMFcWAFylnoy2529uQzTXeBs6lOvfvpWbH2x6u4")
SHEET_ATTEMPTS = 3
UA = {"User-Agent": "VolleyPiter/0.1 (+data sync)"}
DELAY = 1.0

os.makedirs(RAW, exist_ok=True)
os.makedirs(OUT, exist_ok=True)

REFRESH = "--refresh" in sys.argv
OFFLINE = "--offline" in sys.argv

if OFFLINE and os.environ.get("CI"):
    raise SystemExit("offline build is not allowed in CI: данные из кеша не публикуются")


def log(*a):
    print(*a, flush=True)


def fetch(url, cache_name, binary=False):
    path = os.path.join(RAW, cache_name)
    if os.path.exists(path) and not REFRESH:
        if binary:
            with open(path, "rb") as f:
                return f.read()
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            return f.read()
    if OFFLINE:
        raise SystemExit("offline and no cache for " + cache_name)
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()
    if binary:
        with open(path, "wb") as f:
            f.write(data)
        time.sleep(DELAY)
        return data
    text = data.decode("utf-8", "replace")
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    time.sleep(DELAY)
    return text


def strip_tags(s):
    s = re.sub(r"<[^>]+>", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def clean(s):
    return (s or "").replace("\xa0", " ").strip()


def norm_name(s):
    """Canonical key for team matching: case/punctuation/ё-insensitive."""
    s = (s or "").lower().replace("ё", "е")
    s = re.sub(r"[«»\"'`.,]", "", s)
    s = re.sub(r"\s*-\s*", "-", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def norm_venue(s):
    s = re.sub(r"\s+", " ", clean(s)).strip()
    if s.strip(".…? ") == "" or set(s.strip()) <= set("?"):
        return ""
    s = re.sub(r"(?i)(^|\s)просп(ект)?\.?\s*", r"\1пр. ", s)
    s = re.sub(r"(?i)улица", "ул.", s)
    s = re.sub(r"(?i)(^|\s)ул\.?\s*", r"\1ул. ", s)
    s = re.sub(r"(?i)(^|\s)дом\.?\s*(\d)", r"\1д.\2", s)
    s = re.sub(r"(?i)(^|\s)д\.?\s*(\d)", r"\1д.\2", s)
    s = re.sub(r"(?i)(?<![А-Яа-яA-Za-z])В\.?\s*О\.?(?![А-Яа-яA-Za-z])", "В.О.", s)
    s = re.sub(r"(?i)\s+д\.", ", д.", s)
    s = re.sub(r"(\s*,\s*)+", ", ", s)
    s = re.sub(r"(д\.\d+)\s+([А-ЯA-Z])(?![а-яa-z])", r"\1\2", s)
    s = re.sub(r"(д\.)\s+(\d)", r"\1\2", s)
    return s.strip().strip(",")


STREET_TYPES = ("улица", "улиц", "проспект", "просп", "переулок", "бульвар", "шоссе",
                "набережная", "площадь", "проезд", "тупик")
LEGAL_TYPES = {"дом": "д", "строение": "стр", "корпус": "к", "корп": "к", "литера": "лит", "лит": "лит"}


def venue_key(raw):
    """Ключ без улицы/дома/корпуса, регистра и пунктуации — для склейки вариантов."""
    s = norm_venue(raw or "")
    if not s:
        return ""
    s = re.sub(r"\([^)]*\)", " ", s)          # примечания вроде «(ФОК)»
    s = s.lower().replace("ё", "е")
    s = re.sub(r"[.,«»\"'`]", " ", s)
    tokens = []
    i = 0
    parts = s.split()
    while i < len(parts):
        w = parts[i]
        nxt = parts[i + 1] if i + 1 < len(parts) else ""
        if w in STREET_TYPES or w in ("ул", "пр", "пер", "б-р", "ш", "наб", "пл"):
            i += 1
            continue
        if w in LEGAL_TYPES:
            parts[i] = LEGAL_TYPES[w]
        tokens.append(parts[i])
        i += 1
    s = " ".join(tokens)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def canonicalize_venues(games, slots):
    """Один зал — одно название: выбираем самый частый вариант записи."""
    counts = {}
    for g in games:
        v = g.get("venue") or ""
        if v:
            counts.setdefault(venue_key(v), {}).setdefault(v, 0)
            counts[venue_key(v)][v] += 1
    best = {}
    for key, variants in counts.items():
        best[key] = sorted(variants.items(), key=lambda kv: (-kv[1], -len(kv[0]), kv[0]))[0][0]
    # «пр. Ветеранов, 133, к.1» и «пр. Ветеранов, д.133» — один зал
    for alias_key in ("ветеранов д 133", "ветеранов 133 к 1"):
        best[alias_key] = "пр. Ветеранов, 133, к.1"
    names = set()
    for collection in (games, slots):
        for item in collection:
            v = item.get("venue") or ""
            if v:
                item["venue"] = best.get(venue_key(v), v)
                if collection is games:
                    names.add(item["venue"])
    return len(names)


def iso_date(s):
    m = re.match(r"(\d{2})\.(\d{2})\.(\d{4})", s or "")
    return "%s-%s-%s" % (m.group(3), m.group(2), m.group(1)) if m else None


# ---------------------------------------------------------------- site: divisions

TEAM_LINK = r'<td class="cell-team[^"]*"><a href="team\?team=(\d+)"[^>]*>([^<]+)</a>'


CHROME_CANDIDATES = [
    os.environ.get("CHROME_BIN", ""),
    "google-chrome",
    "google-chrome-stable",
    "chromium",
    "chromium-browser",
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
]


def find_chrome():
    for candidate in CHROME_CANDIDATES:
        if not candidate:
            continue
        if os.path.isabs(candidate):
            if os.path.exists(candidate):
                return candidate
        else:
            found = shutil.which(candidate)
            if found:
                return found
    return None


RENDER_FAILED = []


def render_html(url, budget=6000):
    """Готовая страница после выполнения JS (headless Chrome), с повтором."""
    chrome = find_chrome()
    if not chrome:
        RENDER_FAILED.append(url)
        return None
    for attempt in range(2):
        profile = tempfile.mkdtemp(prefix="vp-chrome-", dir=RAW)
        try:
            result = subprocess.run(
                [
                    chrome,
                    "--headless=new",
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--user-data-dir=" + profile,
                    "--virtual-time-budget=%d" % budget,
                    "--disable-extensions",
                    "--disable-background-networking",
                    "--disable-sync",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--metrics-recording-only",
                    "--dump-dom",
                    url,
                ],
                capture_output=True,
                timeout=30,
            )
        except Exception as error:
            log("render failed (attempt %d): %s" % (attempt + 1, error))
            continue
        html = result.stdout.decode("utf-8", "replace")
        if result.returncode == 0 and "<html" in html.lower():
            shutil.rmtree(profile, ignore_errors=True)
            return html
        log("render failed, code %s (attempt %d)" % (result.returncode, attempt + 1))
        shutil.rmtree(profile, ignore_errors=True)
    RENDER_FAILED.append(url)
    return None


def parse_site_standings(html):
    """Готовая таблица со страницы: место, команда, И, В, П, О, Партии.

    Разбираем через lxml и берём только строки верхнего уровня: в шахматке
    внутри ячеек бывают вложенные таблицы, из-за которых regex-разбор ломался.
    """
    from lxml import html as lxml_html

    try:
        doc = lxml_html.fromstring(html)
    except Exception:
        return []

    tables = doc.xpath("//table[contains(concat(' ', normalize-space(@class), ' '), ' s-table ')]")
    if not tables:
        return []
    table = tables[0]
    head_rows = table.xpath("./thead/tr")
    body_rows = table.xpath("./tbody/tr")
    if not body_rows:
        body_rows = table.xpath("./tr")
        if head_rows:
            head_rows = []
        else:
            head_rows = body_rows[:1]
            body_rows = body_rows[1:]
    if not body_rows or not head_rows:
        return []

    def text_of(node):
        return re.sub(r"\s+", " ", node.text_content()).strip()

    header = [text_of(cell).lower() for cell in head_rows[0].xpath("./td | ./th")]

    def column(*names):
        for index, title in enumerate(header):
            if title in names:
                return index
        return None

    col_played = column("и", "игры")
    col_wins = column("в", "победы")
    col_losses = column("п", "поражения")
    col_points = column("оч", "очки", "о")
    col_sets = column("пар", "партии")
    if None in (col_played, col_wins, col_losses, col_points):
        return []

    standings = []
    for row in body_rows:
        cells = row.xpath("./td")
        if len(cells) <= max(col_played, col_wins, col_losses, col_points):
            continue
        first = cells[0]
        team_id = first.get("data-teamid") or ""
        if not team_id or team_id == "0":
            links = first.xpath(".//a/@href")
            for href in links:
                found = re.search(r"team\?team=(\d+)", href)
                if found:
                    team_id = found.group(1)
                    break
        name = (first.get("data-name") or "").strip()
        if not name or name == "...":
            name = text_of(first)
            name = re.sub(r"^\s*\d+\s*", "", name)
        if name == "...":
            name = ""

        def number(index):
            if index is None or index >= len(cells):
                return 0
            digits = re.sub(r"[^0-9]", "", text_of(cells[index]))
            return int(digits) if digits else 0

        standings.append({
            "teamId": team_id,
            "name": name,
            "place": len(standings) + 1,
            "played": number(col_played),
            "wins": number(col_wins),
            "losses": number(col_losses),
            "points": number(col_points),
            "sets": text_of(cells[col_sets]) if col_sets is not None and col_sets < len(cells) else "",
        })

    # таблица может быть нулевой, если в дивизионе ещё не играли:
    # это тоже данные сайта, поэтому отдаём её, если есть названия команд
    standings = [item for item in standings if item["name"]]
    if len(standings) >= 2:
        return standings
    return []


def parse_division(tid, html):
    m = re.search(r"<h1[^>]*>(.*?)</h1>", html, re.S)
    title = strip_tags(m.group(1)) if m else ""
    parts = [p.strip() for p in title.split("/")]
    competition = parts[0] if parts else ""
    gender_raw = parts[1].lower() if len(parts) > 1 else ""
    tail = parts[2] if len(parts) > 2 else ""

    gender = {"юноши": "boys", "девушки": "girls",
              "мужчины": "men", "женщины": "women"}.get(gender_raw, "unknown")

    by = re.search(r"(\d{4})", tail)
    birth_year = int(by.group(1)) if by else None
    tl = re.search(r"(\d+)-я команда", tail)
    team_label = int(tl.group(1)) if tl else None

    games = []
    for gid, body in re.findall(r'<tr id="game-line-(\d+)"[^>]*>(.*?)</tr>', html, re.S):
        d = re.search(r'class="cell-date[^"]*"[^>]*>([^<]*)<', body)
        t = re.search(r'class="cell-time[^"]*"[^>]*>([^<]*)<', body)
        a = re.search(r'class="cell-addr[^"]*"[^>]*>([^<]*)<', body)
        r = re.search(r'class="cell-round"[^>]*>([^<]*)<', body)
        teams = re.findall(TEAM_LINK, '<td class="cell-team">' + body)
        if len(teams) < 2:
            teams = re.findall(r'href="team\?team=(\d+)"[^>]*>([^<]+)</a>', body)
        if len(teams) < 2:
            continue
        sc = re.search(r'<div class="gmscore">(.*?)</div>', body, re.S)
        balls = re.search(r'<div class="gmballs">(.*?)</div>', body, re.S)
        sets = []
        if balls:
            for h, aw in re.findall(r"(\d{1,2})\s*:\s*(\d{1,2})", strip_tags(balls.group(1))):
                sets.append([int(h), int(aw)])
        games.append({
            "id": gid,
            "divisionId": tid,
            "round": int(r.group(1)) if r and r.group(1).strip().isdigit() else None,
            "date": iso_date(clean(d.group(1)) if d else ""),
            "time": clean(t.group(1)) if t else "",
            "venue": norm_venue(a.group(1)) if a else "",
            "homeId": teams[0][0], "homeName": strip_tags(teams[0][1]),
            "awayId": teams[1][0], "awayName": strip_tags(teams[1][1]),
            "score": strip_tags(sc.group(1)) if sc else "",
            "sets": sets,
        })

    return {
        "id": tid, "title": title, "competition": competition,
        "gender": gender, "birthYear": birth_year, "teamLabel": team_label,
    }, games


# ---------------------------------------------------------------- site: team page

def parse_team_page(html):
    logo = ""
    m = re.search(r'<img[^>]+class="[^"]*team-logo-img[^"]*"[^>]+src="([^"]+)"', html) or \
        re.search(r'<img[^>]+src="([^"]+)"[^>]+class="[^"]*team-logo-img', html)
    if m:
        logo = SITE + "/" + m.group(1).lstrip("/")

    players = []
    for b in re.split(r"player-card", html)[1:]:
        pid = re.search(r"player\?pid=(\d+)", b)
        name = re.search(r"<strong>([^<]+)</strong>", b)
        year = re.search(r"(\d{4})\s*г\.р\.", b)
        photo = re.search(r'<img src="([^"]+)"', b)
        if not (pid and name):
            continue
        players.append({
            "id": pid.group(1),
            "name": strip_tags(name.group(1)),
            "birthYear": int(year.group(1)) if year else None,
            "photo": (SITE + "/" + photo.group(1).lstrip("/")) if photo else None,
        })
    return logo, players


# ---------------------------------------------------------------- google sheet

STEP_MINUTES = 75


SECOND_FIRST = re.compile(r"2[-\s]*(?:я\s+)?команд\w*\s+играет\s+перв", re.IGNORECASE)


def _second_team_first(comment):
    """«2 команда играет первой» — единственная фраза, меняющая порядок игр."""
    return bool(SECOND_FIRST.search(comment or ""))


def _parse_time(v):
    if isinstance(v, datetime.time):
        return "%02d:%02d" % (v.hour, v.minute)
    if isinstance(v, datetime.datetime):
        return v.strftime("%H:%M")
    s = str(v or "").strip().replace(".", ":")
    m = re.match(r"^(\d{1,2}):(\d{2})", s)
    return "%02d:%02d" % (int(m.group(1)), int(m.group(2))) if m else ""


def _add_minutes(hhmm, minutes):
    m = re.match(r"^(\d{1,2}):(\d{2})$", hhmm or "")
    if not m:
        return hhmm
    total = int(m.group(1)) * 60 + int(m.group(2)) + minutes
    return "%02d:%02d" % ((total // 60) % 24, total % 60)


def _iso_date(v):
    if isinstance(v, datetime.datetime):
        return v.date().isoformat()
    if isinstance(v, datetime.date):
        return v.isoformat()
    s = str(v or "")
    m = re.search(r"(\d{2})\.(\d{2})\.(\d{4})", s)
    return "%s-%s-%s" % (m.group(3), m.group(2), m.group(1)) if m else None


def _is_red(cell):
    """Красный шрифт в ячейке — признак того, что строку правили вручную."""
    font = cell.font
    if font is None or font.color is None:
        return False
    if font.color.type != "rgb" or not font.color.rgb:
        return False
    return str(font.color.rgb).upper() in ("FFFF0000", "00FF0000", "FF0000")


def parse_sheet(xls_bytes):
    """Одна строка листа — несколько игр: «Игр» + список возрастов, время +1:15."""
    import openpyxl
    wb = openpyxl.load_workbook(io.BytesIO(xls_bytes), data_only=True)
    ws = wb["Назначения"] if "Назначения" in wb.sheetnames else wb.worksheets[0]
    slots = []
    for cells in ws.iter_rows(min_row=2):
        row = [c.value for c in cells]
        if not row or row[1] is None:
            continue
        num = row[0]
        struck = any(c.font is not None and c.font.strike for c in cells)
        corrected = any(_is_red(c) for c in cells if c.value not in (None, ""))
        if struck or (isinstance(num, str) and num.strip().startswith("!")):
            continue  # отменено или перенесено
        raw = row[1]
        nodate = isinstance(raw, str) and "залож" in raw.lower()
        date = None if nodate else _iso_date(raw)
        if not nodate and not date:
            continue
        base = _parse_time(row[3])
        games = row[9] if isinstance(row[9], (int, float)) else 1
        games = max(1, min(8, int(games)))
        ages = [int(a) for a in re.split(r"[.,/]", str(row[12] or "")) if a.strip().isdigit()]
        ages = [a for a in ages if 1990 <= a <= 2030]
        comment = str(row[13] or "").strip()
        group = str(row[4] or "").strip()
        championship = group.lower().startswith(("муж", "жен")) or (not ages and games > 1)
        step = 90 if championship else 75
        labels = list(range(2, max(2, games) + 1)) + [1] if _second_team_first(comment) else list(range(1, games + 1))
        for i in range(games):
            slots.append({
                "num": int(num) if isinstance(num, (int, float)) else None,
                "date": date or "",
                "time": _add_minutes(base, step * i) if base else "",
                "group": str(row[4] or "").strip(),
                "home": str(row[5] or "").strip(),
                "away": str(row[7] or "").strip(),
                "venue": norm_venue(str(row[8] or "")),
                "games": 1,
                "refs": [str(row[10] or "").strip(), str(row[11] or "").strip()],
                "ages": [ages[i]] if i < len(ages) else [],
                "year": ages[i] if i < len(ages) else None,
                "label": labels[i] if not ages else None,
                "comment": comment,
                "corrected": corrected,
            })
    return slots


# ---------------------------------------------------------------- matching

GENDER_MAP = {"дев": "girls", "юн": "boys", "муж": "men", "жен": "women"}
PREFIXES = ("гбоушиор-", "гбоушиор ", "гбоу ", "сшор ", "сш ", "дюсш ")


def alias(n):
    """Club-level key: drop org prefixes so 'СШ Экран' == 'Экран'."""
    s = norm_name(n)
    changed = True
    while changed:
        changed = False
        for p in PREFIXES:
            if s.startswith(p):
                s = s[len(p):]
                changed = True
    # «Сборная СПб» и «Сборная» — одна команда
    s = " ".join(w for w in s.split() if w and w not in ("спб", "санкт-петербург", "санкт", "петербург"))
    return s


def _pair(a, b):
    return tuple(sorted((alias(a), alias(b))))


def _slot_key(s):
    gender = GENDER_MAP.get(str(s.get("group") or "").strip(), None)
    if s.get("year"):
        return (_pair(s["home"], s["away"]), gender, "y", s["year"])
    if s.get("label"):
        return (_pair(s["home"], s["away"]), gender, "l", s["label"])
    return (_pair(s["home"], s["away"]), gender, "*", None)


def _game_key(g, d):
    if d.get("birthYear"):
        return (_pair(g["home"], g["away"]), d.get("gender"), "y", d["birthYear"])
    if d.get("teamLabel"):
        return (_pair(g["home"], g["away"]), d.get("gender"), "l", d["teamLabel"])
    return (_pair(g["home"], g["away"]), d.get("gender"), "*", None)


def match_slots(slots, games, divisions):
    """Таблица главнее сайта: слоты раздаются играм парами по порядку дат."""
    div_by_id = {d["id"]: d for d in divisions}
    by_key = {}
    for s in slots:
        by_key.setdefault(_slot_key(s), []).append(s)
    for v in by_key.values():
        v.sort(key=lambda s: (s.get("date") or "", s.get("time") or "", s.get("num") or 0))

    groups = {}
    for g in games:
        if g.get("score"):
            continue  # сыгранные матчи не трогаем
        d = div_by_id.get(g["divisionId"])
        if not d:
            continue
        groups.setdefault(_game_key(g, d), []).append(g)

    matched = date_filled = 0
    for key, gs in groups.items():
        ss = by_key.get(key) or []
        gs.sort(key=lambda g: (g.get("date") or "9999-12-31", g.get("round") or 0, g["id"]))
        for s, g in zip(ss, gs):
            matched += 1
            s["gameIds"] = [g["id"]]
            s["divisionId"] = g["divisionId"]
            g["slotNum"] = s["num"]
            if not s["date"]:
                if g.get("date"):
                    g["date"] = None
                    g["time"] = ""
                    g["dateSource"] = "sheet"
                    date_filled += 1
                g["corrected"] = bool(s.get("corrected"))
                continue
            g["date"] = s["date"]
            if s["time"]:
                g["time"] = s["time"]
            if s["venue"]:
                g["venue"] = s["venue"]
            g["refs"] = [r for r in (s.get("refs") or []) if r]
            g["comment"] = s.get("comment") or ""
            g["corrected"] = bool(s.get("corrected"))
            g["dateSource"] = "sheet"
            date_filled += 1
    return matched, date_filled


# ---------------------------------------------------------------- main

def load_sheet():
    """Таблица расписания с несколькими попытками: сайт важнее, но без таблицы тоже публикуем."""
    url = "https://docs.google.com/spreadsheets/d/%s/export?format=xlsx" % SHEET_ID
    for attempt in range(1, SHEET_ATTEMPTS + 1):
        try:
            xls = fetch(url, "sheet.xlsx", binary=True)
            slots = parse_sheet(xls)
            if slots:
                return slots
            log("sheet: пустой ответ, попытка %d/%d" % (attempt, SHEET_ATTEMPTS))
        except Exception as e:
            log("sheet failed (попытка %d/%d): %s" % (attempt, SHEET_ATTEMPTS, e))
        time.sleep(3)
    return []


def load_previous():
    path = os.path.join(os.path.dirname(HERE), "docs", "data.json")
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:
        log("previous data unavailable:", e)
        return None


def main():
    t0 = time.time()
    divisions, games = [], []
    teams, team_ids = [], set()
    players = []
    sources = {"site": False, "sheet": False}
    standings = {}

    try:
        for tid in range(1, 19):
            url = "%s/table?tid=%d" % (SITE, tid)
            html = fetch(url, "table-%d.html" % tid)
            div, dgames = parse_division(tid, html)
            site_rows = []
            if os.environ.get("VP_SKIP_RENDER") != "1":
                rendered = render_html(url)
                if rendered:
                    site_rows = parse_site_standings(rendered)
            if site_rows:
                standings[tid] = site_rows
                log("tid=%-2d таблица с сайта: %d команд" % (tid, len(site_rows)))
            divisions.append(div)
            games.extend(dgames)
            for sid, sname in re.findall(TEAM_LINK, html):
                if sid not in team_ids:
                    team_ids.add(sid)
                    teams.append({"id": sid, "name": strip_tags(sname), "divisionId": tid, "logo": None})
            log("tid=%-2d %-45s games=%d" % (tid, div["title"][:45], len(dgames)))

        for i, t in enumerate(teams, 1):
            html = fetch("%s/team?team=%s" % (SITE, t["id"]), "team-%s.html" % t["id"])
            logo, tplayers = parse_team_page(html)
            t["logo"] = logo
            for p in tplayers:
                p["teamId"] = t["id"]
                p["divisionId"] = t["divisionId"]
            players.extend(tplayers)
            if i % 25 == 0:
                log("teams %d/%d, players %d" % (i, len(teams), len(players)))

        sources["site"] = True
    except Exception as e:
        log("site failed:", e)
        prev = load_previous()
        if not prev:
            raise
        divisions = prev.get("divisions", [])
        teams = prev.get("teams", [])
        players = prev.get("players", [])
        games = [dict(g) for g in prev.get("games", [])]
        standings = prev.get("standings", {}) or {}
        log("using previous site data:", len(games), "games")

    names = {t["id"]: t["name"] for t in teams}
    for g in games:
        g["home"] = names.get(g["homeId"], g.get("home") or g.get("homeName") or "")
        g["away"] = names.get(g["awayId"], g.get("away") or g.get("awayName") or "")
        g.pop("homeName", None)
        g.pop("awayName", None)

    slots = load_sheet()
    sources["sheet"] = bool(slots)
    if not slots:
        log("sheet unavailable: публикуем данные сайта без расписания")

    if not sources["site"] and not sources["sheet"]:
        raise SystemExit("no data sources available")

    missing = [d for d in divisions if str(d.get("id")) not in standings]
    if missing:
        log("повторный рендер таблиц: %s" % ", ".join(str(d.get("id")) for d in missing))
        for d in missing:
            tid = d.get("id")
            page = render_html("%s/table?tid=%s" % (SITE, tid), budget=10000)
            rows = parse_site_standings(page) if page else []
            if rows:
                standings[tid] = rows
                log("tid=%s таблица получена со второй попытки: %d команд" % (tid, len(rows)))
    if RENDER_FAILED:
        log("не отрисовались: %s" % ", ".join(sorted(RENDER_FAILED)))
    if standings:
        log("таблицы с сайта: %d дивизионов" % len(standings))
    else:
        log("таблицы с сайта получить не удалось — приложение посчитает их само")

    matched, date_filled = match_slots(slots, games, divisions)
    venue_total = canonicalize_venues(games, slots)

    data = {
        "schema": 1,
        "generatedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "season": "2026-27",
        "sources": sources,
        "offline": OFFLINE,
        "counts": {
            "divisions": len(divisions), "teams": len(teams), "players": len(players),
            "games": len(games), "slots": len(slots),
            "gamesWithResult": sum(1 for g in games if g["score"]),
            "gamesWithoutDate": sum(1 for g in games if not g["date"]),
            "venues": venue_total,
            "slotsMatched": matched,
            "datesFromSheet": date_filled,
        },
        "divisions": divisions, "teams": teams, "players": players,
        "games": games, "slots": slots,
        "standings": {str(k): v for k, v in standings.items()},
    }

    out = os.path.join(OUT, "data.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    log("wrote %s (%.1f KB) in %.1fs" % (out, os.path.getsize(out) / 1024.0, time.time() - t0))
    log("sources:", json.dumps(sources, ensure_ascii=False))
    log("counts:", json.dumps(data["counts"], ensure_ascii=False))


if __name__ == "__main__":
    main()
