package com.idlebosses;

import static org.junit.Assert.assertEquals;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class DropRatesTest
{
	@Test
	public void addsUpEveryRoute()
	{
		DropData data = LootRollerTest.data();
		DropData.BossDrops boss = data.bosses.get(0);
		Map<Integer, Double> rates = DropRates.perKill(data, boss);
		assertEquals(1, rates.get(532), 1e-9);
		assertEquals(0.5, rates.get(1), 1e-9);
		assertEquals(0.2 / 4, rates.get(1621), 1e-9);
		assertEquals(1.0 / 50, rates.get(3), 1e-9);

		// Two rolls: the chance of at least one.
		boss.rolls = 2;
		assertEquals(0.75, DropRates.perKill(data, boss).get(1), 1e-9);
	}

	@Test
	public void matchesTheWikiForRealBosses() throws Exception
	{
		DropData data = DropDataTest.load();
		Map<Integer, Double> mole = DropRates.perKill(data, data.boss(Boss.GIANT_MOLE));
		assertEquals("Always", DropRates.format(mole.get(ItemID.MOLE_SKIN)));
		assertEquals("1/3,000", DropRates.format(mole.get(ItemID.MOLEPET)));

		Map<Integer, Double> vorkath = DropRates.perKill(data, data.boss(Boss.VORKATH));
		assertEquals("1/50", DropRates.format(vorkath.get(ItemID.VORKATH_HEAD)));

		Map<Integer, Double> obor = DropRates.perKill(data, data.boss(Boss.OBOR));
		assertEquals("1/118", DropRates.format(obor.get(ItemID.HILLGIANT_BOSS_CLUB)));
	}

	@Test
	public void formatsLikeTheWiki()
	{
		assertEquals("Always", DropRates.format(1));
		assertEquals("1/8.6", DropRates.format(1 / 8.6));
		assertEquals("1/128", DropRates.format(1.0 / 128));
		assertEquals("1/5,000", DropRates.format(1.0 / 5000));
	}
}
