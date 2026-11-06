package com.idlebosses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import org.junit.Test;

public class ProgressTest
{
	@Test
	public void savesAndLoads()
	{
		Progress progress = new Progress();
		progress.currentBoss = Boss.GIANT_MOLE.getId();
		progress.gpLooted = 1_234_567;
		progress.addKill(Boss.GIANT_MOLE);
		progress.addKill(Boss.GIANT_MOLE);
		progress.addLogItem(7416);

		Gson gson = new Gson();
		Progress loaded = gson.fromJson(gson.toJson(progress), Progress.class);
		assertEquals("giant_mole", loaded.currentBoss);
		assertEquals(1_234_567, loaded.gpLooted);
		assertEquals(2, loaded.kills(Boss.GIANT_MOLE));
		assertTrue(loaded.hasLogItem(7416));
		assertFalse(loaded.addLogItem(7416));
	}

	@Test
	public void lootAddsUpAndSurvivesSaving()
	{
		Progress progress = new Progress();
		progress.addLoot(Boss.OBOR, 532, 1);
		progress.addLoot(Boss.OBOR, 532, 2);
		progress.addLoot(Boss.OBOR, 995, 3_000);

		Progress loaded = new Gson().fromJson(new Gson().toJson(progress), Progress.class);
		assertEquals(3L, (long) loaded.loot(Boss.OBOR).get(532));
		assertEquals(3_000L, (long) loaded.loot(Boss.OBOR).get(995));
		assertTrue(loaded.loot(Boss.VORKATH).isEmpty());
	}

	@Test
	public void oldPerBossLogsJoinTheSharedLog()
	{
		String old = "{\"bosses\":{\"obor\":{\"kills\":3,\"log\":[20756]},\"king_black_dragon\":{\"log\":[11920]}}}";
		Progress loaded = new Gson().fromJson(old, Progress.class).migrate();
		assertEquals(2, loaded.totalSlots());
		assertTrue(loaded.hasLogItem(11920));
		// A dragon pickaxe from one boss fills that slot on every boss's log, like the game.
		assertEquals(1, loaded.logCount(java.util.List.of(11920, 7980)));
		assertTrue(new Gson().toJson(loaded).indexOf("\"log\"") < 0);
	}

	@Test
	public void bossesNeedLogSlotsAndKillsOfTheOneBefore()
	{
		Boss first = null;
		for (Boss boss : Boss.values())
		{
			if (first == null && boss.getUnlockSlots() > 0)
			{
				first = boss;
			}
		}

		Progress progress = new Progress();
		assertTrue(progress.isUnlocked(Boss.OBOR));
		assertFalse(progress.isUnlocked(first));
		for (int item = 1; item <= first.getUnlockSlots(); item++)
		{
			progress.addLogItem(item);
		}
		// The slots alone aren't enough.
		assertFalse(progress.isUnlocked(first));
		for (int kill = 1; kill < first.unlockKills(); kill++)
		{
			progress.addKill(first.previous());
		}
		assertFalse(progress.isUnlocked(first));
		progress.addKill(first.previous());
		assertTrue(progress.isUnlocked(first));
		assertFalse(progress.isUnlocked(Boss.values()[Boss.values().length - 1]));
		assertEquals(first.getUnlockSlots(), progress.totalSlots());
	}

	@Test
	public void logItemsRememberTheKillTheyDroppedOn()
	{
		Progress progress = new Progress().migrate();
		progress.addKill(Boss.OBOR);
		progress.addKill(Boss.OBOR);
		assertTrue(progress.addLogItem(20756, Boss.OBOR));
		assertEquals("obor", progress.logDrop(20756).boss);
		assertEquals(2, progress.logDrop(20756).kills);
		// A second one doesn't move it.
		progress.addKill(Boss.OBOR);
		assertFalse(progress.addLogItem(20756, Boss.OBOR));
		assertEquals(2, progress.logDrop(20756).kills);

		Gson gson = new Gson();
		assertEquals(2, gson.fromJson(gson.toJson(progress), Progress.class).migrate().logDrop(20756).kills);
		// Saves from before it was kept still load, without a drop KC.
		Progress old = gson.fromJson("{\"logItems\":[20756]}", Progress.class).migrate();
		assertTrue(old.hasLogItem(20756));
		assertNull(old.logDrop(20756));
	}

	@Test
	public void aBossYouveFoughtStaysUnlocked()
	{
		Progress progress = new Progress();
		Boss last = Boss.values()[Boss.values().length - 1];
		assertFalse(progress.isUnlocked(last));
		progress.addKill(last);
		assertTrue(progress.isUnlocked(last));
	}

	@Test
	public void unlockKillsAreAboutTenMinutesOfTheBossBefore()
	{
		for (Boss boss : Boss.values())
		{
			if (boss.getUnlockSlots() == 0)
			{
				assertEquals(0, boss.unlockKills());
				continue;
			}
			assertEquals(0, boss.unlockKills() % 5);
			double killMinutes = boss.previous().typicalKillSeconds() / 60;
			double minutes = boss.unlockKills() * killMinutes;
			// Rounding up to a multiple of 5 adds at most 5 kills.
			assertTrue(boss + " needs " + minutes + " minutes", minutes >= 10 && (minutes < 10 + 5 * killMinutes || boss.unlockKills() == 5));
		}
	}

	@Test
	public void oldSavesWithMissingFieldsStillLoad()
	{
		Progress loaded = new Gson().fromJson("{\"bosses\":{\"obor\":{\"kills\":3}}}", Progress.class);
		assertEquals(3, loaded.kills(Boss.OBOR));
		assertEquals(0, loaded.logCount(java.util.List.of(20756)));
		assertEquals(0, loaded.kills(Boss.VORKATH));
	}
}
