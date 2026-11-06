package com.idlebosses;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// What a player has done, saved as JSON per account: kill counts, loot, collection log items, loot value, and the
// coins, gear and cannon bought with it. Like the game's collection log, an item counts once however many bosses'
// logs it's on.
final class Progress
{
	String currentBoss;
	// Everything ever looted, at Grand Exchange prices. Spending coins doesn't lower it.
	long gpLooted;
	// Coins to spend on upgrades. Missing from saves made before upgrades; they start with what they'd looted.
	Long coins;
	Map<String, BossProgress> bosses = new HashMap<>();
	Set<Integer> logItems = new LinkedHashSet<>();
	// Where and when each log item first dropped. Missing for items from saves before it was kept.
	Map<Integer, LogDrop> logDrops = new HashMap<>();
	// Gear bought, by Gear name.
	Set<String> gear = new LinkedHashSet<>();
	// The Gear name of the weapon wielded, or null.
	String weapon;
	CannonState cannon = new CannonState();

	static final class CannonState
	{
		boolean owned;
		// In the bank.
		long cannonballs;
		long granite;
		// In the cannon. Granite ones fire first.
		int loaded;
		int loadedGranite;
	}

	static final class LogDrop
	{
		// The boss's id.
		String boss;
		// The boss's kill count on the kill that dropped it.
		int kills;
	}

	static final class BossProgress
	{
		int kills;
		// Each boss's own log, from before items were shared. Read from old saves and folded into logItems.
		Set<Integer> log;
		// Everything the boss has dropped, by item ID.
		Map<Integer, Long> loot = new HashMap<>();
	}

	private BossProgress get(Boss boss)
	{
		if (bosses == null)
		{
			bosses = new HashMap<>();
		}
		BossProgress progress = bosses.computeIfAbsent(boss.getId(), id -> new BossProgress());
		if (progress.loot == null)
		{
			progress.loot = new HashMap<>();
		}
		return progress;
	}

	// Moves an old save's per-boss logs into the shared one, and fills in anything an old save didn't have.
	Progress migrate()
	{
		if (logItems == null)
		{
			logItems = new LinkedHashSet<>();
		}
		if (logDrops == null)
		{
			logDrops = new HashMap<>();
		}
		if (coins == null)
		{
			coins = gpLooted;
		}
		if (gear == null)
		{
			gear = new LinkedHashSet<>();
		}
		if (cannon == null)
		{
			cannon = new CannonState();
		}
		if (bosses != null)
		{
			for (BossProgress progress : bosses.values())
			{
				if (progress.log != null)
				{
					logItems.addAll(progress.log);
					progress.log = null;
				}
			}
		}
		return this;
	}

	int kills(Boss boss)
	{
		return get(boss).kills;
	}

	void addKill(Boss boss)
	{
		get(boss).kills++;
	}

	boolean hasLogItem(int itemId)
	{
		return logItems.contains(itemId);
	}

	// True if the item is new to the collection log.
	boolean addLogItem(int itemId)
	{
		return logItems.add(itemId);
	}

	// Like addLogItem, remembering the boss that dropped it and its kill count.
	boolean addLogItem(int itemId, Boss boss)
	{
		if (!addLogItem(itemId))
		{
			return false;
		}
		LogDrop drop = new LogDrop();
		drop.boss = boss.getId();
		drop.kills = kills(boss);
		logDrops.put(itemId, drop);
		return true;
	}

	// Where and when the item first dropped, or null if that wasn't kept.
	LogDrop logDrop(int itemId)
	{
		return logDrops == null ? null : logDrops.get(itemId);
	}

	// How many of these log items are filled.
	int logCount(Collection<Integer> log)
	{
		int count = 0;
		for (int itemId : log)
		{
			if (logItems.contains(itemId))
			{
				count++;
			}
		}
		return count;
	}

	int totalSlots()
	{
		return logItems.size();
	}

	int totalKills()
	{
		int total = 0;
		for (Boss boss : Boss.values())
		{
			total += kills(boss);
		}
		return total;
	}

	// Enough log slots across every boss, and enough kills of the boss before it. A boss you've fought stays unlocked.
	boolean isUnlocked(Boss boss)
	{
		if (kills(boss) > 0)
		{
			return true;
		}
		Boss previous = boss.previous();
		return totalSlots() >= boss.getUnlockSlots() && (previous == null || kills(previous) >= boss.unlockKills());
	}

	void addLoot(Boss boss, int itemId, long quantity)
	{
		get(boss).loot.merge(itemId, quantity, Long::sum);
	}

	Map<Integer, Long> loot(Boss boss)
	{
		return get(boss).loot;
	}

	long coins()
	{
		return coins == null ? gpLooted : coins;
	}

	// Loot adds to both the lifetime total and the coins to spend.
	void addLootValue(long value)
	{
		gpLooted += value;
		coins = coins() + value;
	}

	boolean owns(Gear item)
	{
		return gear != null && gear.contains(item.name());
	}

	// The highest tier owned in a ladder, which is the one worn.
	Gear best(Gear.Slot slot)
	{
		Gear best = null;
		for (Gear item : Gear.inSlot(slot))
		{
			if (owns(item))
			{
				best = item;
			}
		}
		return best;
	}

	Gear wielded()
	{
		Gear item = weapon == null ? null : Gear.byName(weapon);
		return item != null && owns(item) ? item : null;
	}
}
