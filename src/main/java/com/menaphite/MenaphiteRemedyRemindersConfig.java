package com.menaphite;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(MenaphiteRemedyRemindersConfig.GROUP)
public interface MenaphiteRemedyRemindersConfig extends Config
{
	String GROUP = "menaphiteRemedyReminders";

	@Range(min = 1, max = 300)
	@ConfigItem(keyName = "remindSeconds", name = "Remind before expiry",
		description = "Seconds before the boost expires to remind you", position = 0)
	default int remindSeconds()
	{
		return 10;
	}

	@ConfigItem(keyName = "sendNotification", name = "Send notification",
		description = "Send a RuneLite notification", position = 1)
	default boolean sendNotification() { return true; }

	@ConfigItem(keyName = "showOverhead", name = "Show overhead",
		description = "Show a reminder above your head for five seconds", position = 2)
	default boolean showOverhead() { return true; }

	@ConfigItem(keyName = "showInfobox", name = "Show infobox",
		description = "Show a Menaphite remedy infobox until the boost expires", position = 3)
	default boolean showInfobox() { return true; }

	@ConfigItem(keyName = "overheadMessage", name = "Overhead message",
		description = "Text displayed above your head", position = 4)
	default String overheadMessage() { return "Sip Menaphite remedy!"; }

	@ConfigItem(keyName = "overheadColour", name = "Overhead colour",
		description = "Colour of the overhead reminder", position = 5)
	default Color overheadColour() { return Color.BLUE; }

	@ConfigSection(name = "Prompt for Menaphite remedy on:",
		description = "Choose which timed boosts to monitor", position = 6)
	String EFFECTS = "effects";

	@ConfigItem(keyName = "saturatedHeart", name = "Saturated heart",
		description = "Remind before saturated heart expires", section = EFFECTS, position = 0)
	default boolean saturatedHeart() { return false; }

	@ConfigItem(keyName = "divineSuperCombat", name = "Divine super combat",
		description = "Remind before divine super combat expires", section = EFFECTS, position = 1)
	default boolean divineSuperCombat() { return true; }

	@ConfigItem(keyName = "divineSuperAttack", name = "Divine super attack",
		description = "Remind before divine super attack expires", section = EFFECTS, position = 2)
	default boolean divineSuperAttack() { return true; }

	@ConfigItem(keyName = "divineSuperStrength", name = "Divine super strength",
		description = "Remind before divine super strength expires", section = EFFECTS, position = 3)
	default boolean divineSuperStrength() { return true; }

	@ConfigItem(keyName = "divineSuperDefence", name = "Divine super defence",
		description = "Remind before divine super defence expires", section = EFFECTS, position = 4)
	default boolean divineSuperDefence() { return true; }

	@ConfigItem(keyName = "divineRanging", name = "Divine ranging",
		description = "Remind before divine ranging expires", section = EFFECTS, position = 5)
	default boolean divineRanging() { return true; }

	@ConfigItem(keyName = "divineBastion", name = "Divine bastion",
		description = "Remind before divine bastion expires", section = EFFECTS, position = 6)
	default boolean divineBastion() { return true; }

	@ConfigItem(keyName = "divineBattlemage", name = "Divine battlemage",
		description = "Remind before divine battlemage expires", section = EFFECTS, position = 7)
	default boolean divineBattlemage() { return true; }

	@ConfigSection(name = "Misc", description = "Additional reminder conditions", position = 7)
	String MISC = "misc";

	@ConfigItem(keyName = "heartOnlyWhenBanked", name = "Remind for heart only when banked",
		description = "Only display Menaphite reminders for the saturated heart when the boost is close to expiring but the heart is no longer in the inventory (e.g. wilderness use cases)",
		section = MISC, position = 0)
	default boolean heartOnlyWhenBanked() { return true; }
}
