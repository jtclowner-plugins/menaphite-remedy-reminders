package com.menaphite;

/** An observed combat decay boundary, advanced using game ticks rather than wall time. */
final class CombatDecayCycle
{
	private int lastTick = -1;
	private boolean known;
	int age;
	int preserveTicks;

	void observe(int tick)
	{
		known = true;
		age = 0;
		lastTick = tick;
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
		if (!known) { return false; }
		age++;
		if (age == 150 || ((age == 100 || age == 125) && preserveTicks < 25))
		{
			age = 0;
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
		return copy;
	}

	void reset()
	{
		known = false;
		lastTick = -1;
		age = 0;
		preserveTicks = 0;
	}
}
