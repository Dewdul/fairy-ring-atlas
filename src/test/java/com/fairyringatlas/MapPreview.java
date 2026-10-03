/*
 * Copyright (c) 2026, Dewdul
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringatlas;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Renders the map offline, without a game client, from the real bundled resources into
 * build/preview/*.png so the drawing can be checked by eye. Run through MapPreviewTest with the
 * environment variable FRA_PREVIEW=1, or as a main from an IDE.
 */
public class MapPreview
{
	private static final String BASE = FairyRingAtlasPlugin.RESOURCES;

	public static void main(String[] args) throws IOException
	{
		File out = new File(args.length > 0 ? args[0] : "build/preview");
		if (!out.isDirectory() && !out.mkdirs())
		{
			throw new IOException("cannot create " + out);
		}
		RingRepository repo = RingRepository.load(new Gson(), BASE);
		Set<String> visited = new HashSet<>();
		for (Ring r : repo.dialable())
		{
			if (!Arrays.asList("BLQ", "CJQ", "AIS", "DLP", "CKQ", "AJP", "BJP").contains(r.getCode()))
			{
				visited.add(r.getCode());
			}
		}
		Map<String, Integer> faves = new HashMap<>();
		faves.put("AIQ", 0);
		faves.put("CKS", 1);
		faves.put("DKR", 2);
		repo.setState(visited, faves, "DKR");

		AtlasPainter painter = new AtlasPainter();
		Layer surface = repo.surface();
		Rectangle big = new Rectangle(30, 30, 1100, 720);
		Rectangle fixed = new Rectangle(30, 30, 512, 334);

		java.awt.Insets wide = FairyRingAtlasPlugin.chromeInsets(false, false);
		java.awt.Insets narrow = FairyRingAtlasPlugin.chromeInsets(true, false);
		MapView fit = FairyRingAtlasPlugin.fitRings(MapView.of(surface, big), repo.ringsIn(Layer.SURFACE), 0, wide);
		render(repo, painter, out, "1-fit", fit, "AIQ", null, new int[]{0, 1, 2}, false);
		render(repo, painter, out, "2-lumbridge-4ppt", MapView.of(surface, big).focusOn(3222.5, 3218.5, 4, wide), "CLP", "DIS", new int[]{3, 2, 2}, false);
		render(repo, painter, out, "3-16ppt", MapView.of(surface, big).focusOn(2996.5, 3114.5, 16, wide), "AIQ", null, DialMath.values("AIQ"), false);
		Layer zanaris = repo.layer("zanaris");
		if (zanaris != null)
		{
			render(repo, painter, out, "4-zanaris", FairyRingAtlasPlugin.fitLayer(zanaris, big, wide), "BKS", null, new int[]{0, 0, 0}, false);
		}
		render(repo, painter, out, "5-fixed-fit", FairyRingAtlasPlugin.fitRings(MapView.of(surface, fixed), repo.ringsIn(Layer.SURFACE), 0, narrow),
			"AIS", null, new int[]{1, 3, 0}, true);
		render(repo, painter, out, "6-kandarin-1ppt", MapView.of(surface, big).focusOn(2620, 3350, 1.25, wide), "CKS", "ALS", new int[]{2, 2, 2}, false);
		ringChecks(repo, painter, out);
		icons(repo, painter, out);
	}

