package com.menaphite;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

final class ReminderTimers
{
	private final Map<Integer, Snapshot> snapshots = new HashMap<>();
	private long tick;

	private static final class Snapshot
	{
		private int duration;
		private long receivedTick;
		private boolean reminded;

		private int remaining(long now)
		{
			return (int) Math.max(0, duration - Math.max(0, now - receivedTick));
		}
	}

	boolean updateTimerFromVarbit(int varbit, int ticks)
	{
		Snapshot snapshot = snapshots.computeIfAbsent(varbit, ignored -> new Snapshot());
		int duration = Math.max(0, ticks);
		boolean reset = duration == 0 || duration > snapshot.remaining(tick);
		if (reset) { snapshot.reminded = false; }
		snapshot.duration = duration;
		// Varbit events precede GameTick; this sample belongs to the upcoming tick.
		snapshot.receivedTick = tick + 1;
		return reset;
	}

	void tick() { tick++; }

	int remaining(int varbit)
	{
		Snapshot snapshot = snapshots.get(varbit);
		return snapshot == null ? 0 : snapshot.remaining(tick);
	}

	int remaining(Effect effect) { return remaining(effect.varbit); }

	boolean reminded(Effect effect)
	{
		Snapshot snapshot = snapshots.get(effect.varbit);
		return snapshot != null && snapshot.reminded;
	}

	boolean remind(Effect effect, int threshold)
	{
		int left = remaining(effect);
		if (left == 0 || left > threshold || reminded(effect)) { return false; }
		snapshots.get(effect.varbit).reminded = true;
		return true;
	}

	boolean applicable(Effect effect)
	{
		int left = remaining(effect);
		int covered = remaining(VarbitID.STATRENEWAL_POTION_TIMER);
		for (Effect combination : Effect.values())
		{
			if (combination.covers(effect)) { covered = Math.max(covered, remaining(combination)); }
		}
		if (effect == Effect.DIVINE_SUPER_DEFENCE)
		{
			int moonlight = remaining(VarbitID.MOONLIGHT_POTION_TIME);
			if (moonlight > 0) { covered = Math.max(covered, moonlight + 1); }
		}
		return left > covered;
	}

	void clear()
	{
		snapshots.clear();
		tick = 0;
	}

	static int reminderTicks(int seconds) { return (int) Math.ceil(Math.max(1, Math.min(300, seconds)) / 0.6); }
	static int secondsRemaining(int ticks) { return (int) Math.ceil(ticks * 0.6); }
}
