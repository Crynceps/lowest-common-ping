package com.lowestcommonping;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LowestCommonPingPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LowestCommonPingPlugin.class);
		RuneLite.main(args);
	}
}
