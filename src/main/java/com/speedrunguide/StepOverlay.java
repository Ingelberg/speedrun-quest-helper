package com.speedrunguide;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The current step, its dialogue keys and notes, and a peek at the steps after it.
 */
class StepOverlay extends OverlayPanel
{
	static final Color CHAT_COLOR = new Color(0, 220, 255);
	private static final Color NOTE_COLOR = new Color(200, 200, 160);
	private static final Color UPCOMING_COLOR = Color.GRAY;
	private static final Color DONE_COLOR = new Color(80, 220, 80);

	private final SpeedrunGuidePlugin plugin;
	private final SpeedrunGuideConfig config;

	@Inject
	StepOverlay(SpeedrunGuidePlugin plugin, SpeedrunGuideConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Guide guide = plugin.getGuide();
		if (!plugin.isActive() || !config.showOverlay() || guide == null)
		{
			return null;
		}

		int index = plugin.getStep();
		int total = guide.steps().size();
		panelComponent.setPreferredSize(new Dimension(config.overlayWidth(), 0));
		panelComponent.getChildren().add(LineComponent.builder()
			.left(guide.getName())
			.right(Math.min(index + 1, total) + "/" + total)
			.rightColor(Color.LIGHT_GRAY)
			.build());

		Guide.Step step = guide.step(index);
		if (step == null)
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("Speedrun complete!")
				.color(DONE_COLOR)
				.build());
			return super.render(graphics);
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left(GuideText.plain(step.getText()))
			.leftColor(step.isNote() ? NOTE_COLOR : Color.WHITE)
			.build());
		if (!step.getChat().isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Chat: " + GuideText.keys(step.getChat()))
				.leftColor(CHAT_COLOR)
				.build());
		}
		if (config.showNotes())
		{
			for (String note : step.getNotes())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(GuideText.plain(note))
					.leftColor(NOTE_COLOR)
					.build());
			}
		}

		for (int i = 1; i <= config.upcomingSteps(); i++)
		{
			Guide.Step upcoming = guide.step(index + i);
			if (upcoming == null)
			{
				break;
			}
			panelComponent.getChildren().add(LineComponent.builder()
				.left((i == 1 ? "Next: " : "Then: ") + GuideText.plain(upcoming.getText()))
				.leftColor(UPCOMING_COLOR)
				.build());
		}
		return super.render(graphics);
	}
}