	/**
	 * The hub's 48x72 icon.png (build/preview/icon.png, copied to the repo root by hand): a crop of
	 * the bundled Zanaris imagery with one fairy ring marker in the plugin's own style, drawn large.
	 */
	private static void icons(RingRepository repo, AtlasPainter painter, File out) throws IOException
	{
		// name, ring, ppt, marker flags, marker scale, marker colour (null: the visited colour)
		Object[][] picks = {
			{"icon", "BKS", 1.5, AtlasPainter.VISITED | AtlasPainter.HOVER, 1.9, new Color(0xFF981F)},
		};
		TileStore tiles = new TileStore(repo.getIndex(), repo.getLayers(), BASE, Runnable::run);
		for (Object[] p : picks)
		{
			Ring r = repo.ring((String) p[1]);
			Layer layer = repo.layer(r.getLayer());
			Rectangle rect = new Rectangle(0, 0, 48, 72);
			MapView v = MapView.of(layer, rect).centerOn(r.getX() + 0.5, r.getY() + 0.5, (Double) p[2]);
			BufferedImage img = new BufferedImage(48, 72, BufferedImage.TYPE_INT_RGB);
			MapRenderer.render(img, v, tiles, layer.getBackgroundColor());
			Graphics2D g = img.createGraphics();
			Scene s = new Scene();
			s.repo = repo;
			s.view = v;
			s.codeLabels = Scene.CodeLabels.NONE;
			s.placeLabels = false;
			s.mapIcons = false;
			if (p[5] != null)
			{
				s.visitedColor = (Color) p[5];
			}
			// the neighbouring rings at their normal size give the icon its map context
			for (Ring other : repo.mappedIn(layer.getId()))
			{
				if (other != r && other.isDialable())
				{
					BufferedImage m = painter.marker(AtlasPainter.VISITED, AtlasPainter.radius(v.getPpt(), false), s);
					g.drawImage(m, (int) Math.round(v.screenX(other.getX() + 0.5) - m.getWidth() / 2.0),
						(int) Math.round(v.screenY(other.getY() + 0.5) - m.getHeight() / 2.0), null);
				}
			}
			g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE);
			g.translate(v.screenX(r.getX() + 0.5), v.screenY(r.getY() + 0.5));
			g.scale((Double) p[4], (Double) p[4]);
			painter.drawMarker(g, 0, 0, 5.5, (Integer) p[3], s);
			g.dispose();
			ImageIO.write(img, "png", new File(out, p[0] + ".png"));
		}
		tiles.close();
	}

	/** Rings whose marker must sit on the right spot of the imagery; one close-up each, ring at the centre. */
	static final List<String> CHECKS = Arrays.asList("AIQ", "BKS", "CKS", "AJR", "DKS", "ALS", "BLP", "DLS", "CIS", "DIP");
	static final int CHECK_SIZE = 240;
	static final double CHECK_PPT = 8;

	/** Writes 7-checks.png: 8 ppt close-ups, each centred on one ring of {@link #CHECKS}. */
	private static void ringChecks(RingRepository repo, AtlasPainter painter, File out) throws IOException
	{
		TileStore tiles = new TileStore(repo.getIndex(), repo.getLayers(), BASE, Runnable::run);
		int n = CHECKS.size();
		int cols = 5;
		int rows = (n + cols - 1) / cols;
		BufferedImage sheet = new BufferedImage(cols * (CHECK_SIZE + 8) + 8, rows * (CHECK_SIZE + 8) + 8, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = sheet.createGraphics();
		g.setColor(new Color(0x2a2620));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		for (int i = 0; i < n; i++)
		{
			Ring r = repo.ring(CHECKS.get(i));
			Layer layer = repo.layer(r.getLayer());
			Rectangle rect = new Rectangle(8 + (i % cols) * (CHECK_SIZE + 8), 8 + (i / cols) * (CHECK_SIZE + 8), CHECK_SIZE, CHECK_SIZE);
			MapView v = MapView.of(layer, rect).centerOn(r.getX() + 0.5, r.getY() + 0.5, CHECK_PPT);
			BufferedImage base = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_INT_RGB);
			MapRenderer.render(base, v, tiles, layer.getBackgroundColor());
			Scene s = new Scene();
			s.repo = repo;
			s.view = v;
			s.dials = new int[]{0, 0, 0};
			Shape clip = g.getClip();
			g.clip(rect);
			g.drawImage(base, rect.x, rect.y, null);
			painter.paintMap(g, s);
			g.setClip(clip);
		}
		g.dispose();
		ImageIO.write(sheet, "png", new File(out, "7-checks.png"));
		tiles.close();
	}

	private static void render(RingRepository repo, AtlasPainter painter, File out, String name, MapView v,
		String selected, String hovered, int[] dials, boolean fixedMode) throws IOException
	{
		TileStore tiles = new TileStore(repo.getIndex(), repo.getLayers(), BASE, Runnable::run);
		ChromePainter chrome = new ChromePainter(painter.ink());
		Rectangle rect = v.rect();
		// the dial interface is centred in the map; its Teleport and close buttons are holes
		int dx = fixedMode ? rect.x : rect.x + rect.width / 2 - 256;
		int dy = fixedMode ? rect.y : rect.y + rect.height / 2 - 167;
		Rectangle confirm = new Rectangle(dx + 172, dy + 254, 169, 53);
		Rectangle close = new Rectangle(dx + 476, dy + 10, 26, 23);

		Scene s = new Scene();
		s.repo = repo;
		s.view = v;
		s.holes.add(confirm);
		s.holes.add(close);
		s.confirmHole = confirm;
		s.selected = selected;
		s.hovered = repo.ring(hovered);
		s.here = "DKR";
		s.dials = dials;
		s.now = 400;
		s.panelCollapsed = fixedMode;
		s.clueX = 3110;
		s.clueY = 3165;
		s.rowVisible = true;

		BufferedImage canvas = new BufferedImage(rect.x * 2 + rect.width, rect.y * 2 + rect.height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = canvas.createGraphics();
		g.setColor(new Color(0x2a2620));
		g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
		g.setColor(new Color(0x404640));
		g.fillRect(dx, dy, 512, 334);
		g.setColor(new Color(0x6b5a40));
		g.fill(confirm);
		g.setColor(Color.WHITE);
		g.drawString("Teleport to this location", confirm.x + 18, confirm.y + 30);
		g.setColor(new Color(0x8a2a1a));
		g.fill(close);

		long t0 = System.nanoTime();
		BufferedImage base = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_INT_RGB);
		Layer layer = repo.layer(v.getLayer());
		MapRenderer.render(base, v, tiles, layer.getBackgroundColor());
		long t1 = System.nanoTime();
		// a second build with every tile cached: the cost of a rebuild while panning
		MapRenderer.render(base, v, tiles, layer.getBackgroundColor());
		long t2 = System.nanoTime();

		chrome.layout(s);
		chrome.paintShadow(g, s);
		Shape clip = g.getClip();
		Area a = new Area(rect);
		a.subtract(new Area(confirm));
		a.subtract(new Area(close));
		g.clip(a);
		g.drawImage(base, rect.x, rect.y, null);
		painter.paintMap(g, s);
		chrome.paint(g, s);
		g.setClip(clip);
		chrome.paintHoles(g, s, s.dialsMatch(selected));
		long t3 = System.nanoTime();
		// steady-state frame: blit plus overlays with layouts and sprites cached
		for (int i = 0; i < 20; i++)
		{
			s.hits.clear();
			chrome.layout(s);
			g.setClip(a);
			g.drawImage(base, rect.x, rect.y, null);
			painter.paintMap(g, s);
			chrome.paint(g, s);
			g.setClip(clip);
			chrome.paintHoles(g, s, s.dialsMatch(selected));
		}
		long t4 = System.nanoTime();
		long[] parts = new long[3];
		for (int i = 0; i < 20; i++)
		{
			s.hits.clear();
			chrome.layout(s);
			long p0 = System.nanoTime();
			g.setClip(a);
			g.drawImage(base, rect.x, rect.y, null);
			long p1 = System.nanoTime();
			painter.paintMap(g, s);
			long p2 = System.nanoTime();
			chrome.paint(g, s);
			long p3 = System.nanoTime();
			parts[0] += p1 - p0;
			parts[1] += p2 - p1;
			parts[2] += p3 - p2;
			g.setClip(clip);
			chrome.paintHoles(g, s, s.dialsMatch(selected));
		}
		System.out.printf("   blit=%.2f map=%.2f chrome=%.2f ms%n", parts[0] / 20e6, parts[1] / 20e6, parts[2] / 20e6);
		g.dispose();
		ImageIO.write(canvas, "png", new File(out, name + ".png"));
		System.out.printf("%-18s ppt=%6.3f z=%d  base(cold)=%6.1f ms  base(warm)=%5.1f ms  first paint=%5.1f ms  frame=%5.2f ms  hits=%d%n",
			name, v.getPpt(), tiles.levelFor(v.getPpt()), (t1 - t0) / 1e6, (t2 - t1) / 1e6, (t3 - t2) / 1e6, (t4 - t3) / 20e6, s.hits.size());
		tiles.close();
	}

	static List<String> names()
	{
		return Arrays.asList("1-fit", "2-lumbridge-4ppt", "3-16ppt", "4-zanaris", "5-fixed-fit", "6-kandarin-1ppt", "7-checks");
	}
}
