package com.speedrunguide;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.WorldType;
import net.runelite.api.ChatMessageType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;

/**
 * Walks through the OSRS Wiki speedrun guide for a Quest Speedrunning quest, one step at a time.
 * The game keeps the time; this only says what to do next. Everything, the sidebar button
 * included, is only active while logged in to a speedrunning world.
 * <p>
 * Threading: {@link #guide} and {@link #step} are read by the overlays on the client thread and
 * changed from both the client thread (quest progress, dialogue) and the Swing thread (panel
 * clicks), so they are volatile and every change goes through {@link #setStep}.
 */
@Slf4j
@PluginDescriptor(
	name = "Speedrun Quest Helper",
	description = "OSRS Wiki speedrun routes for Quest Speedrunning, step by step, with dialogue option highlights",
	tags = {"quest", "speedrun", "speedrunning", "guide", "helper", "trophy", "route", "wiki"}
)
public class SpeedrunGuidePlugin extends Plugin
{
	private static final String SELECTED_KEY = "quest";
	private static final String STEP_KEY_PREFIX = "step.";
	// Short options like "Yes." show up in lots of conversations, so they don't move the guide on
	private static final int MIN_ADVANCE_OPTION_LENGTH = 6;
	private static final int ADVANCE_LOOKAHEAD = 2;
	private static final int AUTO_SELECT_TICKS = 5;
	// Steps whose inventory check is looked at ahead of the current one, so steps without a check get passed
	private static final int DONE_LOOKAHEAD = 2;

	@Inject
	private Client client;

	@Inject
	private SpeedrunGuideConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private KeyManager keyManager;

	@Inject
	private Gson gson;

	@Inject
	private StepOverlay stepOverlay;

	@Inject
	private DialogueOverlay dialogueOverlay;

	@Inject
	private RouteOverlay routeOverlay;

	@Inject
	private ItemOverlay itemOverlay;

	@Inject
	private RouteTracker route;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private DigLog digLog;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Getter
	private GuideLibrary library;
	private GuidePanel panel;
	private NavigationButton navButton;

	@Getter
	@Nullable
	private volatile Guide guide;
	/** Index into the guide's steps; equal to the step count once the run is done. */
	@Getter
	private volatile int step;
	/** Logged in to a speedrunning world. */
	@Getter
	private volatile boolean active;

	/** The guide's quest state when last checked; null until the first check after login or a guide change. */
	@Nullable
	private QuestState questState;
	/** The guide's quest progress value when last read; null until the first read after login or a guide change. */
	@Nullable
	private Integer questProgress;
	/** The dialogue options seen last tick, so one dialogue only moves the guide once. */
	private List<String> shownOptions = Collections.emptyList();
	private int ticks;
	/** The tile you were on last tick and for how many ticks, for {@link #logStandingTile}. */
	@Nullable
	private WorldPoint lastTile;
	private int stillTicks;

	private final HotkeyListener nextListener = new HotkeyListener(() -> config.nextKey())
	{
		@Override
		public void hotkeyPressed()
		{
			if (active)
			{
				next();
			}
		}
	};

	private final HotkeyListener previousListener = new HotkeyListener(() -> config.previousKey())
	{
		@Override
		public void hotkeyPressed()
		{
			if (active)
			{
				previous();
			}
		}
	};

