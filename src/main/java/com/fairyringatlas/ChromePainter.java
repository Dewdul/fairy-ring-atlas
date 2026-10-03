/*
 * Copyright (c) 2026, Dewdul
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringatlas;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The map's chrome in an OSRS style: dark stone frame, the top bar with breadcrumb, dial readout
 * and buttons, the side panel (Favourites, then Elsewhere: the off-surface layers), the info card and the frames around the
 * holes left for the real Teleport and close buttons. Needs no game client.
 */
public class ChromePainter
{
	static final int BAR_H = 22;
	static final int PANEL_W = 150;
	static final int CARD_W = 340;
	static final int CARD_MAX_H = 260;
	/** Menu target of the panel's show/hide toggle. */
	private static final String PANEL_NAME = "Favourites and Elsewhere";

	static final Color FRAME = new Color(0x1e1a14);
	static final Color BRONZE = new Color(0x6b5a40);
	static final Color BRONZE_LIGHT = new Color(0x9a8360);
	static final Color FILL = new Color(24, 20, 14, 238);
	static final Color FILL_LIGHT = new Color(52, 44, 32, 235);
	static final Color TITLE = new Color(0xFF981F);
	static final Color CREAM = new Color(0xF0E2C0);
	static final Color GREY = new Color(0xA79D8B);
	static final Color AMBER = new Color(0xFFB347);
	static final Color DANGER = new Color(0xFF5A4E);
	static final Color GOOD = new Color(0x6BE36B);
	static final Color HINT = new Color(0xFFE36B);

	private final Ink ink;

	public ChromePainter(Ink ink)
	{
		this.ink = ink;
	}

	/** Places the top bar and panel so the map painter can keep labels clear of them. */
	public void layout(Scene s)
	{
		MapView v = s.view;
		// the bar and, when shown, the notice strip under it
		s.topBar = new Rectangle(v.getX(), v.getY(), v.getW(), BAR_H + (s.notice != null ? 18 : 0));
		// the card painted last; the map painter keeps labels from hiding half under it
		s.card = layerCard != null && v.rect().contains(layerCard) ? layerCard : null;
		int top = v.getY() + BAR_H + (s.notice != null ? 18 : 0) + 6;
		if (cards(s).isEmpty() && s.repo.favouriteRings().isEmpty())
		{
			s.panel = null;
		}
		else if (s.panelCollapsed)
		{
			s.panel = new Rectangle(v.getX() + 6, top, 20, 60);
		}
		else
		{
			s.panel = new Rectangle(v.getX() + 6, top, PANEL_W, v.getY() + v.getH() - 6 - top);
		}
	}

	/**
	 * Paints the chrome. It is drawn into a cached translucent layer that is rebuilt only when
	 * something it shows changes, so a steady frame costs one blit.
	 */
	public void paint(Graphics2D g, Scene s)
	{
		MapView v = s.view;
		long key = key(s);
		if (layer == null || layer.getWidth() != v.getW() || layer.getHeight() != v.getH() || key != layerKey)
		{
			if (layer == null || layer.getWidth() != v.getW() || layer.getHeight() != v.getH())
			{
				layer = new BufferedImage(v.getW(), v.getH(), BufferedImage.TYPE_INT_ARGB);
			}
			Graphics2D lg = layer.createGraphics();
			lg.setComposite(AlphaComposite.Clear);
			lg.fillRect(0, 0, v.getW(), v.getH());
			lg.setComposite(AlphaComposite.SrcOver);
			lg.translate(-v.getX(), -v.getY());
			lg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int mark = s.hits.size();
			paintPanel(lg, s);
			paintTopBar(lg, s);
			paintCard(lg, s);
			paintFrame(lg, s);
			lg.dispose();
			layerHits.clear();
			layerHits.addAll(s.hits.subList(mark, s.hits.size()));
			layerCard = s.card;
			layerKey = key;
		}
		else
		{
			s.hits.addAll(layerHits);
			s.card = layerCard;
		}
		g.drawImage(layer, v.getX(), v.getY(), null);
	}

	private long key(Scene s)
	{
		MapView v = s.view;
		Object hover = null;
		if (s.mouse != null)
		{
			for (Hit h : layerHits)
			{
				if (h.isActionable() && h.getArea().contains(s.mouse))
				{
					hover = h;
				}
			}
		}
		boolean flash = s.now < s.flashUntil && (s.now / 250) % 2 == 0;
		return java.util.Objects.hash(v.getX(), v.getY(), v.getW(), v.getH(), v.getLayer(), s.selected, s.hovered,
			java.util.Arrays.hashCode(s.dials), s.query, s.rowVisible, s.notice, s.panelCollapsed, s.panelScroll,
			s.now < s.flashUntil, flash, hover, s.holes, s.repo.getStateHash(), s.here,
			s.visitedColor, s.selectedColor, s.favouriteColor, focusCovered(s));
	}

