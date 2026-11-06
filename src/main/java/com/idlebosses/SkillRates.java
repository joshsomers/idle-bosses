package com.idlebosses;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;

// Typical XP an hour for each skill, so an hour of any skill hits about as hard.
// Hitpoints and Slayer only come alongside combat XP, so they don't hit at all.
final class SkillRates
{
	// At a skill's typical rate you deal this much damage an hour: 4 a second.
	static final double DAMAGE_PER_HOUR = 14_400;

	private static final Map<Skill, Integer> XP_PER_HOUR = new EnumMap<>(Skill.class);

	static
	{
		XP_PER_HOUR.put(Skill.ATTACK, 70_000);
		XP_PER_HOUR.put(Skill.STRENGTH, 70_000);
		XP_PER_HOUR.put(Skill.DEFENCE, 70_000);
		XP_PER_HOUR.put(Skill.RANGED, 80_000);
		XP_PER_HOUR.put(Skill.MAGIC, 100_000);
		XP_PER_HOUR.put(Skill.PRAYER, 300_000);
		XP_PER_HOUR.put(Skill.RUNECRAFT, 50_000);
		XP_PER_HOUR.put(Skill.CONSTRUCTION, 300_000);
		XP_PER_HOUR.put(Skill.AGILITY, 50_000);
		XP_PER_HOUR.put(Skill.HERBLORE, 200_000);
		XP_PER_HOUR.put(Skill.THIEVING, 150_000);
		XP_PER_HOUR.put(Skill.CRAFTING, 150_000);
		XP_PER_HOUR.put(Skill.FLETCHING, 200_000);
		XP_PER_HOUR.put(Skill.HUNTER, 80_000);
		XP_PER_HOUR.put(Skill.MINING, 50_000);
		XP_PER_HOUR.put(Skill.SMITHING, 150_000);
		XP_PER_HOUR.put(Skill.FISHING, 50_000);
		XP_PER_HOUR.put(Skill.COOKING, 250_000);
		XP_PER_HOUR.put(Skill.FIREMAKING, 200_000);
		XP_PER_HOUR.put(Skill.WOODCUTTING, 50_000);
		XP_PER_HOUR.put(Skill.FARMING, 150_000);
		XP_PER_HOUR.put(Skill.SAILING, 60_000);
	}

	private SkillRates()
	{
	}

	// 0 for a skill that doesn't hit.
	static int xpPerHour(Skill skill)
	{
		return XP_PER_HOUR.getOrDefault(skill, 0);
	}
}
