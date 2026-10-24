"""Writes route entries for the speedrun quests into routes.json from short descriptions.

Each step entry names a wiki step by its index (its text becomes the 'match') and optionally a
Quest Helper step by its variable name, whose NPC/object ids and coordinates are copied in.

Usage: python -I build_routes.py <work dir> <routes.json> <quest file> [<quest file>...]
Each quest file is Python defining QUEST (name), PROGRESS (var dict) and STEPS (list of S(...)).
"""
import json
import os
import re
import sys
import urllib.parse
import urllib.request

WORK, ROUTES = sys.argv[1], sys.argv[2]
PAGES_FILE = os.path.join(WORK, "pages.json")
PAGES = json.load(open(PAGES_FILE, encoding="utf-8"))
FOLDERS = {
    "Vampyre Slayer": "vampyreslayer", "Ernest the Chicken": "ernestthechicken",
    "Black Knights' Fortress": "blackknightfortress", "Demon Slayer": "demonslayer",
    "Prince Ali Rescue": "princealirescue", "Below Ice Mountain": "belowicemountain",
    "Temple of the Eye": "templeoftheeye", "Dragon Slayer I": "dragonslayer",
    "A Taste of Hope": "atasteofhope", "Beneath Cursed Sands": "beneathcursedsands",
    "A Kingdom Divided": "akingdomdivided", "Sins of the Father": "sinsofthefather",
    "Dragon Slayer II": "dragonslayerii",
}
WARNINGS = []


def clean(name):
    if not name or name.startswith("http"):
        return None
    name = name.split("#")[0]
    return re.sub(r"\s*\([^)]*\)$", "", name).strip()


def page(title):
    """A wiki page's kind, coordinates and ids, fetched if not cached."""
    if title in PAGES:
        return PAGES[title]
    url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode({
        "action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
        "titles": title, "redirects": 1, "format": "json", "formatversion": 2})
    req = urllib.request.Request(url, headers={"User-Agent": "speedrun-quest-helper route builder"})
    data = json.load(urllib.request.urlopen(req, timeout=60))["query"]
    p = data["pages"][0]
    text = p["revisions"][0]["slots"]["main"]["content"] if p.get("revisions") else ""
    found = []
    for m in re.finditer(r"\{\{(?:Map|ObjectLocLine|LocLine|NPCLocLine)[^{}]*\}\}", text, re.S):
        body = m.group(0)
        plane = re.search(r"plane\s*[=:]\s*(\d)", body)
        pl = int(plane.group(1)) if plane else 0
        for x, y in re.findall(r"x\s*[:=]\s*(\d{3,5})\s*[,|]\s*y\s*[:=]\s*(\d{3,5})", body):
            found.append([int(x), int(y), pl])
        for x, y in re.findall(r"\|\s*(\d{4}),(\d{4})(?=[|,}])", body):
            found.append([int(x), int(y), pl])
    kind = re.search(r"\{\{Infobox (\w+)", text)
    ids = re.findall(r"\|\s*id\d*\s*=\s*([\d, ]+)", text)
    PAGES[title] = {"page": p.get("title", title), "kind": kind.group(1) if kind else None, "coords": found[:12],
                    "ids": sorted({int(x) for g in ids for x in re.findall(r"\d+", g)})[:20]}
    json.dump(PAGES, open(PAGES_FILE, "w", encoding="utf-8"), indent=0, ensure_ascii=False)
    return PAGES[title]


def T(x, y, plane=0, label=None):
    t = {"x": x, "y": y, "plane": plane}
    if label:
        t["label"] = label
    return t


def wiki_npc(title, label=None, coord=0):
    """npcs/npcIds/target for an NPC from its wiki page."""
    p = page(title)
    name = label or clean(p["page"]) or clean(title)
    out = {"npcs": [name]}
    if p["coords"]:
        x, y, pl = p["coords"][coord]
        out["target"] = T(x, y, pl, name)
    else:
        WARNINGS.append(f"no coordinates for {title}")
    return out


