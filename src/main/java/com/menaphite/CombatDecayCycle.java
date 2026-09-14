package com.menaphite;

/** An observed combat decay boundary, advanced using game ticks rather than wall time. */
final class CombatDecayCycle
{
	private int lastTick = -1;
	private boolean known;
	int age;
	int preserveTicks;
	private boolean preserveInCycle;

	void observe(int tick)
	{
		known = true;
		age = 0;
		lastTick = tick;
		preserveInCycle = preserveTicks > 0;
	}

	int logoutSegment()
	{
		return known && !preserveInCycle && age < 100 ? age / 20 : -1;
	}

	void restore(int segment, int tick)
	{
		reset();
		if (segment < 0 || segment > 4) { return; }
		observe(tick);
		age = segment * 20;
	}

	void advance(int tick, boolean preserve)
	{
		if (lastTick >= 0)
		{
			for (int i = lastTick; i < tick; i++) { step(preserve); }
		}
		lastTick = tick;
	}

	boolean step(boolean preserve)
	{
		preserveTicks = preserve ? Math.min(25, preserveTicks + 1) : 0;
		preserveInCycle |= preserve;
		if (!known) { return false; }
		age++;
		if (age == 150 || ((age == 100 || age == 125) && preserveTicks < 25))
		{
			age = 0;
			preserveInCycle = preserve;
			return true;
		}
		return false;
	}

	boolean known() { return known; }

	CombatDecayCycle copy()
	{
		CombatDecayCycle copy = new CombatDecayCycle();
		copy.known = known;
		copy.age = age;
		copy.preserveTicks = preserveTicks;
		copy.preserveInCycle = preserveInCycle;
		return copy;
	}

	void reset()
	{
		known = false;
		lastTick = -1;
		age = 0;
		preserveTicks = 0;
		preserveInCycle = false;
	}
}
