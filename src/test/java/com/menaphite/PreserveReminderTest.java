package com.menaphite;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
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
	public void waitsUntilFinalNinetySecondsBeforeSipTargetThenShowsOneEnablePrompt()
	{
		reminder.tick(plugin, 133); // Unknown cycle.
		verifyNoInteractions(boxes);
		learnCycle();
		reminder.tick(plugin, 151); // Still more than 90 seconds to the sip-window start.
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
		verify(boxes, times(2)).addInfoBox(any()); // Prompts OFF once the missed Menaphite target passes.
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
		wirePlugin();
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
			if (tick < 150) { verify(overhead, never()).showPreserveOff(); }
		}
		verify(notifier).notify(contains("Enable Preserve"));
		verify(notifier).notify(contains("Sip Menaphite remedy! Saturated heart"));
		verify(overhead).showPreserve();
		verify(overhead).show();
		verify(boxes).addInfoBox(any(ReminderInfoBox.class));
		plugin.shutDown();
	}

	@Test
	public void manuallyEnabledPreserveSurvivesOverlappingSipWindowsThenTurnsOff() throws Exception
	{
		startSipWindow();
		when(client.getVarbitValue(VarbitID.DIVINERANGE_POTION_TIME)).thenReturn(22);
		for (int tick = 0; tick <= 22; tick++)
		{
			when(client.getTickCount()).thenReturn(tick);
			plugin.onGameTick(new GameTick());
			if (tick < 22) { verify(overhead, never()).showPreserveOff(); }
		}
		verify(boxes, times(2)).addInfoBox(any(ReminderInfoBox.class));
		verify(overhead).showPreserveOff(); // Both sips missed, both timed boosts expired.
	}

	@Test
	public void delayedSipHandsProtectionOverToTheDecayingBoost() throws Exception
	{
		startSipWindow();
		for (int tick = 0; tick <= 20; tick++)
		{
			when(client.getTickCount()).thenReturn(tick);
			if (tick == 5)
			{
				when(client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME)).thenReturn(0);
				when(client.getVarbitValue(VarbitID.STATRENEWAL_POTION_TIMER)).thenReturn(20);
				when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(70);
				when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(80);
				VarbitChanged sip = new VarbitChanged();
				sip.setVarbitId(VarbitID.STATRENEWAL_POTION_TIMER);
				plugin.onVarbitChanged(sip);
			}
			plugin.onGameTick(new GameTick());
			verify(overhead, never()).showPreserveOff();
		}
		verify(boxes).addInfoBox(any(ReminderInfoBox.class));
		verify(boxes).removeInfoBox(any(ReminderInfoBox.class));
	}

	@Test
	public void losingRemedyEndsSipWindowProtection() throws Exception
	{
		startSipWindow();
		plugin.onGameTick(new GameTick());
		verify(overhead, never()).showPreserveOff();
		when(client.getItemContainer(InventoryID.INV)).thenReturn(null);
		plugin.onGameTick(new GameTick());
		verify(overhead).showPreserveOff();
	}

	@Test
	public void existingRenewalWithoutBoostsDoesNotCreateASipWindow() throws Exception
	{
		startSipWindow();
		when(client.getVarbitValue(VarbitID.STATRENEWAL_POTION_TIMER)).thenReturn(20);
		plugin.onGameTick(new GameTick());
		verify(boxes, never()).addInfoBox(any(ReminderInfoBox.class));
		verify(overhead).showPreserveOff();
	}

	private void startSipWindow() throws Exception
	{
		wirePlugin();
		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.contains(ItemID._4DOSESTATRENEWAL)).thenReturn(true);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME)).thenReturn(17);
		plugin.startUp();
	}

	private void wirePlugin() throws Exception
	{
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
	}

	@Test
	public void everyOverloadFamilySuppressesBothPreserveModes()
	{
		learnCycle();
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(80);
		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(70);
		for (int timer : new int[]{VarbitID.NZONE_OVERLOAD_POTION_EFFECTS, VarbitID.RAIDS_OVERLOAD_TIMER,
			VarbitID.DEADMAN_OVERLOAD_POTION_EFFECTS})
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
		when(client.getVarbitValue(VarbitID.NZONE_OVERLOAD_POTION_EFFECTS)).thenReturn(1);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(boxes).removeInfoBox(any());
	}

	@Test
	public void spentSmellingSaltsUseAPlannedPreserveWindowButCarriedSaltsSuppressIt()
	{
		learnCycle();
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(80);
		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(70);
		when(client.getVarbitValue(VarbitID.TOA_MIDRAIDLOOT_STATS_TIMER)).thenReturn(10);
		when(client.getTickCount()).thenReturn(33);
		reminder.onVarbitChanged(VarbitID.TOA_MIDRAIDLOOT_STATS_TIMER);
		CombatDecayCycle cycle = new CombatDecayCycle();
		cycle.observe(0);
		cycle.advance(33, false);
		PreservePlan plan = PreservePlan.align(cycle, 33, 283, true);
		assertNotNull(plan);
		for (int tick = 33; tick <= plan.enable; tick++)
		{
			when(client.getTickCount()).thenReturn(tick);
			reminder.tick(plugin, Integer.MAX_VALUE);
			if (tick < plan.enable) { verifyNoInteractions(boxes); }
		}
		verify(boxes).addInfoBox(any());

		reminder.reset();
		clearInvocations(boxes, notifier, overhead);
		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.contains(ItemID.TOA_SUPPLY_STATS_1)).thenReturn(true);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verifyNoInteractions(boxes, notifier);
		verify(overhead, never()).showPreserve();
		verify(overhead, never()).showPreserveOff();
	}

	@Test
	public void carriedReboostingPotionsSuppressOnlyTurnOnPreserve()
	{
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(80);
		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(70);
		ItemContainer inventory = mock(ItemContainer.class);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		for (int item : new int[]{ItemID.NZONE1DOSEOVERLOADPOTION, ItemID.NZONE2DOSEOVERLOADPOTION,
			ItemID.NZONE3DOSEOVERLOADPOTION, ItemID.NZONE4DOSEOVERLOADPOTION,
			ItemID.RAIDS_VIAL_OVERLOAD_WEAK_1, ItemID.RAIDS_VIAL_OVERLOAD_WEAK_2,
			ItemID.RAIDS_VIAL_OVERLOAD_WEAK_3, ItemID.RAIDS_VIAL_OVERLOAD_WEAK_4,
			ItemID.RAIDS_VIAL_OVERLOAD_1, ItemID.RAIDS_VIAL_OVERLOAD_2,
			ItemID.RAIDS_VIAL_OVERLOAD_3, ItemID.RAIDS_VIAL_OVERLOAD_4,
			ItemID.RAIDS_VIAL_OVERLOAD_STRONG_1, ItemID.RAIDS_VIAL_OVERLOAD_STRONG_2,
			ItemID.RAIDS_VIAL_OVERLOAD_STRONG_3, ItemID.RAIDS_VIAL_OVERLOAD_STRONG_4,
			ItemID.DEADMAN1DOSEOVERLOAD, ItemID.DEADMAN2DOSEOVERLOAD,
			ItemID.DEADMAN3DOSEOVERLOAD, ItemID.DEADMAN4DOSEOVERLOAD,
			ItemID.TOA_SUPPLY_STATS_1, ItemID.TOA_SUPPLY_STATS_2})
		{
			when(inventory.contains(item)).thenReturn(true);
			reminder.tick(plugin, Integer.MAX_VALUE);
			verifyNoInteractions(boxes, notifier);
			verify(overhead, never()).showPreserve();
			when(inventory.contains(item)).thenReturn(false);
		}
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(boxes).addInfoBox(any());
	}

	@Test
	public void freshDivinesPromptToTurnOffAnOtherwiseWastedPreserve()
	{
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME)).thenReturn(400);
		reminder.tick(plugin, Integer.MAX_VALUE);
		ArgumentCaptor<PreserveInfoBox> capture = ArgumentCaptor.forClass(PreserveInfoBox.class);
		verify(boxes).addInfoBox(capture.capture());
		assertTrue(capture.getValue().getTooltip().contains("Turn off Preserve"));
		verify(overhead).showPreserveOff();

		clearInvocations(boxes, overhead);
		when(client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME)).thenReturn(399);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verifyNoInteractions(boxes, overhead); // Still no decaying boost; keep the OFF prompt.
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(false);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(boxes).removeInfoBox(any());
		verify(overhead).clearPreserve();
	}

	@Test
	public void activePreserveWithoutABenefitingBoostPromptsToTurnOff()
	{
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(true);
		reminder.tick(plugin, Integer.MAX_VALUE);
		ArgumentCaptor<PreserveInfoBox> capture = ArgumentCaptor.forClass(PreserveInfoBox.class);
		verify(boxes).addInfoBox(capture.capture());
		assertTrue(capture.getValue().getTooltip().contains("Turn off Preserve"));
	}

	@Test
	public void plannedMenaphiteSipKeepsPreserveOnUntilItsTarget()
	{
		learnCycle();
		CombatDecayCycle cycle = new CombatDecayCycle();
		cycle.observe(0);
		PreservePlan plan = PreservePlan.align(cycle, 0, 133);
		assertNotNull(plan);
		for (int tick = 0; tick <= plan.enable; tick++)
		{
			when(client.getTickCount()).thenReturn(tick);
			reminder.tick(plugin, 133);
		}
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(true);
		when(client.getTickCount()).thenReturn(plan.enable + 1);
		reminder.tick(plugin, 133);
		verify(boxes).removeInfoBox(any());
		verify(boxes, times(1)).addInfoBox(any());
	}

	@Test
	public void freshDivinesDoNotPromptToTurnOffWhileARegularBoostRemains()
	{
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME)).thenReturn(500);
		when(client.getRealSkillLevel(Skill.WOODCUTTING)).thenReturn(70);
		when(client.getBoostedSkillLevel(Skill.WOODCUTTING)).thenReturn(80);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verifyNoInteractions(boxes, notifier);
		verify(overhead, never()).showPreserveOff();
	}

	@Test
	public void heartProtectionUsesCooldownComparedWithRenewal()
	{
		when(client.getRealSkillLevel(Skill.MAGIC)).thenReturn(99);
		when(client.getBoostedSkillLevel(Skill.MAGIC)).thenReturn(112);
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(true);
		// saturated timer, cooldown units, renewal units, expected OFF (protected).
		int[][] cases = {{450, 45, 0, 1}, {450, 45, 20, 0}, {450, 45, 18, 0},
			{450, 45, 17, 1}, {500, 50, 18, 1}, {0, 50, 0, 0},
			{0, 0, 20, 0}, {10, 1, 1, 0}, {10, 1, 0, 1}};
		for (int[] state : cases)
		{
			reminder.reset();
			clearInvocations(boxes, overhead);
			when(client.getVarbitValue(VarbitID.SATURATED_HEART_TIME)).thenReturn(state[0]);
			when(client.getVarbitValue(VarbitID.IMBUED_HEART_TIMER)).thenReturn(state[1]);
			when(client.getVarbitValue(VarbitID.STATRENEWAL_POTION_TIMER)).thenReturn(state[2]);
			reminder.tick(plugin, Integer.MAX_VALUE);
			verify(overhead, times(state[3])).showPreserveOff();
			verify(boxes, times(state[3])).addInfoBox(any(PreserveInfoBox.class));
		}
	}

	@Test
	public void divineTimersRemainAuthoritativeDuringRenewal()
	{
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.STATRENEWAL_POTION_TIMER)).thenReturn(20);
		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(99);
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(118);
		when(client.getVarbitValue(VarbitID.DIVINESTRENGTH_POTION_TIME)).thenReturn(100);
		when(client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME)).thenReturn(200);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(overhead).showPreserveOff();
		when(client.getVarbitValue(VarbitID.DIVINESTRENGTH_POTION_TIME)).thenReturn(0);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(boxes, never()).removeInfoBox(any()); // Combination still protects Strength.
		when(client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME)).thenReturn(0);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(boxes).removeInfoBox(any()); // Boost survives, but protection is gone.
	}

	@Test
	public void lingeringHeartTimersWithoutBoostsStillPromptOffButOnlyWhenEnabled()
	{
		when(client.getRealSkillLevel(Skill.MAGIC)).thenReturn(99);
		when(client.getBoostedSkillLevel(Skill.MAGIC)).thenReturn(99);
		when(client.getVarbitValue(VarbitID.SATURATED_HEART_TIME)).thenReturn(450);
		when(client.getVarbitValue(VarbitID.IMBUED_HEART_TIMER)).thenReturn(45);
		when(client.getVarbitValue(VarbitID.STATRENEWAL_POTION_TIMER)).thenReturn(20);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(boxes, never()).addInfoBox(any());
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(true);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(overhead).showPreserveOff();
		// Another decaying boost is enough to keep Preserve, even with Magic at base.
		when(client.getRealSkillLevel(Skill.ATTACK)).thenReturn(99);
		when(client.getBoostedSkillLevel(Skill.ATTACK)).thenReturn(100);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(boxes).removeInfoBox(any());
	}

	@Test
	public void convertedHeartDecayCanTeachTheCycleButProtectedHeartCannot() throws Exception
	{
		when(client.getVarbitValue(VarbitID.SATURATED_HEART_TIME)).thenReturn(450);
		when(client.getVarbitValue(VarbitID.IMBUED_HEART_TIMER)).thenReturn(45);
		Field field = PreserveReminder.class.getDeclaredField("cycle");
		field.setAccessible(true);
		CombatDecayCycle cycle = (CombatDecayCycle) field.get(reminder);
		reminder.onStatChanged(new StatChanged(Skill.MAGIC, 0, 99, 112));
		reminder.onStatChanged(new StatChanged(Skill.MAGIC, 0, 99, 111));
		assertFalse(cycle.known());
		when(client.getVarbitValue(VarbitID.STATRENEWAL_POTION_TIMER)).thenReturn(20);
		reminder.onStatChanged(new StatChanged(Skill.MAGIC, 0, 99, 110));
		assertTrue(cycle.known());
	}

	@Test
	public void lateSipAfterBoostExpiryDoesNotCancelOffWarning() throws Exception
	{
		startSipWindow();
		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(99);
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(118);
		for (int tick = 0; tick <= 20; tick++)
		{
			when(client.getTickCount()).thenReturn(tick);
			if (tick == 17)
			{
				when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(99);
				updateVarbit(VarbitID.DIVINECOMBAT_POTION_TIME, 0);
			}
			if (tick == 19) { updateVarbit(VarbitID.STATRENEWAL_POTION_TIMER, 20); }
			plugin.onGameTick(new GameTick());
			if (tick < 17) { verify(overhead, never()).showPreserveOff(); }
			else { verify(overhead).showPreserveOff(); }
		}
		verify(boxes).addInfoBox(any(PreserveInfoBox.class));
		verify(boxes, never()).removeInfoBox(any(PreserveInfoBox.class));
	}

	private void updateVarbit(int id, int value)
	{
		when(client.getVarbitValue(id)).thenReturn(value);
		VarbitChanged event = new VarbitChanged();
		event.setVarbitId(id);
		plugin.onVarbitChanged(event);
	}

	@Test
	public void exhaustiveSipWindowsUseTheFullPlanningHorizon() throws Exception
	{
		assertEquals(10, config.remindSeconds());
		for (int seconds : new int[]{6, 8, 10})
		{
			int lead = ReminderTimers.reminderTicks(seconds);
			long[] results = sipWindowOutcomes(seconds, 150, true);
			assertEquals(100L * 18 * lead, results[3]);
			assertEquals(600 - lead, results[0]);
			assertEquals(624.5 - lead, (double) results[2] / results[3], 0.0);
			assertEquals(649 - lead, results[1]);
		}
		// Emulate the old gate by withholding planning until target - (150 - lead).
		long[] old = sipWindowOutcomes(10, 150 - ReminderTimers.reminderTicks(10), false);
		assertEquals(533, old[0]);
	}

	private long[] sipWindowOutcomes(int seconds, int horizon, boolean invariant) throws Exception
	{
		int expiry = 500;
		int lead = ReminderTimers.reminderTicks(seconds);
		int target = expiry - lead;
		long min = Long.MAX_VALUE, max = Long.MIN_VALUE, sum = 0, count = 0;
		Field field = PreserveReminder.class.getDeclaredField("cycle");
		field.setAccessible(true);
		doReturn(seconds).when(config).remindSeconds();
		int[] now = {0};
		int[] promptedAt = {-1};
		when(client.getTickCount()).thenAnswer(ignored -> now[0]);
		doAnswer(ignored -> { promptedAt[0] = now[0]; return null; }).when(overhead).showPreserve();
		for (int phase = 0; phase < 100; phase++)
		{
			// Keep mock invocation history bounded across this exhaustive simulation.
			clearInvocations(client, config, overhead, boxes, notifier, settings);
			reminder.reset();
			promptedAt[0] = -1;
			CombatDecayCycle tracked = (CombatDecayCycle) field.get(reminder);
			tracked.observe(0);
			tracked.age = phase; // Phase at the start of the five-minute protected boost.
			for (now[0] = target - horizon; now[0] < target && promptedAt[0] < 0; now[0]++)
			{
				reminder.tick(plugin, target);
			}
			assertTrue("No activation prompt for phase " + phase, promptedAt[0] >= 0);
			for (int reaction = 0; reaction <= 17; reaction++)
			{
				int activation = promptedAt[0] + reaction;
				int firstEndpoint = -1;
				for (int sip = target; sip < expiry; sip++)
				{
					CombatDecayCycle simulation = new CombatDecayCycle();
					simulation.observe(0);
					simulation.age = phase;
					int endpoint = -1;
					for (int tick = 1; tick <= expiry + 150; tick++)
					{
						// Activation at tick A affects intervals after A, matching PreservePlan.
						boolean decay = simulation.step(tick > activation);
						// Earlier decays still reset the cycle, but the boost protects the stat.
						if (decay && tick >= sip) { endpoint = tick; break; }
					}
					assertTrue("Missing decay", endpoint >= sip);
					if (firstEndpoint < 0) { firstEndpoint = endpoint; }
					if (invariant)
					{
						assertTrue("Decay inside the permitted sip window", endpoint >= expiry);
						assertEquals("Sip timing changed endpoint for phase " + phase
							+ ", reaction " + reaction + ", sip " + sip, firstEndpoint, endpoint);
					}
					min = Math.min(min, endpoint);
					max = Math.max(max, endpoint);
					sum += endpoint;
					count++;
				}
			}
		}
		System.out.println("Sip window " + seconds + "s; horizon=" + horizon + "; outcomes=" + count
			+ "; min/mean/max=" + min + "/" + (double) sum / count + "/" + max);
		return new long[]{min, max, sum, count};
	}

	private void learnCycle()
	{
		reminder.onStatChanged(new StatChanged(Skill.STRENGTH, 0, 70, 80));
		reminder.onStatChanged(new StatChanged(Skill.STRENGTH, 0, 70, 79));
	}

	@Test
	public void minimumOnlySuppressesOrdinaryOnAndClearsExistingPrompt()
	{
		assertEquals(10, config.minimumPreserveBoost());
		doReturn(true).when(config).preserveNotification();
		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(70);
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(80);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(overhead).showPreserve();
		verify(notifier).notify(contains("Enable Preserve"));
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(79);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(boxes).removeInfoBox(any());
		clearInvocations(boxes, notifier, overhead);
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(true);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verifyNoInteractions(boxes, notifier);
		verify(overhead, never()).showPreserveOff(); // +9 still benefits.
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(70);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(overhead).showPreserveOff();
	}

	@Test
	public void zeroMinimumStillRequiresAPositiveBoost()
	{
		doReturn(0).when(config).minimumPreserveBoost();
		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(70);
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(70);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verifyNoInteractions(boxes);
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(71);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(overhead).showPreserve();
	}

	@Test
	public void minimumUsesAnyEnabledUnprotectedSkillNotSumOfBoosts()
	{
		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(70);
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(79);
		when(client.getRealSkillLevel(Skill.WOODCUTTING)).thenReturn(70);
		when(client.getBoostedSkillLevel(Skill.WOODCUTTING)).thenReturn(80);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verifyNoInteractions(boxes); // Non-combat disabled.
		doReturn(true).when(config).preserveNonCombat();
		when(client.getBoostedSkillLevel(Skill.WOODCUTTING)).thenReturn(79);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verifyNoInteractions(boxes); // +9 and +9 don't add up.
		when(client.getBoostedSkillLevel(Skill.WOODCUTTING)).thenReturn(80);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(overhead).showPreserve();
	}

	@Test
	public void highMinimumDoesNotChangePlannedMenaphiteOrSaltsTiming()
	{
		doReturn(100).when(config).minimumPreserveBoost();
		mixedBoostUsesTheEarlyPlanInsteadOfAnImmediateRegularPrompt();
		reminder.reset();
		clearInvocations(boxes, notifier, overhead);
		when(client.getTickCount()).thenReturn(0);
		spentSmellingSaltsUseAPlannedPreserveWindowButCarriedSaltsSuppressIt();
	}

	@Test
	public void protectedHighBoostDoesNotQualifyAndSettingChangesApplyImmediately()
	{
		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(70);
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(89);
		when(client.getVarbitValue(VarbitID.DIVINESTRENGTH_POTION_TIME)).thenReturn(500);
		when(client.getRealSkillLevel(Skill.ATTACK)).thenReturn(70);
		when(client.getBoostedSkillLevel(Skill.ATTACK)).thenReturn(79);
		reminder.tick(plugin, Integer.MAX_VALUE);
		verifyNoInteractions(boxes);
		doReturn(9).when(config).minimumPreserveBoost();
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(overhead).showPreserve();
		doReturn(10).when(config).minimumPreserveBoost();
		reminder.tick(plugin, Integer.MAX_VALUE);
		verify(boxes).removeInfoBox(any());
	}
}
