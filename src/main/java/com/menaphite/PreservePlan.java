package com.menaphite;

/** Chooses one activation, followed by leaving Preserve on. */
final class PreservePlan
{
	private static final int REACTION_TICKS = 17;
	final int target;
	final int enable;
	final int deadline;
	final int remaining;

	private PreservePlan(int target, int enable, int remaining)
	{
		this.target = target;
		this.enable = enable;
		this.deadline = enable + REACTION_TICKS;
		this.remaining = remaining;
	}

	static PreservePlan align(CombatDecayCycle cycle, int now, int target)
	{
		return align(cycle, now, target, false);
	}

	static PreservePlan align(CombatDecayCycle cycle, int now, int target, boolean regularBoost)
	{
		int delay = target - now;
		if (!cycle.known() || delay < REACTION_TICKS || delay > 500) { return null; }
		int[] outcomes = new int[delay + 1];
		for (int enable = 0; enable <= delay; enable++)
		{
			outcomes[enable] = afterSip(cycle, delay, enable);
		}
		// Regular boosts also benefit from enabling now when sip timing is unchanged.
		int best = afterSip(cycle, delay, Integer.MAX_VALUE) - (regularBoost ? 1 : 0);
		PreservePlan plan = null;
		for (int enable = 0; enable + REACTION_TICKS <= delay; enable++)
		{
			// Score the whole reaction window, not an unrealistically instant click.
			int worst = 150;
			for (int reaction = 0; reaction <= REACTION_TICKS; reaction++)
			{
				worst = Math.min(worst, outcomes[enable + reaction]);
			}
			if (worst > best)
			{
				best = worst;
				plan = new PreservePlan(target, now + enable, best);
			}
		}
		return plan;
	}

	static int afterSip(CombatDecayCycle cycle, int delay, int enable)
	{
		CombatDecayCycle simulation = cycle.copy();
		for (int tick = 1; tick <= delay + 150; tick++)
		{
			// Count a decay on the sip tick conservatively as immediate stat loss.
			if (simulation.step(tick > enable) && tick >= delay) { return tick - delay; }
		}
		return 0;
	}

	boolean matches(CombatDecayCycle cycle, int now, int target)
	{
		if (this.target != target || now > deadline) { return false; }
		for (int activation = Math.max(now, enable); activation <= deadline; activation++)
		{
			if (afterSip(cycle, target - now, activation - now) < remaining) { return false; }
		}
		return true;
	}
}
