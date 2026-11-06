package com.idlebosses;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The chance of getting each item on one kill, counting every way it can drop: the 100% drops,
// each roll of the main table (and the shared tables it leads to), and the tertiary rolls.
final class DropRates
{
	private static final int MAX_TABLE_DEPTH = 3;

	private DropRates()
	{
	}

	static Map<Integer, Double> perKill(DropData data, DropData.BossDrops boss)
	{
		// The chance each route misses the item, multiplied together.
		Map<Integer, Double> miss = new HashMap<>();
		for (DropData.Drop drop : boss.always)
		{
			miss(miss, drop.itemId, 1);
		}

		// The main table is only rolled when a raid's unique roll misses.
		double unique = boss.preRoll == null ? 0 : addSeparateRoll(data, miss, boss.preRoll);
		Map<Integer, Double> perRoll = new HashMap<>();
		addTable(data, perRoll, boss.main, boss.tables, boss.mainOutOf, 1, 0);
		int rolls = Math.max(1, boss.rolls);
		for (Map.Entry<Integer, Double> entry : perRoll.entrySet())
		{
			double hit = (1 - unique) * (1 - Math.pow(1 - entry.getValue(), rolls));
			miss.merge(entry.getKey(), 1 - hit, (a, b) -> a * b);
		}

		for (DropData.Drop drop : boss.tertiary)
		{
			addSeparateRoll(data, miss, drop);
		}

		Map<Integer, Double> chances = new HashMap<>();
		for (Map.Entry<Integer, Double> entry : miss.entrySet())
		{
			chances.put(entry.getKey(), 1 - entry.getValue());
		}
		return chances;
	}

	// "1/3,000", "1/8.6" or "Always".
	static String format(double chance)
	{
		if (chance >= 1 - 1e-9)
		{
			return "Always";
		}
		if (chance <= 0)
		{
			return "Never";
		}
		double kills = 1 / chance;
		if (kills < 10)
		{
			return "1/" + (Math.round(kills * 10) / 10.0);
		}
		return String.format("1/%,d", Math.round(kills));
	}

	// A roll of its own, like a pet or a table it opens. Returns its chance of hitting.
	private static double addSeparateRoll(DropData data, Map<Integer, Double> miss, DropData.Drop drop)
	{
		if (drop.denominator <= 0)
		{
			return 0;
		}
		double chance = (double) drop.numerator / drop.denominator;
		DropData.SharedTable table = drop.table == null ? null : data.table(drop.table);
		if (table != null)
		{
			// One roll on the table when this hits.
			Map<Integer, Double> opened = new HashMap<>();
			addTable(data, opened, table.entries, List.of(), table.outOf, chance, 1);
			for (Map.Entry<Integer, Double> entry : opened.entrySet())
			{
				miss(miss, entry.getKey(), entry.getValue());
			}
		}
		else if (drop.table == null)
		{
			miss(miss, drop.itemId, chance);
		}
		return chance;
	}

	private static void miss(Map<Integer, Double> miss, int itemId, double chance)
	{
		if (itemId > 0)
		{
			miss.merge(itemId, 1 - chance, (a, b) -> a * b);
		}
	}

	// One roll can only give one item, so chances on a roll just add up.
	private static void addTable(DropData data, Map<Integer, Double> perRoll, List<DropData.Drop> entries,
		List<DropData.Drop> tables, int outOf, double chance, int depth)
	{
		if (outOf <= 0)
		{
			return;
		}
		for (List<DropData.Drop> group : List.of(entries, tables))
		{
			for (DropData.Drop drop : group)
			{
				double dropChance = chance * drop.weight / outOf;
				if (drop.table == null)
				{
					if (drop.itemId > 0)
					{
						perRoll.merge(drop.itemId, dropChance, Double::sum);
					}
					continue;
				}
				DropData.SharedTable table = data.table(drop.table);
				if (table != null && depth < MAX_TABLE_DEPTH)
				{
					addTable(data, perRoll, table.entries, List.of(), table.outOf, dropChance, depth + 1);
				}
			}
		}
	}
}
