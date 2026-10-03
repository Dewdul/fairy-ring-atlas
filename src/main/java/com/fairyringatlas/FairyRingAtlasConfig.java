/*
 * Copyright (c) 2026, Dewdul
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringatlas;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(FairyRingAtlasConfig.GROUP)
public interface FairyRingAtlasConfig extends Config
{
	String GROUP = "fairyringatlas";

	enum StartView
	{
		FIT_ALL,
		AROUND_YOU,
		REMEMBER,
	}

	@ConfigSection(name = "Map", description = "Size and contents of the map", position = 0)
	String mapSection = "map";

	@ConfigSection(name = "Markers", description = "How rings are drawn", position = 1)
	String markerSection = "markers";

	@ConfigSection(name = "Travel log", description = "Help with the travel log and the dials", position = 2)
	String logSection = "travelLog";

	@ConfigSection(name = "Advanced", description = "Other options", position = 3, closedByDefault = true)
	String advancedSection = "advanced";

	@ConfigItem(keyName = "openInMapMode", name = "Open as map",
		description = "Show the map over the dials when you use a fairy ring; otherwise start with the dials",
		section = mapSection, position = 0)
	default boolean openInMapMode()
	{
		return true;
	}

	@Range(min = 512, max = 2000)
	@ConfigItem(keyName = "maxWidth", name = "Max width",
		description = "Largest map width in pixels (resizable mode)", section = mapSection, position = 1)
	default int maxWidth()
	{
		return 1100;
	}

	@Range(min = 334, max = 1400)
	@ConfigItem(keyName = "maxHeight", name = "Max height",
		description = "Largest map height in pixels (resizable mode)", section = mapSection, position = 2)
	default int maxHeight()
	{
		return 720;
	}

	@ConfigItem(keyName = "startView", name = "Start view",
		description = "Fit all rings, centre on the ring you are standing at, or remember the last view this session",
		section = mapSection, position = 3)
	default StartView startView()
	{
		return StartView.FIT_ALL;
	}

	@ConfigItem(keyName = "placeLabels", name = "Place names",
		description = "Show world map place names", section = mapSection, position = 4)
	default boolean placeLabels()
	{
		return true;
	}

	@ConfigItem(keyName = "mapIcons", name = "Map icons",
		description = "Show world map icons when zoomed in", section = mapSection, position = 5)
	default boolean mapIcons()
	{
		return true;
	}

	@ConfigItem(keyName = "autoFitSearch", name = "Fit to search",
		description = "Zoom the map to the rings matching the travel log search", section = mapSection, position = 6)
	default boolean autoFitSearch()
	{
		return true;
	}

	@ConfigItem(keyName = "codeLabels", name = "Code labels",
		description = "Which rings show their code beside the marker", section = markerSection, position = 0)
	default Scene.CodeLabels codeLabels()
	{
		return Scene.CodeLabels.ALL;
	}

	@ConfigItem(keyName = "dimUnvisited", name = "Dim unvisited",
		description = "Draw rings that are not in your travel log at half opacity", section = markerSection, position = 1)
	default boolean dimUnvisited()
	{
		return false;
	}

	@ConfigItem(keyName = "visitedColor", name = "Visited colour",
		description = "Colour of rings in your travel log", section = markerSection, position = 2)
	default Color visitedColor()
	{
		return new Color(0x3FD9C8);
	}

	@ConfigItem(keyName = "selectedColor", name = "Selected colour",
		description = "Colour of the selected ring and its travel log row", section = markerSection, position = 3)
	default Color selectedColor()
	{
		return new Color(0xFF981F);
	}

	@ConfigItem(keyName = "favouriteColor", name = "Favourite colour",
		description = "Colour of the favourite star", section = markerSection, position = 4)
	default Color favouriteColor()
	{
		return new Color(0xFFD700);
	}

	@ConfigItem(keyName = "filterTravelLog", name = "Filter travel log",
		description = "Narrow the travel log to the selected ring, moved to the top", section = logSection, position = 0)
	default boolean filterTravelLog()
	{
		return true;
	}

	@ConfigItem(keyName = "dialGuidance", name = "Dial guidance",
		description = "In dial mode, show which way to turn each dial for the selected ring", section = logSection, position = 1)
	default boolean dialGuidance()
	{
		return true;
	}

	@ConfigItem(keyName = "clueHelper", name = "Clue helper",
		description = "Mark the active clue step on the map and preselect fairy ring clue codes. Needs the core Clue Scroll plugin to be enabled", section = advancedSection, position = 0)
	default boolean clueHelper()
	{
		return true;
	}
}
