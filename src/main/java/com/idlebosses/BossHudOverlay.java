package com.idlebosses;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import lombok.Setter;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

// The box on screen: the boss with your streak and its weakness on it, its health, its collection log with the last
// kill's new items framed, and your cannon and coins. A weapon effect that triggers shows as a banner across the boss.
// Its height only changes with the boss, never between kills.
class BossHudOverlay extends OverlayPanel
{
	private static final int WIDTH = 170;
	private static final Color NAME_COLOR = new Color(255, 152, 31);
	private static final Color SUPERIOR_COLOR = new Color(224, 112, 255);
	private static final Color GOLD_COLOR = new Color(255, 215, 0);
	private static final Color WEAK_COLOR = new Color(95, 224, 95);
	private static final Color DIM_COLOR = new Color(176, 168, 144);
	private static final Color STREAK_COLOR = new Color(255, 210, 122);
	private static final Color EMPTY_COLOR = new Color(255, 80, 80);
	private static final Color CHIP_COLOR = new Color(0, 0, 0, 120);
	private static final Color BANNER_COLOR = new Color(0, 0, 0, 150);
	private static final Color SPECIAL_COLOR = new Color(255, 230, 90);
	private static final float FADED_ALPHA = 0.4f;
	private static final long BANNER_MILLIS = 1_200;
	private static final long BANNER_FADE_IN_MILLIS = 100;
	private static final long BANNER_FADE_OUT_MILLIS = 300;

	private final IdleBossesPlugin plugin;
	private final IdleBossesConfig config;
	private final SkillIconManager skillIconManager;
	private final ItemManager itemManager;
	// Weapon icons at half size for the banner, filled in as they load.
	private final Map<Gear, BufferedImage> weaponIcons = new ConcurrentHashMap<>();
	private final Set<Gear> requestedIcons = EnumSet.noneOf(Gear.class);
	private final BossWindow window;
	private final StatusRow statusRow = new StatusRow();
	private final HealthBar healthBar = new HealthBar();
	private final LogGrid logGrid;
	private final Footer footer;

	@Inject
	BossHudOverlay(IdleBossesPlugin plugin, IdleBossesConfig config, BossPortrait portrait, SplatSprites splatSprites,
		ItemManager itemManager, SkillIconManager skillIconManager)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.skillIconManager = skillIconManager;
		this.itemManager = itemManager;
		window = new BossWindow(portrait, splatSprites);
		logGrid = new LogGrid(itemManager);
		footer = new Footer(itemManager);
		setPosition(OverlayPosition.TOP_LEFT);
		// RuneLite draws an overlay you've dragged somewhere above every interface, so it would cover the bank. Drawn
		// with the scene, the game's interfaces always go over it.
		setLayer(OverlayLayer.ABOVE_SCENE);
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
		panelComponent.setGap(new Point(0, 3));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		BossFight fight = plugin.getFight();
		Boss boss = fight.getBoss();
		if (!config.showHud() || !plugin.isReady() || boss == null)
		{
			return null;
		}
		Progress progress = plugin.getProgress();

		boolean gold = plugin.isLogComplete(boss);
		String name = (fight.isSuperior() ? "Superior " : "") + boss.getDisplayName();
		panelComponent.getChildren().add(LineComponent.builder()
			.left(name)
			.leftColor(fight.isSuperior() ? SUPERIOR_COLOR : gold ? GOLD_COLOR : NAME_COLOR)
			.right("KC " + QuantityFormatter.formatNumber(progress.kills(boss)))
			.build());

		Status status = new Status(skillIconManager.getSkillImage(boss.getWeakness(), true),
			boss.getWeakness() == plugin.getLastSkill(), fight.getStreak(), fight.streakBonus());
		Banner banner = banner(plugin.getSpecial());
		if (config.showBossInHud())
		{
			window.status = status;
			window.banner = banner;
			panelComponent.getChildren().add(window);
		}
		else
		{
			statusRow.status = status;
			statusRow.banner = banner;
			panelComponent.getChildren().add(statusRow);
		}

		healthBar.update(fight.getHp(), fight.getMaxHp());
		panelComponent.getChildren().add(healthBar);

