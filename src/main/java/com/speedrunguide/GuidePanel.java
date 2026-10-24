package com.speedrunguide;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * Sidebar: pick a quest, see its trophy times and what the speedrun gives you, and click through
 * the wiki's steps. The current step is highlighted; clicking any step makes it the current one.
 */
class GuidePanel extends PluginPanel
{
	private static final String[] TIERS = {"bronze", "silver", "gold", "platinum", "diamond"};
	private static final Color[] TIER_COLORS = {
		new Color(205, 127, 50),
		new Color(192, 192, 192),
		new Color(255, 205, 40),
		new Color(120, 220, 200),
		new Color(170, 230, 255),
	};
	private static final String NOTE_HEX = "#c8c8a0";
	private static final String CHAT_HEX = "#00dcff";
	private static final String MUTED_HEX = "#a0a0a0";
	private static final int TEXT_WIDTH = 150;
	private static final Color DONE_COLOR = new Color(120, 120, 120);
	private static final Border CURRENT_BORDER = BorderFactory.createCompoundBorder(
		BorderFactory.createMatteBorder(0, 3, 0, 0, ColorScheme.BRAND_ORANGE),
		BorderFactory.createEmptyBorder(5, 3, 5, 5));
	private static final Border ROW_BORDER = BorderFactory.createEmptyBorder(5, 6, 5, 5);

	private final SpeedrunGuidePlugin plugin;
	private final JComboBox<String> questBox = new JComboBox<>();
	private final JPanel trophies = new JPanel(new GridLayout(2, 5, 2, 0));
	private final JLabel progress = new JLabel();
	private final JPanel body = new JPanel();
	private final List<JPanel> rows = new ArrayList<>();
	private boolean updatingBox;

