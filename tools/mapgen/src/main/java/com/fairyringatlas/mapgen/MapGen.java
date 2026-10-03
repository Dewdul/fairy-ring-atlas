/*
 * Copyright (c) 2026, Dewdul
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.fairyringatlas.mapgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.runelite.cache.AreaManager;
import net.runelite.cache.MapImageDumper;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.WorldMapManager;
import net.runelite.cache.definitions.AreaDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.WorldMapElementDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Position;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.XteaKeyManager;

/**
 * Step 1 of the map pipeline (step 2 is mapgen.py).
 *
 * <p>Renders one 256x256 PNG per requested 64x64 region of plane 0 with
 * RuneLite's {@link MapImageDumper} (4 px per game tile, icons and labels off),
 * and exports the cache's world-map place labels and map-function icons as
 * raw JSON for the Python post-processor to filter, quantise and index.
 *
 * <p>Arguments: --cache DIR --keys FILE --regions FILE --out DIR.
 * The regions file is {"regions": [[rx, ry], ...]} and is written by
 * {@code mapgen.py plan}.
 *
 * <p>Output layout ({@code --out}):
 * <ul>
 * <li>{@code 2/<rx>_<ry>.png}: raw RGB render; row 0 is the north edge.</li>
 * <li>{@code missing.json}: requested regions that have no map data.</li>
 * <li>{@code labels.json}: [{t, x, y, z, s, c}] every named world-map element.</li>
 * <li>{@code icons.json}: [{x, y, z, sprite, src}] map-function icons from map
 * locations (src "loc") and from unnamed world-map elements (src "element").</li>
 * </ul>
 */
public class MapGen
{
	private static final int PLANE = 0;

	public static void main(String[] args) throws IOException
	{
		Map<String, String> opts = new LinkedHashMap<>();
		for (int i = 0; i + 1 < args.length; i += 2)
		{
			opts.put(args[i].replaceFirst("^--", ""), args[i + 1]);
		}
		File cacheDir = new File(require(opts, "cache"));
		File outDir = new File(require(opts, "out"));
		File regionsFile = new File(require(opts, "regions"));
		String keysPath = opts.get("keys");

		Gson gson = new GsonBuilder().disableHtmlEscaping().create();

		XteaKeyManager keys = new XteaKeyManager();
		if (keysPath != null && new File(keysPath).isFile())
		{
			try (InputStream in = new FileInputStream(keysPath))
			{
				keys.loadKeys(in);
			}
		}

		List<int[]> wanted = new ArrayList<>();
		try (Reader r = Files.newBufferedReader(regionsFile.toPath(), StandardCharsets.UTF_8))
		{
			JsonObject root = gson.fromJson(r, JsonObject.class);
			for (JsonElement e : root.getAsJsonArray("regions"))
			{
				JsonArray a = e.getAsJsonArray();
				wanted.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()});
			}
		}

		File tileDir = new File(outDir, "2");
		tileDir.mkdirs();

		try (Store store = new Store(cacheDir))
		{
			store.load();

			RegionLoader regionLoader = new RegionLoader(store, keys);
			MapImageDumper dumper = new MapImageDumper(store, regionLoader);
			dumper.setRenderIcons(false);
			dumper.setRenderLabels(false);
			dumper.setTransparency(false);
			dumper.setLowMemory(false);
			dumper.load(); // also calls regionLoader.loadRegions() + calculateBounds()

			System.out.println("Regions in cache: " + regionLoader.getRegions().size());

			JsonArray missing = new JsonArray();
			int rendered = 0;
			for (int[] rc : wanted)
			{
				Region region = regionLoader.findRegionForRegionCoordinates(rc[0], rc[1]);
				if (region == null)
				{
					JsonArray m = new JsonArray();
					m.add(rc[0]);
					m.add(rc[1]);
					missing.add(m);
					continue;
				}
				BufferedImage img = dumper.drawRegion(region, PLANE);
				ImageIO.write(img, "png", new File(tileDir, rc[0] + "_" + rc[1] + ".png"));
				if (++rendered % 100 == 0)
				{
					System.out.println("  rendered " + rendered + " / " + wanted.size());
				}
			}
			System.out.println("Rendered " + rendered + " regions, " + missing.size() + " requested regions have no map data");
			write(gson, missing, new File(outDir, "missing.json"));

			// ---- labels and icons ----
			AreaManager areas = new AreaManager(store);
			areas.load();
			WorldMapManager worldMap = new WorldMapManager(store);
			worldMap.load();
			ObjectManager objects = new ObjectManager(store);
			objects.load();

			JsonArray labels = new JsonArray();
			JsonArray icons = new JsonArray();
			for (WorldMapElementDefinition element : worldMap.getElements())
			{
				AreaDefinition area = areas.getArea(element.getAreaDefinitionId());
				Position p = element.getWorldPosition();
				if (area == null || p == null)
				{
					continue;
				}
				if (area.getName() != null)
				{
					JsonObject l = new JsonObject();
					l.addProperty("t", area.getName());
					l.addProperty("x", p.getX());
					l.addProperty("y", p.getY());
					l.addProperty("z", p.getZ());
					l.addProperty("s", area.getTextScale());
					l.addProperty("c", String.format("#%06x", area.getTextColor() & 0xFFFFFF));
					l.addProperty("area", area.getId());
					l.addProperty("category", area.getCategory());
					labels.add(l);
				}
				else if (area.getSpriteId() >= 0)
				{
					icons.add(icon(p, area, "element"));
				}
			}

			for (Region region : regionLoader.getRegions())
			{
				for (Location loc : region.getLocations())
				{
					ObjectDefinition od = objects.getObject(loc.getId());
					if (od == null || od.getMapAreaId() == -1)
					{
						continue;
					}
					AreaDefinition area = areas.getArea(od.getMapAreaId());
					if (area == null || area.getSpriteId() < 0)
					{
						continue;
					}
					icons.add(icon(loc.getPosition(), area, "loc"));
				}
			}
			System.out.println("Exported " + labels.size() + " labels and " + icons.size() + " icons (all planes, whole world)");
			write(gson, labels, new File(outDir, "labels.json"));
			write(gson, icons, new File(outDir, "icons.json"));
		}
	}

	private static JsonObject icon(Position p, AreaDefinition area, String src)
	{
		JsonObject o = new JsonObject();
		o.addProperty("x", p.getX());
		o.addProperty("y", p.getY());
		o.addProperty("z", p.getZ());
		o.addProperty("sprite", area.getSpriteId());
		o.addProperty("area", area.getId());
		o.addProperty("src", src);
		return o;
	}

	private static void write(Gson gson, JsonElement e, File f) throws IOException
	{
		try (Writer w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8))
		{
			gson.toJson(e, w);
		}
	}

	private static String require(Map<String, String> opts, String key)
	{
		String v = opts.get(key);
		if (v == null)
		{
			throw new IllegalArgumentException("missing --" + key);
		}
		return v;
	}
}
