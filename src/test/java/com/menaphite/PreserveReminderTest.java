package com.menaphite;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.SpriteManager;
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
	private final PreserveReminder reminder = new PreserveReminder();
	private final MenaphiteRemedyRemindersPlugin plugin = new MenaphiteRemedyRemindersPlugin();

	@Before
	public void setup() throws Exception
	{
		SpriteManager sprites = mock(SpriteManager.class);
		when(sprites.getSprite(anyInt(), anyInt())).thenReturn(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));
		for (Object[] entry : new Object[][]{{"client", client}, {"config", config},
			{"infoBoxManager", boxes}, {"spriteManager", sprites}})
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
	public void requiresKnownCombatCycleAndSkipsAlreadyGoodTiming()
	{
		reminder.tick(plugin, 483);
		reminder.onStatChanged(new StatChanged(Skill.WOODCUTTING, 0, 70, 80));
		reminder.onStatChanged(new StatChanged(Skill.WOODCUTTING, 0, 70, 79));
		reminder.tick(plugin, 483);
		verifyNoInteractions(boxes);
		learnCycle();
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(true);
		reminder.tick(plugin, 10); // 84 seconds remain after the planned sip.
		verifyNoInteractions(boxes);
		when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(false);
		reminder.tick(plugin, 483);
		verify(boxes).addInfoBox(any(PreserveInfoBox.class));
		doReturn(false).when(config).promptPreserve();
		reminder.tick(plugin, 483);
		verify(boxes).removeInfoBox(any(PreserveInfoBox.class));
		reminder.reset();
		doReturn(true).when(config).promptPreserve();
		clearInvocations(boxes);
		reminder.tick(plugin, 483);
		verifyNoInteractions(boxes);
	}

	@Test
	public void holdsPrayerAfterLastDoseEvenWhenNoDivineTargetRemains()
	{
		learnCycle();
		CombatDecayCycle cycle = new CombatDecayCycle();
		cycle.observe(0);
		PreservePlan schedule = PreservePlan.align(cycle, 0, 483);
		assertNotNull(schedule);
		reminder.tick(plugin, 483);
		ArgumentCaptor<PreserveInfoBox> capture = ArgumentCaptor.forClass(PreserveInfoBox.class);
		verify(boxes).addInfoBox(capture.capture());
		PreserveInfoBox box = capture.getValue();
		for (int tick = 1; tick <= 483; tick++)
		{
			when(client.getTickCount()).thenReturn(tick);
			when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(schedule.on(tick - 1));
			if (tick == 483)
			{
				when(client.getVarbitValue(VarbitID.STATRENEWAL_POTION_TIMER)).thenReturn(20);
				reminder.onVarbitChanged(VarbitID.STATRENEWAL_POTION_TIMER);
			}
			reminder.tick(plugin, tick == 483 ? Integer.MAX_VALUE : 483);
		}
		assertTrue(box.getTooltip().contains("Estimated next decay: 86s"));
		verify(boxes, never()).removeInfoBox(any());
		for (int tick = 484; tick <= schedule.end; tick++)
		{
			when(client.getTickCount()).thenReturn(tick);
			when(client.isPrayerActive(Prayer.PRESERVE)).thenReturn(schedule.on(tick - 1));
			reminder.tick(plugin, Integer.MAX_VALUE);
		}
		verify(boxes).removeInfoBox(box);
	}

	private void learnCycle()
	{
		reminder.onStatChanged(new StatChanged(Skill.STRENGTH, 0, 70, 80));
		reminder.onStatChanged(new StatChanged(Skill.STRENGTH, 0, 70, 79));
	}
}
