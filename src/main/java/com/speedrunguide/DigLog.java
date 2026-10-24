package com.speedrunguide;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;

/**
 * Learns which tiles a dig step accepts. The game takes more than the one tile the wiki and
 * RuneLite's clue data give, and nothing documents how much, so each dig is recorded: if the
 * step's item arrives soon after, the tile was good, otherwise it was a miss. Results are saved
 * per item id (the item that dig step waits for) and shared between characters. Misses are kept
 * for the chat report only; the area shown is the rectangle around the good tiles.
 * <p>
 * Only used on the client thread.
 */
@Singleton
class DigLog
{
	private static final String KEY = "digTiles";
	private static final Type TYPE = new TypeToken<Map<Integer, Tiles>>()
	{
	}.getType();
	/** Ticks to wait for the item after the dig animation starts. */
	static final int RESULT_TICKS = 4;

	@Data
	static class Tiles
	{
		private Set<String> good = new LinkedHashSet<>();
		private Set<String> missed = new LinkedHashSet<>();
	}

	enum Result
	{
		GOOD,
		MISSED,
	}

	/** Where the learned tiles are saved: one JSON string. */
	interface Store
	{
		@Nullable
		String load();

		void save(String json);
	}

	private final Store store;
	private final Gson gson;
	private Map<Integer, Tiles> tiles;

	/** The dig waiting for its result; null when none is. */
	@Nullable
	private WorldPoint pendingTile;
	private int pendingItem;
	private int pendingTicks;
	@Nullable
	private WorldPoint lastTile;

	@Inject
	DigLog(ConfigManager configManager, Gson gson)
	{
		this(new Store()
		{
			@Override
			public String load()
			{
				return configManager.getConfiguration(SpeedrunGuideConfig.GROUP, KEY);
			}

			@Override
			public void save(String json)
			{
				configManager.setConfiguration(SpeedrunGuideConfig.GROUP, KEY, json);
			}
		}, gson);
	}

	DigLog(Store store, Gson gson)
	{
		this.store = store;
		this.gson = gson;
	}

	/** A dig started on a tile during a step that waits for this item. */
	void dug(WorldPoint tile, int itemId)
	{
		pendingTile = tile;
		pendingItem = itemId;
		pendingTicks = 0;
	}

	/**
	 * Called every tick with whether the awaited item is now in the inventory.
	 *
	 * @return the result once known, with the tile in {@link #lastTile()}; null while waiting or idle
	 */
	@Nullable
	Result tick(boolean haveItem)
	{
		if (pendingTile == null)
		{
			return null;
		}
		Result result;
		if (haveItem)
		{
			result = Result.GOOD;
		}
		else if (++pendingTicks >= RESULT_TICKS)
		{
			result = Result.MISSED;
		}
		else
		{
			return null;
		}

		Tiles t = tilesFor(pendingItem);
		String key = key(pendingTile);
		if (result == Result.GOOD)
		{
			t.getGood().add(key);
			t.getMissed().remove(key);
		}
		else if (!t.getGood().contains(key))
		{
			// One good dig outweighs misses, which can come from digging a tick too early
			t.getMissed().add(key);
		}
		lastTile = pendingTile;
		pendingTile = null;
		save();
		return result;
	}

	@Nullable
	WorldPoint lastTile()
	{
		return lastTile;
	}

	int pendingItem()
	{
		return pendingTile == null ? 0 : pendingItem;
	}

	Tiles tilesFor(int itemId)
	{
		load();
		return tiles.computeIfAbsent(itemId, k -> new Tiles());
	}

	/**
	 * Where digging works for a step: the rectangle spanned by the route's known tiles and the
	 * tiles learned in game, in world x and y on the step target's floor. Digs seem to work in
	 * rectangles, so finding the corners is enough to fill in the rest.
	 *
	 * @return null if the step isn't a dig or nothing is known yet
	 */
	@Nullable
	Rectangle area(Guide.Step step)
	{
		if (step.getTarget() == null)
		{
			return null;
		}
		int plane = step.getTarget().getPlane();
		List<Point> known = new ArrayList<>();
		for (int[] xy : step.getDigArea())
		{
			known.add(new Point(xy[0], xy[1]));
		}
		if (step.getDone() != null && step.getDone().getHaveId() > 0)
		{
			for (String key : tilesFor(step.getDone().getHaveId()).getGood())
			{
				WorldPoint p = parse(key);
				if (p != null && p.getPlane() == plane)
				{
					known.add(new Point(p.getX(), p.getY()));
				}
			}
		}
		return bounds(known);
	}

	@Nullable
	static Rectangle bounds(List<Point> points)
	{
		Rectangle r = null;
		for (Point p : points)
		{
			if (r == null)
			{
				r = new Rectangle(p.x, p.y, 1, 1);
			}
			else
			{
				r.add(new Rectangle(p.x, p.y, 1, 1));
			}
		}
		return r;
	}

	static String key(WorldPoint p)
	{
		return p.getX() + "," + p.getY() + "," + p.getPlane();
	}

	@Nullable
	static WorldPoint parse(String key)
	{
		String[] parts = key.split(",");
		if (parts.length != 3)
		{
			return null;
		}
		try
		{
			return new WorldPoint(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private void load()
	{
		if (tiles != null)
		{
			return;
		}
		String json = store.load();
		Map<Integer, Tiles> loaded = null;
		if (json != null)
		{
			try
			{
				loaded = gson.fromJson(json, TYPE);
			}
			catch (RuntimeException e)
			{
				// A broken save only loses what was learned
			}
		}
		tiles = loaded != null ? loaded : new HashMap<>();
	}

	private void save()
	{
		store.save(gson.toJson(tiles, TYPE));
	}
}
