package com.idlebosses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class LoadoutTest
{
	private static BossFight fight(Boss boss)
	{
		BossFight fight = new BossFight();
		fight.start(boss, false);
		return fight;
	}

	private static long total(List<Hit> hits)
	{
		return hits.stream().mapToLong(Hit::getDamage).sum();
	}

	@Test
	public void theAmuletAddsFlatDamage()
	{
		Loadout loadout = new Loadout(Gear.AMULET_OF_FURY, null, null);
		List<Hit> hits = loadout.attack(100, fight(Boss.OBOR), new Random(1)).getHits();
		assertEquals(1, hits.size());
		assertEquals(107, hits.get(0).getDamage());
		assertEquals(Hit.Type.NORMAL, hits.get(0).getType());
	}

	@Test
	public void tumekensShadowDoublesTheAmulet()
	{
		Loadout loadout = new Loadout(Gear.AMULET_OF_FURY, null, Gear.TUMEKENS_SHADOW);
		assertEquals(114, total(loadout.attack(100, fight(Boss.OBOR), new Random(1)).getHits()));
	}

	@Test
	public void glovesGiveCriticalHits()
	{
		assertEquals(0.06, critRate(new Loadout(null, Gear.FEROCIOUS_GLOVES, null), 200), 0.005);
		// Osmumten's fang rolls twice.
		assertEquals(1 - 0.94 * 0.94, critRate(new Loadout(null, Gear.FEROCIOUS_GLOVES, Gear.OSMUMTENS_FANG), 200), 0.005);
		// The Armadyl crossbow's critical hits deal 3x.
		assertEquals(0.06, critRate(new Loadout(null, Gear.FEROCIOUS_GLOVES, Gear.ARMADYL_CROSSBOW), 300), 0.005);
	}

	private static double critRate(Loadout loadout, long critDamage)
	{
		Random random = new Random(3);
		int drops = 100_000;
		int crits = 0;
		for (int i = 0; i < drops; i++)
		{
			Hit hit = loadout.attack(100, fight(Boss.OBOR), random).getHits().get(0);
			if (hit.getType() == Hit.Type.CRITICAL)
			{
				assertEquals(critDamage, hit.getDamage());
				crits++;
			}
		}
		return (double) crits / drops;
	}

	@Test
	public void clawsAndScytheSplitADropIntoSeveralHits()
	{
		Random random = new Random(5);
		boolean sawClaws = false;
		for (int i = 0; i < 1_000 && !sawClaws; i++)
		{
			List<Hit> hits = new Loadout(null, null, Gear.DRAGON_CLAWS).attack(100, fight(Boss.OBOR), random).getHits();
			if (hits.size() > 1)
			{
				assertEquals(4, hits.size());
				assertEquals(200, total(hits));
				sawClaws = true;
			}
		}
		assertTrue(sawClaws);

		boolean sawScythe = false;
		for (int i = 0; i < 1_000 && !sawScythe; i++)
		{
			List<Hit> hits = new Loadout(null, null, Gear.SCYTHE_OF_VITUR).attack(100, fight(Boss.OBOR), random).getHits();
			if (hits.size() > 1)
			{
				assertEquals(3, hits.size());
				assertEquals(175, total(hits));
				sawScythe = true;
			}
		}
		assertTrue(sawScythe);
	}

	@Test
	public void slayerWeaponsOnlyWorkOnTheirBosses()
	{
		Loadout dragonHunter = new Loadout(null, null, Gear.DRAGON_HUNTER_CROSSBOW);
		assertEquals(120, total(dragonHunter.attack(100, fight(Boss.VORKATH), new Random(1)).getHits()));
		assertEquals(100, total(dragonHunter.attack(100, fight(Boss.OBOR), new Random(1)).getHits()));

		Loadout arclight = new Loadout(null, null, Gear.ARCLIGHT);
		assertEquals(120, total(arclight.attack(100, fight(Boss.KRIL_TSUTSAROTH), new Random(1)).getHits()));
		assertEquals(100, total(arclight.attack(100, fight(Boss.VORKATH), new Random(1)).getHits()));
	}

	@Test
	public void theTwistedBowScalesWithToughness()
	{
		Loadout bow = new Loadout(null, null, Gear.TWISTED_BOW);
		assertEquals(101, total(bow.attack(100, fight(Boss.OBOR), new Random(1)).getHits()));
		assertEquals(108, total(bow.attack(100, fight(Boss.NIGHTMARE), new Random(1)).getHits()));
		assertEquals(112, total(bow.attack(100, fight(Boss.TZKAL_ZUK), new Random(1)).getHits()));
	}

	@Test
	public void soulreaperStacksUntilTheBossDies()
	{
		Loadout axe = new Loadout(null, null, Gear.SOULREAPER_AXE);
		BossFight fight = fight(Boss.OBOR);
		long last = 0;
		for (int i = 0; i < 15; i++)
		{
			last = total(axe.attack(100, fight, new Random(1)).getHits());
		}
		assertEquals(110, last);
		fight.start(Boss.OBOR, false);
		assertEquals(100, total(axe.attack(100, fight, new Random(1)).getHits()));
	}

	@Test
	public void theBlowpipeFiresADartAndTheTentaclePoisons()
	{
		List<Hit> hits = new Loadout(null, null, Gear.TOXIC_BLOWPIPE).attack(100, fight(Boss.OBOR), new Random(1)).getHits();
		assertEquals(2, hits.size());
		assertEquals(Hit.Type.DART, hits.get(1).getType());
		assertEquals(1, hits.get(1).getDamage());

		BossFight fight = fight(Boss.OBOR);
		Loadout tentacle = new Loadout(null, null, Gear.ABYSSAL_TENTACLE);
		Random random = new Random(2);
		for (int i = 0; i < 100 && !fight.isPoisoned(); i++)
		{
			tentacle.attack(100, fight, random).getHits();
		}
		assertTrue(fight.isPoisoned());
		fight.start(Boss.OBOR, false);
		assertFalse(fight.isPoisoned());
	}

	@Test
	public void specialsSayWhatTheyDidAndStaggerTheirHits()
	{
		Random random = new Random(5);
		Loadout.Attack claws = null;
		for (int i = 0; i < 1_000 && claws == null; i++)
		{
			Loadout.Attack attack = new Loadout(null, null, Gear.DRAGON_CLAWS).attack(100, fight(Boss.OBOR), random);
			if (attack.getSpecial() != null)
			{
				claws = attack;
			}
		}
		assertEquals("x4", claws.getSpecial());
		assertEquals(4, claws.getStaggered());

		Loadout.Attack plain = new Loadout(null, null, Gear.DRAGON_SCIMITAR).attack(100, fight(Boss.OBOR), new Random(1));
		assertNull(plain.getSpecial());
		assertEquals(1, plain.getStaggered());

		assertEquals("+50%", firstSpecial(new Loadout(null, null, Gear.SANGUINESTI_STAFF)));
		assertEquals("x3", firstSpecial(new Loadout(null, Gear.FEROCIOUS_GLOVES, Gear.ARMADYL_CROSSBOW)));
		assertEquals("Second roll", firstSpecial(new Loadout(null, Gear.FEROCIOUS_GLOVES, Gear.OSMUMTENS_FANG)));
		assertEquals("Poisoned", firstSpecial(new Loadout(null, null, Gear.ABYSSAL_TENTACLE)));
	}

	private static String firstSpecial(Loadout loadout)
	{
		Random random = new Random(7);
		for (int i = 0; i < 1_000; i++)
		{
			String special = loadout.attack(100, fight(Boss.OBOR), random).getSpecial();
			if (special != null)
			{
				return special;
			}
		}
		return null;
	}

	@Test
	public void theVoidwakerFinishesLowBosses()
	{
		BossFight fight = fight(Boss.OBOR);
		double executeBelow = new Loadout(null, null, Gear.VOIDWAKER).executeBelow();
		// Obor has 120 hitpoints, so 9 left is under 8%.
		fight.addDamage(111);
		assertTrue(fight.land(executeBelow));
		assertEquals(9, fight.takeExecuted());
		assertEquals(0, fight.takeExecuted());

		fight.start(Boss.OBOR, false);
		fight.addDamage(100);
		assertFalse(fight.land(executeBelow));
		assertEquals(20, fight.getHp());
	}
}
