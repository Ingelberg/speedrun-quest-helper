package com.speedrunguide;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;

/**
 * Works out, once per tick, what the current step points at: the NPCs to talk to, ground items
 * to pick up and objects to use that are in view, where to walk, and the path there. Runs and is read on the
 * client thread.
 */
@Singleton
class RouteTracker
{
	@Value
	static class GroundItem
	{
		Tile tile;
		TileItem item;
	}

	/** Tiles from a waypoint that count as being there. */
	private static final int WAYPOINT_REACH = 2;
	/** How far around you ground items and objects are looked for. */
	private static final int SCAN_RADIUS = 20;
	/** Objects count when this close to the target or a waypoint, so a whole wheat field or every ladder isn't outlined. */
	private static final int OBJECT_NEAR = 6;

	private final Client client;
	private final ItemManager itemManager;
	/** Object names by id; objects that change with varbits (impostors) aren't cached. */
	private final Map<Integer, String> objectNames = new HashMap<>();
	/** Whether a wall object id is a door or gate, by id. */
	private final Map<Integer, Boolean> doors = new HashMap<>();

	@Getter
	private List<NPC> npcs = Collections.emptyList();
	@Getter
	private List<GroundItem> items = Collections.emptyList();
	@Getter
	private List<TileObject> objects = Collections.emptyList();
	/** Where the path leads; null if the step has nowhere to go. */
	@Getter
	@Nullable
	private WorldPoint target;
	@Getter
	@Nullable
	private String targetLabel;
	/** The target is an NPC, ground item or object in view, which is outlined already. */
	@Getter
	private boolean targetOutlined;
	@Getter
	private List<WorldPoint> path = Collections.emptyList();
	/** Within the step's {@link Guide.Step#getPathWithin()} range, such as after a teleport lands. */
	@Getter
	private boolean arrived = true;

	private WorldPoint pathFrom;
	private List<WorldPoint> pathGoals;
	/** The step whose waypoints {@link #viaReached} counts. */
	@Nullable
	private Guide.Step viaStep;
	private int viaReached;

