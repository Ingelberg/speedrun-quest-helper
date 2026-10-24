"""Pulls steps out of Quest Helper quest sources and lines them up with the wiki speedrun steps.

Usage: python -I qh_extract.py <qh quests dir> <wiki dir> <work dir> <convert.py dir>
Writes <work dir>/<quest>.txt reports (for reading) and <quest>.json (for drafting routes).
"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

QH, WIKI, WORK, CONVERT = sys.argv[1:5]
sys.path.insert(0, CONVERT)
import convert  # noqa: E402

QUESTS = {
    "Vampyre Slayer": ("vampyreslayer", {"varp": 178}),
    "Ernest the Chicken": ("ernestthechicken", {"varp": 32}),
    "Black Knights' Fortress": ("blackknightfortress", {"varp": 130}),
    "Demon Slayer": ("demonslayer", {"varbit": 2561}),
    "Prince Ali Rescue": ("princealirescue", {"varp": 273}),
    "Below Ice Mountain": ("belowicemountain", {"varbit": 12063}),
    "Temple of the Eye": ("templeoftheeye", {"varbit": 13738}),
    "Dragon Slayer I": ("dragonslayer", {"varp": 176}),
    "A Taste of Hope": ("atasteofhope", {"varbit": 6396}),
    "Beneath Cursed Sands": ("beneathcursedsands", {"varbit": 13841}),
    "A Kingdom Divided": ("akingdomdivided", {"varbit": 12296}),
    "Sins of the Father": ("sinsofthefather", {"varbit": 7255}),
    "Dragon Slayer II": ("dragonslayerii", {"varbit": 6104}),
}

IDS = {}
for kind in ("NpcID", "ObjectID", "ItemID"):
    with open(os.path.join(WORK, kind + ".txt")) as f:
        IDS[kind] = {name: int(v) for name, v in (line.split() for line in f if line.strip())}

NAME_CACHE_FILE = os.path.join(WORK, "names.json")
NAMES = json.load(open(NAME_CACHE_FILE)) if os.path.exists(NAME_CACHE_FILE) else {}


def wiki_name(kind, ident):
    """Page title for an npc/object/item id via the wiki's Special:Lookup redirect."""
    key = f"{kind}:{ident}"
    if key in NAMES:
        return NAMES[key]
    url = "https://oldschool.runescape.wiki/w/Special:Lookup?" + urllib.parse.urlencode({"type": kind, "id": ident})
    req = urllib.request.Request(url, headers={"User-Agent": "speedrun-quest-guide route builder"})
    try:
        final = urllib.request.urlopen(req, timeout=20).geturl()
        title = urllib.parse.unquote(final.rsplit("/w/", 1)[-1]).replace("_", " ")
        if "Special:" in title:
            title = None
    except Exception:
        title = None
    NAMES[key] = title
    time.sleep(0.15)
    return title


def balanced(text, start):
    """Index just past the parenthesis group opening at text[start] == '('."""
    depth, i, in_str = 0, start, False
    while i < len(text):
        c = text[i]
        if in_str:
            if c == "\\":
                i += 1
            elif c == '"':
                in_str = False
        elif c == '"':
            in_str = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return -1


STEP_TYPES = r"(NpcStep|ObjectStep|ItemStep|DetailedQuestStep|DigStep|TileStep|MultiNpcStep|EmoteStep|NpcEmoteStep|WidgetStep|PuzzleWrapperStep)"


def parse_steps(src):
    steps = {}
    for m in re.finditer(r"(?:\bvar\s+|\b\w+\s+)?(\w+)\s*=\s*(?:new\s+" + STEP_TYPES + r"|(DigStep)\.withCustomSpadeRequirement)\s*\(", src):
        name = m.group(1)
        typ = m.group(2) or "DigStep"
        end = balanced(src, m.end() - 1)
        args = src[m.end():end - 1]
        wp = re.search(r"new WorldPoint\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)", args)
        text = re.search(r'"((?:[^"\\]|\\.)*)"', args)
        steps[name] = {
            "type": typ,
            "npcs": [n for n in re.findall(r"NpcID\.(\w+)", args)],
            "objects": [n for n in re.findall(r"ObjectID\.(\w+)", args)],
            "items": [n for n in re.findall(r"ItemID\.(\w+)", args)],
            "point": [int(wp.group(1)), int(wp.group(2)), int(wp.group(3))] if wp else None,
            "text": text.group(1) if text else "",
            "dialog": [],
        }
    for m in re.finditer(r"(\w+)\.addDialogSteps?\(", src):
        if m.group(1) in steps:
            end = balanced(src, m.end() - 1)
            steps[m.group(1)]["dialog"] += re.findall(r'"((?:[^"\\]|\\.)*)"', src[m.end():end - 1])
    # requirement names: var = new ItemRequirement("Name", ItemID.X ...)
    reqs = {}
    for m in re.finditer(r"(\w+)\s*=\s*new ItemRequirement\(\s*\"([^\"]+)\"\s*,\s*(?:ItemID\.(\w+))?", src):
        reqs[m.group(1)] = (m.group(2), m.group(3))
    return steps, reqs


