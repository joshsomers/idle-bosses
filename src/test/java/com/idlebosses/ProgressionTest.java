package com.idlebosses;

import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

// Plays the ladder many times at a typical pace and prints how long each unlock takes, for tuning Boss.unlockSlots. Each
// simulated player first gets the kills the next boss needs, then fights whichever boss gives new log slots fastest.
public class ProgressionTest
{
	private static final int PLAYERS = 200;
	private static final double GIVE_UP_HOURS = 2_000;

	@Test
	public void everyBossUnlocksInAReasonableTime() throws Exception
	{
		DropData data = DropDataTest.load();
		Boss[] bosses = Boss.values();
		double[][] unlockHours = new double[bosses.length][PLAYERS];
		Map<Boss, Map<Integer, Double>> chances = new EnumMap<>(Boss.class);
		for (Boss boss : bosses)
		{
			chances.put(boss, DropRates.perKill(data, data.boss(boss)));
		}

		for (int p = 0; p < PLAYERS; p++)
		{
			Random random = new Random(p);
			LootRoller roller = new LootRoller(data, random);
			// Like the game, an item fills its slot on every boss whose log it's on.
			Set<Integer> owned = new HashSet<>();
			int[] kills = new int[bosses.length];
			double seconds = 0;
			for (int b = 0; b < bosses.length; b++)
			{
				unlockHours[b][p] = bosses[b].getUnlockSlots() == 0 ? 0 : -1;
			}

			while (unlockHours[bosses.length - 1][p] < 0 && seconds < GIVE_UP_HOURS * 3600)
			{
				Boss boss = gateBoss(bosses, owned.size(), kills);
				if (boss == null)
				{
					boss = fastestSlots(data, chances, bosses, owned, kills);
				}
				if (boss == null)
				{
					break;
				}

				seconds += boss.typicalKillSeconds();
				kills[boss.ordinal()]++;
				DropData.BossDrops drops = data.boss(boss);
				for (Loot loot : roller.roll(drops, random.nextInt(30) == 0))
				{
					if (drops.log.contains(loot.getItemId()))
					{
						owned.add(loot.getItemId());
					}
				}
				for (int b = 0; b < bosses.length; b++)
				{
					if (unlockHours[b][p] < 0 && unlocked(bosses[b], owned.size(), kills))
					{
						unlockHours[b][p] = seconds / 3600;
					}
				}
			}
		}

		System.out.println("Boss                      slots  kills   median hours   90th percentile");
		for (int b = 0; b < bosses.length; b++)
		{
			double[] hours = unlockHours[b].clone();
			Arrays.sort(hours);
			double median = hours[PLAYERS / 2];
			double p90 = hours[PLAYERS * 9 / 10];
			System.out.printf("%-25s %5d  %5d   %12.1f   %15.1f%n", bosses[b].getDisplayName(), bosses[b].getUnlockSlots(),
				bosses[b].unlockKills(), median, p90);
			assertTrue(bosses[b] + " never unlocks for some players", hours[0] >= 0);
		}
	}

	static boolean unlocked(Boss boss, int slots, int[] kills)
	{
		Boss previous = boss.previous();
		return slots >= boss.getUnlockSlots() && (previous == null || kills[previous.ordinal()] >= boss.unlockKills());
	}

	// The unlocked boss that gives a new log slot soonest, like a player chasing unlocks would fight, or null.
	static Boss fastestSlots(DropData data, Map<Boss, Map<Integer, Double>> chances, Boss[] bosses, Set<Integer> owned,
		int[] kills)
	{
		Boss fastest = null;
		double best = 0;
		for (Boss boss : bosses)
		{
			if (!unlocked(boss, owned.size(), kills))
			{
				continue;
			}
			double slotsPerSecond = 0;
			for (int itemId : data.boss(boss).log)
			{
				if (!owned.contains(itemId))
				{
					slotsPerSecond += chances.get(boss).getOrDefault(itemId, 0.0);
				}
			}
			slotsPerSecond /= boss.typicalKillSeconds();
			if (slotsPerSecond > best)
			{
				best = slotsPerSecond;
				fastest = boss;
			}
		}
		return fastest;
	}

	// The newest unlocked boss, while the next boss still needs more kills of it, or null. A player going up the
	// ladder fights it first.
	static Boss gateBoss(Boss[] bosses, int slots, int[] kills)
	{
		for (Boss boss : bosses)
		{
			if (!unlocked(boss, slots, kills))
			{
				Boss previous = boss.previous();
				return previous != null && kills[previous.ordinal()] < boss.unlockKills() ? previous : null;
			}
		}
		return null;
	}
}