def S(index, qh=None, npc=None, **kw):
    return {"index": index, "qh": qh, "npc": npc, "kw": kw}


def build(quest, progress, steps):
    folder = FOLDERS[quest]
    data = json.load(open(os.path.join(WORK, folder + ".json"), encoding="utf-8"))
    wiki = data["wiki"]
    entries = []
    for s in steps:
        i = s["index"]
        text = wiki[i]["text"]
        match = text[:70].rstrip()
        e = {"match": match}
        # a step the wiki repeats word for word is told apart by how many came before it
        earlier = sum(1 for j in range(i) if wiki[j]["text"].startswith(match))
        if earlier:
            e["occurrence"] = earlier + 1
        if s["qh"]:
            names = s["qh"] if isinstance(s["qh"], list) else [s["qh"]]
            for name in names:
                st = data["steps"][name]
                if st["npcIds"]:
                    e.setdefault("npcIds", [])
                    e["npcIds"] += [x for x in st["npcIds"] if x not in e["npcIds"]]
                    label = clean(st["npcNames"][0]) if st["npcNames"] else None
                    if label:
                        e.setdefault("npcs", [])
                        if label not in e["npcs"]:
                            e["npcs"].append(label)
                if st["objectIds"]:
                    e.setdefault("objectIds", [])
                    e["objectIds"] += [x for x in st["objectIds"] if x not in e["objectIds"]]
                if st["point"] and "target" not in e:
                    label = (clean(st["npcNames"][0]) if st["npcNames"] else None) or \
                            (clean(st["objectNames"][0]) if st["objectNames"] else None)
                    e["target"] = T(*st["point"], label)
        if s["npc"]:
            for k, v in wiki_npc(*s["npc"] if isinstance(s["npc"], tuple) else (s["npc"],)).items():
                if k == "npcs":
                    e.setdefault("npcs", [])
                    e["npcs"] += [n for n in v if n not in e["npcs"]]
                elif k not in e:
                    e[k] = v
        for k, v in s["kw"].items():
            e[k] = v
        entries.append(e)
    return {"progress": progress, "steps": entries}


def main():
    routes = json.load(open(ROUTES, encoding="utf-8"))
    for qf in sys.argv[3:]:
        ns = {"S": S, "T": T}
        exec(open(qf, encoding="utf-8").read(), ns)
        routes[ns["QUEST"]] = build(ns["QUEST"], ns["PROGRESS"], ns["STEPS"])
        print(ns["QUEST"], len(ns["STEPS"]), "steps")
    # keep one entry per line, like the hand-written file
    out = ["{", ' "_about": ' + json.dumps(routes["_about"], ensure_ascii=False) + ","]
    quests = [q for q in routes if not q.startswith("_")]
    for qi, q in enumerate(quests):
        out.append(" " + json.dumps(q) + ": {")
        if routes[q].get("tested"):
            out.append('  "tested": true,')
        out.append('  "progress": ' + json.dumps(routes[q]["progress"]) + ",")
        out.append('  "steps": [')
        steps = routes[q]["steps"]
        for ei, e in enumerate(steps):
            comma = "," if ei < len(steps) - 1 else ""
            if "split" in e:
                head = {k: v for k, v in e.items() if k != "split"}
                out.append("   " + json.dumps(head, ensure_ascii=False)[:-1] + ', "split": [')
                for pi, part in enumerate(e["split"]):
                    out.append("    " + json.dumps(part, ensure_ascii=False) + ("," if pi < len(e["split"]) - 1 else ""))
                out.append("   ]}" + comma)
            else:
                out.append("   " + json.dumps(e, ensure_ascii=False) + comma)
        out.append("  ]")
        out.append(" }" + ("," if qi < len(quests) - 1 else ""))
    out.append("}")
    open(ROUTES, "w", encoding="utf-8").write("\n".join(out) + "\n")
    for w in WARNINGS:
        print("WARNING", w)


main()
