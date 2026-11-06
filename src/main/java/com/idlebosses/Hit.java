package com.idlebosses;

import lombok.Value;

// One hitsplat's worth of damage, and what kind of hit it was, which picks the splat.
@Value
class Hit
{
	enum Type
	{
		NORMAL,
		CRITICAL,
		POISON,
		CANNON,
		MISS,
		DART
	}

	long damage;
	Type type;
}
