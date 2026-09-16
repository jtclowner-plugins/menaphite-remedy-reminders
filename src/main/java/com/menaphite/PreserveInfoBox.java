package com.menaphite;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;

final class PreserveInfoBox extends InfoBox
{
	PreserveInfoBox(BufferedImage image, Plugin plugin, boolean turnOff)
	{
		super(captionedImage(image, turnOff), plugin);
	}

	private static BufferedImage captionedImage(BufferedImage icon, boolean turnOff)
	{
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setComposite(AlphaComposite.SrcOver.derive(0.5f));
			graphics.drawImage(icon, (32 - icon.getWidth()) / 2, (32 - icon.getHeight()) / 2, null);
			graphics.setComposite(AlphaComposite.SrcOver);
			graphics.setFont(FontManager.getRunescapeSmallFont());
			int y = 15;
			for (String line : new String[]{"Turn", turnOff ? "off" : "on"})
			{
				int x = (32 - graphics.getFontMetrics().stringWidth(line)) / 2;
				graphics.setColor(Color.BLACK);
				graphics.drawString(line, x + 1, y + 1);
				graphics.setColor(Color.WHITE);
				graphics.drawString(line, x, y);
				y += 12;
			}
		}
		finally { graphics.dispose(); }
		return image;
	}

	void update(PreservePlan plan, int predictedTicks, boolean turnOff)
	{
		if (turnOff)
		{
			setTooltip("Turn off Preserve: no active boosts benefit from it.");
			return;
		}
		if (plan == null)
		{
			setTooltip("Turn on Preserve to extend your boosted stats.");
			return;
		}
		setTooltip("Turn on Preserve."
			+ "<br>Estimated first decay after the Menaphite reminder: "
			+ (predictedTicks * 6 / 10) + "s if enabled now.");
	}

	// RuneLite's caption renderer is single-line; the image carries both lines.
	@Override public String getText() { return ""; }
	@Override public Color getTextColor() { return Color.RED; }
}
