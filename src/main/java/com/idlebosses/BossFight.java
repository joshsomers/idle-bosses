package com.idlebosses;

import lombok.Getter;
import net.runelite.api.Skill;

// The boss you're fighting: its health, whether it's a superior, your kill streak, and damage still to land.
// There's no limit on a hit, and damage past a boss's last hitpoint carries on to the next boss.
// Pure state, so it can be tested without a client.
@Getter
final class BossFight
{
	static final double WEAKNESS_MULTIPLIER = 2;
	// Each kill in a row adds 1% damage, up to 25%.
	static final int MAX_STREAK_BONUS = 25;
	static final long STREAK_TIMEOUT_MILLIS = 60_000;
	static final int SUPERIOR_HP_MULTIPLIER = 2;

	private Boss boss;
	private int hp;
	private int maxHp;
	private boolean superior;
	private int streak;
	private long lastXpMillis;
	// Damage from XP drops that hasn't landed yet: what's left after a kill, waiting for the next boss.
	private long pending;
	// XP drops that have hit this boss, for the Soulreaper axe.
	private int hitsOnBoss;
	// Poisoned by the abyssal tentacle until it dies.
	private boolean poisoned;
	// Health the Voidwaker took in its last finishing blow, to show as a hitsplat.
	private long executed;

	// Brings in a boss at full health. Pending damage is kept, so it lands on this one.
	void start(Boss boss, boolean superior)
	{
		this.boss = boss;
		this.superior = superior;
		maxHp = boss.getHitpoints() * (superior ? SUPERIOR_HP_MULTIPLIER : 1);
		hp = maxHp;
		hitsOnBoss = 0;
		poisoned = false;
	}

	static long damage(Skill skill, int xp, Skill weakness, int streak)
	{
		return damage(skill, xp, weakness, streak, 1);
	}

	// Scale makes hits bigger against tougher bosses; see Boss.damageScale.
	static long damage(Skill skill, int xp, Skill weakness, int streak, double scale)
	{
		int xpPerHour = SkillRates.xpPerHour(skill);
		if (xpPerHour <= 0 || xp <= 0)
		{
			return 0;
		}
		double damage = xp * SkillRates.DAMAGE_PER_HOUR / xpPerHour;
		if (skill == weakness)
		{
			damage *= WEAKNESS_MULTIPLIER;
		}
		damage *= 1 + Math.min(streak, MAX_STREAK_BONUS) / 100.0;
		return Math.max(1, Math.round(damage * scale));
	}

	int streakBonus()
	{
		return Math.min(streak, MAX_STREAK_BONUS);
	}

	// Adds an XP drop's damage, to be landed with land(). Returns the damage, or 0 if the skill doesn't hit.
	long hit(Skill skill, int xp, long nowMillis)
	{
		long damage = baseDamage(skill, xp);
		if (damage > 0)
		{
			xpGained(nowMillis);
			pending += damage;
		}
		return damage;
	}

	// What an XP drop hits this boss for before gear, or 0 if the skill doesn't hit.
	long baseDamage(Skill skill, int xp)
	{
		return boss == null ? 0 : damage(skill, xp, boss.getWeakness(), streak, boss.damageScale());
	}

	// An XP drop keeps the streak, and the cannon and poison, going.
	void xpGained(long nowMillis)
	{
		lastXpMillis = nowMillis;
	}

	// True while you're training: there's been XP within the streak's time.
	boolean isActive(long nowMillis)
	{
		return lastXpMillis > 0 && nowMillis - lastXpMillis <= STREAK_TIMEOUT_MILLIS;
	}

	// Adds damage to land with land(), without counting as XP.
	void addDamage(long damage)
	{
		if (boss != null && damage > 0)
		{
			pending += damage;
		}
	}

	void addHitOnBoss()
	{
		hitsOnBoss++;
	}

	void poison()
	{
		poisoned = true;
	}

	// The health the Voidwaker last finished off, then 0 until it does again.
	long takeExecuted()
	{
		long taken = executed;
		executed = 0;
		return taken;
	}

	boolean land()
	{
		return land(0);
	}

	// Lands pending damage on the boss. True when that kills it: start the next boss and call again for the rest.
	// A boss left under executeBelow of its health is finished off.
	boolean land(double executeBelow)
	{
		if (boss == null || pending <= 0)
		{
			return false;
		}
		long used = Math.min(pending, hp);
		hp -= used;
		pending -= used;
		if (hp > 0 && hp < maxHp * executeBelow)
		{
			executed += hp;
			hp = 0;
		}
		if (hp > 0)
		{
			return false;
		}
		streak++;
		return true;
	}

	void clearPending()
	{
		pending = 0;
	}

	// The streak just lost, or 0.
	int expireStreak(long nowMillis)
	{
		if (streak > 0 && nowMillis - lastXpMillis > STREAK_TIMEOUT_MILLIS)
		{
			int lost = streak;
			streak = 0;
			return lost;
		}
		return 0;
	}
}
