package com.idlebosses;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import lombok.Value;

// What you're wearing and wielding, and how it turns an XP drop's damage into hits. Pure, so it can be tested.
final class Loadout
{
	// From the bosses' Wiki attributes, for the dragon hunter crossbow and Arclight. A run counts by its final boss.
	static final Set<Boss> DRAGONS = EnumSet.of(Boss.KING_BLACK_DRAGON, Boss.VORKATH, Boss.ALCHEMICAL_HYDRA,
		Boss.HUEYCOATL, Boss.CHAMBERS_OF_XERIC);
	static final Set<Boss> DEMONS = EnumSet.of(Boss.ABYSSAL_SIRE, Boss.CERBERUS, Boss.DOOM_OF_MOKHAIOTL,
		Boss.DUKE_SUCELLUS, Boss.KRIL_TSUTSAROTH, Boss.SKOTIZO, Boss.YAMA, Boss.FRACTURED_ARCHIVE);
	static final double SANGUINESTI_CHANCE = 1 / 6.0;
	private static final double[] CLAW_HITS = {1, 0.5, 0.25, 0.25};
	private static final double[] SCYTHE_HITS = {1, 0.5, 0.25};
	private static final double[] DOUBLE_HIT = {1, 1};
	private static final double[] ONE_HIT = {1};

	private final Gear amulet;
	private final Gear gloves;
	private final Gear weapon;

	Loadout(Gear amulet, Gear gloves, Gear weapon)
	{
		this.amulet = amulet;
		this.gloves = gloves;
		this.weapon = weapon;
	}

	static Loadout of(Progress progress)
	{
		return new Loadout(progress.best(Gear.Slot.AMULET), progress.best(Gear.Slot.GLOVES), progress.wielded());
	}

	boolean wields(Gear gear)
	{
		return weapon == gear;
	}

	Gear weapon()
	{
		return weapon;
	}

	// The amulet's damage bonus, as a fraction.
	double amuletBonus()
	{
		double bonus = amulet == null ? 0 : amulet.getValue() / 100.0;
		return wields(Gear.TUMEKENS_SHADOW) ? bonus * Gear.TUMEKENS_SHADOW.getValue() : bonus;
	}

	// The health a boss is finished at, as a fraction of its maximum. 0 without the Voidwaker.
	double executeBelow()
	{
		return wields(Gear.VOIDWAKER) ? Gear.VOIDWAKER.getValue() / 100.0 : 0;
	}

	double cannonballSaveChance()
	{
		return wields(Gear.KODAI_WAND) ? Gear.KODAI_WAND.getValue() / 100.0 : 0;
	}

	// One XP drop's hits, and what the weapon's effect did if it triggered.
	@Value
	static class Attack
	{
		List<Hit> hits;
		// The first this many hits are one special's, landing a beat apart. 1 for a normal drop.
		int staggered;
		// What the effect did, for the HUD's banner, like "x4" for the claws' four hits. Null when nothing triggered.
		String special;
	}

	// The hits one XP drop makes. The fight is told about hits on its boss, for the Soulreaper axe and poison.
	Attack attack(long base, BossFight fight, Random random)
	{
		List<Hit> hits = new ArrayList<>();
		Boss boss = fight.getBoss();
		if (base <= 0 || boss == null)
		{
			return new Attack(hits, 0, null);
		}

		String special = null;
		double damage = base * (1 + amuletBonus()) * (1 + weaponBonus(boss, fight.getHitsOnBoss()));
		Hit.Type type = Hit.Type.NORMAL;
		double critChance = gloves == null ? 0 : gloves.getValue() / 100.0;
		boolean crit = random.nextDouble() < critChance;
		// Osmumten's fang gets a second try.
		if (!crit && wields(Gear.OSMUMTENS_FANG) && random.nextDouble() < critChance)
		{
			crit = true;
			special = "Second roll";
		}
		if (crit)
		{
			type = Hit.Type.CRITICAL;
			if (wields(Gear.ARMADYL_CROSSBOW))
			{
				damage *= Gear.ARMADYL_CROSSBOW.getValue();
				special = "x" + Gear.ARMADYL_CROSSBOW.getValue();
			}
			else
			{
				damage *= 2;
			}
		}
		if (wields(Gear.SANGUINESTI_STAFF) && random.nextDouble() < SANGUINESTI_CHANCE)
		{
			damage *= 1 + Gear.SANGUINESTI_STAFF.getValue() / 100.0;
			special = "+" + Gear.SANGUINESTI_STAFF.getValue() + "%";
		}

		double[] split = split(random);
		if (split.length > 1)
		{
			special = "x" + split.length;
		}
		for (double share : split)
		{
			hits.add(new Hit(Math.max(1, Math.round(damage * share)), type));
		}
		if (wields(Gear.TOXIC_BLOWPIPE))
		{
			hits.add(new Hit(Math.max(1, Math.round(Gear.TOXIC_BLOWPIPE.getValue() * boss.damageScale())), Hit.Type.DART));
		}
		if (wields(Gear.ABYSSAL_TENTACLE) && !fight.isPoisoned() && random.nextInt(100) < Gear.ABYSSAL_TENTACLE.getValue())
		{
			fight.poison();
			special = "Poisoned";
		}
		fight.addHitOnBoss();
		return new Attack(hits, split.length, special);
	}

	// A poison tick: about 5% of what you'd deal in the same time at a typical pace.
	static long poisonDamage(Boss boss)
	{
		return Math.max(1, Math.round(boss.damageScale()));
	}

	private double weaponBonus(Boss boss, int hitsOnBoss)
	{
		if (weapon == null)
		{
			return 0;
		}
		switch (weapon)
		{
			case DRAGON_SCIMITAR:
				return weapon.getValue() / 100.0;
			case ARCLIGHT:
				return DEMONS.contains(boss) ? weapon.getValue() / 100.0 : 0;
			case DRAGON_HUNTER_CROSSBOW:
				return DRAGONS.contains(boss) ? weapon.getValue() / 100.0 : 0;
			case SOULREAPER_AXE:
				return Math.min(hitsOnBoss, weapon.getValue()) / 100.0;
			case TWISTED_BOW:
				// +1% for each point of toughness: 1% on Obor, about 8% on the Nightmare.
				return Math.min(boss.damageScale(), weapon.getValue()) / 100.0;
			default:
				return 0;
		}
	}

	// How the drop's damage is shared across its hits.
	private double[] split(Random random)
	{
		if (wields(Gear.DRAGON_CLAWS) && random.nextInt(Gear.DRAGON_CLAWS.getValue()) == 0)
		{
			return CLAW_HITS;
		}
		if (wields(Gear.SCYTHE_OF_VITUR) && random.nextInt(100) < Gear.SCYTHE_OF_VITUR.getValue())
		{
			return SCYTHE_HITS;
		}
		if (wields(Gear.MAGIC_SHORTBOW) && random.nextInt(100) < Gear.MAGIC_SHORTBOW.getValue())
		{
			return DOUBLE_HIT;
		}
		return ONE_HIT;
	}
}
