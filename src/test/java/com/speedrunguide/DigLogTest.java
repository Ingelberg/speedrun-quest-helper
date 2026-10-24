package com.speedrunguide;

import com.google.gson.Gson;
import java.awt.Rectangle;
import java.util.Arrays;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DigLogTest
{
	private static final int ORB = 23069;
	/** The last JSON saved, standing in for the RuneLite config. */
	private String saved;
	private final DigLog log = new DigLog(new DigLog.Store()
	{
		@Override
		public String load()
		{
			return saved;
		}

		@Override
		public void save(String json)
		{
			saved = json;
		}
	}, new Gson());

	@Test
	public void aDigThatGivesTheItemIsGood()
	{
		WorldPoint tile = new WorldPoint(3203, 3214, 0);
		log.dug(tile, ORB);
		assertNull(log.tick(false));
		assertEquals(DigLog.Result.GOOD, log.tick(true));
		assertEquals(tile, log.lastTile());
		assertTrue(log.tilesFor(ORB).getGood().contains("3203,3214,0"));
		assertTrue(saved.contains("3203,3214,0"));
	}

	@Test
	public void aDigWithoutTheItemIsAMissUnlessTheTileWorkedBefore()
	{
		WorldPoint tile = new WorldPoint(3200, 3210, 0);
		log.dug(tile, ORB);
		DigLog.Result result = null;
		for (int i = 0; i < DigLog.RESULT_TICKS; i++)
		{
			result = log.tick(false);
		}
		assertEquals(DigLog.Result.MISSED, result);
		assertTrue(log.tilesFor(ORB).getMissed().contains("3200,3210,0"));

		log.dug(tile, ORB);
		log.tick(true);
		assertTrue(log.tilesFor(ORB).getGood().contains("3200,3210,0"));
		assertTrue(log.tilesFor(ORB).getMissed().isEmpty());
	}

	@Test
	public void nothingHappensWithoutADig()
	{
		assertNull(log.tick(true));
		assertEquals(0, log.pendingItem());
	}

	@Test
	public void theAreaIsTheRectangleAroundKnownAndLearnedTiles()
	{
		Guide.Step step = new Guide.Step();
		Guide.Target target = new Guide.Target();
		target.setX(3203);
		target.setY(3213);
		step.setTarget(target);
		step.setDigArea(Arrays.asList(new int[]{3203, 3213}, new int[]{3203, 3212}));
		Guide.Done done = new Guide.Done();
		done.setHaveId(ORB);
		step.setDone(done);
		assertEquals(new Rectangle(3203, 3212, 1, 2), log.area(step));

		// a learned corner two tiles west fills in everything between
		log.dug(new WorldPoint(3201, 3214, 0), ORB);
		log.tick(true);
		assertEquals(new Rectangle(3201, 3212, 3, 3), log.area(step));
	}

	@Test
	public void noAreaWithoutKnownTiles()
	{
		Guide.Step step = new Guide.Step();
		step.setTarget(new Guide.Target());
		assertNull(log.area(step));
	}

	@Test
	public void keysRoundTrip()
	{
		WorldPoint p = new WorldPoint(3077, 3260, 0);
		assertEquals(p, DigLog.parse(DigLog.key(p)));
		assertNull(DigLog.parse("nonsense"));
	}
}
