package com.idlebosses;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import lombok.Value;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

// The sidebar. The Fight tab has your totals, the boss you're fighting and its collection log, and the boss ladder;
// the Upgrades tab spends coins on gear and the cannon. Each tab opens with a short guide until it's read, and the ?
// by the heading brings the open tab's back. Only touched on the Swing thread; the plugin sends it a fresh State after
// kills, switches and purchases.
class IdleBossesPanel extends PluginPanel
{
	private static final int LOG_COLUMNS = 5;
	private static final float MISSING_ITEM_ALPHA = 0.3f;
	private static final int LOCKED_SHOWN = 3;
	private static final int CANNON_LABEL_MILLIS = 500;
	// The panel, less its border.
	private static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - 20;
	// The boss card, less its padding.
	private static final int CARD_TEXT_WIDTH = CONTENT_WIDTH - 16;
	// A boss list row, less its padding and the icon.
	private static final int LADDER_TEXT_WIDTH = CONTENT_WIDTH - 12 - 32 - 6;
	// An upgrade row's width for its name and effect: the panel, less its border, the row's padding and the icon.
	private static final int ROW_TEXT_WIDTH = CONTENT_WIDTH - 10 - 36 - 6;
	private static final Insets BUTTON_MARGIN = new Insets(2, 5, 2, 5);
	private static final Color GOLD_COLOR = new Color(255, 215, 0);
	private static final Color UNLOCKED_COLOR = new Color(95, 224, 95);
	// The HUD's warm log tiles, and a gold one, blended onto the boss card.
	private static final Color OWNED_LOG_FILL = new Color(92, 74, 46);
	private static final Color COMPLETE_LOG_FILL = new Color(92, 80, 30);
	private static final int GUIDE_ICON_WIDTH = 24;
	// The guide card, less its accent, its padding, the icons and the gap after them.
	private static final int GUIDE_TEXT_WIDTH = CONTENT_WIDTH - 2 - 16 - GUIDE_ICON_WIDTH - 6;
	private static final int GUIDE_SPLAT_DAMAGE = 12;

	// The tabs, each with a guide.
	enum Guide
	{
		FIGHT,
		UPGRADES
	}

	// What the panel's buttons do. The plugin runs the game ones on the client thread.
	interface Actions
	{
		// Remembers that a tab's guide was read, so it isn't shown again.
		void readGuide(Guide guide);

		void fight(Boss boss);

		void buy(Gear gear);

		void wield(Gear gear);

		void buyCannon();

		void buyCannonballs(boolean granite);

		void reloadCannon();
	}

	private final ItemManager itemManager;
	private final SkillIconManager skillIconManager;
	private final SplatSprites splatSprites;
	private final Actions actions;
	private final JPanel content = new JPanel(new DynamicGridLayout(0, 1, 0, 10));
	private final JPanel upgradesContent = new JPanel(new DynamicGridLayout(0, 1, 0, 10));
	private final JPanel tabs = new JPanel(new BorderLayout());
	private final MaterialTab upgradesTab;
	private final JButton help = button("?");
	// The tabs showing their guide: the unread ones, and any brought back with the ?.
	private final Set<Guide> guides;
	// The last State, to rebuild from when a guide opens or closes.
	private State state;
	// Kept across rebuilds so its timer and drawing cache carry on.
	private final BossViewComponent bossView;
	// The cannon fires between rebuilds, so its count is read live.
	private final IntSupplier cannonLoaded;
	private final Timer cannonTimer;
	private JLabel cannonLabel;

