package com.menaphite;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MenaphiteRemedyRemindersLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MenaphiteRemedyRemindersPlugin.class);
		RuneLite.main(args);
	}
}