	private BufferedImage layer;
	private long layerKey;
	private final List<Hit> layerHits = new ArrayList<>();
	private Rectangle layerCard;

	/** Drops the map-sized layer while the interface is closed; it is rebuilt on the next paint. */
	public void release()
	{
		layer = null;
		layerHits.clear();
		layerCard = null;
		layerKey = 0;
	}

	// ------------------------------------------------------------------ top bar

	private void paintTopBar(Graphics2D g, Scene s)
	{
		Rectangle bar = new Rectangle(s.topBar.x, s.topBar.y, s.topBar.width, BAR_H);
		g.setColor(new Color(16, 13, 9, 215));
		g.fillRect(bar.x, bar.y, bar.width, bar.height);
		g.setColor(BRONZE);
		g.fillRect(bar.x, bar.y + bar.height - 1, bar.width, 1);
		s.hits.add(new Hit(Hit.Kind.BLOCK, bar, null, null, null, null));

		int right = bar.x + bar.width - 6;
		for (Rectangle h : s.holes)
		{
			if (h.intersects(bar))
			{
				right = Math.min(right, h.x - 6);
			}
		}
		int ty = bar.y + (bar.height - ink.height(ink.small)) / 2;
		String[] ids = {Hit.DIALS, Hit.FIT, Hit.ZOOM_IN, Hit.ZOOM_OUT, Hit.CLEAR};
		String[] labels = {"Dials", "Fit", "+", "-", "Clear"};
		String[] options = {"Show", "Fit", "Zoom in", "Zoom out", "Clear"};
		String[] targets = {"dials", "map", "", "", "selection"};
		for (int i = 0; i < ids.length; i++)
		{
			if (Hit.CLEAR.equals(ids[i]) && s.selected == null)
			{
				continue;
			}
			int w = Math.max(18, ink.width(ink.small, labels[i]) + 12);
			Rectangle b = new Rectangle(right - w, bar.y + 3, w, bar.height - 6);
			button(g, s, b, labels[i], ids[i], options[i], targets[i]);
			right = b.x - 3;
		}
		right -= 6;

		int x = bar.x + 6;
		Layer layer = s.repo.layer(s.view.getLayer());
		Layer surface = s.repo.surface();
		if (layer == null || layer.isSurface())
		{
			x = text(g, surface.getName(), ink.bold, TITLE, x, ty, right);
		}
		else
		{
			int w = ink.width(ink.small, surface.getName()) + 20;
			Rectangle b = new Rectangle(x, bar.y + 3, w, bar.height - 6);
			button(g, s, b, surface.getName(), Hit.BACK, "Back to", surface.getName());
			triangle(g, b.x + 7, b.y + b.height / 2.0, 3.5, false, CREAM);
			x = b.x + b.width + 6;
			triangle(g, x + 3, bar.y + bar.height / 2.0, 3.5, true, GREY);
			x = text(g, layer.getName(), ink.bold, TITLE, x + 10, ty, right);
		}

		x += 14;
		if (s.dials != null && x < right - 60)
		{
			x = text(g, "Dials:", ink.small, GREY, x, ty, right) + 4;
			String code = s.dialledCode();
			x = text(g, DialMath.spaced(code), ink.bold, Color.WHITE, x, ty, right) + 5;
			Ring dialled = s.repo.ring(code);
			if (x < right - 20)
			{
				arrow(g, x, bar.y + bar.height / 2.0, 9, GREY);
				x += 14;
				boolean match = s.selected != null && s.selected.equals(code);
				x = text(g, dialled == null ? "nothing" : dialled.getName(), ink.small,
					dialled == null ? GREY : match ? GOOD : CREAM, x, ty, right);
			}
		}
		if (s.searching() && x < right - 40)
		{
			int n = s.repo.matching(s.query).size();
			text(g, "Search: " + s.query + " (" + n + ")", ink.small, HINT, x + 14, ty, right);
		}

		if (s.notice != null)
		{
			Rectangle strip = new Rectangle(bar.x, bar.y + bar.height, bar.width, 18);
			g.setColor(new Color(90, 20, 14, 220));
			g.fill(strip);
			text(g, s.notice, ink.small, Color.WHITE, strip.x + 6, strip.y + (18 - ink.height(ink.small)) / 2, strip.x + strip.width - 6);
			s.hits.add(new Hit(Hit.Kind.BLOCK, strip, null, null, null, null));
		}
	}

