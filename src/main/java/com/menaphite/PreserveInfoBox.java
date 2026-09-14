package com.menaphite;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

final class PreserveInfoBox extends InfoBox
{
	private String text;
	private Color color;

	PreserveInfoBox(BufferedImage image, Plugin plugin)
	{
		super(image, plugin);
	}

	void update(PreservePlan plan, int now, boolean active, boolean consumed)
	{
		boolean shouldBeOn = plan.on(now);
		int seconds = ReminderTimers.secondsRemaining(Math.max(0, plan.nextChange(now) - now));
		text = shouldBeOn != active ? (shouldBeOn ? "ON" : "OFF") : seconds + "s";
		color = shouldBeOn != active ? Color.RED : shouldBeOn ? Color.GREEN : Color.CYAN;
		String instruction = shouldBeOn ? (active ? "Keep Preserve on" : "Enable Preserve now")
			: (active ? "Disable Preserve now" : "Leave Preserve off");
		boolean fromNow = consumed || now >= plan.target;
		setTooltip(instruction + "<br>" + (shouldBeOn ? "Keep on for " : "Enable in ") + seconds + "s"
			+ "<br>" + (fromNow ? "Estimated next decay: " : "Estimated first decay after reminder: ")
			+ ReminderTimers.secondsRemaining(Math.max(0, plan.decay - (fromNow ? now : plan.target))) + "s");
	}

	@Override public String getText() { return text; }
	@Override public Color getTextColor() { return color; }
}
