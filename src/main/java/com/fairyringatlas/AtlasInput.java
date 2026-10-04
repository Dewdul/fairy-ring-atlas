/*
 * Copyright (c) 2026, Dewdul
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringatlas;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseWheelListener;

/**
 * Mouse input over the map, on the AWT thread. It touches only the plugin's volatile and
 * synchronised view state. Wheel zooms; a left press on empty map pans; a left press on a marker,
 * card or button is let through so the game runs our RUNELITE menu entry (swallowed instead when
 * the game's menu was still built for something else, so no stale game entry runs); right presses
 * always go through to open the menu we built. Nothing is consumed inside the holes, while a menu
 * is open, or outside the map.
 */
@Singleton
public class AtlasInput extends MouseAdapter implements MouseWheelListener
{
	private static final double WHEEL_STEP = 1.25;

	private final FairyRingAtlasPlugin plugin;
	private volatile boolean dragging;
	private volatile boolean swallow;
	/**
	 * The row the press landed on in a list the player can reorder (Favourites or a group), and
	 * its ring: a click selects it, a drag moves it.
	 */
	private volatile String rowList;
	private volatile String rowCode;
	private volatile boolean rowDragging;
	private int pressX;
	private int pressY;
	private int lastX;
	private int lastY;

	@Inject
	AtlasInput(FairyRingAtlasPlugin plugin)
	{
		this.plugin = plugin;
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
	{
		if (!plugin.isMapInput(e.getX(), e.getY()))
		{
			return e;
		}
		Hit hit = plugin.hitAt(e.getX(), e.getY());
		double rotation = e.getPreciseWheelRotation();
		if (plugin.isGroupsOpen() && plugin.inGroups(e.getX(), e.getY()))
		{
			plugin.scrollGroups((int) Math.round(rotation * 24));
		}
		else if (hit != null && (hit.getKind() == Hit.Kind.PANEL || hit.getKind() == Hit.Kind.CARD
			|| (hit.getKind() == Hit.Kind.CHIP && plugin.inPanel(e.getX(), e.getY()))))
		{
			plugin.scrollPanel((int) Math.round(rotation * 24));
		}
		else
		{
			plugin.zoomAt(e.getX(), e.getY(), Math.pow(WHEEL_STEP, -rotation));
		}
		e.consume();
		return e;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		swallow = false;
		dragging = false;
		if (!SwingUtilities.isLeftMouseButton(e) || !plugin.isMapInput(e.getX(), e.getY()))
		{
			return e;
		}
		Hit hit = plugin.hitAt(e.getX(), e.getY());
		if (hit != null && hit.isDraggableRow())
		{
			// a favourite's or a group's row: click or drag is only known on release, so the press stays with us
			rowList = hit.getId();
			rowCode = hit.getRing().getCode();
			rowDragging = false;
			pressX = e.getX();
			pressY = e.getY();
			swallow = true;
			e.consume();
			return e;
		}
		if (hit != null && hit.isActionable() && plugin.isMenuFor(hit))
		{
			// the game runs the top menu entry, which is ours for this very hit
			return e;
		}
		if (hit == null)
		{
			dragging = true;
			lastX = e.getX();
			lastY = e.getY();
		}
		swallow = true;
		e.consume();
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		String code = rowCode;
		if (code != null)
		{
			if (!rowDragging && Math.abs(e.getX() - pressX) + Math.abs(e.getY() - pressY) > 4)
			{
				rowDragging = true;
			}
			if (rowDragging)
			{
				plugin.rowDrag(rowList, code, e.getY());
			}
			// not consumed, as for a pan
			return e;
		}
		if (dragging)
		{
			plugin.panBy(e.getX() - lastX, e.getY() - lastY);
			lastX = e.getX();
			lastY = e.getY();
			// not consumed: the game took no press, so to it this is a plain move, and its mouse
			// position (hover, the info card, the next menu) keeps up with the pointer
		}
		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		String code = rowCode;
		if (code != null)
		{
			rowCode = null;
			if (rowDragging)
			{
				plugin.rowDrop(rowList, code);
			}
			else
			{
				plugin.rowClick(code);
			}
			rowDragging = false;
		}
		if (dragging || swallow)
		{
			dragging = false;
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		if (swallow)
		{
			swallow = false;
			e.consume();
		}
		return e;
	}

	void reset()
	{
		dragging = false;
		swallow = false;
		rowCode = null;
		rowDragging = false;
	}
}
