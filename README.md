# Speedrun Quest Helper

> **This plugin is vibe coded.** Claude (an AI coding assistant) wrote it with my direction and in-game testing. Cook's Assistant and X Marks the Spot have been played through and tweaked in-game, but not every line has been hand-reviewed.

A RuneLite plugin for [Quest Speedrunning](https://oldschool.runescape.wiki/w/Quest_Speedrunning). It walks you through the OSRS Wiki speedrun guide for a quest, one step at a time. For now it covers Cook's Assistant and X Marks the Spot. The other 13 speedrun quests are already converted from the wiki and will be added once their routes are checked. The game already keeps the time, so this plugin only tells you what to do next.

## Features

It is made for the in-game Quest Speedrunning mode and its trophy times, not for speedrun.com runs: the speedrun.com rules don't allow Plugin Hub plugins.

It is a separate plugin from Quest Helper, which guides normal quest completion. If you use both, Quest Helper's highlights show up alongside these, and you can turn them off in Quest Helper's settings while you speedrun.

The plugin only switches on while you are logged in to a speedrunning world. Elsewhere it has no sidebar button, no overlays and no hotkeys.

- **Step overlay**: the current step, the dialogue keys to press ("Chat: 1, 1, 3"), the guide's notes, and the next step or steps.
- **Dialogue highlights**: the option the guide says to pick is outlined in the chat menu.
- **Sidebar**: pick a quest and see its bronze to diamond trophy times. You also see the skills and items the speedrun gives you, every walkthrough step grouped by section, links to the wiki's maps, and the wiki's optimization tips. Click a step to make it the current one.
- **Follows your run**:
  - On a speedrunning world it switches to the quest you are running.
  - It moves past the first step when the quest starts and goes back to it when you reset the speedrun.
  - It jumps to the end when the quest is complete.
  - When a dialogue from one of the next two steps opens, it moves on to that step.
- **Route help**:
  - The NPCs to talk to and the items to pick up are outlined.
  - A path on the ground leads to the step's NPC, item or spot, such as each X Marks dig tile. A target outside the loaded area gets a path to the edge closest to it.
  - In shops, the items to buy are outlined and Buy 1 becomes their left click.
  - Dig steps learn which tiles work. The game accepts more than the one tile on record, so each dig is reported in chat. The dig area is filled in green: the rectangle around every tile known to work, which grows as digs that work are learned.
  - The data lives in `tools/routes.json`, with coordinates from the wiki.
- **Hotkeys** for next and previous step. They are not set by default; you can set them in the plugin settings.

## How it works

| File | What it does |
| --- | --- |
| `SpeedrunGuidePlugin` | Holds the selected guide and step, follows quest state, and handles dialogue and hotkeys |
| `Guide`, `GuideLibrary` | The guide data model and the loader for `guides.json` |
| `GuideText` | Turns guide HTML into plain text and matches dialogue options |
| `StepOverlay` | The on-screen step box |
| `DialogueOverlay` | Outlines the chat menu option to pick |
| `GuidePanel` | The sidebar |
| `RouteTracker` | Finds the step's NPCs and ground items in view, picks where to walk, and keeps the path |
| `Pathfinder` | Breadth-first search over the loaded area's collision map |
| `RouteOverlay` | Draws the path, the target tile, NPC outlines and ground item marks |
| `ItemOverlay` | Outlines items to buy in shops, items to use in the inventory, and spells such as Home Teleport |
| `DigLog` | Records whether each dig gave the step's item, and saves the good and missed tiles |
| `tools/routes.json` | Hand-checked NPCs, shop items, ground items and coordinates, merged into the guides |
| `tools/fetch.py`, `tools/convert.py` | Download the wiki guides and convert them to `guides.json` |

## Updating the guides

```
python -I tools/fetch.py wiki
python -I tools/convert.py wiki src/main/resources/com/speedrunguide/guides.json
```

## Credits

The guide text comes from the [OSRS Wiki](https://oldschool.runescape.wiki/w/Quest_Speedrunning) speedrun guides and is licensed under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).

Quest progress values, NPC and object ids and some coordinates come from [Quest Helper](https://github.com/Zoinkwiz/quest-helper) (BSD 2-Clause).
