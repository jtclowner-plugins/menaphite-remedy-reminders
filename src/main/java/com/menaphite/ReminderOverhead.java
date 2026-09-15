package com.menaphite;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.Font;
import net.runelite.client.ui.FontManager;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

@Singleton
final class ReminderOverhead extends Overlay
{
	private final Client client;
	private final MenaphiteRemedyRemindersConfig config;
	private volatile long visibleUntil;
	private volatile long preserveUntil;
	private volatile String preserveText;

	@Inject
	ReminderOverhead(Client client, MenaphiteRemedyRemindersConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	void show() { visibleUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); }
	void clear() { visibleUntil = 0; }
	void showPreserve() { showPreserve(config.preserveMessage()); }
	void showPreserveOff() { showPreserve("Turn off Preserve!"); }
	private void showPreserve(String text)
	{
		preserveText = text;
		preserveUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
	}
	void clearPreserve() { preserveUntil = 0; preserveText = null; }

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		Player player = client.getLocalPlayer();
		if (player == null) { return null; }
		boolean menaphite = System.nanoTime() < visibleUntil && config.menaphiteEnabled() && config.showOverhead();
		boolean preserve = System.nanoTime() < preserveUntil && config.preserveEnabled() && config.preserveOverhead();
		if (!menaphite && !preserve) { return null; }
		Font originalFont = graphics.getFont();
		graphics.setFont(FontManager.getRunescapeBoldFont());
		try
		{
			if (menaphite) { draw(graphics, player, config.overheadMessage(), config.overheadColour(), 40); }
			if (preserve)
			{
				draw(graphics, player, preserveText, config.preserveColour(), menaphite ? 65 : 40);
			}
		}
		finally { graphics.setFont(originalFont); }
		return null;
	}

	private void draw(Graphics2D graphics, Player player, String text, Color colour, int height)
	{
		String message = text.trim();
		if (!message.isEmpty())
		{
			Point position = player.getCanvasTextLocation(graphics, message, player.getLogicalHeight() + height);
			if (position != null)
			{
				OverlayUtil.renderTextLocation(graphics, position, message, colour);
			}
		}
	}
}
