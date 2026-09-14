package com.menaphite;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

final class ReminderOverhead extends Overlay
{
	private final Client client;
	private final MenaphiteRemedyRemindersConfig config;
	private volatile long visibleUntil;

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

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (visibleUntil == 0 || System.nanoTime() >= visibleUntil
			|| !config.showOverhead() || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		Player player = client.getLocalPlayer();
		String message = config.overheadMessage().trim();
		if (player != null && !message.isEmpty())
		{
			Point position = player.getCanvasTextLocation(graphics, message, player.getLogicalHeight() + 40);
			if (position != null)
			{
				OverlayUtil.renderTextLocation(graphics, position, message, config.overheadColour());
			}
		}
		return null;
	}
}
