package com.speedrunguide;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.CollisionDataFlag;

/**
 * Breadth-first search over one plane of the loaded scene's collision flags, in scene
 * coordinates. Targets outside the scene, or that can't be reached, get a path to the reachable
 * tile closest to them, so the path always points the right way and grows as you walk.
 */
final class Pathfinder
{
	// The order the game's own pathing tries directions in: W, E, S, N, SW, SE, NW, NE
	private static final int[] DX = {-1, 1, 0, 0, -1, 1, -1, 1};
	private static final int[] DY = {0, 0, -1, 1, -1, -1, 1, 1};

	private Pathfinder()
	{
	}

	/**
	 * @param flags collision flags by [x][y]
	 * @param reach how close counts as arrived (0 = on the tile, 1 = next to it)
	 * @return the tiles to walk, start excluded; empty if already there or boxed in
	 */
	static List<Point> find(int[][] flags, int startX, int startY, int goalX, int goalY, int reach)
	{
		int width = flags.length;
		int height = flags[0].length;
		if (!inside(startX, startY, width, height))
		{
			return Collections.emptyList();
		}

		int[] previous = new int[width * height];
		Arrays.fill(previous, -1);
		int start = startX * height + startY;
		previous[start] = start;
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		queue.add(start);

		int best = start;
		int bestDistance = distance(startX, startY, goalX, goalY);
		while (!queue.isEmpty())
		{
			int tile = queue.poll();
			int x = tile / height;
			int y = tile % height;
			int d = distance(x, y, goalX, goalY);
			if (d < bestDistance)
			{
				best = tile;
				bestDistance = d;
			}
			if (d <= reach)
			{
				best = tile;
				break;
			}
			for (int i = 0; i < DX.length; i++)
			{
				int nx = x + DX[i];
				int ny = y + DY[i];
				if (inside(nx, ny, width, height) && previous[nx * height + ny] == -1 && canMove(flags, x, y, DX[i], DY[i]))
				{
					previous[nx * height + ny] = tile;
					queue.add(nx * height + ny);
				}
			}
		}

		List<Point> path = new ArrayList<>();
		for (int tile = best; tile != start; tile = previous[tile])
		{
			path.add(new Point(tile / height, tile % height));
		}
		Collections.reverse(path);
		return path;
	}

	/** Chebyshev distance: steps when diagonals count as one. */
	static int distance(int x1, int y1, int x2, int y2)
	{
		return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
	}

	/** One step from (x, y) by (dx, dy), using the same wall and object checks as RuneLite's WorldArea. */
	static boolean canMove(int[][] flags, int x, int y, int dx, int dy)
	{
		int xFlags = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		int yFlags = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		int xyFlags = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		if (dx < 0)
		{
			xFlags |= CollisionDataFlag.BLOCK_MOVEMENT_EAST;
		}
		else if (dx > 0)
		{
			xFlags |= CollisionDataFlag.BLOCK_MOVEMENT_WEST;
		}
		if (dy < 0)
		{
			yFlags |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
		}
		else if (dy > 0)
		{
			yFlags |= CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
		}
		if (dx < 0 && dy < 0)
		{
			xyFlags |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST;
		}
		else if (dx < 0 && dy > 0)
		{
			xyFlags |= CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST;
		}
		else if (dx > 0 && dy < 0)
		{
			xyFlags |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST;
		}
		else if (dx > 0 && dy > 0)
		{
			xyFlags |= CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST;
		}

		if (dx != 0 && (flags[x + dx][y] & xFlags) != 0)
		{
			return false;
		}
		if (dy != 0 && (flags[x][y + dy] & yFlags) != 0)
		{
			return false;
		}
		return dx == 0 || dy == 0 || (flags[x + dx][y + dy] & xyFlags) == 0;
	}

	private static boolean inside(int x, int y, int width, int height)
	{
		return x >= 0 && y >= 0 && x < width && y < height;
	}
}
