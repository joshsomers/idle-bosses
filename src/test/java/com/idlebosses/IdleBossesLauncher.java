package com.idlebosses;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class IdleBossesLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(IdleBossesPlugin.class);
		RuneLite.main(args);
	}
}
