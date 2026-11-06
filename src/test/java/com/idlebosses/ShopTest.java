package com.idlebosses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.List;
import java.util.Random;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class ShopTest
{
	private static Progress progress(long coins)
	{
		Progress progress = new Progress().migrate();
		progress.coins = coins;
		return progress;
	}

	@Test
	public void oldSavesStartWithWhatTheyLooted()
	{
		Progress loaded = new Gson().fromJson("{\"gpLooted\":5000000}", Progress.class).migrate();
		assertEquals(5_000_000, loaded.coins());
		loaded.addLootValue(1_000);
		assertEquals(5_001_000, loaded.gpLooted);
		assertEquals(5_001_000, loaded.coins());
		assertTrue(loaded.gear.isEmpty());
		assertFalse(loaded.cannon.owned);
	}

	@Test
	public void ladderTiersAreBoughtInOrder()
	{
		Progress progress = progress(100_000_000);
		assertFalse(Shop.buy(progress, Gear.AMULET_OF_GLORY));
		assertTrue(Shop.buy(progress, Gear.AMULET_OF_POWER));
		assertTrue(Shop.buy(progress, Gear.AMULET_OF_GLORY));
		assertEquals(100_000_000 - 25_000 - 500_000, progress.coins());
		assertEquals(Gear.AMULET_OF_GLORY, progress.best(Gear.Slot.AMULET));
		assertFalse(Shop.buy(progress, Gear.AMULET_OF_GLORY));
	}

	@Test
	public void boughtWeaponsAreWieldedAndCanBeSwapped()
	{
		Progress progress = progress(1_000_000);
		assertTrue(Shop.buy(progress, Gear.DRAGON_SCIMITAR));
		assertEquals(Gear.DRAGON_SCIMITAR, progress.wielded());
		assertTrue(Shop.buy(progress, Gear.MAGIC_SHORTBOW));
		assertEquals(Gear.MAGIC_SHORTBOW, progress.wielded());
		assertTrue(Shop.wield(progress, Gear.DRAGON_SCIMITAR));
		assertEquals(Gear.DRAGON_SCIMITAR, progress.wielded());
		assertFalse(Shop.wield(progress, Gear.TWISTED_BOW));
	}

	@Test
	public void bossWeaponsNeedTheirLogItems()
	{
		Progress progress = progress(1_000_000_000);
		assertEquals(List.of(ItemID.WBR_VOIDWAKER_HILT, ItemID.WBR_VOIDWAKER_BLADE, ItemID.WBR_VOIDWAKER_GEM),
			Shop.missing(progress, Gear.VOIDWAKER));
		assertFalse(Shop.buy(progress, Gear.VOIDWAKER));
		progress.addLogItem(ItemID.WBR_VOIDWAKER_HILT);
		progress.addLogItem(ItemID.WBR_VOIDWAKER_BLADE);
		assertFalse(Shop.buy(progress, Gear.VOIDWAKER));
		progress.addLogItem(ItemID.WBR_VOIDWAKER_GEM);
		assertTrue(Shop.buy(progress, Gear.VOIDWAKER));
		// Log items are never used up.
		assertTrue(progress.hasLogItem(ItemID.WBR_VOIDWAKER_GEM));
	}

	@Test
	public void notEnoughCoinsBuysNothing()
	{
		Progress progress = progress(10_000);
		assertFalse(Shop.buy(progress, Gear.AMULET_OF_POWER));
		assertFalse(Shop.buyCannon(progress));
		assertEquals(10_000, progress.coins());
	}

	@Test
	public void theCannonLoadsGraniteFirstAndFiresUntilEmpty()
	{
		Progress progress = progress(100_000_000);
		assertFalse(Shop.buyCannonballs(progress, false));
		assertTrue(Shop.buyCannon(progress));
		assertTrue(Shop.buyCannonballs(progress, false));
		assertEquals(0, Shop.reload(progress) - Shop.CANNON_CAPACITY);
		assertEquals(70, progress.cannon.cannonballs);

		Random random = new Random(4);
		for (int i = 0; i < Shop.CANNON_CAPACITY; i++)
		{
			Hit shot = Shop.fire(progress, Boss.OBOR, 0, random);
			assertTrue(shot.getDamage() >= 0 && shot.getDamage() <= Shop.CANNON_MAX_HIT);
		}
		assertNull(Shop.fire(progress, Boss.OBOR, 0, random));

		assertTrue(Shop.buyCannonballs(progress, true));
		Shop.reload(progress);
		assertEquals(30, progress.cannon.loadedGranite);
		assertEquals(70, progress.cannon.granite);
		assertEquals(70, progress.cannon.cannonballs);
	}

	@Test
	public void theKodaiWandSavesSomeBalls()
	{
		Progress progress = progress(1_000_000_000);
		Shop.buyCannon(progress);
		Random random = new Random(6);
		int shots = 0;
		int used = 0;
		for (int i = 0; i < 200; i++)
		{
			Shop.buyCannonballs(progress, false);
			Shop.reload(progress);
			while (progress.cannon.loaded > 0)
			{
				int before = progress.cannon.loaded;
				Shop.fire(progress, Boss.OBOR, Gear.KODAI_WAND.getValue() / 100.0, random);
				used += before - progress.cannon.loaded;
				shots++;
			}
		}
		assertEquals(0.85, (double) used / shots, 0.01);
	}
}
