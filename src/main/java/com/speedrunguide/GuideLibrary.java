package com.speedrunguide;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * The bundled speedrun guides, in the wiki's order (shortest quests first). Only quests with a
 * hand-checked route are offered for now; the others stay in guides.json until their routes are done.
 */
final class GuideLibrary
{
	private final List<Guide> guides;

	private static class Root
	{
		List<Guide> quests;
	}

	GuideLibrary(List<Guide> guides)
	{
		this.guides = Collections.unmodifiableList(guides);
	}

	/** The quests that have a route (quest progress, paths and highlights). */
	static GuideLibrary load(Gson gson)
	{
		return new GuideLibrary(loadAll(gson).all().stream()
			.filter(g -> g.getProgress() != null)
			.collect(Collectors.toList()));
	}

	/** Every bundled guide, routed or not. */
	static GuideLibrary loadAll(Gson gson)
	{
		try (InputStream in = GuideLibrary.class.getResourceAsStream("guides.json");
			Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			return new GuideLibrary(gson.fromJson(reader, Root.class).quests);
		}
		catch (IOException e)
		{
			throw new IllegalStateException("guides.json is missing from the plugin jar", e);
		}
	}

	List<Guide> all()
	{
		return guides;
	}

	@Nullable
	Guide byName(@Nullable String name)
	{
		for (Guide g : guides)
		{
			if (g.getName().equals(name))
			{
				return g;
			}
		}
		return null;
	}
}
