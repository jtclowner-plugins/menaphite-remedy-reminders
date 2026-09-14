package com.menaphite;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

final class ReminderInfoBox extends InfoBox
{
	private final Effect effect;
	private int seconds;

	ReminderInfoBox(BufferedImage image, Plugin plugin, Effect effect)
	{
		super(image, plugin);
		this.effect = effect;
	}

	void update(int ticks)
	{
		seconds = ReminderTimers.secondsRemaining(ticks);
		setTooltip("Drink a Menaphite remedy<br>" + effect.displayName + ": " + seconds + "s remaining");
	}

	@Override
	public String getText() { return seconds + "s"; }

	@Override
	public Color getTextColor() { return Color.YELLOW; }
}
