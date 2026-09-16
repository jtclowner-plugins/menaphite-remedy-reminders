package com.menaphite;

import java.awt.Color;
import java.awt.Graphics2D;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
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
		assertEquals(new Color(80, 180, 255), config.overheadColour());
		assertEquals(new Color(180, 100, 255), config.preserveColour());
		assertTrue(config.heartOnlyWhenBanked());
		assertTrue(config.promptPreserve());
		assertTrue(config.menaphiteEnabled());
		assertTrue(config.preserveEnabled());
		assertFalse(config.sendNotification());
		assertFalse(config.preserveNotification());
		assertTrue(config.preserveCombat());
		assertFalse(config.preserveNonCombat());
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
		when(player.getCanvasTextLocation(eq(graphics), eq("Enable Preserve!"), anyInt())).thenReturn(new Point(10, 20));
		overhead.showPreserve();
		overhead.clear(); // Clearing sip output must not clear Preserve output.
		overhead.render(graphics);
		verify(graphics).drawString("Enable Preserve!", 10, 20);
		clearInvocations(graphics);
		overhead.clearPreserve();
		overhead.show();
		overhead.render(graphics);
		verify(graphics).drawString("Custom reminder", 10, 20);
	}

	@Test
	public void remindersRequireADrinkableRemedyAndAcceptEveryDose() throws Exception
	{
		Client client = mock(Client.class);
		Notifier notifier = mock(Notifier.class);
		ReminderOverhead overhead = mock(ReminderOverhead.class);
		ItemContainer inventory = mock(ItemContainer.class);
		MenaphiteRemedyRemindersConfig config = new MenaphiteRemedyRemindersConfig()
		{
			public boolean showInfobox() { return false; }
			public boolean sendNotification() { return true; }
		};
		MenaphiteRemedyRemindersPlugin plugin = createPlugin(client, notifier, config, overhead);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarbitValue(Effect.DIVINE_RANGING.varbit)).thenReturn(16);
		for (int dose : new int[]{ItemID._1DOSESTATRENEWAL, ItemID._2DOSESTATRENEWAL,
			ItemID._3DOSESTATRENEWAL, ItemID._4DOSESTATRENEWAL})
		{
			when(client.getItemContainer(InventoryID.INV)).thenReturn(null);
			plugin.startUp();
			plugin.onGameTick(new GameTick());
			when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
			plugin.onGameTick(new GameTick()); // Empty inventory.
			when(inventory.contains(ItemID.Cert._4DOSESTATRENEWAL)).thenReturn(true);
			plugin.onGameTick(new GameTick()); // Noted remedy is not drinkable.
			verifyNoInteractions(notifier);
			when(inventory.contains(dose)).thenReturn(true);
			plugin.onGameTick(new GameTick());
			verify(notifier).notify("Sip Menaphite remedy! Divine ranging expires in about 10s");
			verify(overhead).show();
			clearInvocations(overhead);
			when(inventory.contains(dose)).thenReturn(false);
			plugin.onGameTick(new GameTick());
			verify(overhead).clear();
			when(inventory.contains(dose)).thenReturn(true);
			plugin.onGameTick(new GameTick());
			verifyNoMoreInteractions(notifier);
			plugin.shutDown();
			reset(inventory, notifier, overhead);
		}
	}

	@Test
	public void heartInventoryConditionDefersReminderAndCanBeDisabled() throws Exception
	{
		Client client = mock(Client.class);
		Notifier notifier = mock(Notifier.class);
		ReminderOverhead overhead = mock(ReminderOverhead.class);
		ItemContainer inventory = mock(ItemContainer.class);
		MenaphiteRemedyRemindersConfig config = spy(new MenaphiteRemedyRemindersConfig()
		{
			public boolean saturatedHeart() { return true; }
			public boolean sendNotification() { return true; }
			public boolean showInfobox() { return false; }
		});
		MenaphiteRemedyRemindersPlugin plugin = createPlugin(client, notifier, config, overhead);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarbitValue(Effect.SATURATED_HEART.varbit)).thenReturn(16);
		plugin.startUp();
		plugin.onGameTick(new GameTick()); // Inventory unavailable.
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		when(inventory.contains(ItemID._4DOSESTATRENEWAL)).thenReturn(true);
		when(inventory.contains(ItemID.SATURATED_HEART)).thenReturn(true);
		plugin.onGameTick(new GameTick());
		verifyNoInteractions(notifier);
		when(inventory.contains(ItemID.SATURATED_HEART)).thenReturn(false);
		when(inventory.contains(ItemID.Cert.SATURATED_HEART)).thenReturn(true);
		plugin.onGameTick(new GameTick());
		verifyNoInteractions(notifier);
		when(inventory.contains(ItemID.Cert.SATURATED_HEART)).thenReturn(false);
		plugin.onGameTick(new GameTick()); // Banked during the reminder window.
		verify(notifier, times(1)).notify(contains("Saturated heart"));
		verify(overhead).show();
		clearInvocations(overhead);
		when(inventory.contains(ItemID.SATURATED_HEART)).thenReturn(true);
		plugin.onGameTick(new GameTick());
		verify(overhead).clear();
		when(inventory.contains(ItemID.SATURATED_HEART)).thenReturn(false);
		plugin.onGameTick(new GameTick());
		verify(notifier, times(1)).notify(anyString());
		plugin.shutDown();
		doReturn(false).when(config).heartOnlyWhenBanked();
		when(inventory.contains(ItemID.SATURATED_HEART)).thenReturn(true);
		plugin.startUp();
		plugin.onGameTick(new GameTick());
		verify(notifier, times(2)).notify(contains("Saturated heart"));
		plugin.shutDown();
		clearInvocations(notifier);
		doReturn(false).when(config).menaphiteEnabled();
		plugin.startUp();
		plugin.onGameTick(new GameTick());
		verifyNoInteractions(notifier);
		plugin.shutDown();
	}

	@Test
	public void sipReminderNeverFiresBeforeItsConfiguredTarget()
	{
		for (int seconds : new int[]{3, 10, 30})
		{
			ReminderTimers timers = new ReminderTimers();
			timers.updateTimerFromVarbit(Effect.SATURATED_HEART.varbit, 500);
			int threshold = ReminderTimers.reminderTicks(seconds);
			for (int elapsed = 0; elapsed <= 500; elapsed++)
			{
				timers.tick();
				assertEquals(elapsed == 500 - threshold, timers.remind(Effect.SATURATED_HEART, threshold));
			}
		}
	}

	@Test
	public void preservePreemptionRequiresAnEligibleDrinkReminder() throws Exception
	{
		Client client = mock(Client.class);
		MenaphiteRemedyRemindersPlugin plugin = createPlugin(client, mock(Notifier.class),
			new MenaphiteRemedyRemindersConfig() {}, mock(ReminderOverhead.class));
		Field field = plugin.getClass().getDeclaredField("preserveReminder");
		field.setAccessible(true);
		PreserveReminder preserve = (PreserveReminder) field.get(plugin);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarbitValue(Effect.SATURATED_HEART.varbit)).thenReturn(500);
		plugin.startUp();
		plugin.onGameTick(new GameTick()); // Heart alerts disabled, inventory unavailable.
		verify(preserve).tick(plugin, Integer.MAX_VALUE);
		clearInvocations(preserve);
		plugin.shutDown();
		when(client.getVarbitValue(Effect.SATURATED_HEART.varbit)).thenReturn(0);
		plugin.startUp();
		plugin.onGameTick(new GameTick());
		verify(preserve).tick(plugin, Integer.MAX_VALUE);
		plugin.shutDown();
	}

	private MenaphiteRemedyRemindersPlugin createPlugin(Client client, Notifier notifier,
		MenaphiteRemedyRemindersConfig config, ReminderOverhead overhead) throws Exception
	{
		MenaphiteRemedyRemindersPlugin plugin = new MenaphiteRemedyRemindersPlugin();
		Object[][] dependencies = {{"client", client}, {"notifier", notifier}, {"config", config},
			{"clientThread", mock(ClientThread.class)}, {"overhead", overhead},
			{"preserveReminder", mock(PreserveReminder.class)},
			{"overlayManager", mock(OverlayManager.class)}, {"infoBoxManager", mock(InfoBoxManager.class)}};
		for (Object[] dependency : dependencies)
		{
			Field field = plugin.getClass().getDeclaredField((String) dependency[0]);
			field.setAccessible(true);
			field.set(plugin, dependency[1]);
		}
		return plugin;
	}
}
