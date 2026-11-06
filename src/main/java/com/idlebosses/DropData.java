package com.idlebosses;

import java.util.Collections;
import java.util.List;
import java.util.Map;

// The drop tables in drops.json, built from the OSRS Wiki by tools/fetch_drops.py. Read with Gson.
final class DropData
{
	String source;
	Map<String, SharedTable> sharedTables = Collections.emptyMap();
	List<BossDrops> bosses = Collections.emptyList();

	BossDrops boss(Boss boss)
	{
		for (BossDrops drops : bosses)
		{
			if (boss.getId().equals(drops.id))
			{
				return drops;
			}
		}
		return null;
	}

	SharedTable table(String name)
	{
		return sharedTables == null ? null : sharedTables.get(name);
	}

	// Gson leaves a list missing from the file as null.
	DropData normalize()
	{
		if (sharedTables == null)
		{
			sharedTables = Collections.emptyMap();
		}
		for (SharedTable table : sharedTables.values())
		{
			table.entries = orEmpty(table.entries);
		}
		bosses = orEmpty(bosses);
		for (BossDrops drops : bosses)
		{
			drops.always = orEmpty(drops.always);
			drops.main = orEmpty(drops.main);
			drops.tables = orEmpty(drops.tables);
			drops.tertiary = orEmpty(drops.tertiary);
			drops.log = orEmpty(drops.log);
		}
		return this;
	}

	private static <T> List<T> orEmpty(List<T> list)
	{
		return list == null ? Collections.emptyList() : list;
	}

	static final class SharedTable
	{
		int outOf;
		List<Drop> entries = Collections.emptyList();
	}

	static final class BossDrops
	{
		String id;
		String name;
		String wikiPage;
		long revision;
		// How many times the main table is rolled each kill.
		int rolls = 1;
		List<Drop> always = Collections.emptyList();
		List<Drop> main = Collections.emptyList();
		int mainOutOf;
		// Weighted slots on the main roll that lead to a shared table, like the rare drop table.
		List<Drop> tables = Collections.emptyList();
		// Independent rolls each kill, like the pet and clue scrolls.
		List<Drop> tertiary = Collections.emptyList();
		// A raid's unique roll, made first: when it hits, its table is rolled once in place of every main roll.
		Drop preRoll;
		// The boss's collection log, in log order.
		List<Integer> log = Collections.emptyList();
	}

	static final class Drop
	{
		int itemId;
		String name;
		int min = 1;
		int max = 1;
		int weight;
		int numerator;
		int denominator;
		boolean noted;
		// Set when this slot rolls a shared table instead of giving an item.
		String table;
	}
}