	@Provides
	SpeedrunGuideConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SpeedrunGuideConfig.class);
	}

	@Override
	protected void startUp()
	{
		library = GuideLibrary.load(gson);
		Guide saved = library.byName(configManager.getConfiguration(SpeedrunGuideConfig.GROUP, SELECTED_KEY));
		guide = saved != null ? saved : library.all().get(0);
		step = savedStep(guide);

		panel = new GuidePanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Speedrun Quest Helper")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(8)
			.panel(panel)
			.build();

		overlayManager.add(stepOverlay);
		overlayManager.add(dialogueOverlay);
		overlayManager.add(routeOverlay);
		overlayManager.add(itemOverlay);
		keyManager.registerKeyListener(nextListener);
		keyManager.registerKeyListener(previousListener);
		panel.showGuide(guide, step);
		clientThread.invokeLater(this::updateActive);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(nextListener);
		keyManager.unregisterKeyListener(previousListener);
		overlayManager.remove(stepOverlay);
		overlayManager.remove(dialogueOverlay);
		overlayManager.remove(routeOverlay);
		overlayManager.remove(itemOverlay);
		clientToolbar.removeNavigation(navButton);
		active = false;
		questState = null;
		questProgress = null;
		shownOptions = Collections.emptyList();
		clientThread.invokeLater(route::sceneCleared);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			// A new login or world may be another profile; start the quest comparison over. Loading
			// screens keep it, as resetting a speedrun teleports you through one.
			questState = null;
			questProgress = null;
		}
		if (state == GameState.LOADING)
		{
			route.sceneCleared();
		}
		if (state == GameState.LOGGED_IN || state == GameState.LOGIN_SCREEN)
		{
			updateActive();
		}
	}

	/** Shows the sidebar button and turns the guide on only on speedrunning worlds. */
	private void updateActive()
	{
		boolean now = client.getGameState() == GameState.LOGGED_IN && isSpeedrunWorld();
		if (now == active)
		{
			return;
		}
		active = now;
		if (now)
		{
			clientToolbar.addNavigation(navButton);
		}
		else
		{
			clientToolbar.removeNavigation(navButton);
			route.clear();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!active)
		{
			return;
		}
		logStandingTile();
		if (config.autoSelect() && ++ticks % AUTO_SELECT_TICKS == 0)
		{
			selectRunningQuest();
		}
		if (config.followQuest())
		{
			followQuest();
		}
		if (config.advanceOnDialogue())
		{
			advanceOnDialogue();
		}
		Set<Integer> inventory = inventoryIds();
		DigLog.Result dig = digLog.tick(inventory.contains(digLog.pendingItem()));
		if (dig != null && config.logDigs())
		{
			WorldPoint tile = digLog.lastTile();
			chat("Dig at " + tile.getX() + ", " + tile.getY() + (dig == DigLog.Result.GOOD ? " worked." : " missed."));
		}
		if (config.advanceWhenDone())
		{
			advanceWhenDone(inventory);
		}
		Guide g = guide;
		route.update(currentStep(), g == null ? null : g.step(step + 1), config.showPath());
	}

	/**
	 * Logs the tile you stop on (developer logs only), which is how route tiles such as "wait
	 * here" spots are found: stand on the tile and read it from the log.
	 */
	private void logStandingTile()
	{
		Player player = client.getLocalPlayer();
		if (!log.isDebugEnabled() || player == null)
		{
			return;
		}
		WorldPoint here = player.getWorldLocation();
		if (here.equals(lastTile))
		{
			if (++stillTicks == 2)
			{
				Guide.Step now = currentStep();
				log.debug("Standing at {}, {}, {} (step {}: {})", here.getX(), here.getY(), here.getPlane(), step,
					now == null ? "-" : GuideText.plain(now.getText()));
			}
		}
		else
		{
			lastTile = here;
			stillTicks = 0;
		}
	}

	/** Moves past steps whose item is now in the inventory, or whose spot has been reached. */
	private void advanceWhenDone(Set<Integer> ids)
	{
		Guide g = guide;
		if (g == null)
		{
			return;
		}
		int current = step;
		Guide.Step now = g.step(current);
		Guide.Done own = now == null ? null : now.getDone();
		Set<String> names = new HashSet<>();
		for (int id : ids)
		{
			names.add(GuideText.normalize(itemManager.getItemComposition(id).getName()));
		}

		// Checks that could already be true before reaching the step only count for the current one
		WorldPoint target = route.getTarget();
		Player player = client.getLocalPlayer();
		if (own != null && ((own.getArrive() > 0 && target != null && player != null
			&& player.getWorldLocation().getPlane() == target.getPlane()
			&& player.getWorldLocation().distanceTo2D(target) <= own.getArrive())
			|| (own.getLack() != null && !names.contains(GuideText.normalize(own.getLack())))
			|| (own.getVarbit() != null && own.varbitDone(client.getVarbitValue(own.getVarbit())))
			|| (player != null && own.inside(player.getWorldLocation().getX(), player.getWorldLocation().getY(),
				player.getWorldLocation().getPlane()))))
		{
			setStep(current + 1);
			return;
		}
		for (int i = current + DONE_LOOKAHEAD; i >= current; i--)
		{
			Guide.Step s = g.step(i);
			Guide.Done done = s == null ? null : s.getDone();
			if (done != null && ((done.getHaveId() > 0 && ids.contains(done.getHaveId()))
				|| (done.getHave() != null && names.contains(GuideText.normalize(done.getHave())))))
			{
				setStep(i + 1);
				return;
			}
		}
	}

	private Set<Integer> inventoryIds()
	{
		Set<Integer> ids = new HashSet<>();
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				if (item.getId() > 0)
				{
					ids.add(item.getId());
				}
			}
		}
		return ids;
	}

	/** Records digs during a dig step, so {@link DigLog} can learn which tiles work. */
	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Player player = client.getLocalPlayer();
		Guide.Step now = currentStep();
		if (!active || event.getActor() != player || player.getAnimation() != AnimationID.HUMAN_DIG
			|| now == null || now.getDone() == null || now.getDone().getHaveId() <= 0)
		{
			return;
		}
		digLog.dug(player.getWorldLocation(), now.getDone().getHaveId());
	}

	private void chat(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(new ChatMessageBuilder().append("[Speedrun Quest Helper] " + message).build())
			.build());
	}

	/** Colors the step's option (such as Empty) on the items it says to use, so it stands out in the right-click menu. */
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		Guide.Step now = currentStep();
		if (!active || !config.highlightShop() || now == null || now.getUseOption() == null)
		{
			return;
		}
		String color = ColorUtil.colorToHexCode(config.highlightColor());
		for (MenuEntry e : event.getMenuEntries())
		{
			if (now.getUseOption().equals(e.getOption()) && GuideText.matchesName(now.getUse(), e.getTarget()))
			{
				e.setOption("<col=" + color + ">" + e.getOption() + "</col>");
			}
		}
	}

	/**
	 * Makes Buy 1 the left click on the shop items the step says to buy, and the step's option
	 * (such as Trade) the left click on its NPCs.
	 */
	@Subscribe
	public void onPostMenuSort(PostMenuSort event)
	{
		Guide.Step now = currentStep();
		if (!active || !config.leftClickBuy() || client.isMenuOpen() || now == null
			|| (now.getShop().isEmpty() && now.getLeftClick() == null))
		{
			return;
		}
		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		// The last entry is the left click
		for (int i = 0; i < entries.length - 1; i++)
		{
			MenuEntry e = entries[i];
			boolean buy = "Buy 1".equals(e.getOption()) && GuideText.matchesName(now.getShop(), e.getTarget());
			boolean npcOption = now.getLeftClick() != null && now.getLeftClick().equals(e.getOption())
				&& e.getNpc() != null && Guide.wantsNpc(now, e.getNpc().getName(), e.getNpc().getId());
			if (buy || npcOption)
			{
				MenuEntry top = entries[entries.length - 1];
				entries[entries.length - 1] = e;
				entries[i] = top;
				menu.setMenuEntries(entries);
				return;
			}
		}
	}

	@Nullable
	Guide.Step currentStep()
	{
		Guide g = guide;
		return g == null ? null : g.step(step);
	}

	boolean isSpeedrunWorld()
	{
		return client.getWorldType().contains(WorldType.QUEST_SPEEDRUNNING);
	}

	/** Switches to the guide whose quest is in progress, as only the quest being run is on a speedrun profile. */
	private void selectRunningQuest()
	{
		Guide current = guide;
		if (current != null && current.quest() != null && current.quest().getState(client) == QuestState.IN_PROGRESS)
		{
			return;
		}
		for (Guide g : library.all())
		{
			Quest q = g.quest();
			if (g != current && q != null && q.getState(client) == QuestState.IN_PROGRESS)
			{
				log.debug("Speedrun of {} detected", g.getName());
				selectGuide(g);
				return;
			}
		}
	}

	private void followQuest()
	{
		Guide g = guide;
		Quest q = g == null ? null : g.quest();
		if (q == null)
		{
			return;
		}
		QuestState now = q.getState(client);
		QuestState before = questState;
		questState = now;
		if (g.getProgress() != null)
		{
			followProgress(g);
		}
		if (before == null || before == now)
		{
			return;
		}

		if (now == QuestState.FINISHED)
		{
			setStep(g.steps().size());
		}
		else if (g.getProgress() != null)
		{
			// Starting and resetting are covered by the progress value
			return;
		}
		else if (now == QuestState.NOT_STARTED)
		{
			// The speedrun was reset or abandoned
			setStep(g.getStartStep());
		}
		else if (before == QuestState.NOT_STARTED && step <= g.getStartStep())
		{
			setStep(g.getStartStep() + 1);
		}
	}

	/**
	 * Keeps the guide in step with the quest's progress value, the way Quest Helper follows a quest.
	 * This also notices a speedrun reset, which puts the value back to the start.
	 */
	private void followProgress(Guide g)
	{
		Guide.Progress var = g.getProgress();
		int value = var.getVarbit() != null ? client.getVarbitValue(var.getVarbit()) : client.getVarpValue(var.getVarp());
		Integer before = questProgress;
		questProgress = value;
		if (before != null && before == value)
		{
			return;
		}
		int at = g.stepForProgress(value);
		if (at < 0)
		{
			return;
		}
		int[] allowed = g.progressRange(at);
		int current = step;
		if (current < allowed[0] || current >= allowed[1])
		{
			setStep(at);
		}
	}

	private void advanceOnDialogue()
	{
		List<String> options = dialogueOptions();
		if (options.equals(shownOptions))
		{
			return;
		}
		shownOptions = options;

		Guide g = guide;
		int current = step;
		Guide.Step now = g == null ? null : g.step(current);
		if (now == null || options.isEmpty() || options.stream().anyMatch(o -> GuideText.matches(now.getChat(), o)))
		{
			return;
		}
		for (int i = current + 1; i <= current + ADVANCE_LOOKAHEAD; i++)
		{
			Guide.Step ahead = g.step(i);
			if (ahead == null)
			{
				return;
			}
			for (String o : options)
			{
				if (GuideText.normalize(o).length() >= MIN_ADVANCE_OPTION_LENGTH && GuideText.matches(ahead.getChat(), o))
				{
					setStep(i);
					return;
				}
			}
		}
	}

	/** The option widgets of the open dialogue menu, or an empty list if none is open. */
	List<Widget> dialogueOptionWidgets()
	{
		Widget menu = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
		if (menu == null || menu.isHidden())
		{
			return Collections.emptyList();
		}
		Widget[] children = menu.getDynamicChildren();
		List<Widget> options = new ArrayList<>();
		// The first child is the "Select an option" title
		for (int i = 1; children != null && i < children.length; i++)
		{
			Widget w = children[i];
			if (w != null && !w.isHidden() && w.getText() != null && !w.getText().isEmpty())
			{
				options.add(w);
			}
		}
		return options;
	}

	private List<String> dialogueOptions()
	{
		List<String> text = new ArrayList<>();
		for (Widget w : dialogueOptionWidgets())
		{
			text.add(w.getText());
		}
		return text;
	}

	void selectGuide(Guide g)
	{
		guide = g;
		step = savedStep(g);
		questState = null;
		questProgress = null;
		shownOptions = Collections.emptyList();
		configManager.setConfiguration(SpeedrunGuideConfig.GROUP, SELECTED_KEY, g.getName());
		SwingUtilities.invokeLater(() -> panel.showGuide(g, step));
	}

	void setStep(int index)
	{
		Guide g = guide;
		if (g == null)
		{
			return;
		}
		int clamped = Math.max(0, Math.min(index, g.steps().size()));
		step = clamped;
		configManager.setConfiguration(SpeedrunGuideConfig.GROUP, STEP_KEY_PREFIX + GuideText.normalize(g.getName()), clamped);
		SwingUtilities.invokeLater(() -> panel.showStep(g, clamped));
	}

	void next()
	{
		setStep(step + 1);
	}

	void previous()
	{
		setStep(step - 1);
	}

	/** Back to the step that starts the quest, for a fresh attempt. */
	void restart()
	{
		Guide g = guide;
		if (g != null)
		{
			setStep(g.getStartStep());
		}
	}

	private int savedStep(Guide g)
	{
		Integer saved = configManager.getConfiguration(SpeedrunGuideConfig.GROUP, STEP_KEY_PREFIX + GuideText.normalize(g.getName()), Integer.class);
		return saved == null ? 0 : Math.max(0, Math.min(saved, g.steps().size()));
	}
}
