package com.idlebosses;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup(IdleBossesConfig.GROUP)
public interface IdleBossesConfig extends Config
{
	// The plugin's old name. Saved progress and settings live under it, so it stays.
	String GROUP = "skilling-bosses";

	@ConfigItem(keyName = "showHud", name = "Show HUD",
		description = "Show the boss, its health, kill count and collection log on screen",
		position = 0)
	default boolean showHud()
	{
		return true;
	}

	@ConfigItem(keyName = "showBossInHud", name = "Show boss in HUD",
		description = "Show the animated boss in the HUD. It's always shown in the sidebar panel",
		position = 1)
	default boolean showBossInHud()
	{
		return true;
	}

	@ConfigItem(keyName = "showMiniBoss", name = "Walking mini boss",
		description = "Also show a small version of the boss in the game world, walking after you",
		position = 2)
	default boolean showMiniBoss()
	{
		return false;
	}

	@ConfigItem(keyName = "miniBossSize", name = "Walking mini boss size",
		description = "How big the walking mini boss is",
		position = 3)
	default MiniBossSize miniBossSize()
	{
		return MiniBossSize.SMALL;
	}

	@ConfigItem(keyName = "chatMessages", name = "Chat messages",
		description = "Kill counts, new collection log items and unlocks in your chatbox. Only you see them",
		position = 5)
	default boolean chatMessages()
	{
		return true;
	}

	@ConfigItem(keyName = "rareDropSound", name = "Rare drop sound",
		description = "Play a sound when you get a pet or a collection log item rarer than 1 in 100",
		position = 6)
	default boolean rareDropSound()
	{
		return true;
	}

	@ConfigItem(keyName = "reloadCannonKey", name = "Reload cannon",
		description = "A key that reloads your cannon, as well as the button in the sidebar",
		position = 7)
	default Keybind reloadCannonKey()
	{
		return Keybind.NOT_SET;
	}
}
