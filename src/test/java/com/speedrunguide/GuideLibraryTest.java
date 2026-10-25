package com.speedrunguide;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GuideLibraryTest
{
	private final GuideLibrary everything = GuideLibrary.loadAll(new Gson());
	private final GuideLibrary library = GuideLibrary.load(new Gson());

	@Test
	public void bundlesAllFifteenQuests()
	{
		assertEquals(15, everything.all().size());
		assertEquals("X Marks the Spot", everything.all().get(0).getName());
		assertEquals("Dragon Slayer II", everything.all().get(14).getName());
	}

	@Test
	public void everyQuestHasARouteNow()
	{
		assertEquals(15, library.all().size());
		for (Guide g : library.all())
		{
			assertNotNull(g.getName(), g.getProgress());
		}
		// only the routes played through in game are marked as tested
		assertTrue(library.byName("Cook's Assistant").isTested());
		assertTrue(library.byName("X Marks the Spot").isTested());
		assertFalse(library.byName("Dragon Slayer II").isTested());
	}

	@Test
	public void ernestLeversFollowQuestHelpersOrder()
	{
		Guide ernest = library.byName("Ernest the Chicken");
		// steps 6-14 are the levers: down A, B, D, up B, A, down F, E, C, up E
		String[] order = {"A", "B", "D", "B", "A", "F", "E", "C", "E"};
		int[] position = {1, 1, 1, 0, 0, 1, 1, 1, 0};
		for (int i = 0; i < order.length; i++)
		{
			Guide.Step step = ernest.steps().get(6 + i);
			assertEquals("Lever " + order[i], step.getTarget().getLabel());
			assertEquals(Integer.valueOf(position[i]), step.getDone().getIs());
			assertTrue(step.getDone().varbitDone(position[i]));
			assertFalse(step.getDone().varbitDone(1 - position[i]));
		}
		assertEquals("Oil can", ernest.steps().get(15).getDone().getHave());

		// searching the bookcase is done once you're in the secret room or the basement
		Guide.Done bookcase = ernest.steps().get(5).getDone();
		assertTrue(bookcase.inside(3093, 3358, 0));
		assertTrue(bookcase.inside(3117, 9754, 0));
		assertFalse(bookcase.inside(3098, 3358, 0));
		assertFalse(bookcase.inside(3093, 3358, 1));
	}

	@Test
	public void progressValuesPointAtStepsInOrder()
	{
		// a quest's progress only goes up, so the steps it is tied to have to come in the same order
		for (Guide g : library.all())
		{
			int last = -1;
			for (int i = 0; i < g.steps().size(); i++)
			{
				for (int value : g.steps().get(i).getProgress())
				{
					assertTrue(g.getName() + " progress " + value + " at step " + i, value > last);
					last = value;
				}
			}
		}
	}

	@Test
	public void everyGuideMatchesARuneLiteQuest()
	{
		for (Guide g : everything.all())
		{
			assertNotNull(g.getName(), g.quest());
		}
	}

	@Test
	public void everyGuideHasStepsAndTrophyTimes()
	{
		for (Guide g : everything.all())
		{
			assertFalse(g.getName(), g.steps().isEmpty());
			assertTrue(g.getName(), g.getStartStep() < g.steps().size());
			assertEquals(g.getName(), 5, g.getTrophies().size());
			for (Guide.Step s : g.steps())
			{
				assertFalse(g.getName() + " has leftover wikitext: " + s.getText(),
					s.getText().contains("{{") || s.getText().contains("[[") || s.getText().contains("}}"));
			}
		}
	}

	@Test
	public void cooksAssistantStartsWithTheCook()
	{
		Guide cook = library.byName("Cook's Assistant");
		Guide.Step first = cook.steps().get(cook.getStartStep());
		assertEquals("Talk to the Cook in Lumbridge Castle's kitchen.", first.getText());
		assertEquals("1, 1", GuideText.keys(first.getChat()));
		assertEquals("2:00", cook.getTrophies().get("platinum"));
	}

	@Test
	public void routeExtrasAreMerged()
	{
		Guide xMarks = library.byName("X Marks the Spot");
		assertEquals(7, xMarks.steps().size());
		Guide.Step shop = xMarks.steps().get(1);
		assertEquals(Collections.singletonList("Spade"), shop.getShop());
		assertTrue(GuideText.matchesName(shop.getNpcs(), "Shop keeper"));
		assertEquals("Dig here", xMarks.steps().get(2).getTarget().getLabel());
		assertEquals(3230, xMarks.steps().get(2).getTarget().getX());

		Guide cook = library.byName("Cook's Assistant");
		assertTrue(GuideText.matchesName(cook.steps().get(0).getNpcs(), "<col=ffff00>Cook</col>"));
		assertEquals(Collections.singletonList("Egg"), cook.steps().get(3).getGround());
		assertEquals("Trade", cook.steps().get(2).getLeftClick());
		assertEquals(2, cook.steps().get(5).getTarget().getPlane());
	}

	@Test
	public void routeStepsKnowWhenTheyAreDone()
	{
		Guide xMarks = library.byName("X Marks the Spot");
		assertEquals("Spade", xMarks.steps().get(1).getDone().getHave());
		// Each dig is done when the item it gives arrives, so a dig on the wrong tile doesn't count
		assertEquals(ItemID.CLUEQUEST_CLUE2, xMarks.steps().get(2).getDone().getHaveId());
		assertEquals(ItemID.CLUEQUEST_CLUE3, xMarks.steps().get(3).getDone().getHaveId());
		assertEquals(ItemID.CLUEQUEST_CLUE4, xMarks.steps().get(4).getDone().getHaveId());
		assertEquals(ItemID.CLUEQUEST_CASKET, xMarks.steps().get(5).getDone().getHaveId());
		assertEquals(3209, xMarks.steps().get(2).getTarget().getY());

		Guide cook = library.byName("Cook's Assistant");
		// the mill: grain into the hopper, then the lever (Quest Helper's MILL_FLOUR check), then the flour
		assertEquals("Grain", cook.steps().get(5).getDone().getLack());
		assertEquals(Integer.valueOf(VarbitID.MILL_FLOUR), cook.steps().get(6).getDone().getVarbit());
		assertEquals(1, cook.steps().get(6).getDone().getValue());
		assertEquals("Pot of flour", cook.steps().get(7).getDone().getHave());
		assertEquals(3, cook.steps().get(9).getDone().getArrive());
		assertEquals(Arrays.asList("Hopper", "Ladder"), cook.steps().get(5).getObjects());
		// compost is done once bought; emptying it is shown on the egg step
		assertEquals("Compost", cook.steps().get(2).getDone().getHave());
		assertEquals("Empty", cook.steps().get(3).getUseOption());
		assertEquals("south", cook.steps().get(4).getPick());
		// the egg in front of Fred's house is south of the chicken pen ones
		assertEquals("south", cook.steps().get(3).getPick());
		assertEquals(3195, cook.steps().get(2).getTarget().getX());
		assertEquals(Arrays.asList("Egg", "Pot of flour", "Bucket of milk"), cook.steps().get(9).getUse());
		assertEquals(InterfaceID.MagicSpellbook.TELEPORT_HOME_STANDARD, cook.steps().get(9).getWidget());
		assertEquals(Arrays.asList("Egg", "Pot of flour", "Bucket of milk"), cook.steps().get(10).getUse());
		assertEquals(3208, cook.steps().get(0).getSpot().getX());
	}

	@Test
	public void questProgressPicksTheStep()
	{
		Guide xMarks = library.byName("X Marks the Spot");
		assertEquals(Integer.valueOf(VarbitID.CLUEQUEST), xMarks.getProgress().getVarbit());
		// 2 is set after talking to Veos and covers buying the spade and the first dig
		assertEquals(1, xMarks.stepForProgress(2));
		assertArrayEquals(new int[]{1, 3}, xMarks.progressRange(1));
		// a reset puts the value back to 0, which only allows the first step
		assertEquals(0, xMarks.stepForProgress(0));
		assertArrayEquals(new int[]{0, 1}, xMarks.progressRange(0));
		assertEquals(6, xMarks.stepForProgress(7));
		assertArrayEquals(new int[]{6, 7}, xMarks.progressRange(6));
		assertEquals(-1, xMarks.stepForProgress(99));

		Guide cook = library.byName("Cook's Assistant");
		assertEquals(Integer.valueOf(29), cook.getProgress().getVarp());
		assertArrayEquals(new int[]{1, cook.steps().size()}, cook.progressRange(cook.stepForProgress(1)));
	}

	@Test
	public void waypointsLeadTheWayAndLaddersChangeFloors()
	{
		Guide xMarks = library.byName("X Marks the Spot");
		Guide.Step third = xMarks.steps().get(4);
		WorldPoint dig = third.getTarget().worldPoint();
		// from the castle, every waypoint is still ahead
		List<WorldPoint> fromCastle = RouteTracker.goals(third, new WorldPoint(3203, 3213, 0), dig, 0);
		assertEquals(third.getVia().size() + 1, fromCastle.size());
		// halfway there, the passed ones drop off
		WorldPoint middle = new WorldPoint(3156, 3241, 0);
		List<WorldPoint> halfway = RouteTracker.goals(third, middle, dig, RouteTracker.reached(third, middle, dig, 0));
		assertEquals(new WorldPoint(3139, 3253, 0), halfway.get(0));
		assertEquals(dig, halfway.get(halfway.size() - 1));

		Guide.Step hopper = library.byName("Cook's Assistant").steps().get(5);
		WorldPoint top = hopper.getTarget().worldPoint();
		assertEquals(Collections.singletonList(new WorldPoint(3164, 3307, 0)), RouteTracker.goals(hopper, new WorldPoint(3166, 3300, 0), top, 0));
		assertEquals(Collections.singletonList(new WorldPoint(3164, 3307, 1)), RouteTracker.goals(hopper, new WorldPoint(3165, 3307, 1), top, 1));
		assertEquals(Collections.singletonList(top), RouteTracker.goals(hopper, new WorldPoint(3164, 3307, 2), top, 2));
	}

	@Test
	public void dialogueOptionsMatchLoosely()
	{
		List<Guide.ChatOption> chat = Collections.singletonList(option("1", "What's Wrong?"));
		assertTrue(GuideText.matches(chat, "What's wrong?"));
		assertTrue(GuideText.matches(chat, "<col=ffffff>1. What's wrong?</col>"));
		assertFalse(GuideText.matches(chat, "Can I help?"));
		assertFalse(GuideText.matches(chat, ""));
	}

	@Test
	public void keysUseBulletsForUnnumberedOptions()
	{
		assertEquals("3, •", GuideText.keys(Arrays.asList(option("3", "a"), option("", "b"))));
	}

	@Test
	public void plainStripsTags()
	{
		assertEquals("Talk to <b>Aris</b> & go", GuideText.plain("Talk to &lt;b&gt;<b>Aris</b>&lt;/b&gt; &amp; go"));
	}

	private static Guide.ChatOption option(String key, String text)
	{
		Guide.ChatOption o = new Guide.ChatOption();
		o.setKey(key);
		o.setText(text);
		return o;
	}
}
