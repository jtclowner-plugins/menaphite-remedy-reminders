package com.menaphite;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

final class PreserveInfoBox extends InfoBox
{
	PreserveInfoBox(BufferedImage image, Plugin plugin)
	{
		super(image, plugin);
	}

	void update(PreservePlan plan)
	{
		if (plan == null)
		{
			setTooltip("Turn on Preserve to extend your boosted stats.");
			return;
		}
		setTooltip("Turn on Preserve."
			+ "<br>Estimated first decay after the Menaphite reminder: at least "
			+ (plan.remaining * 6 / 10) + "s if enabled within the prompt window.");
	}

	@Override public String getText() { return "TURN ON"; }
	@Override public Color getTextColor() { return Color.RED; }
}