def parse_conditionals(src):
    cond = {}
    for m in re.finditer(r"(\w+)\s*=\s*new ConditionalStep\(\s*this\s*,\s*(\w+)", src):
        cond.setdefault(m.group(1), []).append(m.group(2))
    for m in re.finditer(r"(\w+)\.addStep\(", src):
        if m.group(1) not in cond:
            continue
        end = balanced(src, m.end() - 1)
        args = src[m.end():end - 1]
        last = args.rsplit(",", 1)[-1].strip()
        if re.fullmatch(r"\w+", last):
            cond[m.group(1)].append(last)
    return cond


def flatten(name, cond, steps, seen=None):
    seen = seen or set()
    if name in seen:
        return []
    seen.add(name)
    if name in steps:
        return [name]
    out = []
    for child in cond.get(name, []):
        out += flatten(child, cond, steps, seen)
    return out


def parse_progress(src, cond, steps):
    prog = {}
    for m in re.finditer(r"steps\.put\(\s*(\d+)\s*,\s*(\w+)\s*\)", src):
        prog.setdefault(int(m.group(1)), [])
        for s in flatten(m.group(2), cond, steps):
            if s not in prog[int(m.group(1))]:
                prog[int(m.group(1))].append(s)
    return prog


def wiki_steps(quest):
    """The converted wiki steps, each with the wiki link targets it mentions."""
    captured = []
    original = convert.link

    def link(body):
        parts = convert.split_args(body)
        target = parts[0].strip()
        if not target.lower().startswith(("file:", "image:", "category:", ":file:")):
            captured.append(target.split("#")[0].replace("_", " "))
        return original(body)

    convert.link = link
    text = open(os.path.join(WIKI, quest.replace("'", "").replace(" ", "_") + ".wiki"), encoding="utf-8").read()
    lines = text[text.index("==Walkthrough=="):].split("\n")
    # walk the same way convert.walkthrough does, but per line, to pin links to steps
    sections, _ = convert.walkthrough(text)
    convert.link = original
    flat = [(sec["title"], st) for sec in sections for st in sec["steps"]]
    # second pass: links per step text, by re-expanding each list line
    per_step = []
    for title, st in flat:
        per_step.append({"section": title, "text": convert.plain(st["text"]), "chat": [c["text"] for c in st.get("chat", [])],
                         "notes": [convert.plain(n) for n in st.get("notes", [])], "links": []})
    # map list lines to steps in order
    i = -1
    in_walk = True
    for line in lines[1:]:
        s = line.strip()
        if re.match(r"^==[^=]", s) or "Speedrun complete" in s:
            break
        lm = re.match(r"^(?:\{\{Checklist\|)?\s*([*#]+)\s*(.*)$", s)
        if not lm:
            continue
        captured.clear()
        convert.link = link
        body = convert.expand(lm.group(2), [])
        convert.link = original
        depth = len(lm.group(1))
        is_note = re.match(r"^(<b>)?\s*Note\b", body) is not None
        if depth == 1 and not is_note and body:
            i += 1
        if 0 <= i < len(per_step):
            per_step[i]["links"] += [c for c in captured if c not in per_step[i]["links"]]
    return per_step


def main():
    report_all = {}
    for quest, (folder, var) in QUESTS.items():
        files = [os.path.join(QH, folder, f) for f in sorted(os.listdir(os.path.join(QH, folder))) if f.endswith(".java")]
        src = "\n".join(open(f, encoding="utf-8").read() for f in files)
        steps, reqs = parse_steps(src)
        cond = parse_conditionals(src)
        prog = parse_progress(src, cond, steps)
        for st in steps.values():
            st["npcNames"] = [wiki_name("npc", IDS["NpcID"][n]) for n in st["npcs"] if n in IDS["NpcID"]]
            st["npcIds"] = [IDS["NpcID"][n] for n in st["npcs"] if n in IDS["NpcID"]]
            st["objectNames"] = [wiki_name("object", IDS["ObjectID"][n]) for n in st["objects"] if n in IDS["ObjectID"]]
            st["objectIds"] = [IDS["ObjectID"][n] for n in st["objects"] if n in IDS["ObjectID"]]
        json.dump(NAMES, open(NAME_CACHE_FILE, "w"), indent=0)
        wsteps = wiki_steps(quest)
        out = {"quest": quest, "var": var, "progress": prog, "steps": steps, "wiki": wsteps}
        json.dump(out, open(os.path.join(WORK, folder + ".json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
        with open(os.path.join(WORK, folder + ".txt"), "w", encoding="utf-8") as f:
            f.write(f"### {quest} {var}\n\n## Quest Helper progress -> steps\n")
            for v in sorted(prog):
                f.write(f"{v}: {', '.join(prog[v])}\n")
            f.write("\n## Quest Helper steps\n")
            for name, st in steps.items():
                f.write(f"{name} [{st['type']}] npc={st['npcNames'] or ''}{st['npcIds'] or ''} obj={st['objectNames'] or ''}{st['objectIds'] or ''} "
                        f"at={st['point']} :: {st['text'][:110]} {('DIALOG ' + str(st['dialog'])) if st['dialog'] else ''}\n")
            f.write("\n## Wiki steps\n")
            for i, w in enumerate(wsteps):
                f.write(f"{i} [{w['section']}] {w['text'][:170]} | links={w['links']} | chat={w['chat']}\n")
        report_all[quest] = (len(steps), len(prog), len(wsteps))
        print(quest, report_all[quest], flush=True)


main()
