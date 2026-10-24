"""Turns the OSRS Wiki speedrun guide wikitext into guides.json for the plugin.

Usage: python -I convert.py <wiki dir> <out json>
"""
import html
import json
import os
import re
import sys

QUESTS = ["X Marks the Spot", "Cook's Assistant", "Vampyre Slayer", "Ernest the Chicken",
          "Black Knights' Fortress", "Demon Slayer", "Prince Ali Rescue", "Below Ice Mountain",
          "Temple of the Eye", "Dragon Slayer I", "A Taste of Hope", "Beneath Cursed Sands",
          "A Kingdom Divided", "Sins of the Father", "Dragon Slayer II"]

ORDINAL = {1: "1st", 2: "2nd", 3: "3rd"}


def split_args(body):
    """Splits a template body on top-level pipes."""
    args, depth, cur, i = [], 0, "", 0
    while i < len(body):
        two = body[i:i + 2]
        if two in ("{{", "[["):
            depth += 1
            cur += two
            i += 2
            continue
        if two in ("}}", "]]"):
            depth -= 1
            cur += two
            i += 2
            continue
        if body[i] == "|" and depth == 0:
            args.append(cur)
            cur = ""
        else:
            cur += body[i]
        i += 1
    args.append(cur)
    return args


def find_close(text, start, open_tok, close_tok):
    depth, i = 0, start
    while i < len(text):
        if text.startswith(open_tok, i):
            depth += 1
            i += 2
        elif text.startswith(close_tok, i):
            depth -= 1
            i += 2
            if depth == 0:
                return i
        else:
            i += 1
    return -1


def parse_chat(args):
    out = []
    for a in args:
        a = a.strip()
        m = re.match(r"^([0-9])\s*(.*)$", a) or re.match(r"^([A-Z])\s+(.*)$", a)
        if m:
            key, text = m.group(1), m.group(2)
        else:
            key, text = "", a
        text = plain(expand(text, []))
        if text:
            out.append({"key": key, "text": text})
    return out


def floor(args):
    for a in args:
        if a.startswith("uk="):
            n = int(a[3:])
            return "ground floor" if n == 0 else ORDINAL.get(n, str(n) + "th") + " floor"
    return "floor"


def template(body, chat):
    args = split_args(body)
    name = args[0].strip()
    lname = name.lower()
    if lname == "chat option":
        chat.extend(parse_chat(args[1:]))
        return ""
    if lname == "floornumber":
        return floor(args[1:])
    if lname == "fairycode":
        return "'''" + args[1].strip() + "'''"
    if lname == "mes":
        return "“" + raw(args[1], chat) + "”"
    if lname == "scp":
        return args[1].strip() + " " + args[2].strip()
    if lname == "checklist":
        return raw("|".join(args[1:]), chat)
    return ""


def link(body):
    parts = split_args(body)
    target = parts[0].strip()
    if target.lower().startswith(("file:", "image:", "category:")):
        return ""
    if target.startswith(":File:"):
        return parts[-1] if len(parts) > 1 else ""
    return parts[-1] if len(parts) > 1 else target


def expand(text, chat):
    """Wikitext -> small HTML (b/i only), collecting Chat option templates into chat."""
    return render(raw(text, chat))


def raw(text, chat):
    """Expands links and templates, leaving bold/italic quote markup in place."""
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = re.sub(r"<ref[^>]*/>|<ref[^>]*>.*?</ref>", "", text, flags=re.S)
    out, i = "", 0
    while i < len(text):
        if text.startswith("{{", i):
            end = find_close(text, i, "{{", "}}")
            if end < 0:
                i += 2
                continue
            out += template(text[i + 2:end - 2], chat)
            i = end
        elif text.startswith("[[", i):
            end = find_close(text, i, "[[", "]]")
            if end < 0:
                i += 2
                continue
            out += link(text[i + 2:end - 2])
            i = end
        elif text.startswith("}}", i):
            i += 2  # closes a Checklist opened on an earlier line
        else:
            out += text[i]
            i += 1
    return out


def render(out):
    out = re.sub(r"\[https?://\S+\s+([^\]]+)\]", r"\1", out)
    out = re.sub(r"<br\s*/?>", " ", out)
    out = re.sub(r"<(?!/?[bi]>)[^>]+>", "", out)
    out = html.escape(html.unescape(out), quote=False)
    out = re.sub(r"'''(.*?)'''", r"<b>\1</b>", out)
    out = re.sub(r"''(.*?)''", r"<i>\1</i>", out)
    out = out.replace("'''", "").replace("''", "")
    out = re.sub(r"\s+", " ", out).strip()
    out = re.sub(r"\s+([.,;:])", r"\1", out)
    out = re.sub(r"<b>\s*</b>|<i>\s*</i>", "", out)
    return out.strip()


