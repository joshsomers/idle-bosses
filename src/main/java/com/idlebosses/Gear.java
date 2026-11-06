package com.idlebosses;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

// Upgrades bought with coins. The amulet and gloves are ladders: each tier replaces the one before. Weapons are
// owned side by side and one is wielded. A weapon or tier that needs boss loot also needs those items in your
// collection log; collection log items are never used up.
@Getter
enum Gear
{
	AMULET_OF_POWER(Slot.AMULET, "Amulet of power", ItemID.AMULET_OF_POWER, 25_000, 2, "+2% damage"),
	AMULET_OF_GLORY(Slot.AMULET, "Amulet of glory", ItemID.AMULET_OF_GLORY_4, 500_000, 4, "+4% damage"),
	AMULET_OF_FURY(Slot.AMULET, "Amulet of fury", ItemID.ENCHANTED_ONYX_AMULET, 10_000_000, 7, "+7% damage"),
	AMULET_OF_TORTURE(Slot.AMULET, "Amulet of torture", ItemID.ZENYTE_AMULET_ENCHANTED, 75_000_000, 10, "+10% damage"),
	AMULET_OF_RANCOUR(Slot.AMULET, "Amulet of rancour", ItemID.AMULET_OF_RANCOUR, 400_000_000, 12, "+12% damage",
		ItemID.ARAXYTE_FANG),

	RUNE_GLOVES(Slot.GLOVES, "Rune gloves", ItemID.HUNDRED_GAUNTLETS_LEVEL_8, 100_000, 2, "2% critical hits"),
	BARROWS_GLOVES(Slot.GLOVES, "Barrows gloves", ItemID.HUNDRED_GAUNTLETS_LEVEL_10, 5_000_000, 4, "4% critical hits"),
	FEROCIOUS_GLOVES(Slot.GLOVES, "Ferocious gloves", ItemID.FEROCIOUS_GLOVES, 150_000_000, 6, "6% critical hits",
		ItemID.HYDRA_LEATHER),

	DRAGON_SCIMITAR(Slot.WEAPON, "Dragon scimitar", ItemID.DRAGON_SCIMITAR, 100_000, 3, "+3% damage"),
	MAGIC_SHORTBOW(Slot.WEAPON, "Magic shortbow (i)", ItemID.MAGIC_SHORTBOW_I, 750_000, 4, "4% of drops hit twice"),
	ARCLIGHT(Slot.WEAPON, "Arclight", ItemID.ARCLIGHT, 8_000_000, 20, "+20% against demons", ItemID.CATA_SHARD),
	ABYSSAL_TENTACLE(Slot.WEAPON, "Abyssal tentacle", ItemID.ABYSSAL_TENTACLE, 12_000_000, 20, "Hits can poison the boss",
		ItemID.KRAKEN_TENTACLE),
	TOXIC_BLOWPIPE(Slot.WEAPON, "Toxic blowpipe", ItemID.TOXIC_BLOWPIPE_LOADED, 25_000_000, 1, "Every XP drop also fires a dart",
		ItemID.BLOWPIPE_FANG),
	DRAGON_HUNTER_CROSSBOW(Slot.WEAPON, "Dragon hunter crossbow", ItemID.DRAGONHUNTER_XBOW, 30_000_000, 20,
		"+20% against dragons", ItemID.DRAGONHUNTER_XBOW),
	ARMADYL_CROSSBOW(Slot.WEAPON, "Armadyl crossbow", ItemID.ACB, 40_000_000, 3, "Critical hits deal 3x", ItemID.ACB),
	OSMUMTENS_FANG(Slot.WEAPON, "Osmumten's fang", ItemID.OSMUMTENS_FANG, 50_000_000, 2, "Critical hit chance rolled twice",
		ItemID.OSMUMTENS_FANG),
	DRAGON_CLAWS(Slot.WEAPON, "Dragon claws", ItemID.DRAGON_CLAWS, 60_000_000, 15, "1 drop in 15 hits four times",
		ItemID.DRAGON_CLAWS),
	KODAI_WAND(Slot.WEAPON, "Kodai wand", ItemID.KODAI_WAND, 60_000_000, 15, "15% of cannon shots save the ball",
		ItemID.KODAI_INSIGNIA),
	VOIDWAKER(Slot.WEAPON, "Voidwaker", ItemID.VOIDWAKER, 100_000_000, 8, "Finishes a boss under 8% health",
		ItemID.WBR_VOIDWAKER_HILT, ItemID.WBR_VOIDWAKER_BLADE, ItemID.WBR_VOIDWAKER_GEM),
	SANGUINESTI_STAFF(Slot.WEAPON, "Sanguinesti staff", ItemID.SANGUINESTI_STAFF, 150_000_000, 50, "1 hit in 6 deals +50%",
		ItemID.SANGUINESTI_STAFF_UNCHARGED),
	SOULREAPER_AXE(Slot.WEAPON, "Soulreaper axe", ItemID.SOULREAPER, 200_000_000, 10, "+1% per hit on a boss, up to +10%",
		ItemID.SOULREAPER_AXE_LURE, ItemID.SOULREAPER_AXE_STAFF, ItemID.SOULREAPER_AXE_HEAD, ItemID.SOULREAPER_AXE_EYE),
	SCYTHE_OF_VITUR(Slot.WEAPON, "Scythe of Vitur", ItemID.SCYTHE_OF_VITUR, 400_000_000, 10, "10% of drops hit three times",
		ItemID.SCYTHE_OF_VITUR_UNCHARGED),
	TUMEKENS_SHADOW(Slot.WEAPON, "Tumeken's shadow", ItemID.TUMEKENS_SHADOW, 400_000_000, 2, "Doubles your amulet's bonus",
		ItemID.TUMEKENS_SHADOW_UNCHARGED),
	TWISTED_BOW(Slot.WEAPON, "Twisted bow", ItemID.TWISTED_BOW, 600_000_000, 12, "Up to +12% against tougher bosses",
		ItemID.TWISTED_BOW);

	enum Slot
	{
		AMULET,
		GLOVES,
		WEAPON
	}

	private final Slot slot;
	private final String displayName;
	private final int itemId;
	private final long price;
	// The effect's size: a percentage, or for the claws the 1 in N. See Loadout.
	private final int value;
	private final String effect;
	// Collection log items needed before it can be bought.
	private final int[] requires;

	Gear(Slot slot, String displayName, int itemId, long price, int value, String effect, int... requires)
	{
		this.slot = slot;
		this.displayName = displayName;
		this.itemId = itemId;
		this.price = price;
		this.value = value;
		this.effect = effect;
		this.requires = requires;
	}

	// The amulets or gloves in order, or every weapon from cheapest to dearest.
	static List<Gear> inSlot(Slot slot)
	{
		List<Gear> gear = new ArrayList<>();
		for (Gear g : values())
		{
			if (g.slot == slot)
			{
				gear.add(g);
			}
		}
		return gear;
	}

	// The tier below this one in its ladder, or null for the first tier or a weapon.
	Gear previousTier()
	{
		if (slot == Slot.WEAPON)
		{
			return null;
		}
		List<Gear> ladder = inSlot(slot);
		int index = ladder.indexOf(this);
		return index > 0 ? ladder.get(index - 1) : null;
	}

	static Gear byName(String name)
	{
		for (Gear g : values())
		{
			if (g.name().equals(name))
			{
				return g;
			}
		}
		return null;
	}
}
