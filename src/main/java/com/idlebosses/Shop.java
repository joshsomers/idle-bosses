package com.idlebosses;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Spending coins: gear, the cannon and its cannonballs, and loading and firing the cannon. Works on Progress only,
// so it can be tested without a client.
final class Shop
{
	// It's worth about as much damage as the amulet of torture, but costs cannonballs and reloading.
	static final long CANNON_PRICE = 25_000_000;
	// An hour of fire uses about 750, so regular balls cost about 1.1M an hour and granite about 3M.
	static final long CANNONBALL_PRICE = 1_500;
	static final long GRANITE_PRICE = 4_000;
	static final int BALLS_PER_PURCHASE = 100;
	static final int CANNON_CAPACITY = 30;
	// Game ticks between shots, 4.8 seconds.
	static final int CANNON_FIRE_TICKS = 8;
	// About a fifth of what you'd deal between shots at a typical pace, before the boss's toughness.
	static final int CANNON_MAX_HIT = 8;
	static final double GRANITE_BONUS = 0.2;

	private Shop()
	{
	}

	// Collection log items it still needs.
	static List<Integer> missing(Progress progress, Gear item)
	{
		List<Integer> missing = new ArrayList<>();
		for (int itemId : item.getRequires())
		{
			if (!progress.hasLogItem(itemId))
			{
				missing.add(itemId);
			}
		}
		return missing;
	}

	static boolean canBuy(Progress progress, Gear item)
	{
		Gear previous = item.previousTier();
		return !progress.owns(item)
			&& (previous == null || progress.owns(previous))
			&& missing(progress, item).isEmpty()
			&& progress.coins() >= item.getPrice();
	}

	// Buys it if you can. A new weapon is wielded straight away.
	static boolean buy(Progress progress, Gear item)
	{
		if (!canBuy(progress, item))
		{
			return false;
		}
		progress.coins = progress.coins() - item.getPrice();
		progress.gear.add(item.name());
		if (item.getSlot() == Gear.Slot.WEAPON)
		{
			progress.weapon = item.name();
		}
		return true;
	}

	static boolean wield(Progress progress, Gear item)
	{
		if (item.getSlot() != Gear.Slot.WEAPON || !progress.owns(item))
		{
			return false;
		}
		progress.weapon = item.name();
		return true;
	}

	static boolean buyCannon(Progress progress)
	{
		if (progress.cannon.owned || progress.coins() < CANNON_PRICE)
		{
			return false;
		}
		progress.coins = progress.coins() - CANNON_PRICE;
		progress.cannon.owned = true;
		return true;
	}

	static long ballPrice(boolean granite)
	{
		return (granite ? GRANITE_PRICE : CANNONBALL_PRICE) * BALLS_PER_PURCHASE;
	}

	static boolean buyCannonballs(Progress progress, boolean granite)
	{
		long price = ballPrice(granite);
		if (!progress.cannon.owned || progress.coins() < price)
		{
			return false;
		}
		progress.coins = progress.coins() - price;
		if (granite)
		{
			progress.cannon.granite += BALLS_PER_PURCHASE;
		}
		else
		{
			progress.cannon.cannonballs += BALLS_PER_PURCHASE;
		}
		return true;
	}

	// Fills the cannon from the bank, granite first. Returns how many went in.
	static int reload(Progress progress)
	{
		Progress.CannonState cannon = progress.cannon;
		if (!cannon.owned)
		{
			return 0;
		}
		int room = CANNON_CAPACITY - cannon.loaded;
		int granite = (int) Math.min(room, cannon.granite);
		int regular = (int) Math.min(room - granite, cannon.cannonballs);
		cannon.granite -= granite;
		cannon.cannonballs -= regular;
		cannon.loaded += granite + regular;
		cannon.loadedGranite += granite;
		return granite + regular;
	}

	// One shot at the boss, or null when the cannon's empty. The Kodai wand sometimes saves the ball.
	static Hit fire(Progress progress, Boss boss, double saveChance, Random random)
	{
		Progress.CannonState cannon = progress.cannon;
		if (!cannon.owned || cannon.loaded <= 0 || boss == null)
		{
			return null;
		}
		boolean granite = cannon.loadedGranite > 0;
		if (random.nextDouble() >= saveChance)
		{
			cannon.loaded--;
			if (granite)
			{
				cannon.loadedGranite--;
			}
		}
		long maxHit = Math.round(CANNON_MAX_HIT * boss.damageScale() * (granite ? 1 + GRANITE_BONUS : 1));
		long damage = random.nextInt((int) Math.min(Integer.MAX_VALUE - 1, maxHit) + 1);
		return new Hit(damage, damage > 0 ? Hit.Type.CANNON : Hit.Type.MISS);
	}
}