		List<Integer> log = plugin.logItems(boss);
		int owned = progress.logCount(log);
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Collection log")
			.leftColor(DIM_COLOR)
			.right(owned + " / " + log.size())
			.rightColor(gold ? GOLD_COLOR : Color.WHITE)
			.build());
		logGrid.set(boss, log, progress, plugin.getLastLogItems(), plugin.getLastRareLogItems());
		panelComponent.getChildren().add(logGrid);

		footer.set(progress.cannon, progress.coins());
		panelComponent.getChildren().add(footer);
		return super.render(graphics);
	}

	// The banner for a weapon effect that triggered in the last moment, or null.
	private Banner banner(IdleBossesPlugin.Special special)
	{
		if (special == null || special.getWeapon() == null)
		{
			return null;
		}
		long age = System.currentTimeMillis() - special.getMillis();
		if (age < 0 || age >= BANNER_MILLIS)
		{
			return null;
		}
		float alpha = age < BANNER_FADE_IN_MILLIS ? (float) age / BANNER_FADE_IN_MILLIS
			: Math.min(1, (float) (BANNER_MILLIS - age) / BANNER_FADE_OUT_MILLIS);
		Gear weapon = special.getWeapon();
		if (requestedIcons.add(weapon))
		{
			AsyncBufferedImage icon = itemManager.getImage(weapon.getItemId());
			icon.onLoaded(() -> weaponIcons.put(weapon, half(icon)));
		}
		String name = weapon.getDisplayName().replace(" (i)", "");
		return new Banner(weaponIcons.get(weapon), name + "  " + special.getLabel(), alpha);
	}

	// A weapon effect that just triggered: the weapon's icon and what it did, fading in and out.
	private static final class Banner
	{
		final BufferedImage icon;
		final String text;
		final float alpha;

		Banner(BufferedImage icon, String text, float alpha)
		{
			this.icon = icon;
			this.text = text;
			this.alpha = alpha;
		}
	}

	private static void drawBanner(Graphics2D graphics, Banner banner, int x, int y)
	{
		if (banner == null)
		{
			return;
		}
		Composite composite = graphics.getComposite();
		Font font = graphics.getFont();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, banner.alpha)));
		graphics.setFont(FontManager.getRunescapeSmallFont());
		int iconWidth = banner.icon == null ? 0 : banner.icon.getWidth() + 3;
		graphics.setColor(BANNER_COLOR);
		graphics.fillRect(x, y, 2 + iconWidth + graphics.getFontMetrics().stringWidth(banner.text) + 5, 18);
		if (banner.icon != null)
		{
			graphics.drawImage(banner.icon, x + 2, y + 1, null);
		}
		text(graphics, banner.text, x + 2 + iconWidth, y + 13, SPECIAL_COLOR);
		graphics.setFont(font);
		graphics.setComposite(composite);
	}

	// What goes on the boss: the streak, and the weakness skill and whether you're training it.
	private static final class Status
	{
		final BufferedImage skillIcon;
		final boolean weak;
		final int streak;
		final int bonus;

		Status(BufferedImage skillIcon, boolean weak, int streak, int bonus)
		{
			this.skillIcon = skillIcon;
			this.weak = weak;
			this.streak = streak;
			this.bonus = bonus;
		}
	}

	// The weakness skill's icon, its right edge at right: lit with "2x" beside it while you train it, faded otherwise.
	private static void drawWeakness(Graphics2D graphics, Status status, int right, int y)
	{
		if (status.skillIcon == null)
		{
			return;
		}
		int x = right - status.skillIcon.getWidth();
		Composite composite = graphics.getComposite();
		if (!status.weak)
		{
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, FADED_ALPHA));
		}
		graphics.drawImage(status.skillIcon, x, y, null);
		graphics.setComposite(composite);
		if (status.weak)
		{
			Font font = graphics.getFont();
			graphics.setFont(FontManager.getRunescapeSmallFont());
			FontMetrics metrics = graphics.getFontMetrics();
			text(graphics, "2x", x - 2 - metrics.stringWidth("2x"), y + (status.skillIcon.getHeight() + metrics.getAscent()) / 2 - 1,
				WEAK_COLOR);
			graphics.setFont(font);
		}
	}

	// The streak on a dark chip, if there is one.
	private static void drawStreak(Graphics2D graphics, Status status, int x, int y)
	{
		if (status.streak <= 0)
		{
			return;
		}
		Font font = graphics.getFont();
		graphics.setFont(FontManager.getRunescapeSmallFont());
		String streak = "x" + status.streak + "  +" + status.bonus + "%";
		graphics.setColor(CHIP_COLOR);
		graphics.fillRect(x, y, graphics.getFontMetrics().stringWidth(streak) + 6, 14);
		text(graphics, streak, x + 3, y + 11, STREAK_COLOR);
		graphics.setFont(font);
	}

	private static void text(Graphics2D graphics, String text, int x, int y, Color color)
	{
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(color);
		graphics.drawString(text, x, y);
	}

	// The animated boss, in a darker box, with the streak at the bottom left and the weakness at the top right.
	private static final class BossWindow implements LayoutableRenderableEntity
	{
		private static final int HEIGHT = 100;
		private static final Color BACKGROUND = new Color(20, 17, 13, 120);

		private final BossPortrait portrait;
		private final SplatSprites splatSprites;
		private final BossView view = new BossView();
		private Status status;
		private Banner banner;

		@Setter
		private Point preferredLocation = new Point();
		@Setter
		private Dimension preferredSize = new Dimension(WIDTH, HEIGHT);
		private final Rectangle bounds = new Rectangle();

		BossWindow(BossPortrait portrait, SplatSprites splatSprites)
		{
			this.portrait = portrait;
			this.splatSprites = splatSprites;
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			int x = preferredLocation.x;
			int y = preferredLocation.y;
			int width = preferredSize.width;
			graphics.setColor(BACKGROUND);
			graphics.fillRect(x, y, width, HEIGHT);
			view.paint(graphics, x, y, width, HEIGHT, portrait.getSnapshot(), splatSprites);
			// A banner has the top of the box to itself while it shows.
			if (banner == null)
			{
				drawWeakness(graphics, status, x + width - 3, y + 3);
			}
			drawStreak(graphics, status, x + 3, y + HEIGHT - 17);
			drawBanner(graphics, banner, x + 3, y + 3);
			bounds.setBounds(x, y, width, HEIGHT);
			return new Dimension(width, HEIGHT);
		}

		@Override
		public Rectangle getBounds()
		{
			return bounds;
		}
	}

	// The streak and weakness on their own row, when the boss isn't shown.
	private static final class StatusRow implements LayoutableRenderableEntity
	{
		private static final int HEIGHT = 18;

		private Status status;
		private Banner banner;

		@Setter
		private Point preferredLocation = new Point();
		@Setter
		private Dimension preferredSize = new Dimension(WIDTH, HEIGHT);
		private final Rectangle bounds = new Rectangle();

		@Override
		public Dimension render(Graphics2D graphics)
		{
			int x = preferredLocation.x;
			int y = preferredLocation.y;
			if (banner == null)
			{
				drawStreak(graphics, status, x, y + 2);
				drawWeakness(graphics, status, x + preferredSize.width, y);
			}
			drawBanner(graphics, banner, x, y);
			bounds.setBounds(x, y, preferredSize.width, HEIGHT);
			return new Dimension(preferredSize.width, HEIGHT);
		}

		@Override
		public Rectangle getBounds()
		{
			return bounds;
		}
	}

	// A health bar whose lost chunk drains away after a moment, so each hit is easy to see.
	private static final class HealthBar implements LayoutableRenderableEntity
	{
		private static final int HEIGHT = 14;
		private static final long DRAIN_DELAY_MILLIS = 250;
		// Fraction of the bar drained each millisecond.
		private static final double DRAIN_SPEED = 0.0015;
		private static final Color BACKGROUND = new Color(94, 16, 16);
		private static final Color HEALTH = new Color(31, 154, 31);
		private static final Color DRAIN = new Color(240, 208, 112);

		@Setter
		private Point preferredLocation = new Point();
		@Setter
		private Dimension preferredSize = new Dimension(WIDTH, HEIGHT);
		private final Rectangle bounds = new Rectangle();

		private double shown = 1;
		private double trail = 1;
		private long changedMillis;
		private long lastFrameMillis;
		private String text = "";

		void update(int hp, int maxHp)
		{
			double fraction = maxHp > 0 ? (double) hp / maxHp : 0;
			if (fraction > shown)
			{
				// A new boss: no trail.
				trail = fraction;
			}
			else if (fraction < shown)
			{
				changedMillis = System.currentTimeMillis();
			}
			shown = fraction;
			text = hp + " / " + maxHp;
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			long now = System.currentTimeMillis();
			if (trail > shown && now - changedMillis > DRAIN_DELAY_MILLIS)
			{
				trail = Math.max(shown, trail - (now - lastFrameMillis) * DRAIN_SPEED);
			}
			lastFrameMillis = now;

			int x = preferredLocation.x;
			int y = preferredLocation.y;
			int width = preferredSize.width;
			graphics.setColor(BACKGROUND);
			graphics.fillRect(x, y, width, HEIGHT);
			graphics.setColor(DRAIN);
			graphics.fillRect(x, y, (int) Math.round(width * trail), HEIGHT);
			graphics.setColor(HEALTH);
			graphics.fillRect(x, y, (int) Math.round(width * shown), HEIGHT);

			FontMetrics metrics = graphics.getFontMetrics();
			int textX = x + (width - metrics.stringWidth(text)) / 2;
			int textY = y + (HEIGHT + metrics.getAscent()) / 2 - 1;
			graphics.setColor(Color.BLACK);
			graphics.drawString(text, textX + 1, textY + 1);
			graphics.setColor(Color.WHITE);
			graphics.drawString(text, textX, textY);

			bounds.setBounds(x, y, width, HEIGHT);
			return new Dimension(width, HEIGHT);
		}

		@Override
		public Rectangle getBounds()
		{
			return bounds;
		}
	}

	// Half the size, each pixel the average of the four it covers. Shrinking by exactly half keeps item icons sharp,
	// where scaling them by any other amount smears them.
	static BufferedImage half(BufferedImage image)
	{
		int width = image.getWidth() / 2;
		int height = image.getHeight() / 2;
		BufferedImage half = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int alpha = 0;
				int red = 0;
				int green = 0;
				int blue = 0;
				for (int i = 0; i < 4; i++)
				{
					int pixel = image.getRGB(x * 2 + i % 2, y * 2 + i / 2);
					int a = pixel >>> 24;
					alpha += a;
					red += (pixel >> 16 & 0xFF) * a;
					green += (pixel >> 8 & 0xFF) * a;
					blue += (pixel & 0xFF) * a;
				}
				half.setRGB(x, y, alpha == 0 ? 0 : (alpha / 4) << 24 | red / alpha << 16 | green / alpha << 8 | blue / alpha);
			}
		}
		return half;
	}

	// Every item on the boss's log: full size for a log of up to 8, so it's as sharp as your inventory, and exactly half
	// size for a bigger one. Ones you have sit on a warm tile, missing ones are faded, and the last kill's new ones are
	// framed.
	private static final class LogGrid implements LayoutableRenderableEntity
	{
		private static final int FULL_SIZE_ITEMS = 8;
		private static final int ICON_WIDTH = 36;
		private static final int ICON_HEIGHT = 32;
		private static final int GAP = 2;
		private static final float MISSING_ALPHA = 0.3f;
		private static final Color OWNED_TILE = new Color(255, 190, 90, 70);
		private static final Color MISSING_TILE = new Color(0, 0, 0, 70);
		private static final Color LOG_FRAME = new Color(255, 152, 31);
		private static final Color RARE_FRAME = new Color(224, 112, 255);

		// An item's icon at the size the grid draws it, and a faded copy for when you don't have it.
		private static final class Sprite
		{
			final BufferedImage owned;
			final BufferedImage missing;

			Sprite(BufferedImage owned)
			{
				this.owned = owned;
				missing = ImageUtil.alphaOffset(ImageUtil.grayscaleImage(owned), MISSING_ALPHA);
			}
		}

		private final ItemManager itemManager;
		private Boss boss;
		private List<Integer> log = List.of();
		// Filled in as each icon loads.
		private Sprite[] sprites = new Sprite[0];
		private Progress progress;
		private Set<Integer> newItems = Set.of();
		private Set<Integer> rareItems = Set.of();

		@Setter
		private Point preferredLocation = new Point();
		@Setter
		private Dimension preferredSize = new Dimension(WIDTH, 0);
		private final Rectangle bounds = new Rectangle();

		LogGrid(ItemManager itemManager)
		{
			this.itemManager = itemManager;
		}

		void set(Boss boss, List<Integer> log, Progress progress, Set<Integer> newItems, Set<Integer> rareItems)
		{
			if (boss != this.boss)
			{
				this.boss = boss;
				this.log = log;
				boolean full = log.size() <= FULL_SIZE_ITEMS;
				Sprite[] loading = new Sprite[log.size()];
				for (int i = 0; i < log.size(); i++)
				{
					int slot = i;
					AsyncBufferedImage icon = itemManager.getImage(log.get(i));
					icon.onLoaded(() -> loading[slot] = new Sprite(full ? icon : half(icon)));
				}
				sprites = loading;
			}
			this.progress = progress;
			this.newItems = newItems;
			this.rareItems = rareItems;
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			boolean full = log.size() <= FULL_SIZE_ITEMS;
			int cellWidth = full ? ICON_WIDTH : ICON_WIDTH / 2;
			int cellHeight = full ? ICON_HEIGHT : ICON_HEIGHT / 2;
			int perRow = Math.max(1, (preferredSize.width + GAP) / (cellWidth + GAP));
			int rows = (log.size() + perRow - 1) / perRow;
			for (int i = 0; i < log.size(); i++)
			{
				int itemId = log.get(i);
				boolean owned = progress.hasLogItem(itemId);
				int x = preferredLocation.x + (i % perRow) * (cellWidth + GAP);
				int y = preferredLocation.y + (i / perRow) * (cellHeight + GAP);
				graphics.setColor(owned ? OWNED_TILE : MISSING_TILE);
				graphics.fillRect(x, y, cellWidth, cellHeight);
				Sprite sprite = sprites[i];
				if (sprite != null)
				{
					graphics.drawImage(owned ? sprite.owned : sprite.missing, x, y, null);
				}
				if (newItems.contains(itemId))
				{
					graphics.setColor(rareItems.contains(itemId) ? RARE_FRAME : LOG_FRAME);
					graphics.drawRect(x, y, cellWidth - 1, cellHeight - 1);
				}
			}

			int height = rows * cellHeight + Math.max(0, rows - 1) * GAP;
			bounds.setBounds(preferredLocation.x, preferredLocation.y, preferredSize.width, height);
			return new Dimension(preferredSize.width, height);
		}

		@Override
		public Rectangle getBounds()
		{
			return bounds;
		}
	}

	// Your cannon's load on the left, once you have one, and your coins on the right, with half-size icons.
	private static final class Footer implements LayoutableRenderableEntity
	{
		private static final int HEIGHT = 18;
		private static final int ICON_WIDTH = 18;

		private final ItemManager itemManager;
		private boolean requested;
		private BufferedImage ballIcon;
		private BufferedImage graniteIcon;
		private BufferedImage coinsIcon;
		private Progress.CannonState cannon;
		private long coins;

		@Setter
		private Point preferredLocation = new Point();
		@Setter
		private Dimension preferredSize = new Dimension(WIDTH, HEIGHT);
		private final Rectangle bounds = new Rectangle();

		Footer(ItemManager itemManager)
		{
			this.itemManager = itemManager;
		}

		void set(Progress.CannonState cannon, long coins)
		{
			this.cannon = cannon;
			this.coins = coins;
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			if (!requested)
			{
				requested = true;
				AsyncBufferedImage ball = itemManager.getImage(ItemID.MCANNONBALL);
				ball.onLoaded(() -> ballIcon = half(ball));
				AsyncBufferedImage granite = itemManager.getImage(ItemID.GRANITE_CANNONBALL);
				granite.onLoaded(() -> graniteIcon = half(granite));
				AsyncBufferedImage pile = itemManager.getImage(ItemID.COINS_10000);
				pile.onLoaded(() -> coinsIcon = half(pile));
			}
			int x = preferredLocation.x;
			int y = preferredLocation.y;
			int width = preferredSize.width;
			FontMetrics metrics = graphics.getFontMetrics();
			int textY = y + HEIGHT - 4;
			if (cannon.owned)
			{
				BufferedImage icon = cannon.loadedGranite > 0 ? graniteIcon : ballIcon;
				if (icon != null)
				{
					graphics.drawImage(icon, x - 1, y + 1, null);
				}
				text(graphics, cannon.loaded > 0 ? cannon.loaded + " / " + Shop.CANNON_CAPACITY : "Empty", x + ICON_WIDTH + 1, textY,
					cannon.loaded > 0 ? Color.WHITE : EMPTY_COLOR);
			}
			String amount = QuantityFormatter.quantityToStackSize(coins);
			int amountX = x + width - metrics.stringWidth(amount);
			if (coinsIcon != null)
			{
				graphics.drawImage(coinsIcon, amountX - ICON_WIDTH - 1, y + 1, null);
			}
			text(graphics, amount, amountX, textY, Color.YELLOW);
			bounds.setBounds(x, y, width, HEIGHT);
			return new Dimension(width, HEIGHT);
		}

		@Override
		public Rectangle getBounds()
		{
			return bounds;
		}
	}
}
