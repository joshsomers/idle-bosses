package com.idlebosses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.api.Skill;
import org.junit.Test;

public class BossFightTest
{
	@Test
	public void anHourOfAnySkillHitsAsHard()
	{
		// 35 Mining XP at 50k an hour, and 210 Fletching XP at 200k an hour, are both about 10 damage.
		assertEquals(10, BossFight.damage(Skill.MINING, 35, Skill.FARMING, 0));
		assertEquals(15, BossFight.damage(Skill.FLETCHING, 210, Skill.FARMING, 0));
		assertEquals(4 * 3600, Math.round(50_000 * SkillRates.DAMAGE_PER_HOUR / SkillRates.xpPerHour(Skill.MINING)));
	}

	@Test
	public void weaknessDoublesAndStreakAddsUp()
	{
		assertEquals(20, BossFight.damage(Skill.MINING, 35, Skill.MINING, 0));
		// +10% for a 10 kill streak, and the bonus stops at 25%.
		assertEquals(11, BossFight.damage(Skill.MINING, 35, Skill.FARMING, 10));
		assertEquals(BossFight.damage(Skill.MINING, 70, Skill.FARMING, 25), BossFight.damage(Skill.MINING, 70, Skill.FARMING, 90));
	}

	@Test
	public void bigDropsHitInFullAndTinyOnesStillHit()
	{
		// A 10k Woodcutting lamp is 2,880 damage, with no limit.
		assertEquals(2_880, BossFight.damage(Skill.WOODCUTTING, 10_000, Skill.FARMING, 0));
		assertEquals(1, BossFight.damage(Skill.COOKING, 1, Skill.FARMING, 0));
	}

	@Test
	public void hitpointsAndSlayerDontHit()
	{
		assertEquals(0, BossFight.damage(Skill.HITPOINTS, 400, Skill.FARMING, 0));
		assertEquals(0, BossFight.damage(Skill.SLAYER, 400, Skill.FARMING, 0));
	}

	@Test
	public void killsAddToTheStreak()
	{
		BossFight fight = new BossFight();
		fight.start(Boss.OBOR, false);
		assertEquals(120, fight.getHp());

		// 40 XP at 50k an hour is 11.5 damage, doubled for Obor's weakness to Woodcutting: 23 a hit.
		int hits = 0;
		do
		{
			assertEquals(23, fight.hit(Skill.WOODCUTTING, 40, 1_000));
			hits++;
		}
		while (!fight.land());
		assertEquals(6, hits);
		assertEquals(0, fight.getHp());
		assertEquals(1, fight.getStreak());
		// 6 hits of 23 is 138: 18 carries on to the next boss.
		assertEquals(18, fight.getPending());
		fight.start(Boss.OBOR, false);
		assertFalse(fight.land());
		assertEquals(102, fight.getHp());
		assertEquals(0, fight.getPending());
	}

	@Test
	public void oneBigDropKillsSeveralBosses()
	{
		BossFight fight = new BossFight();
		fight.start(Boss.OBOR, false);
		// 5,760 damage against 120 HP bosses: 48 kills exactly.
		fight.hit(Skill.WOODCUTTING, 10_000, 0);
		int kills = 0;
		while (fight.land())
		{
			kills++;
			fight.start(Boss.OBOR, false);
		}
		assertEquals(48, kills);
		assertEquals(120, fight.getHp());
		assertEquals(0, fight.getPending());
	}

	@Test
	public void superiorsHaveDoubleHealth()
	{
		BossFight fight = new BossFight();
		fight.start(Boss.GIANT_MOLE, true);
		assertTrue(fight.isSuperior());
		assertEquals(400, fight.getMaxHp());
	}

	@Test
	public void streakEndsAfterAMinuteWithoutXp()
	{
		BossFight fight = new BossFight();
		fight.start(Boss.OBOR, false);
		fight.hit(Skill.WOODCUTTING, 1_000, 0);
		assertTrue(fight.land());
		fight.start(Boss.OBOR, false);
		fight.clearPending();
		assertEquals(0, fight.expireStreak(BossFight.STREAK_TIMEOUT_MILLIS));
		assertEquals(1, fight.expireStreak(BossFight.STREAK_TIMEOUT_MILLIS + 1));
		assertEquals(0, fight.getStreak());
	}

	@Test
	public void heightIsTheTallestPoint()
	{
		assertEquals(150, BossModels.height(new float[]{0, -40, -150, 2}));
		assertEquals(150, BossModels.height(new float[]{0, 40, 150, -2}));
	}

	@Test
	public void tintKeepsLightness()
	{
		short color = (short) (21 << 10 | 3 << 7 | 70);
		assertEquals(70, BossModels.tint(color, MiniBoss.Variant.SUPERIOR) & 0x7F);
		assertEquals(82, BossModels.tint(color, MiniBoss.Variant.GOLD) & 0x7F);
		assertFalse(BossModels.tint(color, MiniBoss.Variant.GOLD) == color);
	}
}
