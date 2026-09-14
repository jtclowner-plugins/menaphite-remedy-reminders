package com.menaphite;

import com.google.inject.Provides;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Menaphite Remedy Reminders",
	description = "Reminds you to drink a Menaphite remedy before timed boosts expire",
	tags = {"menaphite", "remedy", "potion", "divine", "saturated", "heart"}
)
public class MenaphiteRemedyRemindersPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private MenaphiteRemedyRemindersConfig config;
	@Inject private Notifier notifier;
	@Inject private ItemManager itemManager;
	@Inject private InfoBoxManager infoBoxManager;
	@Inject private ReminderOverhead overhead;
	@Inject private OverlayManager overlayManager;
	@Inject private PreserveReminder preserveReminder;

	private final ReminderTimers timers = new ReminderTimers();
	private final Map<Effect, ReminderInfoBox> infoBoxes = new EnumMap<>(Effect.class);
	private volatile boolean running;
	private boolean needsSync;

	@Provides
	MenaphiteRemedyRemindersConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(MenaphiteRemedyRemindersConfig.class);
	}

	@Override
	protected void startUp()
	{
		running = true;
		overlayManager.add(overhead);
		needsSync = true;
		clientThread.invokeLater(() ->
		{
			if (running && client.getGameState() == GameState.LOGGED_IN)
			{
				preserveReminder.initialize();
				synchronizeTimers();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		running = false;
		preserveReminder.reset();
		overlayManager.remove(overhead);
		overhead.clear();
		clearInfoBoxes();
		timers.clear();
		needsSync = true;
	}

	private void synchronizeTimers()
	{
		for (Effect effect : Effect.values()) { updateTimer(effect.varbit); }
		updateTimer(VarbitID.STATRENEWAL_POTION_TIMER);
		updateTimer(VarbitID.MOONLIGHT_POTION_TIME);
		needsSync = false;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (!running || client.getGameState() != GameState.LOGGED_IN) { return; }
		int id = event.getVarbitId();
		preserveReminder.onVarbitChanged(id);
		if (Effect.forVarbit(id) != null || id == VarbitID.STATRENEWAL_POTION_TIMER
			|| id == VarbitID.MOONLIGHT_POTION_TIME)
		{
			updateTimer(id);
		}
	}

	private void updateTimer(int id)
	{
		int value = client.getVarbitValue(id);
		// CS2 buff_bar_get_value uses 25 game ticks per stat-renewal timer unit.
		int ticks = id == VarbitID.STATRENEWAL_POTION_TIMER ? value * 25 : value;
		if (timers.updateTimerFromVarbit(id, ticks))
		{
			Effect effect = Effect.forVarbit(id);
			if (effect != null) { removeInfoBox(effect); }
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!running || client.getGameState() != GameState.LOGGED_IN) { return; }
		if (needsSync) { synchronizeTimers(); }
		timers.tick();
		updateReminders(true);
		preserveReminder.tick(this, nextReminderTarget());
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (running && client.getGameState() == GameState.LOGGED_IN) { preserveReminder.onStatChanged(event); }
	}

	private int nextReminderTarget()
	{
		int target = Integer.MAX_VALUE;
		if (!hasRemedy()) { return target; }
		int threshold = ReminderTimers.reminderTicks(config.remindSeconds());
		for (Effect effect : Effect.values())
		{
			if (effect.enabled(config) && timers.applicable(effect)
				&& (effect != Effect.SATURATED_HEART || canRemindForHeart()))
			{
				target = Math.min(target, client.getTickCount() + timers.remaining(effect) - threshold);
			}
		}
		return target;
	}

	private boolean hasRemedy()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		return inventory != null && (inventory.contains(ItemID._1DOSESTATRENEWAL)
			|| inventory.contains(ItemID._2DOSESTATRENEWAL)
			|| inventory.contains(ItemID._3DOSESTATRENEWAL)
			|| inventory.contains(ItemID._4DOSESTATRENEWAL));
	}

	private void updateReminders(boolean mayNotify)
	{
		if (!hasRemedy())
		{
			clearOutputs();
			return;
		}
		int threshold = ReminderTimers.reminderTicks(config.remindSeconds());
		boolean anyVisibleReminder = false;
		for (Effect effect : Effect.values())
		{
			boolean applicable = effect.enabled(config) && timers.applicable(effect)
				&& (effect != Effect.SATURATED_HEART || canRemindForHeart());
			if (mayNotify && applicable && timers.remind(effect, threshold))
			{
				if (config.sendNotification())
				{
					notifier.notify("Sip Menaphite remedy! " + effect.displayName + " expires in "
						+ ReminderTimers.secondsRemaining(timers.remaining(effect)) + "s");
				}
				if (config.showOverhead()) { overhead.show(); }
			}
			boolean visible = applicable && timers.reminded(effect) && timers.remaining(effect) <= threshold;
			anyVisibleReminder |= visible;
			if (visible && config.showInfobox())
			{
				ReminderInfoBox box = infoBoxes.get(effect);
				if (box == null)
				{
					box = new ReminderInfoBox(itemManager.getImage(ItemID._4DOSESTATRENEWAL), this, effect);
					infoBoxes.put(effect, box);
					box.update(timers.remaining(effect));
					infoBoxManager.addInfoBox(box);
				}
				box.update(timers.remaining(effect));
			}
			else { removeInfoBox(effect); }
		}
		if (!config.showOverhead() || !anyVisibleReminder) { overhead.clear(); }
	}

	private boolean canRemindForHeart()
	{
		if (!config.heartOnlyWhenBanked()) { return true; }
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		// An unavailable inventory is not evidence that the heart has been banked.
		return inventory != null && !inventory.contains(ItemID.SATURATED_HEART)
			&& !inventory.contains(ItemID.Cert.SATURATED_HEART);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (MenaphiteRemedyRemindersConfig.GROUP.equals(event.getGroup()))
		{
			clientThread.invokeLater(() ->
			{
				if (running && client.getGameState() == GameState.LOGGED_IN)
				{
					updateReminders(false);
					preserveReminder.tick(this, nextReminderTarget());
				}
			});
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case LOGIN_SCREEN_AUTHENTICATOR:
				reset();
				break;
			case HOPPING:
			case CONNECTION_LOST:
				preserveReminder.reset();
				clearOutputs();
				needsSync = true;
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			reset();
			// Wait for new server timer updates; do not reseed from pre-death values.
			needsSync = false;
		}
	}

	private void removeInfoBox(Effect effect)
	{
		ReminderInfoBox box = infoBoxes.remove(effect);
		if (box != null) { infoBoxManager.removeInfoBox(box); }
	}

	private void clearOutputs()
	{
		clearInfoBoxes();
		overhead.clear();
	}

	private void clearInfoBoxes()
	{
		for (ReminderInfoBox box : infoBoxes.values()) { infoBoxManager.removeInfoBox(box); }
		infoBoxes.clear();
	}

	private void reset()
	{
		preserveReminder.reset();
		clearOutputs();
		timers.clear();
		needsSync = true;
	}
}
