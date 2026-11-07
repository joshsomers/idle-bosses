package com.idlebosses;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

@Slf4j
@PluginDescriptor(
	name = "Idle Bosses",
	description = "Every XP drop hits a boss on screen. Kills roll its drop table and fill collection logs that unlock bigger bosses",
	tags = {"skilling", "xp", "boss", "game", "fun", "collection", "log", "loot", "pet"}
)
public class IdleBossesPlugin extends Plugin
{
	private static final String PROGRESS_KEY = "progress";
	// Set once a sidebar tab's guide has been read.
	private static final String FIGHT_GUIDE_KEY = "fightGuideRead";
	private static final String UPGRADES_GUIDE_KEY = "upgradesGuideRead";
	private static final String DROPS_RESOURCE = "drops.json";
	private static final int SUPERIOR_CHANCE = 30;
	// A huge drop's kills are spread over game ticks so the client doesn't stall. Nothing is lost.
	private static final int MAX_KILLS_PER_TICK = 200;
	// Shorter streaks end quietly.
	private static final int STREAK_WORTH_MENTIONING = 5;
	// Our own slot for the level-up fireworks on the player, so it doesn't replace the game's spot animations.
	private static final int FIREWORKS_SLOT = 0x5B05;
	private static final Color CHAT_TAG_COLOR = new Color(255, 152, 31);
	private static final Color RARE_COLOR = new Color(224, 112, 255);
	private static final Color GOOD_COLOR = new Color(0, 160, 0);
	private static final long POISON_MILLIS = 5_000;
	// Between the hits of a special like the Dragon claws'.
	private static final long STAGGER_MILLIS = 120;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private IdleBossesConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BossHudOverlay hudOverlay;

	@Inject
	private BossSceneOverlay sceneOverlay;

	@Inject
	private MiniBoss miniBoss;

	@Inject
	private BossPortrait portrait;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	@Inject
	private SplatSprites splatSprites;

	@Inject
	private KeyManager keyManager;

	private final Random random = new Random();
	private final int[] xp = new int[Skill.values().length];

	private DropData drops;
	private LootRoller roller;
	// Each boss's chance of each item per kill. The tables never change, so it's worked out once.
	private final Map<Boss, Map<Integer, Double>> rates = new EnumMap<>(Boss.class);
	private IdleBossesPanel panel;
	private NavigationButton navButton;
	private Loadout loadout = Loadout.of(new Progress());
	// Game ticks since the cannon last fired, counted while you train.
	private int cannonTicks;
	private long lastPoisonMillis;
	// For the sidebar, which reads it between rebuilds.
	private volatile int cannonLoaded;