	IdleBossesPanel(ItemManager itemManager, SkillIconManager skillIconManager, BossPortrait portrait,
		SplatSprites splatSprites, IntSupplier cannonLoaded, Set<Guide> unreadGuides, Actions actions)
	{
		this.itemManager = itemManager;
		this.skillIconManager = skillIconManager;
		this.splatSprites = splatSprites;
		this.actions = actions;
		this.cannonLoaded = cannonLoaded;
		guides = unreadGuides.isEmpty() ? EnumSet.noneOf(Guide.class) : EnumSet.copyOf(unreadGuides);
		bossView = new BossViewComponent(portrait, splatSprites);
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel top = new JPanel(new DynamicGridLayout(0, 1, 0, 8));
		top.setOpaque(false);
		JPanel title = new JPanel(new BorderLayout());
		title.setOpaque(false);
		title.add(heading("Idle Bosses"), BorderLayout.WEST);
		help.setToolTipText("How this tab works");
		title.add(help, BorderLayout.EAST);
		top.add(title);
		JPanel display = new JPanel(new BorderLayout());
		display.setOpaque(false);
		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		content.setOpaque(false);
		upgradesContent.setOpaque(false);
		MaterialTab fightTab = new MaterialTab("Fight", tabGroup, content);
		tabGroup.addTab(fightTab);
		upgradesTab = new MaterialTab("Upgrades", tabGroup, upgradesContent);
		tabGroup.addTab(upgradesTab);
		help.addActionListener(e -> showGuide(upgradesTab.isSelected() ? Guide.UPGRADES : Guide.FIGHT));
		tabGroup.select(fightTab);
		tabs.setOpaque(false);
		tabs.add(tabGroup, BorderLayout.NORTH);
		top.add(tabs);
		top.add(display);
		add(top, BorderLayout.NORTH);

		cannonTimer = new Timer(CANNON_LABEL_MILLIS, e -> updateCannonLabel());
		cannonTimer.start();
		setState(null);
	}

	void shutDown()
	{
		bossView.timer.stop();
		cannonTimer.stop();
	}

	void setState(State state)
	{
		this.state = state;
		content.removeAll();
		upgradesContent.removeAll();
		cannonLabel = null;
		tabs.setVisible(state != null);
		help.setVisible(state != null);
		if (state == null)
		{
			content.add(small("Log in to fight. Every XP drop you get hits your boss.", CONTENT_WIDTH));
		}
		else
		{
			if (guides.contains(Guide.FIGHT))
			{
				content.add(fightGuide(state));
			}
			content.add(totals(state));
			for (Row row : state.getRows())
			{
				if (row.isCurrent())
				{
					content.add(currentBoss(row));
				}
			}
			content.add(heading("Bosses"));
			JPanel ladder = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
			ladder.setOpaque(false);
			// Every unlocked boss and the next few to aim for; the rest stay a surprise.
			int lockedShown = 0;
			int hidden = 0;
			for (Row row : state.getRows())
			{
				if (!row.isUnlocked() && lockedShown++ >= LOCKED_SHOWN)
				{
					hidden++;
					continue;
				}
				ladder.add(ladderRow(row, state.getTotalSlots()));
			}
			content.add(ladder);
			if (hidden > 0)
			{
				content.add(small(hidden + " more " + (hidden == 1 ? "boss" : "bosses") + " to unlock after these.", CONTENT_WIDTH));
			}
			upgrades(state.getUpgrades());
		}
		content.revalidate();
		content.repaint();
		upgradesContent.revalidate();
		upgradesContent.repaint();
	}

	private void upgrades(Upgrades upgrades)
	{
		if (guides.contains(Guide.UPGRADES))
		{
			upgradesContent.add(upgradesGuide());
		}
		JPanel coins = new JPanel(new GridLayout(1, 1));
		coins.setOpaque(false);
		coins.add(stat("Coins", QuantityFormatter.quantityToStackSize(upgrades.getCoins())));
		upgradesContent.add(coins);

		upgradesContent.add(heading("Cannon"));
		upgradesContent.add(cannon(upgrades.getCannon(), upgrades.getCoins()));

		for (Gear.Slot slot : Gear.Slot.values())
		{
			upgradesContent.add(heading(slot == Gear.Slot.AMULET ? "Amulet" : slot == Gear.Slot.GLOVES ? "Gloves" : "Weapon"));
			JPanel list = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
			list.setOpaque(false);
			for (GearRow row : upgrades.getGear())
			{
				if (row.getGear().getSlot() == slot)
				{
					list.add(gearRow(row));
				}
			}
			upgradesContent.add(list);
		}
	}

	// Brings a tab's guide back.
	private void showGuide(Guide guide)
	{
		if (guides.add(guide))
		{
			setState(state);
		}
	}

	private void closeGuide(Guide guide)
	{
		guides.remove(guide);
		actions.readGuide(guide);
		setState(state);
	}

