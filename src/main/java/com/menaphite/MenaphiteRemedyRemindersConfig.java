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

	@ConfigSection(name = "Menaphite remedy reminders", description = "Drink reminders for timed boosts", position = 0)
	String MENAPHITE = "menaphite";

	@ConfigSection(name = "Preserve reminders", description = "Preserve prayer reminders", position = 1)
	String PRESERVE = "preserve";

	@ConfigItem(keyName = "menaphiteEnabled", name = "Enable Menaphite reminders", description = "Enable all Menaphite drink reminders and their Preserve preemption targets", section = MENAPHITE, position = 0)
	default boolean menaphiteEnabled() { return true; }

	@ConfigItem(keyName = "preserveEnabled", name = "Enable Preserve reminders", description = "Enable all Preserve prayer reminders", section = PRESERVE, position = 0)
	default boolean preserveEnabled() { return true; }
	@Range(min = 1, max = 300)
	@ConfigItem(keyName = "remindSeconds", name = "Remind before expiry",
		description = "Prompt to sip immediately this many seconds before expiry. Preserve planning targets this exact prompt time.", section = MENAPHITE, position = 7)
	default int remindSeconds()
	{
		return 10;
	}

	@ConfigItem(keyName = "sendNotification", name = "Send notification",
		description = "Send a RuneLite notification", section = MENAPHITE, position = 1)
	default boolean sendNotification() { return false; }

	@ConfigItem(keyName = "showOverhead", name = "Send overhead",
		description = "Show a reminder above your head for five seconds", section = MENAPHITE, position = 3)
	default boolean showOverhead() { return true; }

	@ConfigItem(keyName = "showInfobox", name = "Show infobox",
		description = "Show a Menaphite remedy infobox until the boost expires", section = MENAPHITE, position = 2)
	default boolean showInfobox() { return true; }

	@ConfigItem(keyName = "overheadMessage", name = "Overhead message",
		description = "Text displayed above your head", section = MENAPHITE, position = 5)
	default String overheadMessage() { return "Sip Menaphite remedy!"; }

	@ConfigItem(keyName = "overheadColour", name = "Overhead colour",
		description = "Colour of the overhead reminder", section = MENAPHITE, position = 6)
	default Color overheadColour() { return Color.BLUE; }


	@ConfigItem(keyName = "saturatedHeart", name = "Saturated heart",
		description = "Remind before saturated heart expires", section = MENAPHITE, position = 11)
	default boolean saturatedHeart() { return false; }

	@ConfigItem(keyName = "divineSuperCombat", name = "Divine super combat",
		description = "Remind before divine super combat expires", section = MENAPHITE, position = 12)
	default boolean divineSuperCombat() { return true; }

	@ConfigItem(keyName = "divineSuperAttack", name = "Divine super attack",
		description = "Remind before divine super attack expires", section = MENAPHITE, position = 13)
	default boolean divineSuperAttack() { return true; }

	@ConfigItem(keyName = "divineSuperStrength", name = "Divine super strength",
		description = "Remind before divine super strength expires", section = MENAPHITE, position = 14)
	default boolean divineSuperStrength() { return true; }

	@ConfigItem(keyName = "divineSuperDefence", name = "Divine super defence",
		description = "Remind before divine super defence expires", section = MENAPHITE, position = 15)
	default boolean divineSuperDefence() { return true; }

	@ConfigItem(keyName = "divineRanging", name = "Divine ranging",
		description = "Remind before divine ranging expires", section = MENAPHITE, position = 16)
	default boolean divineRanging() { return true; }

	@ConfigItem(keyName = "divineBastion", name = "Divine bastion",
		description = "Remind before divine bastion expires", section = MENAPHITE, position = 17)
	default boolean divineBastion() { return true; }

	@ConfigItem(keyName = "divineBattlemage", name = "Divine battlemage",
		description = "Remind before divine battlemage expires", section = MENAPHITE, position = 18)
	default boolean divineBattlemage() { return true; }

	@ConfigSection(name = "Misc", description = "Additional reminder conditions", position = 2)
	String MISC = "misc";

	@ConfigItem(keyName = "heartOnlyWhenBanked", name = "Remind for heart only when banked",
		description = "Only display Menaphite reminders for the saturated heart when the boost is close to expiring but the heart is no longer in the inventory (e.g. wilderness use cases)",
		section = MISC, position = 0)
	default boolean heartOnlyWhenBanked() { return true; }

	@ConfigItem(keyName = "promptPreserve", name = "Preempt menaphite'd boosts",
		description = "If Menaphite reminders are enabled and a remedy is in your inventory, predicts an optimal time to activate Preserve",
		section = PRESERVE, position = 9)
	default boolean promptPreserve() { return true; }
	@ConfigItem(keyName = "preserveNotification", name = "Send notification", description = "Send a RuneLite Preserve notification", section = PRESERVE, position = 1)
	default boolean preserveNotification() { return false; }

	@ConfigItem(keyName = "preserveInfobox", name = "Show infobox", description = "Show a Preserve activation infobox", section = PRESERVE, position = 2)
	default boolean preserveInfobox() { return true; }

	@ConfigItem(keyName = "preserveOverhead", name = "Send overhead", description = "Show the Preserve message above your head", section = PRESERVE, position = 3)
	default boolean preserveOverhead() { return true; }

	@ConfigItem(keyName = "preserveMessage", name = "Overhead message", description = "Text for the Preserve overhead reminder", section = PRESERVE, position = 4)
	default String preserveMessage() { return "Enable Preserve!"; }

	@ConfigItem(keyName = "preserveColour", name = "Overhead colour", description = "Colour of the Preserve overhead reminder", section = PRESERVE, position = 5)
	default Color preserveColour() { return Color.BLUE; }

	@ConfigItem(keyName = "preserveCombat", name = "Prompt to preserve combat stats", description = "Enables Preserve prayer prompts if the prayer is unlocked and a regularly-decaying combat boost is detected", section = PRESERVE, position = 7)
	default boolean preserveCombat() { return true; }

	@ConfigItem(keyName = "preserveNonCombat", name = "Prompt to preserve non-combat stats", description = "Enables Preserve prayer prompts if the prayer is unlocked and a regularly-decaying non-combat boost is detected", section = PRESERVE, position = 8)
	default boolean preserveNonCombat() { return false; }
}