	private final HotkeyListener reloadHotkey = new HotkeyListener(() -> config.reloadCannonKey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(IdleBossesPlugin.this::reloadCannon);
		}
	};

	@Getter
	private final BossFight fight = new BossFight();
	@Getter
	private Progress progress = new Progress();
	// The RuneScape profile the progress was loaded from.
	private String progressProfile;
	@Getter
	private boolean ready;
	@Getter
	private Skill lastSkill;
	// The log items the last kills brought, which the HUD frames, and which of them are rare.
	@Getter
	private Set<Integer> lastLogItems = Collections.emptySet();
	@Getter
	private Set<Integer> lastRareLogItems = Collections.emptySet();
	// The last weapon effect to trigger, for the HUD's banner.
	@Getter
	private Special special;
	// A special's later hits, which land a beat apart like the real thing.
	private final List<QueuedHit> queuedHits = new ArrayList<>();

	@Provides
	IdleBossesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(IdleBossesConfig.class);
	}

	@Override
	protected void startUp()
	{
		drops = loadDrops();
		roller = new LootRoller(drops, random);
		rates.clear();

		splatSprites.load(spriteManager);
		panel = new IdleBossesPanel(itemManager, skillIconManager, portrait, splatSprites, () -> cannonLoaded, unreadGuides(),
			new PanelActions());
		navButton = NavigationButton.builder()
			.tooltip("Idle Bosses")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(hudOverlay);
		overlayManager.add(sceneOverlay);
		keyManager.registerKeyListener(reloadHotkey);
		// Progress loads on the first game tick, once the account is known.
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(hudOverlay);
		overlayManager.remove(sceneOverlay);
		keyManager.unregisterKeyListener(reloadHotkey);
		clientToolbar.removeNavigation(navButton);
		clientThread.invoke(() ->
		{
			if (ready)
			{
				saveProgress();
			}
			miniBoss.shutDown();
			portrait.clear();
			sceneOverlay.clear();
			ready = false;
		});
		IdleBossesPanel oldPanel = panel;
		SwingUtilities.invokeLater(oldPanel::shutDown);
		panel = null;
		navButton = null;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		clientThread.invoke(() ->
		{
			if (ready)
			{
				loadProgress();
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			ready = false;
			queuedHits.clear();
			miniBoss.set(null, MiniBoss.Variant.NORMAL, config.miniBossSize(), false);
			portrait.clear();
			sceneOverlay.clear();
			pushPanel();
		}
		else if (state == GameState.LOADING)
		{
			// The scene is about to move, which would leave the boss somewhere else. It comes back on the next tick.
			miniBoss.despawn();
			sceneOverlay.clear();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!ready)
		{
			// Stats arrive by the first tick after logging in. Before that every skill looks like 0 XP.
			int[] experiences = client.getSkillExperiences();
			System.arraycopy(experiences, 0, xp, 0, Math.min(xp.length, experiences.length));
			ready = true;
			loadProgress();
		}

		miniBoss.gameTick();
		long now = System.currentTimeMillis();
		autoAttacks(now);
		// Anything a huge drop couldn't land in one go.
		if (fight.getPending() > 0)
		{
			landDamage();
		}
		int lost = fight.expireStreak(now);
		if (lost >= STREAK_WORTH_MENTIONING)
		{
			chat(new ChatMessageBuilder().append(ChatColorType.NORMAL)
				.append("Your " + lost + " kill streak ended after a minute without XP."));
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		miniBoss.clientTick();
		portrait.clientTick();
		landQueuedHits(System.currentTimeMillis());
	}

	private void landQueuedHits(long now)
	{
		boolean landed = false;
		for (Iterator<QueuedHit> it = queuedHits.iterator(); it.hasNext(); )
		{
			QueuedHit queued = it.next();
			if (queued.getDueMillis() <= now)
			{
				it.remove();
				showHit(queued.getHit());
				landed = true;
			}
		}
		if (landed)
		{
			miniBoss.hit();
			landDamage();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		int index = skill.ordinal();
		if (index >= xp.length)
		{
			return;
		}
		int previous = xp[index];
		xp[index] = event.getXp();
		if (!ready || previous <= 0 || event.getXp() <= previous)
		{
			return;
		}

		int gained = event.getXp() - previous;
		if (SkillRates.xpPerHour(skill) <= 0)
		{
			return;
		}
		lastSkill = skill;
		long base = fight.baseDamage(skill, gained);
		if (base <= 0)
		{
			return;
		}

		long now = System.currentTimeMillis();
		fight.xpGained(now);
		Loadout.Attack attack = loadout.attack(base, fight, random);
		List<Hit> hits = attack.getHits();
		for (int i = 0; i < hits.size(); i++)
		{
			if (i > 0 && i < attack.getStaggered())
			{
				queuedHits.add(new QueuedHit(hits.get(i), now + i * STAGGER_MILLIS));
			}
			else
			{
				showHit(hits.get(i));
			}
		}
		if (attack.getSpecial() != null)
		{
			special = new Special(loadout.weapon(), attack.getSpecial(), now);
		}
		miniBoss.hit();
		landDamage();
	}

	// The cannon and poison, which keep hitting while you train.
	private void autoAttacks(long now)
	{
		Boss boss = fight.getBoss();
		if (!ready || boss == null || !fight.isActive(now))
		{
			return;
		}
		Progress.CannonState cannon = progress.cannon;
		cannonTicks = Math.min(cannonTicks + 1, Shop.CANNON_FIRE_TICKS);
		if (cannon.loaded > 0 && cannonTicks >= Shop.CANNON_FIRE_TICKS)
		{
			cannonTicks = 0;
			int before = cannon.loaded;
			Hit shot = Shop.fire(progress, boss, loadout.cannonballSaveChance(), random);
			if (shot != null)
			{
				showHit(shot);
				if (cannon.loaded == before)
				{
					special = new Special(Gear.KODAI_WAND, "Saved", now);
				}
				cannonLoaded = cannon.loaded;
				if (cannon.loaded == 0)
				{
					chat(new ChatMessageBuilder().append(ChatColorType.NORMAL).append("Your cannon is out of ammo!"));
				}
			}
		}
		if (fight.isPoisoned() && now - lastPoisonMillis >= POISON_MILLIS)
		{
			lastPoisonMillis = now;
			showHit(new Hit(Loadout.poisonDamage(boss), Hit.Type.POISON));
		}
	}

	// Adds a hit's damage and shows its splat. It lands with the next landDamage().
	private void showHit(Hit hit)
	{
		fight.addDamage(hit.getDamage());
		int shown = (int) Math.min(Integer.MAX_VALUE, hit.getDamage());
		sceneOverlay.addSplat(shown, hit.getType());
		portrait.hit(shown, hit.getType());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (IdleBossesConfig.GROUP.equals(event.getGroup()))
		{
			clientThread.invoke(this::showMiniBoss);
		}
	}

	// "Dropped at KC 41", naming the boss when it came from another one, or null if it wasn't kept.
	private static String dropped(Boss boss, Progress.LogDrop drop)
	{
		if (drop == null)
		{
			return null;
		}
		Boss from = Boss.byId(drop.boss);
		String kc = "KC " + QuantityFormatter.formatNumber(drop.kills);
		return from == null || from == boss ? "Dropped at " + kc : "Dropped by " + from.getDisplayName() + " at " + kc;
	}

	// The boss's collection log, in the game's order.
	List<Integer> logItems(Boss boss)
	{
		DropData.BossDrops bossDrops = drops == null ? null : drops.boss(boss);
		return bossDrops == null ? Collections.emptyList() : bossDrops.log;
	}

	boolean isLogComplete(Boss boss)
	{
		DropData.BossDrops bossDrops = drops.boss(boss);
		return bossDrops != null && !bossDrops.log.isEmpty() && progress.logCount(bossDrops.log) >= bossDrops.log.size();
	}

	// Lands the damage waiting in the fight. One big drop can kill several bosses in a row; each rolls its loot.
	private void landDamage()
	{
		KillRun run = null;
		while (run == null || run.kills < MAX_KILLS_PER_TICK)
		{
			Boss boss = fight.getBoss();
			boolean killed = fight.land(loadout.executeBelow());
			long executed = fight.takeExecuted();
			if (executed > 0)
			{
				// The Voidwaker's finishing blow.
				int shown = (int) Math.min(Integer.MAX_VALUE, executed);
				sceneOverlay.addSplat(shown, Hit.Type.CRITICAL);
				portrait.hit(shown, Hit.Type.CRITICAL);
				special = new Special(Gear.VOIDWAKER, "Finished", System.currentTimeMillis());
			}
			if (!killed)
			{
				break;
			}
			if (run == null)
			{
				run = new KillRun(boss, unlockedBosses(), isLogComplete(boss));
			}
			kill(run);
		}
		if (run != null)
		{
			finishKills(run);
		}
	}

	private void kill(KillRun run)
	{
		Boss boss = fight.getBoss();
		DropData.BossDrops bossDrops = drops.boss(boss);
		boolean superior = fight.isSuperior();
		progress.addKill(boss);
		run.kills++;
		run.superiors += superior ? 1 : 0;

		List<Loot> loot = bossDrops == null ? Collections.emptyList() : roller.roll(bossDrops, superior);
		Set<Integer> logIds = bossDrops == null ? Collections.emptySet() : new LinkedHashSet<>(bossDrops.log);
		for (Loot item : loot)
		{
			run.value += itemManager.getItemPrice(item.getItemId()) * (long) item.getQuantity();
			progress.addLoot(boss, item.getItemId(), item.getQuantity());
			if (!logIds.contains(item.getItemId()) || !progress.addLogItem(item.getItemId(), boss))
			{
				continue;
			}
			run.newLogItems.add(item.getItemId());
			double chance = rates(boss).getOrDefault(item.getItemId(), item.getChance());
			boolean rareItem = chance <= Loot.RARE_CHANCE;
			run.rare |= rareItem;
			chat(new ChatMessageBuilder()
				.append(ChatColorType.NORMAL)
				.append("New collection log item: ")
				.append(rareItem ? RARE_COLOR : CHAT_TAG_COLOR, item.getName())
				.append(ChatColorType.NORMAL)
				.append(" (" + DropRates.format(chance) + ")"));
		}
		run.loot.addAll(loot);

		fight.start(boss, random.nextInt(SUPERIOR_CHANCE) == 0);
	}

	private void finishKills(KillRun run)
	{
		Boss boss = run.boss;
		progress.addLootValue(run.value);
		showLoot(LootRoller.merge(run.loot), run.newLogItems);

		String what = run.kills == 1 && run.superiors == 1 ? "superior " + boss.getDisplayName() : boss.getDisplayName();
		String extra = "";
		if (run.kills > 1)
		{
			extra = " (" + run.kills + " kills from one XP drop"
				+ (run.superiors > 0 ? ", " + run.superiors + " of them superior" : "") + ")";
		}
		chat(new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append("Your " + what + " kill count is: ")
			.append(Color.RED, String.valueOf(progress.kills(boss)))
			.append(ChatColorType.NORMAL)
			.append("." + extra));

		if (run.rare)
		{
			celebrate();
		}
		if (!run.logCompleteBefore && isLogComplete(boss))
		{
			chat(new ChatMessageBuilder()
				.append(ChatColorType.NORMAL)
				.append(boss.getDisplayName() + " collection log complete. Your " + boss.getDisplayName() + " is gold now."));
		}
		for (Boss unlocked : Boss.values())
		{
			if (!run.unlockedBefore.contains(unlocked) && progress.isUnlocked(unlocked))
			{
				chat(new ChatMessageBuilder()
					.append(GOOD_COLOR, unlocked.getDisplayName() + " unlocked!")
					.append(ChatColorType.NORMAL)
					.append(" Pick it in the Idle Bosses panel."));
			}
		}

		Boss next = fight.getBoss();
		MiniBoss.Variant variant = variant(next, fight.isSuperior());
		miniBoss.kill(next, variant);
		portrait.kill(next, variant);
		if (fight.isSuperior())
		{
			chat(new ChatMessageBuilder().append(RARE_COLOR, "A superior " + next.getDisplayName() + " has appeared!"));
		}

		saveProgress();
		pushPanel();
	}

	// A weapon effect that triggered, and when.
	@Value
	static class Special
	{
		Gear weapon;
		// What it did, like "x4".
		String label;
		long millis;
	}

	@Value
	private static class QueuedHit
	{
		Hit hit;
		long dueMillis;
	}

	// The kills one landing of damage caused, gathered so chat and the HUD show them together.
	private static final class KillRun
	{
		final Boss boss;
		final Set<Boss> unlockedBefore;
		final boolean logCompleteBefore;
		final List<Loot> loot = new ArrayList<>();
		final Set<Integer> newLogItems = new LinkedHashSet<>();
		int kills;
		int superiors;
		long value;
		boolean rare;

		KillRun(Boss boss, Set<Boss> unlockedBefore, boolean logCompleteBefore)
		{
			this.boss = boss;
			this.unlockedBefore = unlockedBefore;
			this.logCompleteBefore = logCompleteBefore;
		}
	}

	private Set<Boss> unlockedBosses()
	{
		Set<Boss> unlocked = EnumSet.noneOf(Boss.class);
		for (Boss boss : Boss.values())
		{
			if (progress.isUnlocked(boss))
			{
				unlocked.add(boss);
			}
		}
		return unlocked;
	}

	private void showLoot(List<Loot> loot, Set<Integer> newLogItems)
	{
		Set<Integer> rare = new HashSet<>();
		for (Loot item : loot)
		{
			if (newLogItems.contains(item.getItemId()) && item.isRare())
			{
				rare.add(item.getItemId());
			}
		}
		lastLogItems = newLogItems;
		lastRareLogItems = rare;
	}

	// The level-up fireworks and a ding, for a pet or a rare log item.
	private void celebrate()
	{
		Player player = client.getLocalPlayer();
		if (player != null)
		{
			player.createSpotAnim(FIREWORKS_SLOT, SpotanimID.LEVELUP_ANIM, 0, 0);
		}
		if (config.rareDropSound())
		{
			client.playSoundEffect(SoundEffectID.GE_ADD_OFFER_DINGALING);
		}
	}

	private void switchBoss(Boss boss)
	{
		if (!ready || !progress.isUnlocked(boss) || boss == fight.getBoss())
		{
			return;
		}
		progress.currentBoss = boss.getId();
		fight.start(boss, false);
		sceneOverlay.clear();
		showMiniBoss();
		saveProgress();
		pushPanel();
	}

	private void startCurrentBoss()
	{
		Boss boss = Boss.byId(progress.currentBoss);
		if (boss == null || !progress.isUnlocked(boss) || drops.boss(boss) == null)
		{
			boss = Boss.values()[0];
		}
		progress.currentBoss = boss.getId();
		fight.start(boss, false);
	}

	// Shows the current boss in the HUD and sidebar, and walking in the world if that's turned on.
	private void showMiniBoss()
	{
		Boss boss = fight.getBoss();
		MiniBoss.Variant variant = boss == null ? MiniBoss.Variant.NORMAL : variant(boss, fight.isSuperior());
		miniBoss.set(boss, variant, config.miniBossSize(), ready && config.showMiniBoss());
		if (ready && boss != null)
		{
			portrait.set(boss, variant);
		}
		else
		{
			portrait.clear();
		}
	}

	private MiniBoss.Variant variant(Boss boss, boolean superior)
	{
		if (superior)
		{
			return MiniBoss.Variant.SUPERIOR;
		}
		return isLogComplete(boss) ? MiniBoss.Variant.GOLD : MiniBoss.Variant.NORMAL;
	}

	private void chat(ChatMessageBuilder message)
	{
		if (!config.chatMessages())
		{
			return;
		}
		String text = new ChatMessageBuilder()
			.append(CHAT_TAG_COLOR, "[Idle Bosses] ")
			.build() + message.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(text)
			.build());
	}

	private void loadProgress()
	{
		String json = configManager.getRSProfileConfiguration(IdleBossesConfig.GROUP, PROGRESS_KEY);
		Progress loaded = null;
		if (json != null)
		{
			try
			{
				loaded = gson.fromJson(json, Progress.class);
				loaded = loaded == null ? null : loaded.migrate();
			}
			catch (JsonParseException e)
			{
				log.warn("Couldn't read saved Idle Bosses progress", e);
			}
		}
		progress = loaded != null ? loaded : new Progress().migrate();
		progressProfile = configManager.getRSProfileKey();
		loadout = Loadout.of(progress);
		cannonLoaded = progress.cannon.loaded;
		fight.clearPending();
		queuedHits.clear();
		special = null;
		startCurrentBoss();
		lastLogItems = Collections.emptySet();
		lastRareLogItems = Collections.emptySet();
		sceneOverlay.clear();
		if (ready)
		{
			showMiniBoss();
		}
		pushPanel();
	}

	private void saveProgress()
	{
		// Never write one account's progress over another's.
		String profile = configManager.getRSProfileKey();
		if (profile == null || !profile.equals(progressProfile))
		{
			return;
		}
		configManager.setRSProfileConfiguration(IdleBossesConfig.GROUP, PROGRESS_KEY, gson.toJson(progress));
	}

	private void pushPanel()
	{
		IdleBossesPanel target = panel;
		if (target == null)
		{
			return;
		}
		IdleBossesPanel.State state = ready ? panelState() : null;
		SwingUtilities.invokeLater(() -> target.setState(state));
	}

	private IdleBossesPanel.State panelState()
	{
		List<IdleBossesPanel.Row> rows = new ArrayList<>();
		for (Boss boss : Boss.values())
		{
			DropData.BossDrops bossDrops = drops.boss(boss);
			if (bossDrops == null)
			{
				continue;
			}
			Map<Integer, String> names = itemNames(bossDrops);
			List<IdleBossesPanel.LogItem> log = new ArrayList<>();
			Set<Integer> owned = new LinkedHashSet<>();
			for (int itemId : bossDrops.log)
			{
				log.add(new IdleBossesPanel.LogItem(itemId, names.getOrDefault(itemId, "Unknown item"),
					rates(boss).getOrDefault(itemId, 0.0), dropped(boss, progress.logDrop(itemId))));
				if (progress.hasLogItem(itemId))
				{
					owned.add(itemId);
				}
			}

			List<IdleBossesPanel.LootItem> loot = new ArrayList<>();
			for (Map.Entry<Integer, Long> entry : progress.loot(boss).entrySet())
			{
				int itemId = entry.getKey();
				long quantity = entry.getValue();
				loot.add(new IdleBossesPanel.LootItem(itemId, names.getOrDefault(itemId, "Unknown item"), quantity,
					itemManager.getItemPrice(itemId) * quantity));
			}
			// Most valuable first, like the loot tracker.
			loot.sort(Comparator.comparingLong(IdleBossesPanel.LootItem::getValue).reversed()
				.thenComparing(Comparator.comparingLong(IdleBossesPanel.LootItem::getQuantity).reversed()));
			int icon = bossDrops.log.isEmpty() ? -1 : bossDrops.log.get(0);
			rows.add(new IdleBossesPanel.Row(boss, icon, progress.isUnlocked(boss), boss == fight.getBoss(),
				progress.kills(boss), boss.previous() == null ? 0 : progress.kills(boss.previous()), log, owned, loot));
		}
		return new IdleBossesPanel.State(rows, progress.totalKills(), progress.totalSlots(), progress.gpLooted, upgradesState());
	}

	private IdleBossesPanel.Upgrades upgradesState()
	{
		Loadout worn = Loadout.of(progress);
		Gear amulet = progress.best(Gear.Slot.AMULET);
		Gear gloves = progress.best(Gear.Slot.GLOVES);
		List<IdleBossesPanel.GearRow> gear = new ArrayList<>();
		for (Gear item : Gear.values())
		{
			List<String> needs = new ArrayList<>();
			for (int itemId : Shop.missing(progress, item))
			{
				needs.add(itemManager.getItemComposition(itemId).getName());
			}
			Gear previous = item.previousTier();
			boolean active = item == amulet || item == gloves || worn.wields(item);
			gear.add(new IdleBossesPanel.GearRow(item, progress.owns(item), active, Shop.canBuy(progress, item),
				previous == null || progress.owns(previous), needs.isEmpty() ? null : String.join(", ", needs)));
		}
		Progress.CannonState cannon = progress.cannon;
		return new IdleBossesPanel.Upgrades(progress.coins(), gear,
			new IdleBossesPanel.CannonRow(cannon.owned, cannon.cannonballs, cannon.granite));
	}

	// After anything bought, wielded or loaded.
	private void changedUpgrades()
	{
		loadout = Loadout.of(progress);
		cannonLoaded = progress.cannon.loaded;
		saveProgress();
		pushPanel();
	}

	private void reloadCannon()
	{
		if (ready && Shop.reload(progress) > 0)
		{
			changedUpgrades();
		}
	}

	// The sidebar tabs whose guide hasn't been read. Kept per RuneLite profile, not per account, since it's the person
	// who has read it.
	private Set<IdleBossesPanel.Guide> unreadGuides()
	{
		Set<IdleBossesPanel.Guide> unread = EnumSet.noneOf(IdleBossesPanel.Guide.class);
		for (IdleBossesPanel.Guide guide : IdleBossesPanel.Guide.values())
		{
			if (!Boolean.TRUE.equals(configManager.getConfiguration(IdleBossesConfig.GROUP, guideKey(guide), Boolean.class)))
			{
				unread.add(guide);
			}
		}
		return unread;
	}

	private static String guideKey(IdleBossesPanel.Guide guide)
	{
		return guide == IdleBossesPanel.Guide.FIGHT ? FIGHT_GUIDE_KEY : UPGRADES_GUIDE_KEY;
	}

	// The sidebar's buttons. The game ones run on the client thread.
	private final class PanelActions implements IdleBossesPanel.Actions
	{
		@Override
		public void readGuide(IdleBossesPanel.Guide guide)
		{
			configManager.setConfiguration(IdleBossesConfig.GROUP, guideKey(guide), true);
		}

		@Override
		public void fight(Boss boss)
		{
			clientThread.invoke(() -> switchBoss(boss));
		}

		@Override
		public void buy(Gear gear)
		{
			clientThread.invoke(() ->
			{
				if (ready && Shop.buy(progress, gear))
				{
					changedUpgrades();
				}
			});
		}

		@Override
		public void wield(Gear gear)
		{
			clientThread.invoke(() ->
			{
				if (ready && Shop.wield(progress, gear))
				{
					changedUpgrades();
				}
			});
		}

		@Override
		public void buyCannon()
		{
			clientThread.invoke(() ->
			{
				if (ready && Shop.buyCannon(progress))
				{
					changedUpgrades();
				}
			});
		}

		@Override
		public void buyCannonballs(boolean granite)
		{
			clientThread.invoke(() ->
			{
				if (ready && Shop.buyCannonballs(progress, granite))
				{
					changedUpgrades();
				}
			});
		}

		@Override
		public void reloadCannon()
		{
			clientThread.invoke(IdleBossesPlugin.this::reloadCannon);
		}
	}

	private Map<Integer, Double> rates(Boss boss)
	{
		return rates.computeIfAbsent(boss, b ->
		{
			DropData.BossDrops bossDrops = drops.boss(b);
			return bossDrops == null ? Collections.emptyMap() : DropRates.perKill(drops, bossDrops);
		});
	}

	private Map<Integer, String> itemNames(DropData.BossDrops bossDrops)
	{
		Map<Integer, String> names = new HashMap<>();
		List<List<DropData.Drop>> groups = new ArrayList<>(List.of(bossDrops.always, bossDrops.main, bossDrops.tertiary));
		for (DropData.SharedTable table : drops.sharedTables.values())
		{
			groups.add(table.entries);
		}
		for (List<DropData.Drop> group : groups)
		{
			for (DropData.Drop drop : group)
			{
				if (drop.itemId > 0 && drop.name != null)
				{
					names.putIfAbsent(drop.itemId, drop.name);
				}
			}
		}
		return names;
	}

	private DropData loadDrops()
	{
		try (InputStream in = getClass().getResourceAsStream(DROPS_RESOURCE))
		{
			if (in == null)
			{
				log.warn("Idle Bosses drop tables are missing");
				return new DropData();
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				DropData data = gson.fromJson(reader, DropData.class);
				return data == null ? new DropData() : data.normalize();
			}
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Couldn't read the Idle Bosses drop tables", e);
			return new DropData().normalize();
		}
	}
}
