package com.idlebosses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

public class LootRollerTest
{
	private static DropData.Drop item(int id, String name, int weight)
	{
		DropData.Drop drop = new DropData.Drop();
		drop.itemId = id;
		drop.name = name;
		drop.weight = weight;
		return drop;
	}

	private static DropData.Drop tertiary(int id, String name, int numerator, int denominator)
	{
		DropData.Drop drop = item(id, name, 0);
		drop.numerator = numerator;
		drop.denominator = denominator;
		return drop;
	}

	private static DropData.Drop table(String name, int weight)
	{
		DropData.Drop drop = new DropData.Drop();
		drop.table = name;
		drop.weight = weight;
		return drop;
	}

	static DropData data()
	{
		DropData data = new DropData();
		DropData.SharedTable gems = new DropData.SharedTable();
		gems.outOf = 4;
		gems.entries = List.of(item(1623, "Uncut sapphire", 3), item(1621, "Uncut emerald", 1));
		data.sharedTables = Map.of("gem_drop_table", gems);

		DropData.BossDrops boss = new DropData.BossDrops();
		boss.id = "test";
		boss.always = List.of(item(532, "Big bones", 0));
		boss.main = List.of(item(1, "Common", 50), item(2, "Uncommon", 10));
		boss.tables = List.of(table("gem_drop_table", 20));
		// 20 of 100 gives nothing.
		boss.mainOutOf = 100;
		boss.tertiary = List.of(tertiary(3, "Pet", 1, 50));
		data.bosses = List.of(boss);
		return data.normalize();
	}

	@Test
	public void ratesMatchTheTable()
	{
		DropData data = data();
		LootRoller roller = new LootRoller(data, new Random(7));
		DropData.BossDrops boss = data.bosses.get(0);

		int kills = 100_000;
		Map<Integer, Integer> seen = new HashMap<>();
		for (int i = 0; i < kills; i++)
		{
			for (Loot loot : roller.roll(boss, false))
			{
				seen.merge(loot.getItemId(), 1, Integer::sum);
			}
		}

		assertEquals(kills, (int) seen.get(532));
		assertNear(0.50, seen.get(1), kills);
		assertNear(0.10, seen.get(2), kills);
		assertNear(0.20 * 3 / 4, seen.get(1623), kills);
		assertNear(0.20 / 4, seen.get(1621), kills);
		assertNear(1.0 / 50, seen.get(3), kills);
	}

	@Test
	public void chanceCarriesThroughSharedTables()
	{
		DropData data = data();
		LootRoller roller = new LootRoller(data, new Random(1));
		for (int i = 0; i < 2_000; i++)
		{
			for (Loot loot : roller.roll(data.bosses.get(0), false))
			{
				if (loot.getItemId() == 1621)
				{
					assertEquals(0.05, loot.getChance(), 1e-9);
					return;
				}
			}
		}
		throw new AssertionError("Never rolled the emerald");
	}

	@Test
	public void superiorsRollTheMainTableAgain()
	{
		DropData data = data();
		DropData.BossDrops boss = data.bosses.get(0);
		boss.main = List.of(item(1, "Common", 100));
		boss.tables = List.of();
		LootRoller roller = new LootRoller(data, new Random(3));
		assertEquals(1, quantity(roller.roll(boss, false), 1));
		assertEquals(2, quantity(roller.roll(boss, true), 1));
	}

	@Test
	public void tertiaryRollsCanOpenATable()
	{
		DropData data = data();
		DropData.BossDrops boss = data.bosses.get(0);
		DropData.Drop unsired = new DropData.Drop();
		unsired.table = "gem_drop_table";
		unsired.numerator = 1;
		unsired.denominator = 10;
		boss.tertiary = List.of(unsired);
		boss.main = List.of();
		boss.tables = List.of();

		LootRoller roller = new LootRoller(data, new Random(11));
		int kills = 100_000;
		int emeralds = 0;
		for (int i = 0; i < kills; i++)
		{
			emeralds += quantity(roller.roll(boss, false), 1621);
		}
		// 1/10 to open the table, then 1/4 for the emerald.
		assertNear(1.0 / 40, emeralds, kills);
		assertEquals(1.0 / 40, DropRates.perKill(data, boss).get(1621), 1e-9);
	}

	@Test
	public void aRaidUniqueReplacesTheCommonRolls()
	{
		DropData data = data();
		DropData.BossDrops boss = data.bosses.get(0);
		boss.main = List.of(item(1, "Common", 100));
		boss.tables = List.of();
		boss.rolls = 3;
		DropData.Drop unique = new DropData.Drop();
		unique.table = "gem_drop_table";
		unique.numerator = 1;
		unique.denominator = 5;
		boss.preRoll = unique;

		LootRoller roller = new LootRoller(data, new Random(5));
		int kills = 100_000;
		int uniques = 0;
		for (int i = 0; i < kills; i++)
		{
			List<Loot> loot = roller.roll(boss, false);
			int gems = quantity(loot, 1623) + quantity(loot, 1621);
			uniques += gems;
			assertEquals(gems > 0 ? 0 : 3, quantity(loot, 1));
		}
		assertNear(0.2, uniques, kills);

		Map<Integer, Double> rates = DropRates.perKill(data, boss);
		assertEquals(0.8, rates.get(1), 1e-9);
		assertEquals(0.2 / 4, rates.get(1621), 1e-9);
		assertEquals(1.0 / 50, rates.get(3), 1e-9);
	}

	@Test
	public void petsAreRare()
	{
		assertTrue(new Loot(3, "Pet", 1, 1.0 / 3000).isRare());
		assertTrue(!new Loot(4, "Club", 1, 1.0 / 50).isRare());
	}

	private static int quantity(List<Loot> loot, int itemId)
	{
		return loot.stream().filter(l -> l.getItemId() == itemId).mapToInt(Loot::getQuantity).sum();
	}

	private static void assertNear(double expected, int count, int kills)
	{
		assertEquals(expected, (double) count / kills, 0.006);
	}
}
