package com.menaphite;

import java.util.function.Predicate;
import net.runelite.api.gameval.VarbitID;

enum Effect
{
	SATURATED_HEART("Saturated heart", VarbitID.SATURATED_HEART_TIME, MenaphiteRemedyRemindersConfig::saturatedHeart),
	DIVINE_SUPER_COMBAT("Divine super combat", VarbitID.DIVINECOMBAT_POTION_TIME, MenaphiteRemedyRemindersConfig::divineSuperCombat),
	DIVINE_SUPER_ATTACK("Divine super attack", VarbitID.DIVINEATTACK_POTION_TIME, MenaphiteRemedyRemindersConfig::divineSuperAttack),
	DIVINE_SUPER_STRENGTH("Divine super strength", VarbitID.DIVINESTRENGTH_POTION_TIME, MenaphiteRemedyRemindersConfig::divineSuperStrength),
	DIVINE_SUPER_DEFENCE("Divine super defence", VarbitID.DIVINEDEFENCE_POTION_TIME, MenaphiteRemedyRemindersConfig::divineSuperDefence),
	DIVINE_RANGING("Divine ranging", VarbitID.DIVINERANGE_POTION_TIME, MenaphiteRemedyRemindersConfig::divineRanging),
	DIVINE_BASTION("Divine bastion", VarbitID.DIVINEBASTION_POTION_TIME, MenaphiteRemedyRemindersConfig::divineBastion),
	DIVINE_BATTLEMAGE("Divine battlemage", VarbitID.DIVINEBATTLEMAGE_POTION_TIME, MenaphiteRemedyRemindersConfig::divineBattlemage);

	final String displayName;
	final int varbit;
	private final Predicate<MenaphiteRemedyRemindersConfig> enabled;

	Effect(String displayName, int varbit, Predicate<MenaphiteRemedyRemindersConfig> enabled)
	{
		this.displayName = displayName;
		this.varbit = varbit;
		this.enabled = enabled;
	}

	boolean enabled(MenaphiteRemedyRemindersConfig config)
	{
		return enabled.test(config);
	}

	static Effect forVarbit(int varbit)
	{
		for (Effect effect : values())
		{
			if (effect.varbit == varbit) { return effect; }
		}
		return null;
	}

	boolean covers(Effect effect)
	{
		if (this == DIVINE_SUPER_COMBAT)
		{
			return effect == DIVINE_SUPER_ATTACK || effect == DIVINE_SUPER_STRENGTH || effect == DIVINE_SUPER_DEFENCE;
		}
		if (this == DIVINE_BASTION)
		{
			return effect == DIVINE_RANGING || effect == DIVINE_SUPER_DEFENCE;
		}
		return this == DIVINE_BATTLEMAGE && effect == DIVINE_SUPER_DEFENCE;
	}
}
