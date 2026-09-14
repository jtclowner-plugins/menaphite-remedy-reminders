package com.menaphite;

import org.junit.Test;
import static org.junit.Assert.*;

public class PreservePlanTest
{
	@Test
	public void preserveRequiresContinuousWindowsAndAllowsPartialExtension()
	{
		assertEquals(100, decayWithPreserve(100, 100)); // Never enabled.
		assertEquals(125, decayWithPreserve(73, 100)); // First extension only.
		assertEquals(150, decayWithPreserve(73, 125)); // Both extensions earned.
		assertEquals(100, decayWithPreserve(76, 150)); // Too late for the first window.
		CombatDecayCycle cycle = new CombatDecayCycle();
		cycle.observe(0);
		for (int tick = 0; tick < 100; tick++)
		{
			boolean decayed = cycle.step(tick != 90);
			assertEquals(tick == 99, decayed); // One interrupted tick loses the first extension.
		}
	}

	@Test
	public void defaultReminderCanAlignToMoreThanEightySeconds()
	{
		CombatDecayCycle cycle = new CombatDecayCycle();
		cycle.observe(0);
		PreservePlan plan = PreservePlan.align(cycle, 0, 500 - ReminderTimers.reminderTicks(10));
		assertNotNull(plan);
		assertEquals(142, plan.decay - plan.target);
		assertTrue(plan.matches(cycle, 0));
		assertNull(PreservePlan.align(new CombatDecayCycle(), 0, 483));
		assertNull(PreservePlan.align(cycle, 0, 50)); // Cannot move a boundary into that window.
		cycle.advance(23, false);
		PreservePlan late = PreservePlan.finish(cycle, 23);
		assertNotNull(late);
		assertEquals(127, late.decay - 23);
		cycle.reset();
		assertFalse(cycle.known());
	}

	@Test
	public void everyProducedScheduleActuallyDeliversItsPredictedFirstDecay()
	{
		for (int age = 0; age < 150; age++)
		{
			CombatDecayCycle initial = new CombatDecayCycle();
			initial.observe(0);
			initial.advance(age, true);
			for (int delay = 2; delay <= 500; delay += 13)
			{
				PreservePlan plan = PreservePlan.align(initial, age, age + delay);
				if (plan == null) { continue; }
				assertTrue(plan.decay - plan.target >= 125);
				assertTrue(plan.decay - plan.target <= 150);
				CombatDecayCycle simulation = initial.copy();
				int firstDecay = -1;
				for (int tick = age; tick < plan.decay; tick++)
				{
					if (simulation.step(plan.on(tick)) && tick + 1 > plan.target)
					{
						firstDecay = tick + 1;
						break;
					}
				}
				assertEquals(plan.decay, firstDecay);
			}
		}
	}

	private int decayWithPreserve(int on, int off)
	{
		CombatDecayCycle cycle = new CombatDecayCycle();
		cycle.observe(0);
		for (int tick = 0; tick < 150; tick++)
		{
			if (cycle.step(tick >= on && tick < off)) { return tick + 1; }
		}
		throw new AssertionError("Missing decay");
	}
}