def plain(s):
    return html.unescape(re.sub(r"<[^>]+>", "", s)).strip()


def images(line):
    found = []
    for m in re.finditer(r"\[\[(?:File|Image):", line):
        end = find_close(line, m.start(), "[[", "]]")
        if end < 0:
            continue
        parts = split_args(line[m.start() + 2:end - 2])
        name = parts[0].split(":", 1)[1].strip()
        caption = ""
        for p in parts[1:]:
            p = p.strip()
            if p and not re.match(r"^(thumb|frame|frameless|right|left|center|none|\d+px|link=.*|upright.*)$", p):
                caption = plain(expand(p, []))
        if not re.search(r"icon|marker", name, re.I):
            found.append({"file": name, "caption": caption})
    return found


def details(text):
    start = text.find("{{Quest details")
    end = find_close(text, start, "{{", "}}")
    fields = {}
    for arg in split_args(text[start + 2:end - 2])[1:]:
        if "=" in arg:
            k, v = arg.split("=", 1)
            fields[k.strip()] = v.strip()
    return fields


def bullets(value):
    """Top-level list items of a details field; ** items are folded under their parent."""
    items = []
    for line in value.split("\n"):
        m = re.match(r"^\s*(\*+)\s*(.*)$", line)
        if not m:
            continue
        text = expand(m.group(2), [])
        if not text:
            continue
        if len(m.group(1)) > 1 and items:
            items.append("• " + text)
        else:
            items.append(text)
    return items


def trophies(text):
    m = re.search(r"\{\{Quest Speedrunning trophy times(.*?)\}\}", text, re.S)
    out = {}
    for k, v in re.findall(r"\|\s*(\w+)\s*=\s*([0-9:]+)", m.group(1)):
        out[k.lower()] = v
    return out


def walkthrough(text):
    lines = text[text.index("==Walkthrough=="):].split("\n")[1:]
    sections, tips = [], []
    current = None
    in_table = 0
    done = False
    gallery = False

    def section(title):
        nonlocal current
        current = {"title": title, "steps": [], "images": []}
        sections.append(current)

    for line in lines:
        s = line.strip()
        if done:
            tips.append(line)
            continue
        if re.match(r"^==[^=]", s):
            break
        if "Speedrun complete" in s:
            before = s.split("'''Speedrun complete")[0]
            if current is not None:
                current["images"].extend(images(before))
            done = True
            continue
        if s.startswith("{|"):
            in_table += 1
            continue
        if in_table:
            if s.startswith("|}"):
                in_table -= 1
            continue
        if s.startswith("<gallery"):
            gallery = True
            continue
        if gallery:
            if s.startswith("</gallery"):
                gallery = False
            elif "|" in s:
                if current is None:
                    section("Speedrun")
                f, cap = s.split("|", 1)
                current["images"].append({"file": f.strip(), "caption": plain(expand(cap, []))})
            continue
        m = re.match(r"^(=+)\s*(.*?)\s*=+$", s)
        if m:
            section(m.group(2))
            continue
        if not s or s in ("{{Checklist|", "}}"):
            continue
        if current is None:
            section("Speedrun")
        current["images"].extend(images(s))
        m = re.match(r"^(?:\{\{Checklist\|)?\s*([*#]+)\s*(.*)$", s)
        if not m:
            text_ = expand(s, [])
            if text_:
                current["steps"].append({"text": text_, "notes": [], "chat": [], "note": True})
            continue
        chat = []
        body = expand(m.group(2), chat)
        if not body and not chat:
            continue
        depth = len(m.group(1))
        steps = current["steps"]
        is_note = re.match(r"^(<b>)?\s*Note\b", body) is not None
        if (depth > 1 or is_note) and steps:
            steps[-1]["notes"].append(body)
            steps[-1]["chat"].extend(chat)
        else:
            steps.append({"text": body, "notes": [], "chat": chat, "note": False})

    # merge sections that only hold images into the next, drop empty ones
    sections = [sec for sec in sections if sec["steps"] or sec["images"]]
    for sec in sections:
        for st in sec["steps"]:
            if not st["notes"]:
                del st["notes"]
            if not st["chat"]:
                del st["chat"]
            if not st["note"]:
                del st["note"]
        if not sec["images"]:
            del sec["images"]
    return sections, tips_from(tips)


