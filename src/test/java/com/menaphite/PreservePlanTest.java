package com.menaphite;

import org.junit.Test;
import static org.junit.Assert.*;

public class PreservePlanTest
{
	@Test
	public void preserveWindowsAndLogoutSegments()
	{
		CombatDecayCycle cycle = new CombatDecayCycle();
		cycle.observe(0);
		cycle.advance(39, false);
		assertEquals(1, cycle.logoutSegment());
		cycle.restore(1, 1000);
		assertEquals(20, cycle.age);
		cycle.advance(1001, true);
		assertEquals(-1, cycle.logoutSegment());
		for (int stop : new int[]{100, 125})
		{
			cycle.reset();
			cycle.observe(0);
			for (int tick = 0; tick < stop + 25; tick++)
			{
				assertEquals(tick == stop + 24, cycle.step(tick >= 73 && tick < stop));
			}
		}
	}

	@Test
	public void singleActivationImprovesTimingAcrossTheWholeReactionWindow()
	{
		for (int age = 0; age < 100; age++)
		{
			CombatDecayCycle initial = new CombatDecayCycle();
			initial.observe(0);
			initial.advance(age, false);
			int target = age + 133; // Final 90 seconds, default 10-second reminder.
			PreservePlan plan = PreservePlan.align(initial, age, target);
			if (plan == null) { continue; }
			assertTrue(plan.enable >= age);
			assertEquals(17, plan.deadline - plan.enable);
			assertTrue(plan.remaining > PreservePlan.afterSip(initial, 133, Integer.MAX_VALUE));
			for (int enable = plan.enable; enable <= plan.deadline; enable++)
			{
				CombatDecayCycle simulation = initial.copy();
				int decay = -1;
				for (int tick = age; tick < target + 150; tick++)
				{
					if (simulation.step(tick >= enable) && tick + 1 >= target)
					{
						decay = tick + 1;
						break;
					}
				}
				assertTrue(decay - target >= plan.remaining);
			}
		}
	}

	@Test
	public void strengthAndHeartWaitOnlyUntilTheEarlySafeActivationWindow()
	{
		CombatDecayCycle cycle = new CombatDecayCycle();
		cycle.observe(0);
		cycle.advance(33, false); // 40.2 seconds to ordinary Strength decay.
		int target = 33 + 282 - ReminderTimers.reminderTicks(10); // Heart: about 2:49.
		assertEquals(2, PreservePlan.afterSip(cycle, target - 33, 0));
		PreservePlan plan = PreservePlan.align(cycle, 33, target, true);
		assertNotNull(plan);
		assertEquals(43, plan.enable - 33); // 25.8 seconds, well before the final 90 seconds.
		assertEquals(102, plan.remaining); // 61.2 seconds after sipping, including reaction time.
	}

	@Test
	public void selectsTheEarliestOptimalWindowAgainstIndependentBoundaryCalculation()
	{
		for (int age = 0; age < 100; age++)
		{
			for (int delay : new int[]{33, 100, 133, 145, 265, 483})
			{
				CombatDecayCycle cycle = new CombatDecayCycle();
				cycle.observe(0);
				cycle.advance(age, false);
				for (boolean regular : new boolean[]{false, true})
				{
					int best = referenceDecay(age, delay, Integer.MAX_VALUE) - (regular ? 1 : 0);
					int earliest = -1;
					for (int activation = 0; activation + 17 <= delay; activation++)
					{
						int worst = 150;
						for (int reaction = 0; reaction <= 17; reaction++)
						{
							worst = Math.min(worst, referenceDecay(age, delay, activation + reaction));
						}
						if (worst > best) { best = worst; earliest = activation; }
					}
					PreservePlan plan = PreservePlan.align(cycle, age, age + delay, regular);
					if (earliest < 0) { assertNull(plan); }
					else
					{
						assertNotNull(plan);
						assertEquals(age + earliest, plan.enable);
						assertEquals(best, plan.remaining);
						assertTrue(referenceDecay(age, delay, earliest) >= best); // Instant reaction and sip.
					}
				}
			}
		}
	}

	// With one activation left on, each full cycle is either 60 or 90 seconds.
	// Jump between boundaries instead of reusing the production tick simulator.
	private int referenceDecay(int age, int sip, int activation)
	{
		int start = -age;
		while (true)
		{
			int boundary = start + (activation <= start + 75 ? 150 : 100);
			if (boundary >= sip) { return boundary - sip; }
			start = boundary;
		}
	}

	@Test
	public void ignoresUnknownOrTooLateCycles()
	{
		CombatDecayCycle cycle = new CombatDecayCycle();
		assertNull(PreservePlan.align(cycle, 0, 133));
		cycle.observe(0);
		assertNull(PreservePlan.align(cycle, 0, 16));
		assertNull(PreservePlan.align(cycle, 0, 501));
	}
}
