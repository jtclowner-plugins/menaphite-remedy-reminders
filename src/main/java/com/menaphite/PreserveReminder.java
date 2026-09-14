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
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

final class PreserveReminder
{
	@Inject private Client client;
	@Inject private MenaphiteRemedyRemindersConfig config;
	@Inject private SpriteManager spriteManager;
	@Inject private InfoBoxManager infoBoxManager;

	private final CombatDecayCycle cycle = new CombatDecayCycle();
	private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
	private PreservePlan plan;
	private PreserveInfoBox box;
	private int lastRemedy;
	private boolean remedyConsumed;
	private boolean followingSip;

	void initialize()
	{
		for (Skill skill : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC})
		{
			levels.put(skill, client.getBoostedSkillLevel(skill));
		}
		lastRemedy = client.getVarbitValue(VarbitID.STATRENEWAL_POTION_TIMER);
	}

	void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		if (skill != Skill.ATTACK && skill != Skill.STRENGTH && skill != Skill.DEFENCE
			&& skill != Skill.RANGED && skill != Skill.MAGIC) { return; }
		int current = event.getBoostedLevel();
		Integer previous = levels.put(skill, current);
		if (previous != null && previous == current + 1 && current >= event.getLevel() && !divineProtected(skill))
		{
			int now = client.getTickCount();
			cycle.advance(now, client.isPrayerActive(Prayer.PRESERVE));
			cycle.observe(now);
		}
	}

	private boolean divineProtected(Skill skill)
	{
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
		if (id == VarbitID.STATRENEWAL_POTION_TIMER)
		{
			int value = client.getVarbitValue(id);
			remedyConsumed |= value > lastRemedy && value > 0;
			lastRemedy = value;
		}
	}

	void tick(Plugin plugin, int target)
	{
		int now = client.getTickCount();
		boolean active = client.isPrayerActive(Prayer.PRESERVE);
		cycle.advance(now, active);
		boolean sip = remedyConsumed;
		remedyConsumed = false;
		if (!config.promptPreserve() || !cycle.known() || client.getVarbitValue(VarbitID.PRAYER_PRESERVE_UNLOCKED) == 0
			|| client.getRealSkillLevel(Skill.PRAYER) < 55 || client.getBoostedSkillLevel(Skill.PRAYER) <= 0)
		{
			cancel();
			return;
		}
		if (sip && plan != null)
		{
			// Drinking ends alignment: only extend the current cycle, never force a decay afterwards.
			plan = PreservePlan.finish(cycle, now);
			followingSip = true;
		}
		if (followingSip)
		{
			if (plan == null || now >= plan.end) { cancel(); return; }
			if (!plan.matches(cycle, now)) { plan = PreservePlan.finish(cycle, now); }
		}
		else
		{
			if (target == Integer.MAX_VALUE) { cancel(); return; }
			if (plan != null && (Math.abs(plan.target - target) > 1 || !plan.matches(cycle, now)))
			{
				plan = null;
			}
			if (plan == null)
			{
				int delay = Math.max(0, target - now);
				// A loss of at most ten seconds from a full preserved interval needs no correction.
				if (PreservePlan.predictedAfterSip(cycle, delay, active) < 134)
				{
					plan = target > now ? PreservePlan.align(cycle, now, target) : PreservePlan.finish(cycle, now);
				}
			}
		}
		if (plan == null || now >= plan.end) { removeBox(); return; }
		if (box == null)
		{
			BufferedImage image = spriteManager.getSprite(SpriteID.Prayeron.PRESERVE, 0);
			if (image == null) { return; }
			box = new PreserveInfoBox(image, plugin);
			box.update(plan, now, active, followingSip);
			infoBoxManager.addInfoBox(box);
		}
		box.update(plan, now, active, followingSip);
	}

	void cancel()
	{
		plan = null;
		followingSip = false;
		removeBox();
	}

	private void removeBox()
	{
		if (box != null) { infoBoxManager.removeInfoBox(box); box = null; }
	}

	void reset()
	{
		cancel();
		cycle.reset();
		levels.clear();
		lastRemedy = 0;
		remedyConsumed = false;
	}
}