	// Only what the Fight tab can't show: what hits the boss, its weakness, what unlocks bosses and where coins come from.
	private JPanel fightGuide(State state)
	{
		Row current = state.getRows().stream().filter(Row::isCurrent).findFirst().orElse(state.getRows().get(0));
		return guide(Guide.FIGHT,
			guideStep(new JLabel(new SplatIcon()), "Every XP drop you get hits your boss. Any skill works."),
			guideStep(new JLabel(new ImageIcon(skillIconManager.getSkillImage(current.getBoss().getWeakness(), true))),
				"The skill it's weak to does 2x damage."),
			guideStep(current.getIconItemId(), "Kills roll its real drops. Log slots, and kills of this boss, unlock the next."),
			guideStep(ItemID.COINS_10000, "Your loot's value goes into coins for Upgrades."));
	}

	// And what the Upgrades tab can't.
	private JPanel upgradesGuide()
	{
		return guide(Guide.UPGRADES,
			guideStep(ItemID.COINS_10000, "Coins come from the value of your loot."),
			guideStep(ItemID.DRAGON_SCIMITAR, "Wield one weapon at a time. Swapping back is free."),
			guideStep(Gear.VOIDWAKER.getItemId(), "Some upgrades also need a log item. It isn't used up."),
			guideStep(ItemID.TWPART1, "Reload the cannon by hand, here or with the hotkey in settings."));
	}

