package com.idlebosses;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class DropDataTest
{
	static DropData load() throws Exception
	{
		try (Reader reader = new InputStreamReader(IdleBossesPlugin.class.getResourceAsStream("drops.json"), StandardCharsets.UTF_8))
		{
			return new Gson().fromJson(reader, DropData.class).normalize();
		}
	}

	@Test
	public void everyBossHasDropsAndALog() throws Exception
	{
		DropData data = load();
		for (Boss boss : Boss.values())
		{
			DropData.BossDrops drops = data.boss(boss);
			assertNotNull(boss + " has no drops", drops);
			assertFalse(boss + " has no collection log", drops.log.isEmpty());
			assertTrue(boss + " has no main table", drops.mainOutOf > 0 || drops.main.isEmpty());
		}
	}

	@Test
	public void weightsFitTheirTables() throws Exception
	{
		DropData data = load();
		for (DropData.BossDrops drops : data.bosses)
		{
			int total = sum(drops.main) + sum(drops.tables);
			assertTrue(drops.id + " main weights " + total + " are over " + drops.mainOutOf, total <= drops.mainOutOf);
			for (DropData.Drop table : drops.tables)
			{
				assertNotNull(drops.id + " links to a missing table " + table.table, data.table(table.table));
			}
			for (DropData.Drop drop : drops.tertiary)
			{
				assertTrue(drops.id + " " + drop.name, drop.numerator > 0 && drop.denominator >= drop.numerator);
			}
			if (drops.preRoll != null)
			{
				assertTrue(drops.id + " unique roll", drops.preRoll.numerator > 0 && drops.preRoll.denominator >= drops.preRoll.numerator);
				assertNotNull(drops.id + " unique roll opens a missing table", data.table(drops.preRoll.table));
			}
		}
		for (String name : data.sharedTables.keySet())
		{
			DropData.SharedTable table = data.table(name);
			assertTrue(name + " weights are over " + table.outOf, sum(table.entries) <= table.outOf);
		}
	}

	@Test
	public void everyLogItemCanDrop() throws Exception
	{
		DropData data = load();
		for (DropData.BossDrops drops : data.bosses)
		{
			Set<Integer> reachable = new HashSet<>();
			collect(data, drops.always, reachable, 0);
			collect(data, drops.main, reachable, 0);
			collect(data, drops.tables, reachable, 0);
			collect(data, drops.tertiary, reachable, 0);
			if (drops.preRoll != null)
			{
				collect(data, List.of(drops.preRoll), reachable, 0);
			}
			for (int itemId : drops.log)
			{
				assertTrue(drops.id + " log item " + itemId + " never drops", reachable.contains(itemId));
			}
		}
	}

	private static void collect(DropData data, List<DropData.Drop> drops, Set<Integer> into, int depth)
	{
		for (DropData.Drop drop : drops)
		{
			if (drop.table != null)
			{
				DropData.SharedTable table = data.table(drop.table);
				if (table != null && depth < 3)
				{
					collect(data, table.entries, into, depth + 1);
				}
			}
			else
			{
				assertTrue(drop.name + " has no item id", drop.itemId > 0);
				into.add(drop.itemId);
			}
		}
	}

	private static int sum(List<DropData.Drop> drops)
	{
		return drops.stream().mapToInt(d -> d.weight).sum();
	}
}
