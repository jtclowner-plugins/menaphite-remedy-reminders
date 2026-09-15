package com.menaphite;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

final class PreserveReminder
{
	@Inject private Client client;
	@Inject private MenaphiteRemedyRemindersConfig config;
	@Inject private SpriteManager spriteManager;
	@Inject private InfoBoxManager infoBoxManager;
	@Inject private ConfigManager configManager;
	@Inject private Notifier notifier;
	@Inject private ReminderOverhead overhead;
	private static final String SAVED_SEGMENT = "combatDecaySegment";
	private static final int FRESH_DIVINE_TICKS = 400;

	private final CombatDecayCycle cycle = new CombatDecayCycle();
	private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
	private PreservePlan plan;
	private PreserveInfoBox box;
	private boolean boxTurnsOff;
	private boolean tracking;
	private boolean restored;
	private boolean notified;
	private boolean overheadShown;


	void initialize(boolean login)
	{
		if (!tracking)
		{
			Integer segment = configManager.getRSProfileConfiguration(MenaphiteRemedyRemindersConfig.GROUP, SAVED_SEGMENT, int.class);
			configManager.unsetRSProfileConfiguration(MenaphiteRemedyRemindersConfig.GROUP, SAVED_SEGMENT);
			if (login && !cycle.known() && segment != null && !client.isPrayerActive(Prayer.PRESERVE))
			{
				cycle.restore(segment, client.getTickCount());
				restored = cycle.known();
			}
			tracking = true;
		}
		for (Skill skill : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC})
		{
			levels.put(skill, client.getBoostedSkillLevel(skill));
		}
	}

	void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		if (skill != Skill.ATTACK && skill != Skill.STRENGTH && skill != Skill.DEFENCE
			&& skill != Skill.RANGED && skill != Skill.MAGIC) { return; }
		int current = event.getBoostedLevel();
		Integer previous = levels.put(skill, current);
		if (previous != null && previous == current + 1 && current >= event.getLevel()
			&& !reboostingEffectActive() && !divineProtected(skill))
		{
			int now = client.getTickCount();
			cycle.advance(now, client.isPrayerActive(Prayer.PRESERVE));
			cycle.observe(now);
			restored = false;
		}
	}

	private boolean divineProtected(Skill skill)
	{
		if (client.getVarbitValue(VarbitID.STATRENEWAL_POTION_TIMER) > 0) { return false; }
		boolean melee = skill == Skill.ATTACK || skill == Skill.STRENGTH || skill == Skill.DEFENCE;
		if (melee && client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME) > 0) { return true; }
		if ((skill == Skill.DEFENCE || skill == Skill.RANGED)
			&& client.getVarbitValue(VarbitID.DIVINEBASTION_POTION_TIME) > 0) { return true; }
		if ((skill == Skill.DEFENCE || skill == Skill.MAGIC)
			&& client.getVarbitValue(VarbitID.DIVINEBATTLEMAGE_POTION_TIME) > 0) { return true; }
		int varbit;
		switch (skill)
		{
			case ATTACK: varbit = VarbitID.DIVINEATTACK_POTION_TIME; break;
			case STRENGTH: varbit = VarbitID.DIVINESTRENGTH_POTION_TIME; break;
			case DEFENCE: varbit = VarbitID.DIVINEDEFENCE_POTION_TIME; break;
			case RANGED: varbit = VarbitID.DIVINERANGE_POTION_TIME; break;
			case MAGIC:
				if (client.getVarbitValue(VarbitID.SATURATED_HEART_TIME) > 0) { return true; }
				varbit = VarbitID.DIVINEMAGIC_POTION_TIME;
				break;
			default: return false;
		}
		return client.getVarbitValue(varbit) > 0;
	}

	void onVarbitChanged(int id)
	{
		if (id == VarbitID.PRAYER_PRESERVE && !client.isPrayerActive(Prayer.PRESERVE))
		{
			cycle.preserveTicks = 0;
		}
	}

	void tick(Plugin plugin, int target)
	{
		int now = client.getTickCount();
		boolean active = client.isPrayerActive(Prayer.PRESERVE);
		cycle.advance(now, active);
		int lead = ReminderTimers.reminderTicks(config.remindSeconds());
		boolean regular = hasRegularBoost();
		boolean turnOff = active && !hasNonDivineBoost() && hasFreshDivineBoost();
		boolean preempt = config.promptPreserve() && target != Integer.MAX_VALUE && target > now;
		if (!config.preserveEnabled() || (!regular && !preempt && !turnOff) || reboostingEffectActive()
			|| client.getVarbitValue(VarbitID.PRAYER_PRESERVE_UNLOCKED) == 0
			|| client.getRealSkillLevel(Skill.PRAYER) < 55 || client.getBoostedSkillLevel(Skill.PRAYER) <= 0)
		{
			cancel();
			return;
		}
		if (turnOff)
		{
			plan = null;
			showPrompt(plugin, true);
			return;
		}
		if (active)
		{
			cancel();
			return;
		}
		if (preempt)
		{
			// A regular boost permits early activation, but never bypass the sip timing.
			if (!cycle.known() || (!regular && (long) target - now + lead > 150))
			{
				cancel();
				return;
			}
			if (plan != null && !plan.matches(cycle, now, target)) { plan = null; }
			if (plan == null) { plan = PreservePlan.align(cycle, now, target, regular); }
			if (plan == null || now < plan.enable)
			{
				clearOutputs();
				return;
			}
		}
		else { plan = null; }
		showPrompt(plugin, false);
	}

	private void showPrompt(Plugin plugin, boolean turnOff)
	{
		if (config.preserveNotification() && !notified)
		{
			notifier.notify(turnOff ? "Turn off Preserve: fresh divine boosts do not benefit from it."
				: "Enable Preserve and leave it on to extend your boosted stats.");
			notified = true;
		}
		if (config.preserveOverhead() && !overheadShown)
		{
			if (turnOff) { overhead.showPreserveOff(); }
			else { overhead.showPreserve(); }
			overheadShown = true;
		}
		if (!config.preserveOverhead()) { overhead.clearPreserve(); overheadShown = false; }
		if (!config.preserveInfobox()) { removeBox(); return; }
		boolean added = false;
		if (box != null && boxTurnsOff != turnOff) { removeBox(); }
		if (box == null)
		{
			BufferedImage image = spriteManager.getSprite(SpriteID.Prayeron.PRESERVE, 0);
			if (image == null) { return; }
			box = new PreserveInfoBox(image, plugin, turnOff);
			boxTurnsOff = turnOff;
			added = true;
		}
		box.update(plan, plan == null ? 0 : PreservePlan.afterSip(cycle, plan.target - now, 0), turnOff);
		if (restored) { box.setTooltip(box.getTooltip() + "<br>Cycle estimated from the saved logout segment."); }
		if (added) { infoBoxManager.addInfoBox(box); }
	}

	void cancel()
	{
		plan = null;
		clearOutputs();
	}

	private void clearOutputs()
	{
		notified = false;
		overheadShown = false;
		overhead.clearPreserve();
		removeBox();
	}

	private boolean hasRegularBoost()
	{
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL || skill == Skill.HITPOINTS || skill == Skill.PRAYER) { continue; }
			boolean combat = skill == Skill.ATTACK || skill == Skill.STRENGTH || skill == Skill.DEFENCE
				|| skill == Skill.RANGED || skill == Skill.MAGIC;
			if ((combat ? config.preserveCombat() : config.preserveNonCombat())
				&& client.getBoostedSkillLevel(skill) > client.getRealSkillLevel(skill)
				&& (!combat || !divineProtected(skill))) { return true; }
		}
		return false;
	}

	private boolean hasNonDivineBoost()
	{
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL || skill == Skill.HITPOINTS || skill == Skill.PRAYER) { continue; }
			if (client.getBoostedSkillLevel(skill) > client.getRealSkillLevel(skill) && !divineProtected(skill))
			{
				return true;
			}
		}
		return false;
	}

	private boolean hasFreshDivineBoost()
	{
		return client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME) >= FRESH_DIVINE_TICKS
			|| client.getVarbitValue(VarbitID.DIVINEBASTION_POTION_TIME) >= FRESH_DIVINE_TICKS
			|| client.getVarbitValue(VarbitID.DIVINEBATTLEMAGE_POTION_TIME) >= FRESH_DIVINE_TICKS
			|| client.getVarbitValue(VarbitID.DIVINEATTACK_POTION_TIME) >= FRESH_DIVINE_TICKS
			|| client.getVarbitValue(VarbitID.DIVINESTRENGTH_POTION_TIME) >= FRESH_DIVINE_TICKS
			|| client.getVarbitValue(VarbitID.DIVINEDEFENCE_POTION_TIME) >= FRESH_DIVINE_TICKS
			|| client.getVarbitValue(VarbitID.DIVINERANGE_POTION_TIME) >= FRESH_DIVINE_TICKS
			|| client.getVarbitValue(VarbitID.DIVINEMAGIC_POTION_TIME) >= FRESH_DIVINE_TICKS;
	}

	private boolean reboostingEffectActive()
	{
		return client.getVarbitValue(VarbitID.NZONE_OVERLOAD_POTION_EFFECTS) > 0
			|| client.getVarbitValue(VarbitID.RAIDS_OVERLOAD_TIMER) > 0
			|| client.getVarbitValue(VarbitID.DEADMAN_OVERLOAD_POTION_EFFECTS) > 0
			|| client.getVarbitValue(VarbitID.TOA_MIDRAIDLOOT_STATS_TIMER) > 0;
	}

	private void removeBox()
	{
		if (box != null) { infoBoxManager.removeInfoBox(box); box = null; boxTurnsOff = false; }
	}

	void reset()
	{
		if (tracking) { configManager.unsetRSProfileConfiguration(MenaphiteRemedyRemindersConfig.GROUP, SAVED_SEGMENT); }
		cancel();
		cycle.reset();
		levels.clear();
		tracking = false;
		restored = false;
	}

	void logout()
	{
		if (!tracking) { return; }
		int segment = client.isPrayerActive(Prayer.PRESERVE) ? -1 : cycle.logoutSegment();
		reset();
		if (segment >= 0)
		{
			// Normal cycles restart their current 12-second segment on login.
			// Preserve-affected cycles are deliberately not extrapolated across logout.
			configManager.setRSProfileConfiguration(MenaphiteRemedyRemindersConfig.GROUP, SAVED_SEGMENT, segment);
		}
	}
}
