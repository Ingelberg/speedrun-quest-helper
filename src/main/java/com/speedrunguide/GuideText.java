package com.speedrunguide;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.runelite.client.util.Text;

/**
 * Text helpers for guide strings, which hold a tiny HTML subset.
 */
final class GuideText
{
	private static final Pattern TAG = Pattern.compile("<[^>]+>");
	// Some dialogue menus number their options ("1. Yes."); the guide only has the text
	private static final Pattern OPTION_NUMBER = Pattern.compile("^\\d+[.:]\\s*");
	private static final Pattern NOT_WORD = Pattern.compile("[^a-z0-9]+");

	private GuideText()
	{
	}

	/** The guide HTML as plain text, for overlays. */
	static String plain(String html)
	{
		return TAG.matcher(html).replaceAll("")
			.replace("&lt;", "<")
			.replace("&gt;", ">")
			.replace("&amp;", "&");
	}

	/** Plain text made safe to put in the guide HTML. */
	static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Lower case letters and digits only, so "What's Wrong?" matches "What's wrong?". */
	static String normalize(String text)
	{
		String s = OPTION_NUMBER.matcher(Text.removeTags(text).trim()).replaceFirst("");
		return NOT_WORD.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("");
	}

	/** The keys of a step's dialogue options, such as "1, 1, 3". */
	static String keys(List<Guide.ChatOption> chat)
	{
		return chat.stream()
			.map(c -> c.getKey().isEmpty() ? "•" : c.getKey())
			.collect(Collectors.joining(", "));
	}

	/** Whether an NPC or item name is one of the step's names, ignoring case, tags and punctuation. */
	static boolean matchesName(List<String> names, @Nullable String name)
	{
		if (name == null || names.isEmpty())
		{
			return false;
		}
		String shown = normalize(name);
		for (String n : names)
		{
			if (normalize(n).equals(shown))
			{
				return true;
			}
		}
		return false;
	}

	/** Whether a dialogue option on screen is one of the step's options. */
	static boolean matches(List<Guide.ChatOption> chat, String option)
	{
		String shown = normalize(option);
		if (shown.isEmpty())
		{
			return false;
		}
		for (Guide.ChatOption c : chat)
		{
			if (normalize(c.getText()).equals(shown))
			{
				return true;
			}
		}
		return false;
	}
}