	GuidePanel(SpeedrunGuidePlugin plugin)
	{
		this.plugin = plugin;
		setLayout(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

		JLabel title = new JLabel("Speedrun Quest Helper");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(LEFT_ALIGNMENT);
		top.add(title);
		top.add(gap(6));

		for (Guide g : plugin.getLibrary().all())
		{
			questBox.addItem(g.getName());
		}
		questBox.setAlignmentX(LEFT_ALIGNMENT);
		questBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		questBox.addActionListener(e ->
		{
			Guide picked = plugin.getLibrary().byName((String) questBox.getSelectedItem());
			if (!updatingBox && picked != null && picked != plugin.getGuide())
			{
				plugin.selectGuide(picked);
			}
		});
		top.add(questBox);
		top.add(gap(6));

		trophies.setAlignmentX(LEFT_ALIGNMENT);
		trophies.setToolTipText("Trophy times from the wiki; beat them on the in-game timer");
		top.add(trophies);
		top.add(gap(6));

		JPanel buttons = new JPanel(new GridLayout(1, 4, 3, 0));
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		buttons.add(button("◀", "Previous step", plugin::previous));
		buttons.add(button("▶", "Next step", plugin::next));
		buttons.add(button("↺", "Restart: back to the step that starts the quest", plugin::restart));
		buttons.add(button("Wiki", "Open this guide on the OSRS Wiki", () ->
		{
			Guide g = plugin.getGuide();
			if (g != null)
			{
				LinkBrowser.browse(g.url());
			}
		}));
		top.add(buttons);
		top.add(gap(4));

		progress.setAlignmentX(LEFT_ALIGNMENT);
		progress.setFont(FontManager.getRunescapeSmallFont());
		progress.setForeground(Color.LIGHT_GRAY);
		top.add(progress);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		add(top, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);
	}

	void showGuide(Guide guide, int step)
	{
		updatingBox = true;
		questBox.setSelectedItem(guide.getName());
		updatingBox = false;

		trophies.removeAll();
		Map<String, String> times = guide.getTrophies();
		for (int i = 0; i < TIERS.length; i++)
		{
			JLabel name = new JLabel(TIERS[i].substring(0, 1).toUpperCase() + TIERS[i].substring(1, Math.min(4, TIERS[i].length())), SwingConstants.CENTER);
			name.setFont(FontManager.getRunescapeSmallFont());
			name.setForeground(TIER_COLORS[i]);
			trophies.add(name);
		}
		for (int i = 0; i < TIERS.length; i++)
		{
			JLabel time = new JLabel(times.getOrDefault(TIERS[i], "-"), SwingConstants.CENTER);
			time.setForeground(TIER_COLORS[i]);
			trophies.add(time);
		}

		body.removeAll();
		rows.clear();
		body.add(collapsible("Given at the start", setupHtml(guide), false));
		body.add(gap(6));

		int index = 0;
		for (Guide.Section section : guide.getSections())
		{
			JLabel heading = new JLabel(section.getTitle());
			heading.setFont(FontManager.getRunescapeBoldFont());
			heading.setForeground(ColorScheme.BRAND_ORANGE);
			heading.setBorder(BorderFactory.createEmptyBorder(6, 0, 3, 0));
			heading.setAlignmentX(LEFT_ALIGNMENT);
			body.add(heading);

			for (Guide.Step s : section.getSteps())
			{
				JPanel row = stepRow(s, index++);
				rows.add(row);
				body.add(row);
				body.add(gap(2));
			}
			for (Guide.Image image : section.getImages())
			{
				body.add(imageLink(image));
			}
		}

		if (!guide.getTips().isEmpty())
		{
			body.add(gap(8));
			body.add(collapsible("Optimizations", tipsHtml(guide), false));
		}

		body.add(gap(10));
		JLabel credit = new JLabel(html("<span style='color:" + MUTED_HEX + "'>Guide from the OSRS Wiki, CC BY-NC-SA 3.0. Click to open.</span>"));
		credit.setFont(FontManager.getRunescapeSmallFont());
		credit.setAlignmentX(LEFT_ALIGNMENT);
		credit.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		credit.addMouseListener(click(() -> LinkBrowser.browse(guide.url())));
		body.add(credit);

		showStep(guide, step);
		revalidate();
		repaint();
	}

	void showStep(Guide guide, int step)
	{
		if (guide != plugin.getGuide() || rows.size() != guide.steps().size())
		{
			return;
		}
		int total = rows.size();
		progress.setText(step >= total ? "Speedrun complete!" : "Step " + (step + 1) + " of " + total);
		for (int i = 0; i < total; i++)
		{
			JPanel row = rows.get(i);
			boolean current = i == step;
			row.setBackground(current ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			row.setBorder(current ? CURRENT_BORDER : ROW_BORDER);
			Color text = i < step ? DONE_COLOR : Color.WHITE;
			for (java.awt.Component c : row.getComponents())
			{
				c.setForeground(text);
			}
		}
		if (step < total)
		{
			JPanel row = rows.get(step);
			SwingUtilities.invokeLater(() -> body.scrollRectToVisible(row.getBounds()));
		}
	}

	private JPanel stepRow(Guide.Step step, int index)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setToolTipText("Make this the current step");

		JLabel number = new JLabel(step.isNote() ? "" : String.valueOf(index + 1));
		number.setFont(FontManager.getRunescapeSmallFont());
		number.setVerticalAlignment(SwingConstants.TOP);
		number.setPreferredSize(new Dimension(20, 0));
		row.add(number, BorderLayout.WEST);

		StringBuilder sb = new StringBuilder();
		sb.append(step.isNote() ? "<span style='color:" + NOTE_HEX + "'>" + step.getText() + "</span>" : step.getText());
		for (Guide.ChatOption option : step.getChat())
		{
			sb.append("<br><span style='color:").append(CHAT_HEX).append("'><b>")
				.append(option.getKey().isEmpty() ? "•" : GuideText.escape(option.getKey()))
				.append("</b> ").append(GuideText.escape(option.getText())).append("</span>");
		}
		for (String note : step.getNotes())
		{
			sb.append("<br><span style='color:").append(NOTE_HEX).append("'>").append(note).append("</span>");
		}
		JLabel text = new JLabel(html(sb.toString()));
		text.setVerticalAlignment(SwingConstants.TOP);
		row.add(text, BorderLayout.CENTER);

		row.addMouseListener(click(() -> plugin.setStep(index)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JComponent imageLink(Guide.Image image)
	{
		String caption = image.getCaption().isEmpty() ? image.getFile().replaceFirst("\\.\\w+$", "") : image.getCaption();
		JLabel link = new JLabel(html("<u>Image:</u> " + GuideText.escape(caption)));
		link.setFont(FontManager.getRunescapeSmallFont());
		link.setForeground(new Color(140, 180, 255));
		link.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 0));
		link.setAlignmentX(LEFT_ALIGNMENT);
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.setToolTipText("Open the picture on the OSRS Wiki");
		link.addMouseListener(click(() -> LinkBrowser.browse(image.url())));
		return link;
	}

	private static String setupHtml(Guide guide)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("<b>Start:</b> ").append(guide.getStart());
		list(sb, "Skills", guide.getSkills());
		list(sb, "Items", guide.getItems());
		list(sb, "Fights", guide.getKills());
		return sb.toString();
	}

	private static void list(StringBuilder sb, String title, List<String> items)
	{
		if (items.isEmpty())
		{
			return;
		}
		sb.append("<br><br><b>").append(title).append(":</b>");
		for (String item : items)
		{
			sb.append("<br>").append(item.startsWith("•") ? "&nbsp;&nbsp;" + item : item);
		}
	}

	private static String tipsHtml(Guide guide)
	{
		StringBuilder sb = new StringBuilder();
		for (Guide.Tip tip : guide.getTips())
		{
			if (sb.length() > 0)
			{
				sb.append("<br><br>");
			}
			if (tip.getHeading() != null)
			{
				sb.append("<b>").append(tip.getHeading()).append("</b><br>");
			}
			sb.append(String.join("<br><br>", tip.getParagraphs()));
		}
		return sb.toString();
	}

	/** A heading that shows and hides a block of text. */
	private JPanel collapsible(String title, String contentHtml, boolean open)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel header = new JLabel();
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(Color.WHITE);
		header.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel content = new JLabel(html(contentHtml, TEXT_WIDTH + 20));
		content.setForeground(Color.LIGHT_GRAY);
		content.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
		content.setVisible(open);

		Runnable label = () -> header.setText((content.isVisible() ? "▾ " : "▸ ") + title);
		label.run();
		header.addMouseListener(click(() ->
		{
			content.setVisible(!content.isVisible());
			label.run();
			panel.setMaximumSize(null);
			panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
			body.revalidate();
		}));

		panel.add(header, BorderLayout.NORTH);
		panel.add(content, BorderLayout.CENTER);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	private static JButton button(String text, String tooltip, Runnable action)
	{
		JButton b = new JButton(text);
		b.setToolTipText(tooltip);
		b.setFocusable(false);
		b.addActionListener(e -> action.run());
		return b;
	}

	private static MouseAdapter click(Runnable action)
	{
		return new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					action.run();
				}
			}
		};
	}

	private static JComponent gap(int height)
	{
		JPanel gap = new JPanel();
		gap.setOpaque(false);
		gap.setAlignmentX(LEFT_ALIGNMENT);
		gap.setPreferredSize(new Dimension(0, height));
		gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return gap;
	}

	private static String html(String body)
	{
		return html(body, TEXT_WIDTH);
	}

	private static String html(String body, int width)
	{
		return "<html><div style='width:" + width + "px'>" + body + "</div></html>";
	}
}