	private void button(Graphics2D g, Scene s, Rectangle b, String label, String id, String option, String target)
	{
		boolean hover = s.mouse != null && b.contains(s.mouse);
		g.setColor(FRAME);
		g.fillRoundRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2, 5, 5);
		g.setColor(hover ? new Color(0x5a4b36) : new Color(0x3a3125));
		g.fillRoundRect(b.x, b.y, b.width, b.height, 4, 4);
		g.setColor(hover ? BRONZE_LIGHT : BRONZE);
		g.drawRoundRect(b.x, b.y, b.width - 1, b.height - 1, 4, 4);
		int tw = ink.width(ink.small, label);
		int tx = Hit.BACK.equals(id) ? b.x + 13 : b.x + (b.width - tw) / 2;
		ink.text(g, label, ink.small, hover ? Color.WHITE : CREAM, tx, b.y + (b.height - ink.height(ink.small)) / 2, Ink.Style.SHADOW);
		s.hits.add(new Hit(Hit.Kind.BUTTON, b, null, id, option, target));
	}

	/** Draws text clipped to a right edge; returns the x after it. */
	private int text(Graphics2D g, String t, Font f, Color c, int x, int y, int right)
	{
		String fitted = ink.fit(t, f, right - x);
		if (fitted == null || fitted.isEmpty())
		{
			return x;
		}
		ink.text(g, fitted, f, c, x, y, Ink.Style.SHADOW);
		return x + ink.width(f, fitted);
	}

	// ------------------------------------------------------------------ Elsewhere panel

	/** A card: an off-surface layer (or the house) and its rings. */
	private static final class Card
	{
		final String id;
		final String name;
		final List<Ring> rings;

		Card(String id, String name, List<Ring> rings)
		{
			this.id = id;
			this.name = name;
			this.rings = rings;
		}
	}

	private List<Card> cards(Scene s)
	{
		List<Card> out = new ArrayList<>();
		for (Layer l : s.repo.getLayers())
		{
			if (l.isSurface())
			{
				continue;
			}
			List<Ring> rings = s.repo.ringsIn(l.getId());
			if (!rings.isEmpty())
			{
				out.add(new Card(l.getId(), l.getName(), rings));
			}
		}
		List<Ring> house = s.repo.ringsIn(Layer.POH);
		if (!house.isEmpty())
		{
			out.add(new Card(Layer.POH, "Your house", house));
		}
		return out;
	}

	private void paintPanel(Graphics2D g, Scene s)
	{
		Rectangle p = s.panel;
		if (p == null)
		{
			return;
		}
		panelBox(g, p);
		if (s.panelCollapsed)
		{
			s.hits.add(new Hit(Hit.Kind.BUTTON, p, null, Hit.TOGGLE_PANEL, "Show", PANEL_NAME));
			// the search matches a ring elsewhere: the tab says so (and flashes with the cards)
			boolean matches = false;
			for (Card c : cards(s))
			{
				for (Ring r : c.rings)
				{
					matches |= s.searching() && r.matches(s.query);
				}
			}
			boolean flash = s.now < s.flashUntil && (s.now / 250) % 2 == 0;
			if (matches)
			{
				g.setColor(flash ? Color.WHITE : HINT);
				g.setStroke(new BasicStroke(1.5f));
				g.drawRoundRect(p.x, p.y, p.width - 1, p.height - 1, 8, 8);
			}
			triangle(g, p.x + p.width / 2.0, p.y + 12, 4, true, CREAM);
			for (int i = 0; i < 3; i++)
			{
				g.setColor(matches ? HINT : i == 0 ? s.visitedColor : BRONZE_LIGHT);
				g.fill(AtlasPainter.circle(p.x + p.width / 2.0, p.y + 26 + i * 10, 2.5));
			}
			return;
		}
		s.hits.add(new Hit(Hit.Kind.PANEL, p, null, null, null, null));
		int sh = ink.height(ink.small);
		ink.text(g, "Favourites", ink.bold, TITLE, p.x + 6, p.y + 4, Ink.Style.SHADOW);
		Rectangle toggle = new Rectangle(p.x + p.width - 20, p.y + 3, 16, 15);
		button(g, s, toggle, "", Hit.TOGGLE_PANEL, "Hide", PANEL_NAME);
		triangle(g, toggle.x + 8, toggle.y + 7.5, 3.5, false, CREAM);

		Rectangle content = new Rectangle(p.x + 4, p.y + 22, p.width - 8, p.height - 26);
		Shape oldClip = g.getClip();
		g.clip(content);
		int y = paintFavourites(g, s, content, content.y - s.panelScroll);
		ink.text(g, "Elsewhere", ink.bold, TITLE, content.x + 2, y, Ink.Style.SHADOW);
		y += ink.height(ink.bold) + 4;
		Layer active = s.repo.layer(s.view.getLayer());
		boolean flash = s.now < s.flashUntil && (s.now / 250) % 2 == 0;
		Composite oldComp = g.getComposite();
		for (Card c : cards(s))
		{
			List<Rectangle> chips = new ArrayList<>();
			int cx = content.x + 5;
			int cy = y + 4 + sh + 2;
			for (Ring r : c.rings)
			{
				int w = ink.width(ink.small, r.getCode()) + 8;
				if (cx + w > content.x + content.width - 4 && cx > content.x + 5)
				{
					cx = content.x + 5;
					cy += sh + 4;
				}
				chips.add(new Rectangle(cx, cy, w, sh + 2));
				cx += w + 3;
			}
			int h = cy + sh + 2 + 5 - y;
			Rectangle card = new Rectangle(content.x, y, content.width, h);
			boolean isActive = active != null && active.getId().equals(c.id);
			boolean matches = false;
			for (Ring r : c.rings)
			{
				matches |= s.searching() && r.matches(s.query);
			}
			if (s.searching() && !matches)
			{
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
			}
			g.setColor(isActive ? new Color(70, 52, 30, 240) : FILL_LIGHT);
			g.fillRoundRect(card.x, card.y, card.width, card.height, 6, 6);
			g.setColor(isActive ? TITLE : matches ? (flash ? Color.WHITE : HINT) : BRONZE);
			g.setStroke(new BasicStroke(isActive || matches ? 1.5f : 1f));
			g.drawRoundRect(card.x, card.y, card.width - 1, card.height - 1, 6, 6);
			ink.text(g, ink.fit(c.name, ink.small, card.width - 10), ink.small, CREAM, card.x + 5, card.y + 4, Ink.Style.SHADOW);
			Ring house = Layer.POH.equals(c.id) ? c.rings.get(0) : null;
			if (content.intersects(card))
			{
				if (house != null)
				{
					s.hits.add(new Hit(Hit.Kind.CHIP, clipTo(card, content), house, null, "Select", house.getCode() + " " + house.getName()));
				}
				else
				{
					s.hits.add(new Hit(Hit.Kind.CARD, clipTo(card, content), null, c.id, "Open", c.name));
				}
			}
			for (int i = 0; i < c.rings.size(); i++)
			{
				Ring r = c.rings.get(i);
				Rectangle chip = chips.get(i);
				chip(g, s, r, chip);
				if (house == null && content.intersects(chip))
				{
					s.hits.add(new Hit(Hit.Kind.CHIP, clipTo(chip, content), r, null, "Select", r.getCode() + " " + r.getName()));
				}
			}
			g.setComposite(oldComp);
			y += h + 5;
		}
		g.setClip(oldClip);
		int overflow = y + s.panelScroll - (content.y + content.height);
		if (s.panelScroll > 0)
		{
			chevron(g, p.x + p.width / 2.0, content.y + 3, -1);
		}
		if (overflow > s.panelScroll)
		{
			chevron(g, p.x + p.width / 2.0, content.y + content.height - 3, 1);
		}
		panelScrollMax = Math.max(0, overflow);
	}

	/**
	 * The Favourites list at the top of the panel, one row per favourite in favourites-block
	 * order. Returns the y below it.
	 */
	private int paintFavourites(Graphics2D g, Scene s, Rectangle content, int y)
	{
		int sh = ink.height(ink.small);
		List<Ring> faves = s.repo.favouriteRings();
		if (faves.isEmpty())
		{
			for (String t : ink.wrap("Star a code in your travel log to list it here.", ink.small, content.width - 8))
			{
				ink.text(g, t, ink.small, GREY, content.x + 4, y + 2, Ink.Style.SHADOW);
				y += sh;
			}
			return y + 12;
		}
		Composite oldComp = g.getComposite();
		for (Ring r : faves)
		{
			Rectangle row = new Rectangle(content.x, y, content.width, sh + 6);
			boolean sel = r.getCode().equals(s.selected);
			boolean hover = s.mouse != null && row.contains(s.mouse) && content.contains(s.mouse);
			if (s.searching() && !r.matches(s.query))
			{
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
			}
			if (sel || hover)
			{
				g.setColor(sel ? new Color(70, 52, 30, 240) : FILL_LIGHT);
				g.fillRoundRect(row.x, row.y, row.width, row.height, 6, 6);
				g.setColor(sel ? TITLE : BRONZE_LIGHT);
				g.setStroke(new BasicStroke(1f));
				g.drawRoundRect(row.x, row.y, row.width - 1, row.height - 1, 6, 6);
			}
			Rectangle chip = new Rectangle(row.x + 3, row.y + 2, ink.width(ink.small, r.getCode()) + 8, sh + 2);
			chip(g, s, r, chip);
			int tx = chip.x + chip.width + 5;
			ink.text(g, ink.fit(r.getName(), ink.small, row.x + row.width - 3 - tx), ink.small, sel ? TITLE : CREAM, tx, row.y + 3, Ink.Style.SHADOW);
			g.setComposite(oldComp);
			if (content.intersects(row))
			{
				s.hits.add(new Hit(Hit.Kind.CHIP, clipTo(row, content), r, Hit.FAVE, "Select", r.getCode() + " " + r.getName()));
			}
			y += row.height + 2;
		}
		return y + 8;
	}

	/** Largest useful panel scroll of the last paint, for the input handler to clamp to. */
	volatile int panelScrollMax;

	private void chip(Graphics2D g, Scene s, Ring r, Rectangle c)
	{
		String code = r.getCode();
		boolean sel = code.equals(s.selected);
		boolean visited = s.repo.isVisited(code);
		boolean fave = s.repo.isFavourite(code);
		Color fill = sel ? s.selectedColor : visited ? AtlasPainter.withAlpha(s.visitedColor, 200) : new Color(0x4a4f4d);
		g.setColor(fill);
		g.fillRoundRect(c.x, c.y, c.width, c.height, 5, 5);
		if (fave)
		{
			g.setColor(s.favouriteColor);
			g.setStroke(new BasicStroke(1.4f));
			g.drawRoundRect(c.x, c.y, c.width - 1, c.height - 1, 5, 5);
		}
		boolean hover = s.mouse != null && c.contains(s.mouse);
		if (hover)
		{
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(1f));
			g.drawRoundRect(c.x - 1, c.y - 1, c.width + 1, c.height + 1, 6, 6);
		}
		Color text = sel || visited ? Ink.DARK : new Color(0xC9CFCD);
		ink.text(g, code, ink.small, text, c.x + 4, c.y + 1, Ink.Style.PLAIN);
	}

	private void panelBox(Graphics2D g, Rectangle p)
	{
		g.setColor(new Color(0, 0, 0, 70));
		g.fillRoundRect(p.x + 2, p.y + 2, p.width, p.height, 8, 8);
		g.setColor(FILL);
		g.fillRoundRect(p.x, p.y, p.width, p.height, 8, 8);
		g.setColor(BRONZE);
		g.setStroke(new BasicStroke(1f));
		g.drawRoundRect(p.x, p.y, p.width - 1, p.height - 1, 8, 8);
	}

	// ------------------------------------------------------------------ info card

	/** One laid-out card line. */
	private static final class Line
	{
		final String text;
		final Font font;
		final Color color;
		final int gapBefore;

		Line(String text, Font font, Color color, int gapBefore)
		{
			this.text = text;
			this.font = font;
			this.color = color;
			this.gapBefore = gapBefore;
		}
	}

	private void paintCard(Graphics2D g, Scene s)
	{
		Ring ring = s.hovered != null ? s.hovered : s.repo.ring(s.selected);
		MapView v = s.view;
		int left = s.panel != null ? s.panel.x + s.panel.width + 6 : v.getX() + 6;
		int maxW = Math.min(Math.min(CARD_W, Math.max(220, v.getW() * 45 / 100)), v.getX() + v.getW() - 6 - left);
		// on a small (fixed mode) map the card keeps to under half the height so the map stays usable
		int maxH = Math.min(CARD_MAX_H, v.getH() * (v.getH() < 450 ? 48 : 62) / 100);
		java.awt.Point focus = cardFocus(s);
		if (ring == null)
		{
			String hint = "Click a fairy ring to plan your trip";
			int w = ink.width(ink.small, hint) + 12;
			int hh = ink.height(ink.small) + 8;
			Rectangle r = place(s, left, w, hh, hh, null);
			// like the card, the hint keeps labels from under it and absorbs presses
			s.card = r;
			if (r != null)
			{
				s.hits.add(new Hit(Hit.Kind.BLOCK, r, null, null, null, null));
				panelBox(g, r);
				ink.text(g, hint, ink.small, GREY, r.x + 6, r.y + 4, Ink.Style.SHADOW);
			}
			return;
		}
		int pad = 8;
		int textW = maxW - pad * 2;
		boolean isSelected = ring.getCode() != null && ring.getCode().equals(s.selected);
		boolean visited = !ring.isDialable() || s.repo.isVisited(ring.getCode());

		List<Line> lines = new ArrayList<>();
		String title = (ring.getCode() != null && ring.isDialable() ? ring.getCode() + "  " : "") + ring.getName();
		for (String t : ink.wrap(title, ink.bold, textW))
		{
			lines.add(new Line(t, ink.bold, TITLE, 0));
		}
		if (ring.getArea() != null)
		{
			lines.add(new Line(ink.fit(ring.getArea(), ink.small, textW), ink.small, GREY, 0));
		}
		if (ring.isDialable())
		{
			StringBuilder status = new StringBuilder(visited ? "In your travel log" : "Not in your travel log");
			if (s.repo.isFavourite(ring.getCode()))
			{
				status.append(" - favourite");
			}
			if (ring.getCode().equals(s.repo.getLastCode()))
			{
				status.append(" - last destination");
			}
			if (ring.getCode().equals(s.here))
			{
				status.append(" - you are here");
			}
			lines.add(new Line(ink.fit(status.toString(), ink.small, textW), ink.small, visited ? s.visitedColor : GREY, 2));
			// the game's own one-click teleports from any ring, without opening the dials
			String oneClick = s.repo.isFavourite(ring.getCode()) ? "Favourites > " + ring.getCode()
				: ring.getCode().equals(s.repo.getLastCode()) ? "Last-destination (" + ring.getCode() + ")" : null;
			if (oneClick != null)
			{
				addWrapped(lines, "1 click: right-click any fairy ring > " + oneClick, ink.small, HINT, textW, 0);
			}
		}
		addWrapped(lines, ring.getDescription(), ink.regular, Color.WHITE, textW, 4);
		boolean first = true;
		for (String req : ring.getRequirements())
		{
			addWrapped(lines, "- " + req, ink.small, visited ? GREY : AMBER, textW, first ? 4 : 0);
			first = false;
		}
		if (ring.isNoStaffReturn() && !s.repo.isStaffless())
		{
			// the game's no_staff_return flag: this ring works without a staff, but only to Zanaris
			addWrapped(lines, "No staff needed to leave: this ring can take you back to Zanaris.", ink.small, GREY, textW, 4);
		}
		first = true;
		for (String d : ring.getDanger())
		{
			addWrapped(lines, "! " + d, ink.small, DANGER, textW, first ? 4 : 0);
			first = false;
		}
		// places come after what the player must know before going, so a short card cuts them first
		List<String> poi = ring.getPoi();
		if (!poi.isEmpty())
		{
			addWrapped(lines, "Nearby: " + String.join(", ", poi.subList(0, Math.min(6, poi.size()))), ink.small, CREAM, textW, 4);
		}
		List<String> notes = ring.getNotes();
		for (int i = 0; i < Math.min(2, notes.size()); i++)
		{
			addWrapped(lines, notes.get(i), ink.small, new Color(0xB8C4D6), textW, i == 0 ? 4 : 0);
		}

		// the next step for the selected ring
		Line step = null;
		boolean dialRow = false;
		if (isSelected && ring.isDialable())
		{
			String code = ring.getCode();
			if (s.dialsMatch(code))
			{
				step = new Line("Ready: click Teleport", ink.bold, GOOD, 6);
			}
			else if (s.repo.hasLogRow(code))
			{
				step = new Line(s.rowVisible ? "Click the highlighted code in your travel log, then Teleport."
					: s.searching() ? "Clear the travel log search to show this code."
					: "Use this code in your travel log, then Teleport.", ink.small, HINT, 6);
			}
			else
			{
				step = new Line("Not in your travel log yet - dial it:", ink.small, HINT, 6);
				dialRow = s.dials != null;
			}
		}
		int stepH = 0;
		List<String> stepLines = new ArrayList<>();
		if (step != null)
		{
			stepLines = ink.wrap(step.text, step.font, textW);
			stepH = step.gapBefore + stepLines.size() * ink.height(step.font) + (dialRow ? 24 : 0);
		}

		int h = pad * 2 + stepH;
		int cut = lines.size();
		for (int i = 0; i < lines.size(); i++)
		{
			Line l = lines.get(i);
			int lh = l.gapBefore + ink.height(l.font);
			if (h + lh > maxH)
			{
				cut = i;
				break;
			}
			h += lh;
		}
		if (cut < lines.size() && cut > 0)
		{
			// the last line shown ends in "..." so the cut is visible
			Line last = lines.get(cut - 1);
			String more = last.text.endsWith(".") ? last.text.substring(0, last.text.length() - 1) : last.text;
			lines.set(cut - 1, new Line(ink.fit(more + " ...", last.font, textW).replace(" ...", "..."), last.font, last.color, last.gapBefore));
		}
		Rectangle card = place(s, left, maxW, h, Math.min(h, 120), focus);
		if (card == null)
		{
			s.card = null;
			return;
		}
		s.card = card;
		s.hits.add(new Hit(Hit.Kind.BLOCK, card, null, null, null, null));
		panelBox(g, card);
		g.setColor(TITLE);
		g.fillRect(card.x + 1, card.y + 4, 2, card.height - 8);

		int y = card.y + pad;
		int bottom = card.y + card.height - pad - stepH;
		for (int i = 0; i < cut; i++)
		{
			Line l = lines.get(i);
			int lh = ink.height(l.font);
			if (y + l.gapBefore + lh > bottom)
			{
				ink.text(g, "...", ink.small, GREY, card.x + pad, y, Ink.Style.SHADOW);
				break;
			}
			y += l.gapBefore;
			ink.text(g, l.text, l.font, l.color, card.x + pad, y, Ink.Style.SHADOW);
			y += lh;
		}

		if (step != null)
		{
			y = bottom + step.gapBefore;
			g.setColor(BRONZE);
			g.fillRect(card.x + pad, y - 3, card.width - pad * 2, 1);
			for (String t : stepLines)
			{
				ink.text(g, t, step.font, step.color, card.x + pad, y, Ink.Style.SHADOW);
				y += ink.height(step.font);
			}
			if (dialRow)
			{
				int x = dialTokens(g, card.x + pad, y + 2, DialMath.plan(s.dials, ring.getCode()), ring.getCode());
				String label = "Show dials";
				int bw = ink.width(ink.small, label) + 12;
				Rectangle b = new Rectangle(Math.max(x + 8, card.x + card.width - pad - bw), y + 3, bw, 17);
				button(g, s, b, label, Hit.DIALS, "Show", "dials");
			}
		}
	}

	private void addWrapped(List<Line> lines, String text, Font f, Color c, int w, int gap)
	{
		boolean first = true;
		for (String t : ink.wrap(text, f, w))
		{
			lines.add(new Line(t, f, c, first ? gap : 0));
			first = false;
		}
	}

	/**
	 * Draws the per-dial plan as tokens: the target letter, then a rotate arrow and a count or a
	 * check mark. Returns the x after the last token.
	 */
	int dialTokens(Graphics2D g, int x, int y, int[] plan, String code)
	{
		for (int d = 0; d < 3; d++)
		{
			String letter = String.valueOf(code.charAt(d));
			ink.text(g, letter, ink.bold, Color.WHITE, x, y + 2, Ink.Style.SHADOW);
			x += ink.width(ink.bold, letter) + 4;
			int k = plan[d];
			if (k == 0)
			{
				AtlasPainter.drawCheck(g, x + 6, y + 9, 4.5, GOOD);
				x += 14;
			}
			else
			{
				AtlasPainter.drawRotateArrow(g, x + 7, y + 10, 5.5, DialMath.clockwise(k), HINT, 1.6f);
				x += 17;
				String n = "x" + DialMath.clicks(k);
				ink.text(g, n, ink.small, HINT, x, y + 3, Ink.Style.SHADOW);
				x += ink.width(ink.small, n);
			}
			x += 10;
		}
		return x;
	}

	/** Whether the card drawn last now covers its own ring (after a pan), which forces a re-layout. */
	private boolean focusCovered(Scene s)
	{
		java.awt.Point f = cardFocus(s);
		return f != null && layerCard != null && AtlasPainter.grow(layerCard, 12).contains(f);
	}

	/** The screen point of the ring the card describes, which the card must not cover. */
	private java.awt.Point cardFocus(Scene s)
	{
		Ring r = s.hovered != null ? s.hovered : s.repo.ring(s.selected);
		MapView v = s.view;
		if (r == null || !r.isMapped() || !v.getLayer().equals(r.getLayer()))
		{
			return null;
		}
		return new java.awt.Point((int) v.screenX(r.getX() + 0.5), (int) v.screenY(r.getY() + 0.5));
	}

	/**
	 * Finds a spot for a card: bottom-left (beside the panel), else bottom-right, top-right or
	 * top-left, clear of the holes and of the ring it describes. Failing that, it slides a corner
	 * spot off the holes, shrinking to at least minH.
	 */
	private Rectangle place(Scene s, int left, int w, int h, int minH, java.awt.Point focus)
	{
		MapView v = s.view;
		int top = v.getY() + BAR_H + (s.notice != null ? 18 : 0) + 6;
		int bottom = v.getY() + v.getH() - 6;
		int right = v.getX() + v.getW() - 6;
		if (w <= 40)
		{
			return null;
		}
		int[][] spots = {{left, 1}, {right - w, 1}, {right - w, 0}, {left, 0}};
		for (int[] spot : spots)
		{
			Rectangle r = new Rectangle(spot[0], spot[1] == 1 ? bottom - h : top, w, h);
			if (r.y >= top && clear(s, r, focus))
			{
				return r;
			}
		}
		Rectangle best = null;
		for (int pass = 0; pass < 2; pass++)
		{
			for (int[] spot : spots)
			{
				int x = spot[0];
				int lo = top;
				int hi = bottom;
				for (Rectangle hole : s.holes)
				{
					Rectangle gh = AtlasPainter.grow(hole, 6);
					if (gh.x < x + w && gh.x + gh.width > x)
					{
						if (spot[1] == 1 && gh.y < hi && gh.y + gh.height > lo)
						{
							hi = Math.min(hi, gh.y);
						}
						else if (spot[1] == 0 && gh.y < lo + h && gh.y + gh.height > lo)
						{
							lo = Math.max(lo, gh.y + gh.height);
						}
					}
				}
				int hh = Math.min(h, hi - lo);
				Rectangle r = new Rectangle(x, spot[1] == 1 ? hi - hh : lo, w, hh);
				if (hh < minH || (pass == 0 && !clear(s, r, focus)))
				{
					continue;
				}
				if (hh == h)
				{
					return r;
				}
				if (best == null || hh > best.height)
				{
					best = r;
				}
			}
			if (best != null)
			{
				return best;
			}
		}
		return null;
	}

	private static boolean clear(Scene s, Rectangle r, java.awt.Point focus)
	{
		for (Rectangle hole : s.holes)
		{
			if (AtlasPainter.grow(hole, 6).intersects(r))
			{
				return false;
			}
		}
		return focus == null || !AtlasPainter.grow(r, 12).contains(focus);
	}

	// ------------------------------------------------------------------ frame and holes

	private void paintFrame(Graphics2D g, Scene s)
	{
		MapView v = s.view;
		Rectangle m = v.rect();
		g.setStroke(new BasicStroke(1f));
		g.setColor(BRONZE);
		g.drawRect(m.x + 2, m.y + 2, m.width - 5, m.height - 5);
		g.setColor(FRAME);
		g.drawRect(m.x, m.y, m.width - 1, m.height - 1);
		g.drawRect(m.x + 1, m.y + 1, m.width - 3, m.height - 3);
	}

	/** Shadow outside the map; drawn before the map itself. */
	public void paintShadow(Graphics2D g, Scene s)
	{
		Rectangle m = s.view.rect();
		for (int i = 1; i <= 4; i++)
		{
			g.setColor(new Color(0, 0, 0, 70 - i * 15));
			g.drawRect(m.x - i, m.y - i, m.width - 1 + i * 2, m.height - 1 + i * 2);
		}
	}

	/** Frames the holes so the real buttons read as buttons on the map. */
	public void paintHoles(Graphics2D g, Scene s, boolean ready)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setStroke(new BasicStroke(1f));
		for (Rectangle h : s.holes)
		{
			g.setColor(new Color(0, 0, 0, 90));
			g.fillRect(h.x - 2, h.y + h.height + 2, h.width + 5, 3);
			g.fillRect(h.x + h.width + 2, h.y - 1, 3, h.height + 3);
			g.setColor(FRAME);
			g.drawRect(h.x - 1, h.y - 1, h.width + 1, h.height + 1);
			g.drawRect(h.x - 2, h.y - 2, h.width + 3, h.height + 3);
			g.setColor(BRONZE_LIGHT);
			g.drawRect(h.x - 3, h.y - 3, h.width + 5, h.height + 5);
			if (ready && h.equals(s.confirmHole))
			{
				double t = 0.5 + 0.5 * Math.sin(s.now / 160.0);
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(AtlasPainter.withAlpha(GOOD, (int) (120 + 135 * t)));
				g.setStroke(new BasicStroke(2.5f));
				g.drawRoundRect(h.x - 6, h.y - 6, h.width + 11, h.height + 11, 6, 6);
				g.setStroke(new BasicStroke(1f));
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			}
		}
	}

	// ------------------------------------------------------------------ glyphs

	static void triangle(Graphics2D g, double cx, double cy, double r, boolean pointRight, Color c)
	{
		Path2D p = new Path2D.Double();
		double d = pointRight ? 1 : -1;
		p.moveTo(cx + d * r, cy);
		p.lineTo(cx - d * r * 0.7, cy - r);
		p.lineTo(cx - d * r * 0.7, cy + r);
		p.closePath();
		g.setColor(c);
		g.fill(p);
	}

	/** A small up (dir -1) or down (dir 1) scroll hint. */
	private static void chevron(Graphics2D g, double cx, double cy, int dir)
	{
		Path2D p = new Path2D.Double();
		p.moveTo(cx, cy + 2 * dir);
		p.lineTo(cx + 4, cy - 2 * dir);
		p.lineTo(cx - 4, cy - 2 * dir);
		p.closePath();
		g.setColor(CREAM);
		g.fill(p);
	}

	/** A right-pointing arrow of the given length starting at (x, y). */
	static void arrow(Graphics2D g, double x, double y, double len, Color c)
	{
		g.setColor(c);
		g.setStroke(new BasicStroke(1.4f));
		g.draw(new java.awt.geom.Line2D.Double(x, y, x + len - 2, y));
		Path2D p = new Path2D.Double();
		p.moveTo(x + len, y);
		p.lineTo(x + len - 4, y - 3);
		p.lineTo(x + len - 4, y + 3);
		p.closePath();
		g.fill(p);
	}

	private static Rectangle clipTo(Rectangle r, Rectangle bounds)
	{
		return r.intersection(bounds);
	}
}