def tips_from(lines):
    out = []
    heading = None
    for line in lines:
        s = line.strip()
        if re.match(r"^==\s*Rewards", s):
            break
        m = re.match(r"^(=+)\s*(.*?)\s*=+$", s)
        if m:
            if m.group(2).lower() != "optimizations":
                heading = m.group(2)
            continue
        if not s or s.startswith(("[[File:", "{|", "|", "!", "<gallery", "</gallery")):
            continue
        lm = re.match(r"^[*#]+\s*(.*)$", s)
        text = expand(lm.group(1) if lm else s, [])
        if not text:
            continue
        if lm:
            text = "• " + text
        if out and out[-1]["heading"] == heading:
            out[-1]["paragraphs"].append(text)
        else:
            out.append({"heading": heading, "paragraphs": [text]})
    return out


def start_step(sections):
    """Index (over all steps) of the step that starts the quest, skipping set-up sections and notes."""
    i = 0
    for sec in sections:
        setup = re.search(r"set-?up|before", sec["title"], re.I)
        for st in sec["steps"]:
            if not setup and not st.get("note") and not re.match(r"^(<b>)?Before\b", st["text"]):
                return i
            i += 1
    return 0


ROUTE_FIELDS = ("npcs", "shop", "ground", "target", "done", "progress", "via", "digArea", "use", "leftClick", "objects", "spot", "useOption", "pick", "widget", "pathWithin")


def apply_routes(name, sections, routes):
    """Merges the hand-checked extras from routes.json into the wiki steps."""
    for entry in routes.get(name, {}).get("steps", []):
        for sec in sections:
            found = next((i for i, st in enumerate(sec["steps"]) if plain(st["text"]).startswith(entry["match"])), None)
            if found is not None:
                break
        else:
            raise SystemExit(f"routes.json: no {name} step starts with {entry['match']!r}")
        step = sec["steps"][found]
        if "split" in entry:
            parts = []
            for part in entry["split"]:
                new = {"text": part["text"]}
                new.update({k: part[k] for k in ROUTE_FIELDS if k in part})
                parts.append(new)
            # the wiki step's own dialogue and notes stay with its first part
            for k in ("chat", "notes"):
                if k in step:
                    parts[0][k] = step[k]
            sec["steps"][found:found + 1] = parts
        else:
            step.update({k: entry[k] for k in ROUTE_FIELDS if k in entry})


def main():
    src, dst = sys.argv[1], sys.argv[2]
    routes = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "routes.json"), encoding="utf-8"))
    revs = json.load(open(os.path.join(src, "revisions.json"), encoding="utf-8"))
    quests = []
    for q in QUESTS:
        fname = q.replace("'", "").replace(" ", "_") + ".wiki"
        text = open(os.path.join(src, fname), encoding="utf-8").read()
        d = details(text)
        sections, tips = walkthrough(text)
        apply_routes(q, sections, routes)
        quest = {
            "name": q,
            "page": q.replace(" ", "_") + "/Speedrun_guide",
            "revision": revs[q],
            "start": expand(d.get("start", ""), []),
            "skills": bullets(d.get("requirements", "")),
            "items": bullets(d.get("items", "")),
            "trophies": trophies(text),
            "sections": sections,
        }
        quest["startStep"] = start_step(sections)
        if "progress" in routes.get(q, {}):
            quest["progress"] = routes[q]["progress"]
        if d.get("kills") and d["kills"].strip().lower() != "none":
            kills = d["kills"]
            if "*" in kills:
                kills = chr(10).join("*" + k for k in kills.split("*")[1:] if k.strip())
                quest["kills"] = bullets(kills)
            else:
                quest["kills"] = [expand(kills, [])]
        if tips:
            quest["tips"] = tips
        quests.append(quest)
        n = sum(len(s["steps"]) for s in sections)
        print(f"{q}: {len(sections)} sections, {n} steps, {len(tips)} tip groups")
    with open(dst, "w", encoding="utf-8") as f:
        json.dump({"source": "https://oldschool.runescape.wiki", "license": "CC BY-NC-SA 3.0",
                   "quests": quests}, f, ensure_ascii=False, indent=1)


main()
