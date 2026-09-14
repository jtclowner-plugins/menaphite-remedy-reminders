package com.menaphite;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.Notifier;
import net.runelite.client.game.ItemManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PreserveReminderTest
{
	private final Client client = mock(Client.class);
	private final MenaphiteRemedyRemindersConfig config = spy(new MenaphiteRemedyRemindersConfig() {});
	private final InfoBoxManager boxes = mock(InfoBoxManager.class);
	private final ConfigManager settings = mock(ConfigManager.class);
	private final Notifier notifier = mock(Notifier.class);
	private final ReminderOverhead overhead = mock(ReminderOverhead.class);
	private final PreserveReminder reminder = new PreserveReminder();
	private final MenaphiteRemedyRemindersPlugin plugin = new MenaphiteRemedyRemindersPlugin();

	@Before
	public void setup() throws Exception
	{
		SpriteManager sprites = mock(SpriteManager.class);
		when(sprites.getSprite(anyInt(), anyInt())).thenReturn(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));
		for (Object[] entry : new Object[][]{{"client", client}, {"config", config},
			{"infoBoxManager", boxes}, {"spriteManager", sprites}, {"configManager", settings},
			{"notifier", notifier}, {"overhead", overhead}})
		{
			Field field = PreserveReminder.class.getDeclaredField((String) entry[0]);
			field.setAccessible(true);
			field.set(reminder, entry[1]);
		}
		when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);
		when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(99);
		when(client.getVarbitValue(VarbitID.PRAYER_PRESERVE_UNLOCKED)).thenReturn(1);
	}

	@Test
	public void waitsUntilFinalNinetySecondsThenShowsOneEnablePrompt()
	{
		reminder.tick(plugin, 133); // Unknown cycle.
		verifyNoInteractions(boxes);
		learnCycle();
		reminder.tick(plugin, 134); // Still more than 90 seconds to expiry.
		verifyNoInteractions(boxes);
		CombatDecayCycle cycle = new CombatDecayCycle();
		cycle.observe(0);
		PreservePlan plan = PreservePlan.align(cycle, 0, 133);
		assertNotNull(plan);
		ArgumentCaptor<PreserveInfoBox> capture = ArgumentCaptor.forClass(PreserveInfoBox.class);
		for (int tick = 0; tick <= plan.enable + 14; tick++)
		{
			when(client.getTickCount()).thenReturn(tick);
			reminder.tick(plugin, 133);
			if (tick < plan.enable) { verifyNoInteractions(boxes); }
		}
		verify(boxes).addInfoBox(capture.capture());
		verify(boxes, never()).removeInfoBox(any());
		assertEquals("", capture.getValue().getText()); // Two-line instruction is drawn onto the sprite.
		assertFalse(capture.getValue().getTooltip().contains("leave it on"));
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(true);
		for (int tick = plan.enable + 15; tick <= 150; tick++)
		{
			when(client.getTickCount()).thenReturn(tick);
			reminder.tick(plugin, 133);
		}
		verify(boxes).removeInfoBox(capture.getValue());
		verify(boxes, times(1)).addInfoBox(any()); // Never requests OFF or another activation.
	}

	@Test
	public void logoutEstimateIsRestoredAndObservedDecayCorrectsIt() throws Exception
	{
		reminder.initialize(false);
		learnCycle();
		when(client.getTickCount()).thenReturn(39);
		reminder.tick(plugin, Integer.MAX_VALUE);
		reminder.logout();
		verify(settings).setRSProfileConfiguration(MenaphiteRemedyRemindersConfig.GROUP, "combatDecaySegment", 1);
		when(settings.getRSProfileConfiguration(MenaphiteRemedyRemindersConfig.GROUP, "combatDecaySegment", int.class)).thenReturn(1);
		when(client.getTickCount()).thenReturn(1000);
		reminder.initialize(true);
		Field field = PreserveReminder.class.getDeclaredField("cycle");
		field.setAccessible(true);
		CombatDecayCycle cycle = (CombatDecayCycle) field.get(reminder);
		assertEquals(20, cycle.age);
		learnCycle();
		assertEquals(0, cycle.age);
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(true);
		reminder.logout();
		verify(settings, times(1)).setRSProfileConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void missingBoostOrDisabledSettingClearsThePrompt()
	{
		learnCycle();
		CombatDecayCycle cycle = new CombatDecayCycle();
		cycle.observe(0);
		PreservePlan plan = PreservePlan.align(cycle, 0, 133);
		assertNotNull(plan);
		reminder.tick(plugin, 133);
		when(client.getTickCount()).thenReturn(plan.enable);
		reminder.tick(plugin, 133);
		verify(boxes).addInfoBox(any());
		doReturn(false).when(config).promptPreserve();
		reminder.tick(plugin, 133);
		verify(boxes).removeInfoBox(any());
		clearInvocations(boxes);
		doReturn(true).when(config).promptPreserve();
		reminder.tick(plugin, Integer.MAX_VALUE);
		verifyNoInteractions(boxes);
	}
	@Test
	public void regularBoostsRespectSkillTogglesAndTimedProtection()
	{
		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(70);
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(80);
		doReturn(false).when(config).preserveEnabled();
		reminder.tick(plugin, Integer.MAX_VALUE);
		verifyNoInteractions(boxes, notifier);
		doReturn(true).when(config).preserveEnabled();
		when(client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME)).thenReturn(500);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verifyNoInteractions(boxes, notifier);
		when(client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME)).thenReturn(0);
		reminder.tick(plugin, Integer.MAX_VALUE);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(boxes).addInfoBox(any());
		verify(overhead).showPreserve();
		verifyNoInteractions(notifier); // Default off.
		doReturn(false).when(config).preserveCombat();
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(boxes).removeInfoBox(any());
		clearInvocations(boxes, overhead);
		when(client.getBoostedSkillLevel(Skill.WOODCUTTING)).thenReturn(80);
		when(client.getRealSkillLevel(Skill.WOODCUTTING)).thenReturn(70);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verifyNoInteractions(boxes);
		doReturn(true).when(config).preserveNonCombat();
		doReturn(false).when(config).preserveInfobox();
		doReturn(true).when(config).preserveNotification();
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(notifier).notify(contains("Enable Preserve"));
		verify(overhead).showPreserve();
		verifyNoInteractions(boxes);
	}

	@Test
	public void mixedBoostUsesTheEarlyPlanInsteadOfAnImmediateRegularPrompt()
	{
		learnCycle();
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(80);
		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(70);
		CombatDecayCycle cycle = new CombatDecayCycle();
		cycle.observe(0);
		PreservePlan plan = PreservePlan.align(cycle, 0, 483, true);
		assertNotNull(plan);
		assertTrue(plan.enable < 333); // Can act before the final 90 seconds.
		for (int tick = 0; tick <= plan.enable; tick++)
		{
			when(client.getTickCount()).thenReturn(tick);
			reminder.tick(plugin, 483);
			if (tick < plan.enable) { verifyNoInteractions(boxes); }
		}
		verify(boxes).addInfoBox(any());
	}

	@Test
	public void heartSipStillFiresAfterPreserveHasBeenEnabled() throws Exception
	{
		doReturn(true).when(config).saturatedHeart();
		doReturn(true).when(config).sendNotification();
		doReturn(true).when(config).preserveNotification();
		ItemManager items = mock(ItemManager.class);
		when(items.getImage(anyInt())).thenReturn(new net.runelite.client.util.AsyncBufferedImage(mock(ClientThread.class), 32, 32, BufferedImage.TYPE_INT_ARGB));
		for (Object[] entry : new Object[][]{{"client", client}, {"config", config},
			{"clientThread", mock(ClientThread.class)}, {"notifier", notifier}, {"itemManager", items},
			{"infoBoxManager", boxes}, {"overhead", overhead}, {"overlayManager", mock(OverlayManager.class)},
			{"preserveReminder", reminder}})
		{
			Field field = MenaphiteRemedyRemindersPlugin.class.getDeclaredField((String) entry[0]);
			field.setAccessible(true);
			field.set(plugin, entry[1]);
		}
		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.contains(ItemID._4DOSESTATRENEWAL)).thenReturn(true);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarbitValue(VarbitID.SATURATED_HEART_TIME)).thenReturn(150);
		learnCycle();
		CombatDecayCycle cycle = new CombatDecayCycle();
		cycle.observe(0);
		PreservePlan plan = PreservePlan.align(cycle, 0, 133);
		assertNotNull(plan);
		plugin.startUp();
		for (int tick = 0; tick <= 150; tick++)
		{
			when(client.getTickCount()).thenReturn(tick);
			when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(tick > plan.enable + 5);
			plugin.onGameTick(new GameTick());
		}
		verify(notifier).notify(contains("Enable Preserve"));
		verify(notifier).notify(contains("Sip Menaphite remedy! Saturated heart"));
		verify(overhead).showPreserve();
		verify(overhead).show();
		verify(boxes).addInfoBox(any(ReminderInfoBox.class));
		plugin.shutDown();
	}

	@Test
	public void saltsAndEveryOverloadFamilySuppressBothPreserveModes()
	{
		learnCycle();
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(80);
		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(70);
		for (int timer : new int[]{VarbitID.NZONE_OVERLOAD_POTION_EFFECTS, VarbitID.RAIDS_OVERLOAD_TIMER,
			VarbitID.DEADMAN_OVERLOAD_POTION_EFFECTS, VarbitID.TOA_MIDRAIDLOOT_STATS_TIMER})
		{
			when(client.getVarbitValue(timer)).thenReturn(10);
			reminder.tick(plugin, Integer.MAX_VALUE);
			reminder.tick(plugin, 133);
			verifyNoInteractions(boxes, notifier);
			verify(overhead, never()).showPreserve();
			when(client.getVarbitValue(timer)).thenReturn(0);
		}
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(boxes).addInfoBox(any());
		when(client.getVarbitValue(VarbitID.TOA_MIDRAIDLOOT_STATS_TIMER)).thenReturn(1);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(boxes).removeInfoBox(any());
	}

	private void learnCycle()
	{
		reminder.onStatChanged(new StatChanged(Skill.STRENGTH, 0, 70, 80));
		reminder.onStatChanged(new StatChanged(Skill.STRENGTH, 0, 70, 79));
	}
}
