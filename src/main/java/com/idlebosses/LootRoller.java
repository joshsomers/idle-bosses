package com.idlebosses;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Rolls a kill's loot the way the game does: the 100% drops, a raid's unique roll, the main table (sometimes
// more than once, and not at all when the raid's unique hits), then each tertiary roll on its own.
final class LootRoller
{
	// The rare drop table can point at the gem table, so allow a couple of levels.
	private static final int MAX_TABLE_DEPTH = 3;

	private final DropData data;
	private final Random random;

	LootRoller(DropData data, Random random)
	{
		this.data = data;
		this.random = random;
	}

	// A superior gets one extra roll on the main table.
	List<Loot> roll(DropData.BossDrops boss, boolean superior)
	{
		List<Loot> loot = new ArrayList<>();
		for (DropData.Drop drop : boss.always)
		{
			add(loot, drop, 1);
		}

		// A raid's unique takes the place of its common rolls.
		boolean unique = boss.preRoll != null && rollSeparately(loot, boss.preRoll);
		int rolls = (unique ? 0 : Math.max(1, boss.rolls)) + (superior ? 1 : 0);
		for (int i = 0; i < rolls; i++)
		{
			rollTable(loot, boss.main, boss.tables, boss.mainOutOf, 1, 0);
		}

		for (DropData.Drop drop : boss.tertiary)
		{
			rollSeparately(loot, drop);
		}
		return merge(loot);
	}

	// One roll of its own at the drop's chance. Returns whether it hit.
	private boolean rollSeparately(List<Loot> loot, DropData.Drop drop)
	{
		if (drop.denominator <= 0 || random.nextInt(drop.denominator) >= drop.numerator)
		{
			return false;
		}
		double chance = (double) drop.numerator / drop.denominator;
		DropData.SharedTable table = drop.table == null ? null : data.table(drop.table);
		if (table != null)
		{
			// A separate roll that opens a table, like Abyssal Sire's Unsired at the Font of Consumption.
			rollTable(loot, table.entries, List.of(), table.outOf, chance, 1);
		}
		else if (drop.table == null)
		{
			add(loot, drop, chance);
		}
		return true;
	}

	private void rollTable(List<Loot> loot, List<DropData.Drop> entries, List<DropData.Drop> tables, int outOf, double chance, int depth)
	{
		if (outOf <= 0)
		{
			return;
		}

		int roll = random.nextInt(outOf);
		for (List<DropData.Drop> group : List.of(entries, tables))
		{
			for (DropData.Drop drop : group)
			{
				if (roll >= drop.weight)
				{
					roll -= drop.weight;
					continue;
				}

				double dropChance = chance * drop.weight / outOf;
				if (drop.table == null)
				{
					add(loot, drop, dropChance);
					return;
				}

				DropData.SharedTable table = data.table(drop.table);
				if (table != null && depth < MAX_TABLE_DEPTH)
				{
					rollTable(loot, table.entries, List.of(), table.outOf, dropChance, depth + 1);
				}
				return;
			}
		}
		// What's left of the weights is a roll that gives nothing.
	}

	private void add(List<Loot> loot, DropData.Drop drop, double chance)
	{
		if (drop.itemId <= 0)
		{
			return;
		}
		int min = Math.max(1, drop.min);
		int quantity = min + random.nextInt(Math.max(min, drop.max) - min + 1);
		loot.add(new Loot(drop.itemId, drop.name, quantity, chance));
	}

	// Two rolls of the same item show as one stack, keeping the rarer chance.
	static List<Loot> merge(List<Loot> loot)
	{
		Map<Integer, Loot> merged = new LinkedHashMap<>();
		for (Loot item : loot)
		{
			merged.merge(item.getItemId(), item, (a, b) ->
				new Loot(a.getItemId(), a.getName(), a.getQuantity() + b.getQuantity(), Math.min(a.getChance(), b.getChance())));
		}
		return new ArrayList<>(merged.values());
	}
}