	// A card at the top of the tab, until it's read.
	private JPanel guide(Guide guide, JPanel... steps)
	{
		JPanel card = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, ColorScheme.BRAND_ORANGE),
			new EmptyBorder(8, 8, 8, 8)));
		card.add(heading("How it works"));
		for (JPanel step : steps)
		{
			card.add(step);
		}
		JButton read = button("Got it");
		read.addActionListener(e -> closeGuide(guide));
		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		footer.setOpaque(false);
		footer.add(read);
		card.add(footer);
		return card;
	}

	// A step with an item at exactly half size, as the HUD shows them, so the card stays small but sharp.
	private JPanel guideStep(int itemId, String text)
	{
		JLabel icon = new JLabel();
		AsyncBufferedImage image = itemManager.getImage(itemId);
		Runnable apply = () ->
		{
			icon.setIcon(new ImageIcon(BossHudOverlay.half(image)));
			icon.revalidate();
		};
		apply.run();
		image.onLoaded(() -> SwingUtilities.invokeLater(apply));
		return guideStep(icon, text);
	}

	private static JPanel guideStep(JLabel icon, String text)
	{
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setVerticalAlignment(SwingConstants.TOP);
		icon.setPreferredSize(new Dimension(GUIDE_ICON_WIDTH, icon.getPreferredSize().height));
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(icon, BorderLayout.WEST);
		JLabel label = small(text, GUIDE_TEXT_WIDTH);
		label.setForeground(Color.WHITE);
		row.add(label, BorderLayout.CENTER);
		return row;
	}

	private JPanel cannon(CannonRow cannon, long coins)
	{
		JPanel list = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
		list.setOpaque(false);
		if (!cannon.isOwned())
		{
			list.add(itemRow(ItemID.TWPART1, true, "Dwarf multicannon",
				"Fires every 8 ticks while you train", buyButton(Shop.CANNON_PRICE, coins >= Shop.CANNON_PRICE, actions::buyCannon)));
			return list;
		}

		JButton reload = button("Reload");
		reload.addActionListener(e -> actions.reloadCannon());
		cannonLabel = small("");
		updateCannonLabel();
		list.add(itemRow(ItemID.TWPART1, true, "Dwarf multicannon", cannonLabel, reload));
		list.add(itemRow(ItemID.MCANNONBALL, true, "Cannonballs",
			QuantityFormatter.formatNumber(cannon.getCannonballs()) + " banked",
			buyButton(Shop.ballPrice(false), coins >= Shop.ballPrice(false), () -> actions.buyCannonballs(false))));
		list.add(itemRow(ItemID.GRANITE_CANNONBALL, true, "Granite cannonballs",
			QuantityFormatter.formatNumber(cannon.getGranite()) + " banked, +20% damage",
			buyButton(Shop.ballPrice(true), coins >= Shop.ballPrice(true), () -> actions.buyCannonballs(true))));
		return list;
	}

	private void updateCannonLabel()
	{
		if (cannonLabel != null)
		{
			int loaded = cannonLoaded.getAsInt();
			cannonLabel.setText(loaded > 0 ? loaded + " / " + Shop.CANNON_CAPACITY + " loaded" : "Empty");
			cannonLabel.setForeground(loaded > 0 ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		}
	}

	private JPanel gearRow(GearRow row)
	{
		Gear gear = row.getGear();
		JComponent right;
		String detail = gear.getEffect();
		if (row.isActive())
		{
			JLabel active = small(gear.getSlot() == Gear.Slot.WEAPON ? "Wielding" : "Wearing");
			active.setForeground(UNLOCKED_COLOR);
			right = active;
		}
		else if (row.isOwned() && gear.getSlot() == Gear.Slot.WEAPON)
		{
			JButton wield = button("Wield");
			wield.addActionListener(e -> actions.wield(gear));
			right = wield;
		}
		else if (row.isOwned())
		{
			right = small("Owned");
		}
		else if (row.getNeeds() != null)
		{
			detail = "Needs " + row.getNeeds();
			right = small(QuantityFormatter.quantityToStackSize(gear.getPrice()));
		}
		else
		{
			JButton buy = buyButton(gear.getPrice(), row.isBuyable(), () -> actions.buy(gear));
			if (!row.isBuyable() && gear.previousTier() != null && !row.isPreviousOwned())
			{
				buy.setToolTipText("Buy the " + gear.previousTier().getDisplayName() + " first");
			}
			right = buy;
		}
		JPanel panel = itemRow(gear.getItemId(), row.isOwned() || row.getNeeds() == null, gear.getDisplayName(), detail, right);
		if (row.getNeeds() != null)
		{
			// The effect the needs line takes the place of.
			panel.setToolTipText(gear.getEffect());
		}
		return panel;
	}

	private JButton buyButton(long price, boolean enabled, Runnable onBuy)
	{
		JButton buy = button(QuantityFormatter.quantityToStackSize(price));
		buy.setEnabled(enabled);
		buy.setToolTipText("Buy for " + QuantityFormatter.formatNumber(price) + " coins");
		buy.addActionListener(e -> onBuy.run());
		return buy;
	}

	private static JButton button(String text)
	{
		JButton button = new JButton(text);
		button.setFocusable(false);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setMargin(BUTTON_MARGIN);
		return button;
	}

	private JPanel itemRow(int itemId, boolean bright, String name, String detail, JComponent right)
	{
		JLabel label = new JLabel();
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JPanel row = itemRow(itemId, bright, name, label, right);
		label.setText(wrap(detail, right));
		return row;
	}

	// Lets text wrap onto more lines in the room the button leaves, rather than being cut off.
	private static String wrap(String text, JComponent right)
	{
		return wrap(text, ROW_TEXT_WIDTH - right.getPreferredSize().width - 6);
	}

	// Text that wraps at a set width. Swing only measures unsized HTML at its real height once it has been painted,
	// so without one the sidebar jumps after every rebuild.
	private static String wrap(String text, int width)
	{
		// Unitless, because Swing scales a width in px by a third.
		return "<html><body style='width:" + width + "'>" + text + "</body></html>";
	}

	private JPanel itemRow(int itemId, boolean bright, String name, JLabel detail, JComponent right)
	{
		JPanel panel = new JPanel(new BorderLayout(6, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(4, 4, 4, 6));

		JLabel icon = new JLabel();
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setPreferredSize(new Dimension(36, 32));
		addIcon(icon, itemId, bright);
		panel.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel(new DynamicGridLayout(0, 1, 0, 1));
		text.setOpaque(false);
		JLabel title = new JLabel(wrap(name, right));
		title.setFont(FontManager.getRunescapeSmallFont());
		title.setForeground(bright ? Color.WHITE : ColorScheme.MEDIUM_GRAY_COLOR);
		text.add(title);
		text.add(detail);
		panel.add(text, BorderLayout.CENTER);
		panel.add(right, BorderLayout.EAST);
		return panel;
	}

	private JPanel totals(State state)
	{
		JPanel totals = new JPanel(new GridLayout(1, 3, 4, 0));
		totals.setOpaque(false);
		totals.add(stat("Kills", QuantityFormatter.formatNumber(state.getTotalKills())));
		totals.add(stat("Log slots", String.valueOf(state.getTotalSlots())));
		totals.add(stat("Looted", QuantityFormatter.quantityToStackSize(state.getGpLooted())));
		return totals;
	}

	private JPanel currentBoss(Row row)
	{
		JPanel card = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(8, 8, 8, 8));

		// The name with the kill count on the right, as on the HUD.
		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		JLabel name = new JLabel(row.getBoss().getDisplayName());
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(row.isLogComplete() ? GOLD_COLOR : ColorScheme.BRAND_ORANGE);
		addIcon(name, row.getIconItemId(), true);
		header.add(name, BorderLayout.CENTER);
		JLabel kills = small("KC " + QuantityFormatter.formatNumber(row.getKills()));
		kills.setForeground(Color.WHITE);
		header.add(kills, BorderLayout.EAST);
		card.add(header);
		Skill weakness = row.getBoss().getWeakness();
		bossView.weakness = skillIconManager.getSkillImage(weakness, true);
		bossView.setToolTipText("2x damage from " + weakness.getName());
		card.add(bossView);
		JLabel logCount = small("Collection log " + row.getOwned().size() + " / " + row.getLog().size(), CARD_TEXT_WIDTH);
		if (row.isLogComplete())
		{
			logCount.setForeground(GOLD_COLOR);
		}
		card.add(logCount);

		JPanel log = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
		log.setOpaque(false);
		for (LogItem item : row.getLog())
		{
			log.add(logRow(item, row.getOwned().contains(item.getItemId()), row.isLogComplete()));
		}
		card.add(log);

		card.add(small("Loot, worth " + QuantityFormatter.quantityToStackSize(row.lootValue()) + " gp", CARD_TEXT_WIDTH));
		if (row.getLoot().isEmpty())
		{
			card.add(small("Drops from your next kill show here.", CARD_TEXT_WIDTH));
		}
		else
		{
			JPanel loot = new JPanel(new GridLayout(0, LOG_COLUMNS, 2, 2));
			loot.setOpaque(false);
			for (LootItem item : row.getLoot())
			{
				JLabel slot = new JLabel();
				slot.setHorizontalAlignment(SwingConstants.CENTER);
				slot.setPreferredSize(new Dimension(36, 32));
				slot.setToolTipText(item.getName() + " x " + QuantityFormatter.formatNumber(item.getQuantity())
					+ (item.getValue() > 0 ? " (" + QuantityFormatter.quantityToStackSize(item.getValue()) + " gp)" : ""));
				int shown = (int) Math.min(Integer.MAX_VALUE, item.getQuantity());
				addIcon(slot, itemManager.getImage(item.getItemId(), shown, shown > 1), true);
				loot.add(slot);
			}
			card.add(loot);
		}
		return card;
	}

	// A log item with its chance per kill and, once you have it, the KC it dropped at. Ones you have are bright on a
	// warm fill, gold once the log is complete.
	private JPanel logRow(LogItem item, boolean owned, boolean logComplete)
	{
		JPanel panel = new JPanel(new BorderLayout(6, 0));
		Color fill = !owned ? ColorScheme.DARK_GRAY_COLOR : logComplete ? COMPLETE_LOG_FILL : OWNED_LOG_FILL;
		panel.setBackground(fill);
		// In the fill's colour, so every row is the same size.
		panel.setBorder(BorderFactory.createLineBorder(fill));

		JLabel icon = new JLabel();
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setPreferredSize(new Dimension(36, 32));
		addIcon(icon, item.getItemId(), owned);
		panel.add(icon, BorderLayout.WEST);

		JLabel rate = small(DropRates.format(item.getChance()));
		rate.setForeground(owned ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.BRAND_ORANGE);
		rate.setBorder(new EmptyBorder(0, 0, 0, 6));
		rate.setToolTipText("Chance on each kill");

		// The card, less this row's frame, the icon, the gaps either side of the name and the rate.
		int textWidth = CARD_TEXT_WIDTH - 2 - 36 - 12 - rate.getPreferredSize().width;
		JPanel text = new JPanel(new DynamicGridLayout(0, 1, 0, 1));
		text.setOpaque(false);
		JLabel name = small(item.getName(), textWidth);
		name.setForeground(owned ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		text.add(name);
		if (owned && item.getDropped() != null)
		{
			JLabel dropped = small(item.getDropped(), textWidth);
			dropped.setForeground(UNLOCKED_COLOR);
			text.add(dropped);
		}
		panel.add(text, BorderLayout.CENTER);
		panel.add(rate, BorderLayout.EAST);
		return panel;
	}

	private JPanel ladderRow(Row row, int totalSlots)
	{
		JPanel panel = new JPanel(new BorderLayout(6, 0));
		panel.setBackground(row.isCurrent() ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		boolean gold = row.isUnlocked() && row.isLogComplete();
		panel.setBorder(gold
			? BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(GOLD_COLOR), new EmptyBorder(4, 5, 4, 5))
			: new EmptyBorder(5, 6, 5, 6));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 28));
		addIcon(icon, row.getIconItemId(), row.isUnlocked());
		panel.add(icon, BorderLayout.WEST);

		JComponent right = null;
		if (row.isCurrent())
		{
			JLabel fighting = small("Fighting");
			fighting.setForeground(UNLOCKED_COLOR);
			right = fighting;
		}
		else if (row.isUnlocked())
		{
			JButton fight = new JButton("Fight");
			fight.setFocusable(false);
			fight.addActionListener(e -> actions.fight(row.getBoss()));
			right = fight;
		}
		int textWidth = LADDER_TEXT_WIDTH - (right == null ? 0 : right.getPreferredSize().width + 6);

		JPanel text = new JPanel(new DynamicGridLayout(0, 1, 0, 1));
		text.setOpaque(false);
		JLabel name = new JLabel();
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(!row.isUnlocked() ? ColorScheme.MEDIUM_GRAY_COLOR : gold ? GOLD_COLOR : Color.WHITE);
		// The weakness skill after the name, bright even while the boss is locked.
		Skill weakness = row.getBoss().getWeakness();
		BufferedImage skillIcon = skillIconManager.getSkillImage(weakness, true);
		name.setIcon(new ImageIcon(skillIcon));
		name.setHorizontalTextPosition(SwingConstants.LEADING);
		name.setToolTipText("2x damage from " + weakness.getName());
		name.setText(lines(row.getBoss().getDisplayName(), name.getFontMetrics(name.getFont()),
			textWidth - skillIcon.getWidth() - name.getIconTextGap()));
		text.add(name);

		String status;
		if (!row.isUnlocked())
		{
			status = needs(row, totalSlots);
		}
		else
		{
			String log = "log " + row.getOwned().size() + "/" + row.getLog().size();
			if (gold)
			{
				log = "<font color='" + ColorUtil.toHexColor(GOLD_COLOR) + "'>" + log + "</font>";
			}
			status = "KC " + QuantityFormatter.formatNumber(row.getKills()) + ", " + log;
		}
		text.add(small(status, textWidth));
		panel.add(text, BorderLayout.CENTER);
		if (right != null)
		{
			panel.add(right, BorderLayout.EAST);
		}
		return panel;
	}

	// A name too long for the width, broken onto more lines rather than cut off. Broken by hand, not wrapped by Swing,
	// so an icon after it sits by the words instead of at the wrap width.
	private static String lines(String text, FontMetrics metrics, int width)
	{
		if (metrics.stringWidth(text) <= width)
		{
			return text;
		}
		StringBuilder lines = new StringBuilder("<html>");
		String line = "";
		for (String word : text.split(" "))
		{
			String longer = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && metrics.stringWidth(longer) > width)
			{
				lines.append(line).append("<br>");
				line = word;
			}
			else
			{
				line = longer;
			}
		}
		return lines.append(line).append("</html>").toString();
	}

	// What's left to unlock a boss: log slots, kills of the boss before it, or both.
	private static String needs(Row row, int totalSlots)
	{
		List<String> needs = new ArrayList<>();
		int slots = row.getBoss().getUnlockSlots() - totalSlots;
		if (slots > 0)
		{
			needs.add(slots + " more log " + (slots == 1 ? "slot" : "slots"));
		}
		Boss previous = row.getBoss().previous();
		int kills = row.getBoss().unlockKills() - row.getPreviousKills();
		if (previous != null && kills > 0)
		{
			needs.add(kills + " more " + previous.getDisplayName() + " " + (kills == 1 ? "kill" : "kills"));
		}
		return "Needs " + String.join(" and ", needs);
	}

	private void addIcon(JLabel label, int itemId, boolean bright)
	{
		addIcon(label, itemManager.getImage(itemId), bright);
	}

	// Sets the icon straight away, so the label has its final size on the first layout, and again once it loads.
	private static void addIcon(JLabel label, AsyncBufferedImage image, boolean bright)
	{
		Runnable apply = () ->
		{
			BufferedImage shown = bright ? image : ImageUtil.alphaOffset(ImageUtil.grayscaleImage(image), MISSING_ITEM_ALPHA);
			label.setIcon(new ImageIcon(shown));
			label.revalidate();
		};
		apply.run();
		image.onLoaded(() -> SwingUtilities.invokeLater(apply));
	}

	private static JPanel stat(String label, String value)
	{
		JPanel stat = new JPanel(new DynamicGridLayout(0, 1, 0, 1));
		stat.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		stat.setBorder(new EmptyBorder(4, 5, 4, 5));
		stat.add(small(label));
		JLabel number = new JLabel(value);
		number.setFont(FontManager.getRunescapeBoldFont());
		number.setForeground(Color.WHITE);
		stat.add(number);
		return stat;
	}

	private static JLabel heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		return label;
	}

	private static JLabel small(String text)
	{
		JLabel label = new JLabel("<html>" + text + "</html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	// Small text that can run onto more lines, wrapped at the room it has.
	private static JLabel small(String text, int width)
	{
		JLabel label = small(text);
		label.setText(wrap(text, width));
		return label;
	}

	// The guide's hitsplat, drawn once its sprite has loaded.
	private final class SplatIcon implements Icon
	{
		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			BossView.drawSplat((Graphics2D) graphics, splatSprites.get(Hit.Type.NORMAL), GUIDE_SPLAT_DAMAGE,
				x + getIconWidth() / 2, y + getIconHeight() / 2);
		}

		@Override
		public int getIconWidth()
		{
			return GUIDE_ICON_WIDTH;
		}

		@Override
		public int getIconHeight()
		{
			return GUIDE_ICON_WIDTH;
		}
	}

	// The animated boss. A timer checks for a new frame and only repaints when there is one.
	private static final class BossViewComponent extends JComponent
	{
		private static final int HEIGHT = 150;
		private static final int FRAME_MILLIS = 40;

		private final BossPortrait portrait;
		private final SplatSprites splatSprites;
		private final BossView view = new BossView();
		private final Timer timer;
		private BossPortrait.Snapshot painted;
		private boolean moving;
		// The boss's weakness skill, drawn at the top right as on the HUD.
		private BufferedImage weakness;

		BossViewComponent(BossPortrait portrait, SplatSprites splatSprites)
		{
			this.portrait = portrait;
			this.splatSprites = splatSprites;
			setPreferredSize(new Dimension(0, HEIGHT));
			timer = new Timer(FRAME_MILLIS, e ->
			{
				if (portrait.getSnapshot() != painted || moving)
				{
					repaint();
				}
			});
			timer.start();
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics;
			g.setColor(ColorScheme.DARK_GRAY_COLOR);
			g.fillRect(0, 0, getWidth(), getHeight());
			painted = portrait.getSnapshot();
			moving = view.paint(g, 0, 0, getWidth(), getHeight(), painted, splatSprites);
			if (weakness != null)
			{
				g.drawImage(weakness, getWidth() - 3 - weakness.getWidth(), 3, null);
			}
		}
	}

	@Value
	static class State
	{
		List<Row> rows;
		int totalKills;
		int totalSlots;
		long gpLooted;
		Upgrades upgrades;
	}

	@Value
	static class Upgrades
	{
		long coins;
		List<GearRow> gear;
		CannonRow cannon;
	}

	@Value
	static class GearRow
	{
		Gear gear;
		boolean owned;
		// Worn or wielded.
		boolean active;
		boolean buyable;
		boolean previousOwned;
		// The collection log items it still needs, by name, or null.
		String needs;
	}

	@Value
	static class CannonRow
	{
		boolean owned;
		long cannonballs;
		long granite;
	}

	@Value
	static class Row
	{
		Boss boss;
		// The first item on its collection log, usually the pet.
		int iconItemId;
		boolean unlocked;
		boolean current;
		int kills;
		// Kills of the boss before it, which unlocking it needs some of.
		int previousKills;
		List<LogItem> log;
		Set<Integer> owned;
		// Everything the boss has dropped, most valuable first.
		List<LootItem> loot;

		boolean isLogComplete()
		{
			return !log.isEmpty() && owned.size() >= log.size();
		}

		long lootValue()
		{
			return loot.stream().mapToLong(LootItem::getValue).sum();
		}
	}

	@Value
	static class LogItem
	{
		int itemId;
		String name;
		// The chance of getting it on one kill.
		double chance;
		// Where and when you got it, like "Dropped at KC 41", or null if you don't have it or that wasn't kept.
		String dropped;
	}

	@Value
	static class LootItem
	{
		int itemId;
		String name;
		long quantity;
		// At Grand Exchange prices.
		long value;
	}
}
