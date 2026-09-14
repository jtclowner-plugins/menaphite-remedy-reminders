package com.menaphite;

import java.awt.Color;
import java.awt.Graphics2D;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ReminderTest
{
	@Test
	public void defaults()
	{
		MenaphiteRemedyRemindersConfig config = new MenaphiteRemedyRemindersConfig() {};
		assertEquals(17, ReminderTimers.reminderTicks(config.remindSeconds()));
		assertEquals("Sip Menaphite remedy!", config.overheadMessage());
		assertEquals(Color.BLUE, config.overheadColour());
		for (Effect effect : Effect.values())
		{
			assertEquals(effect != Effect.SATURATED_HEART, effect.enabled(config));
		}
	}

	@Test
	public void timerSamplesInterpolateAndNotifyOnceUntilRefreshed()
	{
		ReminderTimers timers = new ReminderTimers();
		for (Effect effect : Effect.values())
		{
			timers.updateTimerFromVarbit(effect.varbit, 18);
			timers.tick();
			assertEquals(18, timers.remaining(effect));
			assertFalse(timers.remind(effect, 17));
			timers.tick();
			assertTrue(timers.remind(effect, 17));
			assertFalse(timers.remind(effect, 17));
			timers.updateTimerFromVarbit(effect.varbit, 500);
			assertFalse(timers.reminded(effect));
			timers.updateTimerFromVarbit(effect.varbit, 1);
			timers.tick();
			assertEquals(1, timers.remaining(effect));
			timers.tick();
			assertFalse(timers.remind(effect, 17));
		}
	}

	@Test
	public void coveredEffectsAreSuppressed()
	{
		ReminderTimers timers = new ReminderTimers();
		for (Effect effect : Effect.values()) { timers.updateTimerFromVarbit(effect.varbit, 10); }
		assertFalse(timers.applicable(Effect.DIVINE_SUPER_ATTACK));
		assertFalse(timers.applicable(Effect.DIVINE_SUPER_STRENGTH));
		assertFalse(timers.applicable(Effect.DIVINE_SUPER_DEFENCE));
		assertFalse(timers.applicable(Effect.DIVINE_RANGING));
		assertTrue(timers.applicable(Effect.DIVINE_SUPER_COMBAT));
		timers.updateTimerFromVarbit(VarbitID.STATRENEWAL_POTION_TIMER, 25);
		assertFalse(timers.applicable(Effect.SATURATED_HEART));
		timers.clear();
		timers.updateTimerFromVarbit(VarbitID.MOONLIGHT_POTION_TIME, 10);
		timers.updateTimerFromVarbit(Effect.DIVINE_SUPER_DEFENCE.varbit, 11);
		assertFalse(timers.applicable(Effect.DIVINE_SUPER_DEFENCE));
	}

	@Test
	public void overheadUsesConfiguredTextAndColourAndClears()
	{
		Client client = mock(Client.class);
		Player player = mock(Player.class);
		Graphics2D graphics = mock(Graphics2D.class);
		MenaphiteRemedyRemindersConfig config = new MenaphiteRemedyRemindersConfig()
		{
			public String overheadMessage() { return "Custom reminder"; }
			public Color overheadColour() { return Color.CYAN; }
		};
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getCanvasTextLocation(eq(graphics), eq("Custom reminder"), anyInt())).thenReturn(new Point(10, 20));
		ReminderOverhead overhead = new ReminderOverhead(client, config);
		overhead.show();
		overhead.render(graphics);
		verify(graphics).setColor(Color.CYAN);
		verify(graphics).drawString("Custom reminder", 10, 20);
		clearInvocations(graphics);
		overhead.clear();
		overhead.render(graphics);
		verifyNoInteractions(graphics);
	}

	@Test
	public void standardNotifierReceivesReminderText() throws Exception
	{
		MenaphiteRemedyRemindersPlugin plugin = new MenaphiteRemedyRemindersPlugin();
		Client client = mock(Client.class);
		Notifier notifier = mock(Notifier.class);
		MenaphiteRemedyRemindersConfig config = new MenaphiteRemedyRemindersConfig()
		{
			public boolean showInfobox() { return false; }
		};
		Object[][] dependencies = {{"client", client}, {"notifier", notifier}, {"config", config},
			{"clientThread", mock(ClientThread.class)}, {"overhead", new ReminderOverhead(client, config)},
			{"overlayManager", mock(OverlayManager.class)}, {"infoBoxManager", mock(InfoBoxManager.class)}};
		for (Object[] dependency : dependencies)
		{
			Field field = plugin.getClass().getDeclaredField((String) dependency[0]);
			field.setAccessible(true);
			field.set(plugin, dependency[1]);
		}
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarbitValue(Effect.DIVINE_RANGING.varbit)).thenReturn(16);
		plugin.startUp();
		plugin.onGameTick(new GameTick());
		plugin.onGameTick(new GameTick());
		verify(notifier).notify("Sip Menaphite remedy! Divine ranging expires in 10s");
		verifyNoMoreInteractions(notifier);
		plugin.shutDown();
	}
}
