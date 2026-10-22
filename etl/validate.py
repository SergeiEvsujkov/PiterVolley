# -*- coding: utf-8 -*-
import json, io, collections, re

p = r"C:\Users\evsuj\Documents\deepseek-harness\default-workspace\pv-etl\out\data.json"
d = json.load(io.open(p, encoding="utf-8"))
L = []
L.append("counts: %s" % d["counts"])

divs = {x["id"]: x for x in d["divisions"]}
teams = d["teams"]
players = d["players"]
games = d["games"]
slots = d["slots"]

L.append("\n== games with results sample (tid=15):")
for g in [g for g in games if g["divisionId"] == 15][:6]:
    L.append("  %s %s %s | %s vs %s | score=%r sets=%r venue=%r" %
             (g["id"], g["date"], g["time"], g["home"], g["away"], g["score"], g["sets"], g["venue"]))

L.append("\n== score format distribution:")
fmt = collections.Counter()
for g in games:
    if g["score"]:
        fmt[re.sub(r"\d+", "N", g["score"])] += 1
        if len(g["sets"]) not in (3, 4, 5):
            L.append("  BAD sets len=%d game=%s score=%r sets=%r" % (len(g["sets"]), g["id"], g["score"], g["sets"]))
L.append("  %s" % dict(fmt))

L.append("\n== games without date/time/venue:")
L.append("  no date: %d, no time: %d, no venue: %d" % (
    sum(1 for g in games if not g["date"]),
    sum(1 for g in games if not g["time"]),
    sum(1 for g in games if not g["venue"])))

L.append("\n== teams per division / players per division:")
cnt_t = collections.Counter(t["divisionId"] for t in teams)
cnt_p = collections.Counter(p["divisionId"] for p in players)
for tid in sorted(divs):
    L.append("  tid=%-2d teams=%-3d players=%-4d games=%-3d res=%-3d  %s" % (
        tid, cnt_t[tid], cnt_p[tid],
        sum(1 for g in games if g["divisionId"] == tid),
        sum(1 for g in games if g["divisionId"] == tid and g["score"]),
        divs[tid]["title"]))

L.append("\n== players: duplicate pids (same player in several teams):")
dup = collections.Counter(p["id"] for p in players)
dups = {k: v for k, v in dup.items() if v > 1}
L.append("  %d duplicated pids, e.g. %s" % (len(dups), list(dups.items())[:5]))
for pid in list(dups)[:3]:
    L.append("   pid=%s -> %s" % (pid, [(p["name"], p["teamId"]) for p in players if p["id"] == pid]))

L.append("\n== players without birthYear: %d / %d" % (sum(1 for p in players if not p["birthYear"]), len(players)))
L.append("== players without photo: %d" % sum(1 for p in players if not p["photo"]))

L.append("\n== teams with 0 players: %d" % sum(1 for t in teams if not any(p["teamId"] == t["id"] for p in players)))
zero = [t for t in teams if not any(p["teamId"] == t["id"] for p in players)]
for t in zero[:15]:
    L.append("   %s (tid=%s)" % (t["name"], t["divisionId"]))

L.append("\n== unmatched slots: %d" % sum(1 for s in slots if not s.get("gameIds")))
for s in [s for s in slots if not s.get("gameIds")][:8]:
    L.append("   %s %s %-22s vs %-22s %s" % (s["date"], s["time"], s["home"], s["away"], s["venue"]))

L.append("\n== near-duplicate team names (same normalized key):")
keys = collections.defaultdict(set)
for t in teams:
    k = t["name"].lower().replace("ё", "е").strip()
    keys[k].add(t["id"])
L.append("  %d keys" % len(keys))
for k, v in list(keys.items())[:100]:
    if len(v) > 1:
        L.append("   %r -> %s" % (k, [(x, [t["name"] for t in teams if t["id"] == x]) for x in v]))

io.open(r"C:\Users\evsuj\Documents\deepseek-harness\default-workspace\pv-etl\out\validation.txt", "w", encoding="utf-8").write("\n".join(L))
print("ok", len(L))
