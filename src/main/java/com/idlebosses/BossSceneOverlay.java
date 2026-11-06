package com.idlebosses;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

// The game's health bar and hitsplats, drawn over the mini boss. It isn't a real NPC, so the game can't draw them.
class BossSceneOverlay extends Overlay
{
	private static final long SPLAT_MILLIS = 1200;
	// Like the game, the health bar shows for a while after the last hit.
	private static final long HEALTH_BAR_MILLIS = 6000;
	private static final int HEALTH_BAR_GAP = 18;
	private static final int[][] SPLAT_OFFSETS = BossView.SPLAT_OFFSETS;

	private final Client client;
	private final MiniBoss miniBoss;
	private final SpriteManager spriteManager;
	private final IdleBossesPlugin plugin;

	private final Deque<BossPortrait.Splat> splats = new ArrayDeque<>();
	private final SplatSprites splatSprites;
	private long lastHitMillis;
	private BufferedImage barFront;
	private BufferedImage barBack;

	@Inject
	BossSceneOverlay(Client client, MiniBoss miniBoss, SpriteManager spriteManager, IdleBossesPlugin plugin,
		SplatSprites splatSprites)
	{
		this.splatSprites = splatSprites;
		this.client = client;
		this.miniBoss = miniBoss;
		this.spriteManager = spriteManager;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	void addSplat(int damage, Hit.Type type)
	{
		long now = System.currentTimeMillis();
		lastHitMillis = now;
		splats.addLast(new BossPortrait.Splat(damage, type, now));
		while (splats.size() > SPLAT_OFFSETS.length)
		{
			splats.removeFirst();
		}
	}

	void clear()
	{
		splats.clear();
		lastHitMillis = 0;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		LocalPoint location = miniBoss.getLocation();
		if (location == null)
		{
			return null;
		}

		long now = System.currentTimeMillis();
		int level = miniBoss.getLevel();
		int height = miniBoss.getModelHeight();
		if (now - lastHitMillis < HEALTH_BAR_MILLIS)
		{
			drawHealthBar(graphics, Perspective.localToCanvas(client, location, level, height + HEALTH_BAR_GAP));
		}

		Point middle = Perspective.localToCanvas(client, location, level, height / 2);
		Iterator<BossPortrait.Splat> it = splats.iterator();
		int slot = 0;
		while (it.hasNext())
		{
			BossPortrait.Splat splat = it.next();
			if (now - splat.getMillis() > SPLAT_MILLIS)
			{
				it.remove();
				continue;
			}
			if (middle != null)
			{
				BossView.drawSplat(graphics, splatSprites.get(splat.getType()), splat.getDamage(), middle.getX() + SPLAT_OFFSETS[slot][0],
					middle.getY() + SPLAT_OFFSETS[slot][1]);
			}
			slot++;
		}
		return null;
	}

	private void drawHealthBar(Graphics2D graphics, Point point)
	{
		BossFight fight = plugin.getFight();
		if (point == null || fight.getMaxHp() <= 0)
		{
			return;
		}
		if (barFront == null || barBack == null)
		{
			barFront = spriteManager.getSprite(SpriteID.StandardHealth30.FRONT, 0);
			barBack = spriteManager.getSprite(SpriteID.StandardHealth30.BACK, 0);
			if (barFront == null || barBack == null)
			{
				return;
			}
		}

		int width = barBack.getWidth();
		int barHeight = barBack.getHeight();
		int x = point.getX() - width / 2;
		int y = point.getY() - barHeight / 2;
		int filled = (int) Math.ceil(width * (double) fight.getHp() / fight.getMaxHp());
		graphics.drawImage(barBack, x, y, null);
		if (filled > 0)
		{
			graphics.drawImage(barFront, x, y, x + filled, y + barHeight, 0, 0, filled, barHeight, null);
		}
	}

}
