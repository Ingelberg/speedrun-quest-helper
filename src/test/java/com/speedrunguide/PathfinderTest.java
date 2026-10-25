package com.speedrunguide;

import java.awt.Point;
import java.util.List;
import net.runelite.api.CollisionDataFlag;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PathfinderTest
{
	private static int[][] open(int size)
	{
		return new int[size][size];
	}

	@Test
	public void walksDiagonallyInTheOpen()
	{
		List<Point> path = Pathfinder.find(open(10), 0, 0, 3, 3, 0);
		assertEquals(3, path.size());
		assertEquals(new Point(3, 3), path.get(2));
	}

	@Test
	public void stopsNextToTheGoalWhenReachIsOne()
	{
		List<Point> path = Pathfinder.find(open(10), 0, 0, 5, 0, 1);
		assertEquals(new Point(4, 0), path.get(path.size() - 1));
	}

	@Test
	public void goesAroundAWall()
	{
		int[][] flags = open(10);
		// a solid column at x = 2 from y = 0 to 7
		for (int y = 0; y <= 7; y++)
		{
			flags[2][y] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		}
		List<Point> path = Pathfinder.find(flags, 0, 0, 4, 0, 0);
		assertEquals(new Point(4, 0), path.get(path.size() - 1));
		assertTrue(path.stream().noneMatch(p -> p.x == 2 && p.y <= 7));
	}

	@Test
	public void headsTowardsATargetOutsideTheScene()
	{
		List<Point> path = Pathfinder.find(open(10), 5, 5, 40, 5, 0);
		assertEquals(new Point(9, 5), path.get(path.size() - 1));
	}

	@Test
	public void wallsBlockTheSideTheyAreOn()
	{
		int[][] flags = open(3);
		// a wall on the east side of (1, 1): stepping from (2, 1) west into it is blocked
		flags[1][1] = CollisionDataFlag.BLOCK_MOVEMENT_EAST;
		assertFalse(Pathfinder.canMove(flags, 2, 1, -1, 0));
		assertTrue(Pathfinder.canMove(flags, 1, 0, 0, 1));
	}

	@Test
	public void pathsGoThroughOpenedDoors()
	{
		int[][] flags = open(5);
		// a wall along x = 2 with a closed door at (2, 2): its west side blocks (1, 2) -> (2, 2)
		for (int y = 0; y < 5; y++)
		{
			flags[2][y] = CollisionDataFlag.BLOCK_MOVEMENT_WEST;
			flags[1][y] = CollisionDataFlag.BLOCK_MOVEMENT_EAST;
		}
		assertTrue(Pathfinder.find(flags, 0, 2, 4, 2, 0).stream().noneMatch(p -> p.x == 4));

		Pathfinder.openDoor(flags, 2, 2);
		List<Point> path = Pathfinder.find(flags, 0, 2, 4, 2, 0);
		assertEquals(new Point(4, 2), path.get(path.size() - 1));
		assertTrue(path.contains(new Point(2, 2)));
		// the rest of the wall stays closed
		assertFalse(Pathfinder.canMove(flags, 1, 0, 1, 0));
	}

	@Test
	public void alreadyThereMeansNoPath()
	{
		assertTrue(Pathfinder.find(open(5), 2, 2, 2, 2, 0).isEmpty());
	}
}
