package com.speedrunguide;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Outlines the items the current step says to buy in an open shop, to use in the inventory
 * (such as the ancient casket to use on Veos), and the step's interface component (such as the
 * Home Teleport spell).
 */
class ItemOverlay extends Overlay
{
	private final Client client;
	private final SpeedrunGuidePlugin plugin;
	private final SpeedrunGuideConfig config;
	private final ItemManager itemManager;
	private final RouteTracker route;

	@Inject
	ItemOverlay(Client client, SpeedrunGuidePlugin plugin, SpeedrunGuideConfig config, ItemManager itemManager, RouteTracker route)
	{
		this.route = route;
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Guide.Step step = plugin.currentStep();
		if (!plugin.isActive() || !config.highlightShop() || step == null)
		{
			return null;
		}
		graphics.setColor(config.highlightColor());
		graphics.setStroke(new BasicStroke(2));
		outline(graphics, InterfaceID.Shopmain.ITEMS, step.getShop());
		// On a step that arrives somewhere (such as a teleport), the spell comes first, then the items
		if (route.isArrived())
		{
			outline(graphics, InterfaceID.Inventory.ITEMS, step.getUse());
		}
		if (step.getWidget() > 0 && (step.getPathWithin() <= 0 || !route.isArrived()))
		{
			Widget widget = client.getWidget(step.getWidget());
			if (widget != null && !widget.isHidden())
			{
				Rectangle bounds = widget.getBounds();
				graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
			}
		}
		return null;
	}

	private void outline(Graphics2D graphics, int containerId, List<String> names)
	{
		if (names.isEmpty())
		{
			return;
		}
		Widget container = client.getWidget(containerId);
		if (container == null || container.isHidden() || container.getDynamicChildren() == null)
		{
			return;
		}
		for (Widget item : container.getDynamicChildren())
		{
			if (item != null && !item.isHidden() && item.getItemId() > 0
				&& GuideText.matchesName(names, itemManager.getItemComposition(item.getItemId()).getName()))
			{
				Rectangle bounds = item.getBounds();
				graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
			}
		}
	}
}
