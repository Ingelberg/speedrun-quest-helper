package com.speedrunguide;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemLayer;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.ColorUtil;

/**
 * Draws the walking path on the ground, fills the tile it leads to (or the whole area a dig
 * works in), and outlines the NPCs to talk to, the items to pick up and the objects to use.
 * Tiles are filled, not outlined.
 */
class RouteOverlay extends Overlay
{
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 4;
	private static final int TARGET_FILL_ALPHA = 110;
	/** Heights above the ground (in local units) the arrows point down from. */
	private static final int NPC_ARROW_HEIGHT = 80;
	private static final int OBJECT_ARROW_HEIGHT = 150;
	private static final int TILE_ARROW_HEIGHT = 60;
	private static final BasicStroke ARROW_OUTLINE = new BasicStroke(1.5f);

	private final Client client;
	private final SpeedrunGuidePlugin plugin;
	private final SpeedrunGuideConfig config;
	private final RouteTracker route;
	private final ModelOutlineRenderer outlines;
	private final DigLog digLog;

	@Inject
	RouteOverlay(Client client, SpeedrunGuidePlugin plugin, SpeedrunGuideConfig config, RouteTracker route, ModelOutlineRenderer outlines, DigLog digLog)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.route = route;
		this.outlines = outlines;
		this.digLog = digLog;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isActive())
		{
			return null;
		}

		Rectangle digArea = config.showDigTiles() ? digArea() : null;
		if (config.showPath())
		{
			if (config.showNextPath())
			{
				Color next = config.nextPathColor();
				Set<WorldPoint> current = new HashSet<>(route.getPath());
				for (WorldPoint wp : route.getNextPath())
				{
					if (!current.contains(wp) && !wp.equals(route.getTarget()))
					{
						fill(graphics, wp, next);
					}
				}
			}
			Color path = config.pathColor();
			for (WorldPoint wp : route.getPath())
			{
				if (!wp.equals(route.getTarget()) && (digArea == null || !digArea.contains(wp.getX(), wp.getY())))
				{
					fill(graphics, wp, path);
				}
			}
			// The tile under an NPC or item isn't filled; the NPC or item is outlined instead
			if (digArea == null && !route.isTargetOutlined())
			{
				fill(graphics, route.getTarget(), ColorUtil.colorWithAlpha(config.targetColor(), TARGET_FILL_ALPHA));
			}
		}
		if (digArea != null)
		{
			drawDigArea(graphics, digArea);
		}
		if (config.showPath())
		{
			labelTarget(graphics);
			drawSpot(graphics);
		}

		if (config.highlightNpcs())
		{
			Color color = config.npcColor();
			for (NPC npc : route.getNpcs())
			{
				outlines.drawOutline(npc, OUTLINE_WIDTH, color, OUTLINE_FEATHER);
				Point text = npc.getCanvasTextLocation(graphics, npc.getName(), npc.getLogicalHeight() + 40);
				if (text != null)
				{
					OverlayUtil.renderTextLocation(graphics, text, npc.getName(), color);
				}
			}
		}

		if (config.highlightObjects())
		{
			Color color = config.npcColor();
			for (TileObject object : route.getObjects())
			{
				outlines.drawOutline(object, OUTLINE_WIDTH, color, OUTLINE_FEATHER);
			}
		}

		if (config.highlightGroundItems())
		{
			Color color = config.npcColor();
			for (RouteTracker.GroundItem g : route.getItems())
			{
				ItemLayer layer = g.getTile().getItemLayer();
				if (layer != null)
				{
					outlines.drawOutline(layer, g.getItem(), OUTLINE_WIDTH, color, OUTLINE_FEATHER);
				}
			}
		}

		if (config.showArrows())
		{
			drawArrows(graphics);
		}
		return null;
	}

	/** Bobbing arrows, like Quest Helper's, over everything the step points at. */
	private void drawArrows(Graphics2D graphics)
	{
		Color color = config.npcColor();
		int plane = client.getTopLevelWorldView().getPlane();
		if (config.highlightNpcs())
		{
			for (NPC npc : route.getNpcs())
			{
				LocalPoint local = npc.getLocalLocation();
				arrow(graphics, local == null ? null : Perspective.localToCanvas(client, local, plane, npc.getLogicalHeight() + NPC_ARROW_HEIGHT), color);
			}
		}
		if (config.highlightObjects())
		{
			for (TileObject object : route.getObjects())
			{
				arrow(graphics, object.getCanvasLocation(OBJECT_ARROW_HEIGHT), color);
			}
		}
		if (config.highlightGroundItems())
		{
			for (RouteTracker.GroundItem g : route.getItems())
			{
				arrow(graphics, Perspective.localToCanvas(client, g.getTile().getLocalLocation(), plane, TILE_ARROW_HEIGHT), color);
			}
		}
		// A target with nothing outlined on it, such as a dig spot, gets one too
		WorldPoint target = route.getTarget();
		if (!route.isTargetOutlined() && target != null && target.getPlane() == plane)
		{
			LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), target);
			arrow(graphics, local == null ? null : Perspective.localToCanvas(client, local, plane, TILE_ARROW_HEIGHT), color);
		}
	}

	/** A down arrow whose tip is at {@code tip}, bobbing up and down a few pixels. */
	private static void arrow(Graphics2D graphics, @Nullable Point tip, Color color)
	{
		if (tip == null)
		{
			return;
		}
		int bob = (int) Math.round(Math.sin(System.currentTimeMillis() / 180.0) * 4);
		int x = tip.getX();
		int y = tip.getY() - 6 - bob;
		Polygon shape = new Polygon(
			new int[]{x, x - 9, x - 4, x - 4, x + 4, x + 4, x + 9},
			new int[]{y, y - 11, y - 11, y - 24, y - 24, y - 11, y - 11},
			7);
		graphics.setColor(color);
		graphics.fill(shape);
		graphics.setColor(Color.BLACK);
		graphics.setStroke(ARROW_OUTLINE);
		graphics.draw(shape);
	}

	/** The dig step's area on this floor, or null when the step isn't a dig or is on another floor. */
	@Nullable
	private Rectangle digArea()
	{
		Guide.Step step = plugin.currentStep();
		if (step == null || step.getTarget() == null || step.getTarget().getPlane() != client.getTopLevelWorldView().getPlane())
		{
			return null;
		}
		return digLog.area(step);
	}

	/** Fills every tile of the area. */
	private void drawDigArea(Graphics2D graphics, Rectangle area)
	{
		int plane = client.getTopLevelWorldView().getPlane();
		Color fill = ColorUtil.colorWithAlpha(config.targetColor(), TARGET_FILL_ALPHA);
		for (int x = area.x; x < area.x + area.width; x++)
		{
			for (int y = area.y; y < area.y + area.height; y++)
			{
				fill(graphics, new WorldPoint(x, y, plane), fill);
			}
		}
	}

	private void labelTarget(Graphics2D graphics)
	{
		// An outlined NPC shows its own name; outlined items and objects need no label
		if (!route.isTargetOutlined())
		{
			label(graphics, route.getTarget(), route.getTargetLabel());
		}
	}

	/** The step's own marked tile, such as where to wait for the Cook. */
	private void drawSpot(Graphics2D graphics)
	{
		Guide.Step step = plugin.currentStep();
		Guide.Target spot = step == null ? null : step.getSpot();
		if (spot == null)
		{
			return;
		}
		fill(graphics, spot.worldPoint(), ColorUtil.colorWithAlpha(config.targetColor(), TARGET_FILL_ALPHA));
		label(graphics, spot.worldPoint(), spot.getLabel());
	}

	private void label(Graphics2D graphics, @Nullable WorldPoint where, @Nullable String label)
	{
		if (where == null || label == null || where.getPlane() != client.getTopLevelWorldView().getPlane())
		{
			return;
		}
		LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), where);
		Point text = local == null ? null : Perspective.getCanvasTextLocation(client, graphics, local, label, 0);
		if (text != null)
		{
			OverlayUtil.renderTextLocation(graphics, text, label, Color.WHITE);
		}
	}

	private void fill(Graphics2D graphics, @Nullable WorldPoint wp, Color color)
	{
		if (wp == null || wp.getPlane() != client.getTopLevelWorldView().getPlane())
		{
			return;
		}
		Polygon poly = tile(wp);
		if (poly != null)
		{
			graphics.setColor(color);
			graphics.fill(poly);
		}
	}

	@Nullable
	private Polygon tile(WorldPoint wp)
	{
		LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), wp);
		return local == null ? null : Perspective.getCanvasTilePoly(client, local);
	}
}
