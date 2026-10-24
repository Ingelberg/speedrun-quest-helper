package com.speedrunguide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;

/**
 * One quest's speedrun guide, as converted from the OSRS Wiki by {@code tools/convert.py}.
 * Text fields hold a tiny HTML subset (b and i tags, escaped entities).
 */
@Getter
public class Guide
{
	static final String WIKI = "https://oldschool.runescape.wiki/w/";

	private String name;
	/** Wiki page title, such as {@code Cook's_Assistant/Speedrun_guide}. */
	private String page;
	private long revision;
	private String start;
	private List<String> skills = Collections.emptyList();
	private List<String> items = Collections.emptyList();
	private List<String> kills = Collections.emptyList();
	/** Trophy tier (bronze, silver, gold, platinum, diamond) to time, such as {@code 3:30}. */
	private Map<String, String> trophies = Collections.emptyMap();
	private List<Section> sections = Collections.emptyList();
	private List<Tip> tips = Collections.emptyList();
	/** Index into {@link #steps()} of the step that starts the quest; earlier steps are set-up. */
	private int startStep;
	/** The variable holding the quest's progress, if the route has steps tied to it. */
	@Nullable
	private Progress progress;

	@Getter(AccessLevel.NONE)
	private transient List<Step> steps;
	@Getter(AccessLevel.NONE)
	private transient Quest quest;
	@Getter(AccessLevel.NONE)
	private transient boolean questLooked;

	@Data
	public static class Section
	{
		private String title;
		private List<Step> steps = Collections.emptyList();
		private List<Image> images = Collections.emptyList();
	}

	@Data
	public static class Step
	{
		private String text;
		private List<String> notes = Collections.emptyList();
		private List<ChatOption> chat = Collections.emptyList();
		/** A remark in the walkthrough rather than something to do. */
		private boolean note;
		/** NPCs to talk to, outlined in the world. */
		private List<String> npcs = Collections.emptyList();
		/** NPC ids to outline as well, for NPCs whose name is shared with others. */
		private List<Integer> npcIds = Collections.emptyList();
		/** Items to buy, outlined in shops with Buy 1 as their left click. */
		private List<String> shop = Collections.emptyList();
		/** Items to pick up, outlined on the ground. */
		private List<String> ground = Collections.emptyList();
		/** Where the path leads when no matching NPC or ground item is in view. */
		@Nullable
		private Target target;
		/** When the step is finished, so the guide can move on by itself. */
		@Nullable
		private Done done;
		/** Quest progress values at which this is the current step. */
		private List<Integer> progress = Collections.emptyList();
		/** Waypoints the path goes through, in order, before the target. */
		private List<Target> via = Collections.emptyList();
		/** Tiles (x, y on the target's plane) where digging is known to work. */
		private List<int[]> digArea = Collections.emptyList();
		/** Inventory items to use, outlined in the inventory. */
		private List<String> use = Collections.emptyList();
		/** An option made the left click on the step's NPCs, such as Trade. */
		@Nullable
		private String leftClick;
		/** Objects to outline when near the target or a waypoint, such as the wheat or a ladder. */
		private List<String> objects = Collections.emptyList();
		/** Object ids to outline as well (Quest Helper's), for objects with common names such as Door. */
		private List<Integer> objectIds = Collections.emptyList();
		/** A tile filled and labelled on its own, such as where to wait for a wandering NPC. */
		@Nullable
		private Target spot;
		/** A right-click option on the {@link #use} items to color, such as Empty on the compost. */
		@Nullable
		private String useOption;
		/** "south" to only outline (and walk to) the southernmost of the {@link #ground} items and {@link #objects}. */
		@Nullable
		private String pick;
		/** An interface component to outline, such as the Home Teleport spell; 0 means none. */
		private int widget;
		/**
		 * The step arrives somewhere, such as a teleport landing near the target: until within this
		 * many tiles of it, the path and {@link #use} items are hidden and the {@link #widget} is
		 * outlined; after, the other way round. 0 means always arrived.
		 */
		private int pathWithin;
	}

