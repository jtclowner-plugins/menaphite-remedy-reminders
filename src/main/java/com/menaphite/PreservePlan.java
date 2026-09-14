package com.menaphite;

import java.util.ArrayList;
import java.util.List;

/** Searches reachable 60/75/90-second cycles for a boundary just before the sip. */
final class PreservePlan
{
	private static final int[] DURATIONS = {100, 125, 150};
	private final List<int[]> windows = new ArrayList<>();
	final int target;
	final int decay;
	final int end;

	private static final class Route
	{
		final Route previous;
		final int start;
		final int duration;
		final int cost;

		Route(Route previous, int start, int duration)
		{
			this.previous = previous;
			this.start = start;
			this.duration = duration;
			cost = (previous == null ? 0 : previous.cost) + Math.max(0, duration - 100);
		}
	}

	private PreservePlan(Route route, int target)
	{
		this.target = target;
		decay = route.start + route.duration;
		end = route.start + route.duration - 25;
		for (Route part = route; part != null; part = part.previous)
		{
			if (part.duration > 100)
			{
				// Start two ticks before the required 45-second boundary for reaction time.
				windows.add(0, new int[]{part.start + 73, part.start + part.duration - 25});
			}
		}
	}

	static PreservePlan align(CombatDecayCycle cycle, int now, int target)
	{
		int horizon = target - now;
		if (!cycle.known() || horizon < 2 || horizon > 500) { return null; }
		Route[] routes = new Route[horizon];
		int start = now - cycle.age;
		for (int duration : DURATIONS)
		{
			int offset = duration - cycle.age;
			if (offset > 0 && offset < horizon && reachable(cycle, duration))
			{
				routes[offset] = new Route(null, start, duration);
			}
		}
		for (int offset = 1; offset < horizon; offset++)
		{
			if (routes[offset] == null) { continue; }
			for (int duration : DURATIONS)
			{
				int next = offset + duration;
				if (next >= horizon) { continue; }
				Route route = new Route(routes[offset], now + offset, duration);
				if (routes[next] == null || route.cost < routes[next].cost) { routes[next] = route; }
			}
		}
		// Leave at least two ticks between the boundary and sip, and at least 75s afterwards.
		for (int offset = horizon - 2; offset >= Math.max(1, horizon - 25); offset--)
		{
			if (routes[offset] != null)
			{
				return new PreservePlan(new Route(routes[offset], now + offset, 150), target);
			}
		}
		// Already in the desired final cycle: only the Preserve holding window is needed.
		int ageAtSip = cycle.age + horizon;
		if (ageAtSip <= 25 && reachable(cycle, 150))
		{
			return new PreservePlan(new Route(null, start, 150), target);
		}
		return null;
	}

	static PreservePlan finish(CombatDecayCycle cycle, int now)
	{
		if (!cycle.known()) { return null; }
		for (int duration : new int[]{150, 125})
		{
			if (cycle.age < duration - 25 && reachable(cycle, duration))
			{
				return new PreservePlan(new Route(null, now - cycle.age, duration), now);
			}
		}
		return null;
	}

	private static boolean reachable(CombatDecayCycle cycle, int duration)
	{
		CombatDecayCycle simulation = cycle.copy();
		int remaining = duration - cycle.age;
		for (int i = 0; i < remaining; i++)
		{
			boolean on = duration > 100 && simulation.age >= 73 && simulation.age < duration - 25;
			if (simulation.step(on)) { return i == remaining - 1; }
		}
		return false;
	}

	static int predictedAfterSip(CombatDecayCycle cycle, int delay, boolean preserve)
	{
		CombatDecayCycle simulation = cycle.copy();
		for (int i = 1; i <= delay + 150; i++)
		{
			if (simulation.step(preserve) && i > delay) { return i - delay; }
		}
		return 0;
	}

	boolean on(int tick)
	{
		for (int[] window : windows)
		{
			if (tick >= window[0] && tick < window[1]) { return true; }
		}
		return false;
	}

	int nextChange(int tick)
	{
		for (int[] window : windows)
		{
			if (tick < window[0]) { return window[0]; }
			if (tick < window[1]) { return window[1]; }
		}
		return end;
	}

	boolean matches(CombatDecayCycle cycle, int now)
	{
		CombatDecayCycle simulation = cycle.copy();
		for (int tick = now; tick < decay; tick++)
		{
			boolean boundary = simulation.step(on(tick));
			if (tick + 1 >= target && boundary) { return tick + 1 == decay; }
		}
		return false;
	}
}
