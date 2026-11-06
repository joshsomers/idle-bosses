package com.idlebosses;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;

// The game's hitsplat for each kind of hit, shared by the HUD, the sidebar and the walking boss.
// Loaded once in the background; until a splat has loaded, the normal red one stands in.
@Singleton
class SplatSprites
{
	private final Map<Hit.Type, BufferedImage> sprites = new ConcurrentHashMap<>();

	void load(SpriteManager spriteManager)
	{
		for (Hit.Type type : Hit.Type.values())
		{
			spriteManager.getSpriteAsync(spriteId(type), 0, sprite ->
			{
				if (sprite != null)
				{
					sprites.put(type, sprite);
				}
			});
		}
	}

	BufferedImage get(Hit.Type type)
	{
		BufferedImage sprite = sprites.get(type);
		return sprite != null ? sprite : sprites.get(Hit.Type.NORMAL);
	}

	private static int spriteId(Hit.Type type)
	{
		switch (type)
		{
			// The max hit splat.
			case CRITICAL:
				return SpriteID.Hitmark._24;
			case POISON:
				return SpriteID.Hitmark._2;
			case CANNON:
				return SpriteID.Hitmark._12;
			case MISS:
				return SpriteID.Hitmark._0;
			// The venom splat, for the toxic blowpipe.
			case DART:
				return SpriteID.Hitmark._11;
			default:
				return SpriteID.Hitmark._1;
		}
	}
}
