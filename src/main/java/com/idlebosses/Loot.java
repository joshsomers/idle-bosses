package com.idlebosses;

import lombok.Value;

@Value
class Loot
{
	// Pets and log items rarer than this get the fanfare.
	static final double RARE_CHANCE = 1.0 / 100;

	int itemId;
	String name;
	int quantity;
	// The chance of the roll that gave this drop. DropRates has the chance of the item on a whole kill.
	double chance;

	boolean isRare()
	{
		return chance <= RARE_CHANCE;
	}
}
