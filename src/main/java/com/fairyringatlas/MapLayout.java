/*
 * Copyright (c) 2026, Dewdul
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringatlas;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Where the map goes. Fixed mode: exactly over the dial interface. Resizable: as large as the
 * config allows, centred on the dials, clear of the chatbox, the side panel and the travel log,
 * and never smaller than the dials (the holes for Teleport and close must fall inside the map).
 */
public final class MapLayout
{
	private static final int[] OBSTACLES = {
		InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER,
		InterfaceID.ToplevelOsrsStretch.SIDE_MENU,
		InterfaceID.ToplevelPreEoc.CHAT_CONTAINER,
		InterfaceID.ToplevelPreEoc.SIDE_CONTAINER,
		InterfaceID.ToplevelPreEoc.SIDE_STATIC_LAYER,
		InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_LAYER,
		InterfaceID.FairyringsLog.UNIVERSE,
	};

	private MapLayout()
	{
	}

	/** The map rectangle for the live client, or null when the dials are not on screen. */
	static Rectangle fromClient(Client client, int maxW, int maxH)
	{
		Rectangle dial = bounds(client, InterfaceID.Fairyrings.ROOT_RECT0);
		if (dial == null)
		{
			return null;
		}
		if (!client.isResized())
		{
			return dial;
		}
		List<Rectangle> obstacles = new ArrayList<>();
		for (int id : OBSTACLES)
		{
			Rectangle r = bounds(client, id);
			if (r != null)
			{
				obstacles.add(r);
			}
		}
		Rectangle canvas = new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
		return compute(canvas, dial, obstacles, maxW, maxH);
	}

	/** The holes: the CONFIRM button and the close button, where present. */
	static List<Rectangle> holes(Client client)
	{
		List<Rectangle> out = new ArrayList<>(2);
		Rectangle confirm = bounds(client, InterfaceID.Fairyrings.CONFIRM);
		if (confirm != null)
		{
			out.add(confirm);
		}
		Rectangle close = bounds(client, InterfaceID.Fairyrings.ROOT_GRAPHIC27);
		if (close != null)
		{
			out.add(close);
		}
		return out;
	}

	static Rectangle bounds(Client client, int component)
	{
		Widget w = client.getWidget(component);
		if (w == null || w.isHidden())
		{
			return null;
		}
		Rectangle r = w.getBounds();
		return r == null || r.width <= 0 || r.height <= 0 ? null : r;
	}

	/**
	 * Resizable-mode layout. Each obstacle overlapping the usable area cuts it from the side
	 * (bottom, right or left) that keeps the most room while leaving the dials inside.
	 */
	public static Rectangle compute(Rectangle canvas, Rectangle dial, List<Rectangle> obstacles, int maxW, int maxH)
	{
		int left = canvas.x + 6;
		int top = canvas.y + 6;
		int right = canvas.x + canvas.width - 6;
		int bottom = canvas.y + canvas.height - 6;
		for (Rectangle o : obstacles)
		{
			if (o.x >= right || o.x + o.width <= left || o.y >= bottom || o.y + o.height <= top || o.intersects(dial))
			{
				continue;
			}
			long best = -1;
			int[] cut = null;
			int[][] options = {
				{left, top, right, o.y - 6},
				{left, top, o.x - 8, bottom},
				{o.x + o.width + 8, top, right, bottom},
				{left, o.y + o.height + 6, right, bottom},
			};
			for (int[] c : options)
			{
				boolean keepsDial = c[0] <= dial.x && c[1] <= dial.y && c[2] >= dial.x + dial.width && c[3] >= dial.y + dial.height;
				long area = (long) Math.max(0, c[2] - c[0]) * Math.max(0, c[3] - c[1]);
				if (keepsDial && area > best)
				{
					best = area;
					cut = c;
				}
			}
			if (cut != null)
			{
				left = cut[0];
				top = cut[1];
				right = cut[2];
				bottom = cut[3];
			}
		}

		int w = Math.max(dial.width, Math.min(maxW, right - left));
		int h = Math.max(dial.height, Math.min(maxH, bottom - top));
		int x = dial.x + dial.width / 2 - w / 2;
		int y = dial.y + dial.height / 2 - h / 2;
		x = Math.max(left, Math.min(x, right - w));
		y = Math.max(top, Math.min(y, bottom - h));
		// the dials (and so the holes) must lie inside the map
		x = Math.min(x, dial.x);
		y = Math.min(y, dial.y);
		x = Math.max(x, dial.x + dial.width - w);
		y = Math.max(y, dial.y + dial.height - h);
		return new Rectangle(x, y, w, h);
	}
}
