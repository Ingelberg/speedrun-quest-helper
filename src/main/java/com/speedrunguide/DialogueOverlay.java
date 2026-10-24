package com.speedrunguide;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Outlines the dialogue option the current step says to pick.
 */
class DialogueOverlay extends Overlay
{
	private final SpeedrunGuidePlugin plugin;
	private final SpeedrunGuideConfig config;

	@Inject
	DialogueOverlay(SpeedrunGuidePlugin plugin, SpeedrunGuideConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Guide guide = plugin.getGuide();
		Guide.Step step = guide == null ? null : guide.step(plugin.getStep());
		if (!plugin.isActive() || !config.highlightDialogue() || step == null || step.getChat().isEmpty())
		{
			return null;
		}

		graphics.setColor(config.highlightColor());
		graphics.setStroke(new BasicStroke(2));
		for (Widget option : plugin.dialogueOptionWidgets())
		{
			if (GuideText.matches(step.getChat(), option.getText()))
			{
				Rectangle bounds = option.getBounds();
				graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
			}
		}
		return null;
	}
}
