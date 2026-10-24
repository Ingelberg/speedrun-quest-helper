"""Fetches the wiki pages the speedrun steps link to and records what each is (NPC, scenery, item,
shop...) and where its map puts it.

Usage: python -I wiki_pages.py <work dir>
"""
import glob
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

WORK = sys.argv[1]
OUT = os.path.join(WORK, "pages.json")
pages = json.load(open(OUT, encoding="utf-8")) if os.path.exists(OUT) else {}

titles = set()
for f in glob.glob(os.path.join(WORK, "*.json")):
    if os.path.basename(f) in ("names.json", "pages.json"):
        continue
    d = json.load(open(f, encoding="utf-8"))
    for w in d.get("wiki", []):
        for link in w["links"]:
            t = link.strip()
            if t:
                titles.add(t[0].upper() + t[1:])
todo = sorted(t for t in titles if t not in pages)
print(len(titles), "titles,", len(todo), "to fetch")


def coords(text):
    """Every map coordinate in the page, as (x, y, plane)."""
    found = []
    for m in re.finditer(r"\{\{(?:Map|ObjectLocLine|LocLine|NPCLocLine)[^{}]*\}\}", text, re.S):
        body = m.group(0)
        plane = re.search(r"plane\s*[=:]\s*(\d)", body)
        p = int(plane.group(1)) if plane else 0
        for x, y in re.findall(r"x\s*[:=]\s*(\d{3,5})\s*[,|]\s*y\s*[:=]\s*(\d{3,5})", body):
            found.append((int(x), int(y), p))
        for x, y in re.findall(r"\|\s*(\d{4}),(\d{4})(?=[|,}])", body):
            found.append((int(x), int(y), p))
    return found


for i in range(0, len(todo), 40):
    chunk = todo[i:i + 40]
    url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode({
        "action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
        "titles": "|".join(chunk), "redirects": 1, "format": "json", "formatversion": 2})
    req = urllib.request.Request(url, headers={"User-Agent": "speedrun-quest-guide route builder"})
    data = json.load(urllib.request.urlopen(req, timeout=60))["query"]
    redirect = {r["from"]: r["to"] for r in data.get("redirects", [])}
    normal = {n["from"]: n["to"] for n in data.get("normalized", [])}
    by_title = {}
    for p in data.get("pages", []):
        text = p["revisions"][0]["slots"]["main"]["content"] if p.get("revisions") else ""
        kind = re.search(r"\{\{Infobox (\w+)", text)
        ids = re.findall(r"\|\s*id\d*\s*=\s*([\d, ]+)", text)
        by_title[p["title"]] = {
            "page": p["title"],
            "kind": kind.group(1) if kind else None,
            "coords": coords(text)[:12],
            "ids": sorted({int(x) for group in ids for x in re.findall(r"\d+", group)})[:20],
        }
    for t in chunk:
        target = redirect.get(normal.get(t, t), normal.get(t, t))
        pages[t] = by_title.get(target, {"page": target, "kind": None, "coords": [], "ids": []})
    json.dump(pages, open(OUT, "w", encoding="utf-8"), indent=0, ensure_ascii=False)
    time.sleep(0.5)
print("done", len(pages))
