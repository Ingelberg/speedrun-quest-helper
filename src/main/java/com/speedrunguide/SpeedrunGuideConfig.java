package com.speedrunguide;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(SpeedrunGuideConfig.GROUP)
public interface SpeedrunGuideConfig extends Config
{
	String GROUP = "speedrunquestguide";

	@ConfigSection(
		name = "Tracking",
		description = "How the guide follows your run",
		position = 0
	)
	String tracking = "tracking";

	@ConfigSection(
		name = "Overlay",
		description = "The step overlay",
		position = 1
	)
	String overlay = "overlay";

	@ConfigSection(
		name = "Route",
		description = "Path, NPC, item and shop highlights for steps that have them",
		position = 2
	)
	String route = "route";

	@ConfigSection(
		name = "Hotkeys",
		description = "Keys to move through the steps",
		position = 3
	)
	String hotkeys = "hotkeys";

	@ConfigItem(
		keyName = "showPath",
		name = "Show path",
		description = "Draw the way to the step's NPC, item or spot on the ground",
		section = route,
		position = 0
	)
	default boolean showPath()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "pathColor",
		name = "Path color",
		description = "Color of the path and the spot it leads to",
		section = route,
		position = 1
	)
	default Color pathColor()
	{
		return new Color(255, 200, 0, 160);
	}

	@Alpha
	@ConfigItem(
		keyName = "targetColor",
		name = "Target tile color",
		description = "Color of the exact tile the path leads to, such as where to dig",
		section = route,
		position = 2
	)
	default Color targetColor()
	{
		return new Color(60, 255, 60, 230);
	}

	@ConfigItem(
		keyName = "showDigTiles",
		name = "Show dig area",
		description = "Fill the tiles where digging for the current step works. The area grows as digs are learned",
		section = route,
		position = 3
	)
	default boolean showDigTiles()
	{
		return true;
	}

	@ConfigItem(
		keyName = "logDigs",
		name = "Report digs in chat",
		description = "Say in chat whether each dig during a dig step worked, with the tile",
		section = route,
		position = 4
	)
	default boolean logDigs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightNpcs",
		name = "Highlight NPCs",
		description = "Outline the NPCs the step says to talk to",
		section = route,
		position = 5
	)
	default boolean highlightNpcs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightGroundItems",
		name = "Highlight ground items",
		description = "Outline the items the step says to pick up",
		section = route,
		position = 6
	)
	default boolean highlightGroundItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightObjects",
		name = "Highlight objects",
		description = "Outline the objects the step says to use, such as the wheat, a ladder or the hopper",
		section = route,
		position = 7
	)
	default boolean highlightObjects()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "npcColor",
		name = "Outline color",
		description = "Outline color for NPCs, ground items and objects",
		section = route,
		position = 8
	)
	default Color npcColor()
	{
		return new Color(0, 255, 255, 220);
	}

	@ConfigItem(
		keyName = "highlightShop",
		name = "Highlight shop and inventory items",
		description = "Outline the items the step says to buy in shops, and to use in your inventory",
		section = route,
		position = 9
	)
	default boolean highlightShop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "leftClickBuy",
		name = "Left-click shortcuts",
		description = "Make Buy 1 the left click on the items the step says to buy, and the step's option the left click on its NPCs (such as Trade on Fayeth)",
		section = route,
		position = 10
	)
	default boolean leftClickBuy()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoSelect",
		name = "Pick quest automatically",
		description = "On a speedrunning world, switch to the guide for the quest you are running",
		section = tracking,
		position = 0
	)
	default boolean autoSelect()
	{
		return true;
	}

	@ConfigItem(
		keyName = "followQuest",
		name = "Follow quest progress",
		description = "Move past the first step when the quest starts, back to it when the speedrun is reset, and to the end when the quest is complete",
		section = tracking,
		position = 1
	)
	default boolean followQuest()
	{
		return true;
	}

	@ConfigItem(
		keyName = "advanceOnDialogue",
		name = "Advance on dialogue",
		description = "Move to the next step when a dialogue option from it shows up",
		section = tracking,
		position = 2
	)
	default boolean advanceOnDialogue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "advanceWhenDone",
		name = "Advance when done",
		description = "Move on when a step's item is in your inventory, you dig at its spot, or you reach it (for steps that have route data)",
		section = tracking,
		position = 3
	)
	default boolean advanceWhenDone()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightDialogue",
		name = "Highlight dialogue options",
		description = "Outline the dialogue option the guide says to pick",
		section = tracking,
		position = 4
	)
	default boolean highlightDialogue()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlight color",
		description = "Outline color for the dialogue option to pick",
		section = tracking,
		position = 5
	)
	default Color highlightColor()
	{
		return new Color(0, 255, 255, 200);
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show the current step on screen",
		section = overlay,
		position = 0
	)
	default boolean showOverlay()
	{
		return true;
	}

	@Range(max = 5)
	@ConfigItem(
		keyName = "upcomingSteps",
		name = "Upcoming steps",
		description = "How many of the following steps to show under the current one",
		section = overlay,
		position = 2
	)
	default int upcomingSteps()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "showNotes",
		name = "Show notes",
		description = "Show the guide's notes for the current step",
		section = overlay,
		position = 3
	)
	default boolean showNotes()
	{
		return true;
	}

	@Range(min = 150, max = 500)
	@ConfigItem(
		keyName = "overlayWidth",
		name = "Width",
		description = "Overlay width in pixels",
		section = overlay,
		position = 4
	)
	default int overlayWidth()
	{
		return 240;
	}

	@ConfigItem(
		keyName = "nextKey",
		name = "Next step",
		description = "Mark the current step done",
		section = hotkeys,
		position = 0
	)
	default Keybind nextKey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "previousKey",
		name = "Previous step",
		description = "Go back one step",
		section = hotkeys,
		position = 1
	)
	default Keybind previousKey()
	{
		return Keybind.NOT_SET;
	}
}
