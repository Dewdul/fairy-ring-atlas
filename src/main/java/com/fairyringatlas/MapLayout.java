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
 * config allows, growing up and left from the dials' bottom-right corner (where Teleport and the
 * close button are moved), clear of the chatbox, the side panel and the travel log, and never
 * smaller than the dials.
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

	/** Room above the map for the game's mouse-over text in the canvas's top-left corner. */
	static final int TOP_MARGIN = 24;

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
	 * (bottom, right, left or top) that clears it and keeps the most room. A cut never goes past
	 * the dials' own edge, so on a small canvas, where the dials sit within a few pixels of the
	 * edge, the map still stops at the dials instead of covering the chatbox, side panel and log.
	 */
	public static Rectangle compute(Rectangle canvas, Rectangle dial, List<Rectangle> obstacles, int maxW, int maxH)
	{
		int dialRight = dial.x + dial.width;
		int dialBottom = dial.y + dial.height;
		// the inset canvas, grown to hold the dials when they sit closer than the inset to an edge
		int left = Math.min(canvas.x + 6, dial.x);
		// the top keeps clear of the game's mouse-over text ("Cancel", "Walk here") at the top-left
		int top = Math.min(canvas.y + TOP_MARGIN, dial.y);
		int right = Math.max(canvas.x + canvas.width - 6, dialRight);
		int bottom = Math.max(canvas.y + canvas.height - 6, dialBottom);
		for (Rectangle o : obstacles)
		{
			if (o.x >= right || o.x + o.width <= left || o.y >= bottom || o.y + o.height <= top || o.intersects(dial))
			{
				continue;
			}
			int[][] options = {
				{left, top, right, Math.max(o.y - 6, dialBottom)},
				{left, top, Math.max(o.x - 8, dialRight), bottom},
				{Math.min(o.x + o.width + 8, dial.x), top, right, bottom},
				{left, Math.min(o.y + o.height + 6, dial.y), right, bottom},
			};
			// least overlap with the obstacle first (none, when a cut clears it), then most room
			int[] cut = null;
			long bestOverlap = Long.MAX_VALUE;
			long bestArea = -1;
			for (int[] c : options)
			{
				Rectangle r = new Rectangle(c[0], c[1], Math.max(0, c[2] - c[0]), Math.max(0, c[3] - c[1]));
				Rectangle in = r.intersection(o);
				long overlap = in.isEmpty() ? 0 : (long) in.width * in.height;
				long area = (long) r.width * r.height;
				if (overlap < bestOverlap || (overlap == bestOverlap && area > bestArea))
				{
					bestOverlap = overlap;
					bestArea = area;
					cut = c;
				}
			}
			left = cut[0];
			top = cut[1];
			right = cut[2];
			bottom = cut[3];
		}

		// the map grows up and left from the dials' bottom-right corner: Teleport and the close
		// button, which the game shows only inside the dials, are moved into that corner
		int w = Math.max(dial.width, Math.min(maxW, dialRight - left));
		int h = Math.max(dial.height, Math.min(maxH, dialBottom - top));
		return new Rectangle(dialRight - w, dialBottom - h, w, h);
	}
}