	@Inject
	RouteTracker(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	void sceneCleared()
	{
		objectNames.clear();
		doors.clear();
		clear();
	}

	void clear()
	{
		npcs = Collections.emptyList();
		items = Collections.emptyList();
		objects = Collections.emptyList();
		target = null;
		targetLabel = null;
		targetOutlined = false;
		arrived = true;
		path = Collections.emptyList();
		pathFrom = null;
		pathGoals = null;
	}

	void update(@Nullable Guide.Step step, boolean wantPath)
	{
		Player player = client.getLocalPlayer();
		WorldView view = client.getTopLevelWorldView();
		if (step == null || player == null || view == null)
		{
			clear();
			return;
		}
		WorldPoint here = player.getWorldLocation();

		List<NPC> foundNpcs = new ArrayList<>();
		if (!step.getNpcs().isEmpty() || !step.getNpcIds().isEmpty())
		{
			for (NPC npc : view.npcs())
			{
				if (Guide.wantsNpc(step, npc.getName(), npc.getId()))
				{
					foundNpcs.add(npc);
				}
			}
		}
		List<GroundItem> foundItems = new ArrayList<>();
		List<TileObject> foundObjects = new ArrayList<>();
		if (!step.getGround().isEmpty() || !step.getObjects().isEmpty() || !step.getObjectIds().isEmpty())
		{
			scan(view, step, here, foundItems, foundObjects);
		}
		if ("south".equals(step.getPick()))
		{
			foundObjects = southmost(foundObjects, TileObject::getWorldLocation);
			foundItems = southmost(foundItems, g -> g.getTile().getWorldLocation());
		}
		npcs = foundNpcs;
		items = foundItems;
		objects = foundObjects;

		// Something real in view beats the wiki's coordinates, as NPCs wander and items have several spawns
		WorldPoint to = null;
		String label = null;
		int reach = 0;
		List<WorldPoint> goals = Collections.emptyList();
		NPC nearestNpc = nearest(foundNpcs, here);
		GroundItem nearestItem = nearestItem(foundItems, here);
		if (nearestNpc != null)
		{
			to = nearestNpc.getWorldLocation();
			label = nearestNpc.getName();
			reach = 1;
			goals = Collections.singletonList(to);
		}
		else if (nearestItem != null)
		{
			to = nearestItem.getTile().getWorldLocation();
			label = itemManager.getItemComposition(nearestItem.getItem().getId()).getName();
			goals = Collections.singletonList(to);
		}
		else if (step.getPick() != null && !foundObjects.isEmpty())
		{
			to = foundObjects.get(0).getWorldLocation();
			label = step.getTarget() == null ? null : step.getTarget().getLabel();
			reach = 1;
			goals = Collections.singletonList(to);
		}
		else if (step.getTarget() != null)
		{
			to = step.getTarget().worldPoint();
			label = step.getTarget().getLabel();
			if (step != viaStep)
			{
				viaStep = step;
				viaReached = 0;
			}
			viaReached = reached(step, here, to, viaReached);
			goals = goals(step, here, to, viaReached);
		}
		target = to;
		targetLabel = label;
		targetOutlined = nearestNpc != null || nearestItem != null || objectAt(foundObjects, to);

		// Some paths only make sense after a teleport has landed
		arrived = step.getPathWithin() <= 0 || step.getTarget() == null
			|| withinOnPlane(here, step.getTarget().worldPoint(), step.getPathWithin());
		if (!arrived)
		{
			wantPath = false;
		}
		if (!wantPath || goals.isEmpty())
		{
			path = Collections.emptyList();
			pathFrom = null;
			pathGoals = null;
		}
		else if (!here.equals(pathFrom) || !goals.equals(pathGoals))
		{
			path = chainPath(view, flagsWithDoorsOpen(view), here, goals, reach);
			pathFrom = here;
			pathGoals = goals;
		}
	}

	/**
	 * The points the path should go through from here, starting at waypoint {@code from} (the
	 * ones before it are reached). On another floor than the target, that is the next waypoint
	 * on this floor (such as a ladder). Otherwise it is the waypoints on this floor in a row,
	 * then the target, unless a waypoint on another floor comes first: routes like the Black
	 * Knights' Fortress go up and down several times on the way.
	 */
	static List<WorldPoint> goals(Guide.Step step, WorldPoint here, WorldPoint to, int from)
	{
		List<Guide.Target> via = step.getVia();
		if (to.getPlane() != here.getPlane())
		{
			for (int i = from; i < via.size(); i++)
			{
				if (via.get(i).getPlane() == here.getPlane())
				{
					return Collections.singletonList(via.get(i).worldPoint());
				}
			}
			return Collections.emptyList();
		}
		List<WorldPoint> goals = new ArrayList<>();
		for (int i = from; i < via.size(); i++)
		{
			WorldPoint wp = via.get(i).worldPoint();
			if (wp.getPlane() != here.getPlane())
			{
				return goals;
			}
			goals.add(wp);
		}
		goals.add(to);
		return goals;
	}

	/**
	 * How many of the step's waypoints are behind you: one is reached when you get near it on
	 * its floor, or, on the target's floor, once you are closer to the target than it is.
	 */
	static int reached(Guide.Step step, WorldPoint here, @Nullable WorldPoint to, int from)
	{
		List<Guide.Target> via = step.getVia();
		int r = from;
		while (r < via.size())
		{
			WorldPoint wp = via.get(r).worldPoint();
			boolean near = wp.getPlane() == here.getPlane() && wp.distanceTo2D(here) <= WAYPOINT_REACH;
			boolean passed = to != null && wp.getPlane() == here.getPlane() && to.getPlane() == here.getPlane()
				&& wp.distanceTo2D(to) >= here.distanceTo2D(to) - WAYPOINT_REACH;
			if (!near && !passed)
			{
				break;
			}
			r++;
		}
		return r;
	}

	/** Paths from one goal to the next, joined, stopping where a goal is outside the loaded area. */
	/**
	 * The current floor's collision flags with every closed door and gate (a wall with an Open
	 * option) treated as open, so paths go through them. Null when there is no collision data.
	 */
	@Nullable
	private int[][] flagsWithDoorsOpen(WorldView view)
	{
		CollisionData[] maps = view.getCollisionMaps();
		if (maps == null || view.isInstance() || maps[view.getPlane()] == null)
		{
			return null;
		}
		int[][] original = maps[view.getPlane()].getFlags();
		int[][] flags = new int[original.length][];
		for (int x = 0; x < original.length; x++)
		{
			flags[x] = original[x].clone();
		}
		Tile[][] tiles = view.getScene().getTiles()[view.getPlane()];
		for (int x = 0; x < tiles.length && x < flags.length; x++)
		{
			for (int y = 0; y < tiles[x].length && y < flags[x].length; y++)
			{
				Tile tile = tiles[x][y];
				if (tile != null && tile.getWallObject() != null && isDoor(tile.getWallObject().getId()))
				{
					Pathfinder.openDoor(flags, x, y);
				}
			}
		}
		return flags;
	}

	/** Whether an object can be opened, by its Open option; objects that change with varbits aren't cached. */
	private boolean isDoor(int id)
	{
		Boolean cached = doors.get(id);
		if (cached != null)
		{
			return cached;
		}
		ObjectComposition def = client.getObjectDefinition(id);
		if (def == null)
		{
			return false;
		}
		boolean impostor = def.getImpostorIds() != null;
		if (impostor)
		{
			def = def.getImpostor();
			if (def == null)
			{
				return false;
			}
		}
		boolean door = false;
		for (String action : def.getActions())
		{
			if ("Open".equalsIgnoreCase(action))
			{
				door = true;
				break;
			}
		}
		if (!impostor)
		{
			doors.put(id, door);
		}
		return door;
	}

	private static List<WorldPoint> chainPath(WorldView view, @Nullable int[][] flags, WorldPoint from, List<WorldPoint> goals, int targetReach)
	{
		List<WorldPoint> all = new ArrayList<>();
		WorldPoint at = from;
		for (int i = 0; i < goals.size(); i++)
		{
			WorldPoint goal = goals.get(i);
			boolean last = i == goals.size() - 1;
			int reach = last ? targetReach : WAYPOINT_REACH;
			List<WorldPoint> leg = flags == null ? Collections.emptyList() : findPath(view, flags, at, goal, reach);
			all.addAll(leg);
			if (!leg.isEmpty())
			{
				at = leg.get(leg.size() - 1);
			}
			if (at.distanceTo2D(goal) > Math.max(reach, 1))
			{
				break;
			}
		}
		return all;
	}

	private static List<WorldPoint> findPath(WorldView view, int[][] flags, WorldPoint from, WorldPoint to, int reach)
	{
		int baseX = view.getBaseX();
		int baseY = view.getBaseY();
		int goalX = to.getX() - baseX;
		int goalY = to.getY() - baseY;
		// Standing on a blocked tile (an object) is impossible, so arriving next to it is enough
		if (reach == 0 && goalX >= 0 && goalY >= 0 && goalX < flags.length && goalY < flags[0].length
			&& (flags[goalX][goalY] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
		{
			reach = 1;
		}

		List<WorldPoint> path = new ArrayList<>();
		for (Point p : Pathfinder.find(flags, from.getX() - baseX, from.getY() - baseY, goalX, goalY, reach))
		{
			path.add(new WorldPoint(p.x + baseX, p.y + baseY, view.getPlane()));
		}
		return path;
	}

	/** Looks through the tiles around you for the step's ground items and objects. */
	private void scan(WorldView view, Guide.Step step, WorldPoint here, List<GroundItem> items, List<TileObject> objects)
	{
		Scene scene = view.getScene();
		Tile[][] tiles = scene.getTiles()[view.getPlane()];
		int baseX = view.getBaseX();
		int baseY = view.getBaseY();
		int cx = here.getX() - baseX;
		int cy = here.getY() - baseY;
		List<WorldPoint> anchors = anchors(step);
		for (int x = Math.max(0, cx - SCAN_RADIUS); x <= Math.min(tiles.length - 1, cx + SCAN_RADIUS); x++)
		{
			for (int y = Math.max(0, cy - SCAN_RADIUS); y <= Math.min(tiles[x].length - 1, cy + SCAN_RADIUS); y++)
			{
				Tile tile = tiles[x][y];
				if (tile == null)
				{
					continue;
				}
				if (!step.getGround().isEmpty() && tile.getGroundItems() != null)
				{
					for (TileItem item : tile.getGroundItems())
					{
						if (GuideText.matchesName(step.getGround(), itemManager.getItemComposition(item.getId()).getName()))
						{
							items.add(new GroundItem(tile, item));
						}
					}
				}
				if ((!step.getObjects().isEmpty() || !step.getObjectIds().isEmpty()) && near(tile.getWorldLocation(), anchors))
				{
					for (TileObject object : objectsOn(tile))
					{
						if (Guide.wantsObject(step, objectName(object.getId()), object.getId()))
						{
							objects.add(object);
						}
					}
				}
			}
		}
	}

	private static List<TileObject> objectsOn(Tile tile)
	{
		List<TileObject> found = new ArrayList<>();
		if (tile.getGameObjects() != null)
		{
			for (GameObject object : tile.getGameObjects())
			{
				// Objects bigger than a tile are listed on each of their tiles; keep the one they start on
				if (object != null && object.getSceneMinLocation().equals(tile.getSceneLocation()))
				{
					found.add(object);
				}
			}
		}
		if (tile.getWallObject() != null)
		{
			found.add(tile.getWallObject());
		}
		if (tile.getDecorativeObject() != null)
		{
			found.add(tile.getDecorativeObject());
		}
		if (tile.getGroundObject() != null)
		{
			found.add(tile.getGroundObject());
		}
		return found;
	}

	@Nullable
	private String objectName(int id)
	{
		String cached = objectNames.get(id);
		if (cached != null)
		{
			return cached;
		}
		ObjectComposition def = client.getObjectDefinition(id);
		if (def == null)
		{
			return null;
		}
		if (def.getImpostorIds() != null)
		{
			ObjectComposition impostor = def.getImpostor();
			return impostor == null ? null : impostor.getName();
		}
		objectNames.put(id, def.getName());
		return def.getName();
	}

	/** The step's target and waypoints, which objects have to be near. */
	private static List<WorldPoint> anchors(Guide.Step step)
	{
		List<WorldPoint> anchors = new ArrayList<>();
		if (step.getTarget() != null)
		{
			anchors.add(step.getTarget().worldPoint());
		}
		for (Guide.Target via : step.getVia())
		{
			anchors.add(via.worldPoint());
		}
		return anchors;
	}

	private static boolean near(WorldPoint p, List<WorldPoint> anchors)
	{
		if (anchors.isEmpty())
		{
			return true;
		}
		for (WorldPoint a : anchors)
		{
			if (a.getPlane() == p.getPlane() && a.distanceTo2D(p) <= OBJECT_NEAR)
			{
				return true;
			}
		}
		return false;
	}

	private static <T> List<T> southmost(List<T> things, Function<T, WorldPoint> location)
	{
		if (things.size() < 2)
		{
			return things;
		}
		T south = things.get(0);
		for (T t : things)
		{
			if (location.apply(t).getY() < location.apply(south).getY())
			{
				south = t;
			}
		}
		return Collections.singletonList(south);
	}

	private static boolean withinOnPlane(WorldPoint a, WorldPoint b, int distance)
	{
		return a.getPlane() == b.getPlane() && a.distanceTo2D(b) <= distance;
	}

	/** Whether one of the outlined objects covers or touches the target tile. */
	private static boolean objectAt(List<TileObject> objects, @Nullable WorldPoint to)
	{
		if (to == null)
		{
			return false;
		}
		for (TileObject object : objects)
		{
			if (object.getWorldLocation().getPlane() == to.getPlane() && object.getWorldLocation().distanceTo2D(to) <= 1)
			{
				return true;
			}
		}
		return false;
	}

	@Nullable
	private static NPC nearest(List<NPC> npcs, WorldPoint here)
	{
		NPC best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (NPC npc : npcs)
		{
			int d = npc.getWorldLocation().distanceTo2D(here);
			if (d < bestDistance)
			{
				best = npc;
				bestDistance = d;
			}
		}
		return best;
	}

	@Nullable
	private static GroundItem nearestItem(List<GroundItem> items, WorldPoint here)
	{
		GroundItem best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (GroundItem g : items)
		{
			int d = g.getTile().getWorldLocation().distanceTo2D(here);
			if (d < bestDistance && g.getTile().getWorldLocation().getPlane() == here.getPlane())
			{
				best = g;
				bestDistance = d;
			}
		}
		return best;
	}
}
