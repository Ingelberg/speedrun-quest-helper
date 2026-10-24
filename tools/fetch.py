"""Downloads the OSRS Wiki speedrun guides as wikitext.

Usage: python -I fetch.py <out dir>
Then:  python -I convert.py <out dir> ../src/main/resources/com/speedrunguide/guides.json
"""
import json
import os
import sys
import urllib.parse
import urllib.request

QUESTS = ["X Marks the Spot", "Cook's Assistant", "Vampyre Slayer", "Ernest the Chicken",
          "Black Knights' Fortress", "Demon Slayer", "Prince Ali Rescue", "Below Ice Mountain",
          "Temple of the Eye", "Dragon Slayer I", "A Taste of Hope", "Beneath Cursed Sands",
          "A Kingdom Divided", "Sins of the Father", "Dragon Slayer II"]


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    revisions = {}
    for q in QUESTS:
        url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode({
            "action": "parse", "page": q + "/Speedrun guide", "prop": "wikitext|revid",
            "format": "json", "redirects": 1})
        req = urllib.request.Request(url, headers={"User-Agent": "speedrun-quest-guide RuneLite plugin data builder"})
        page = json.load(urllib.request.urlopen(req))["parse"]
        with open(os.path.join(out, q.replace("'", "").replace(" ", "_") + ".wiki"), "w", encoding="utf-8") as f:
            f.write(page["wikitext"]["*"])
        revisions[q] = page["revid"]
        print(q, page["revid"])
    with open(os.path.join(out, "revisions.json"), "w", encoding="utf-8") as f:
        json.dump(revisions, f, indent=1)


main()