	/** A varbit or varp, as Quest Helper reads it for the quest's progress. */
	@Data
	public static class Progress
	{
		@Nullable
		private Integer varbit;
		@Nullable
		private Integer varp;
	}

	@Data
	public static class Done
	{
		/** An item that is in the inventory once the step is done. */
		@Nullable
		private String have;
		/** An exact item id that is in the inventory once the step is done; 0 means not used. */
		private int haveId;
		/** Getting within this many tiles of the target finishes the step; 0 means not used. */
		private int arrive;
		/** An item that has left the inventory once the step is done, such as grain put in a hopper. */
		@Nullable
		private String lack;
		/** A varbit that reaches {@link #value} once the step is done. */
		@Nullable
		private Integer varbit;
		private int value;
	}

	@Data
	public static class Target
	{
		private int x;
		private int y;
		private int plane;
		private String label;

		WorldPoint worldPoint()
		{
			return new WorldPoint(x, y, plane);
		}
	}

	@Data
	public static class ChatOption
	{
		/** The key to press: a digit for chat options, a letter for some teleport menus, or empty. */
		private String key;
		private String text;
	}

	@Data
	public static class Image
	{
		private String file;
		private String caption;

		String url()
		{
			return WIKI + "File:" + file.replace(' ', '_');
		}
	}

	@Data
	public static class Tip
	{
		@Nullable
		private String heading;
		private List<String> paragraphs = Collections.emptyList();
	}

	/** Every step in order, across sections. */
	List<Step> steps()
	{
		if (steps == null)
		{
			List<Step> all = new ArrayList<>();
			sections.forEach(s -> all.addAll(s.getSteps()));
			steps = Collections.unmodifiableList(all);
		}
		return steps;
	}

	/** Whether an NPC is one the step asks for, by name or id. */
	static boolean wantsNpc(Step step, @Nullable String name, int id)
	{
		return step.getNpcIds().contains(id) || GuideText.matchesName(step.getNpcs(), name);
	}

	/** Whether an object is one the step asks for, by name or id. */
	static boolean wantsObject(Step step, @Nullable String name, int id)
	{
		return step.getObjectIds().contains(id) || GuideText.matchesName(step.getObjects(), name);
	}

	@Nullable
	Step step(int index)
	{
		List<Step> all = steps();
		return index >= 0 && index < all.size() ? all.get(index) : null;
	}

	/** The step that is current when the quest progress has this value; -1 if no step is tied to it. */
	int stepForProgress(int value)
	{
		List<Step> all = steps();
		for (int i = 0; i < all.size(); i++)
		{
			if (all.get(i).getProgress().contains(value))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * The steps a progress value allows: from its own step up to (not including) the next step tied
	 * to a progress value. The first one also allows the set-up steps before it.
	 *
	 * @return {from, to}
	 */
	int[] progressRange(int stepIndex)
	{
		List<Step> all = steps();
		boolean first = true;
		for (int i = 0; i < stepIndex; i++)
		{
			if (!all.get(i).getProgress().isEmpty())
			{
				first = false;
				break;
			}
		}
		int to = all.size();
		for (int i = stepIndex + 1; i < all.size(); i++)
		{
			if (!all.get(i).getProgress().isEmpty())
			{
				to = i;
				break;
			}
		}
		return new int[]{first ? 0 : stepIndex, to};
	}

	String url()
	{
		return WIKI + page;
	}

	/** The RuneLite quest this guide is for, used to follow quest progress; null if it can't be matched. */
	@Nullable
	Quest quest()
	{
		if (!questLooked)
		{
			questLooked = true;
			String key = GuideText.normalize(name);
			for (Quest q : Quest.values())
			{
				if (GuideText.normalize(q.getName()).equals(key))
				{
					quest = q;
					break;
				}
			}
		}
		return quest;
	}
}
