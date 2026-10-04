/*
 * Copyright (c) 2026, Dewdul
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringatlas;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;

/**
 * One entry of rings.json. Gson fills the fields; the repository then patches the position and
 * the travel-log row from the game's own DB table.
 */
@Getter
public class Ring
{
	public static final String KIND_DESTINATION = "destination";

	private String code;
	private String kind = KIND_DESTINATION;
	private String name;
	private String description;
	@Setter
	private int x;
	@Setter
	private int y;
	@Setter
	private int plane;
	@Setter
	private String layer;
	@Setter
	private String area;
	private List<String> requirements;
	private List<String> danger;
	private List<String> poi;
	private List<String> tags;
	private List<String> notes;
	private int logVarbit = -1;
	private boolean noStaffReturn;
	/** What must hold before a first visit can unlock the ring (UnlockCheck); null when nothing. */
	private List<Condition> unlock;

	/** Packed widget id of this code's travel log row, from the DB table; -1 until read. */
	@Setter
	private transient int rowComponent = -1;
	/** Packed widget id of the star beside the row; -1 until read. */
	@Setter
	private transient int starComponent = -1;
	private transient String haystack;

	public Ring()
	{
	}

	Ring(String code, String name, int x, int y, String layer)
	{
		this.code = code;
		this.name = name;
		this.x = x;
		this.y = y;
		this.layer = layer;
	}

	/** A real dial target: a destination with a three-letter code. */
	public boolean isDialable()
	{
		return KIND_DESTINATION.equals(kind) && DialMath.isCode(code);
	}

	/** Whether the ring has a place on some map layer (everything but the house). */
	public boolean isMapped()
	{
		return layer != null && !Layer.POH.equals(layer) && (x != 0 || y != 0);
	}

	public String getName()
	{
		return name == null ? (code == null ? "?" : code) : name;
	}

	public List<String> getRequirements()
	{
		return nonNull(requirements);
	}

	public List<String> getDanger()
	{
		return nonNull(danger);
	}

	public List<String> getPoi()
	{
		return nonNull(poi);
	}

	public List<String> getNotes()
	{
		return nonNull(notes);
	}

	public List<Condition> getUnlock()
	{
		return unlock == null ? Collections.emptyList() : unlock;
	}

	/**
	 * One unlock condition from rings.json: a quest state, a varbit or varp comparison, or a need
	 * the client cannot see ("unknown"). The label reads after "needs".
	 */
	@Getter
	public static class Condition
	{
		private String type;
		private String quest;
		private String state;
		private int id;
		private String op;
		private int value;
		private String label;

		public Condition()
		{
		}

		Condition(String type, String quest, String state, int id, String op, int value, String label)
		{
			this.type = type;
			this.quest = quest;
			this.state = state;
			this.id = id;
			this.op = op;
			this.value = value;
			this.label = label;
		}
	}

	/**
	 * Case-insensitive search over code (with or without spaces), name, area, POIs, tags and
	 * description.
	 */
	public boolean matches(String query)
	{
		if (query == null || query.isEmpty())
		{
			return true;
		}
		if (haystack == null)
		{
			StringBuilder sb = new StringBuilder();
			if (code != null)
			{
				sb.append(code).append('\n').append(DialMath.spaced(code)).append('\n');
			}
			append(sb, name);
			append(sb, area);
			append(sb, description);
			for (String s : getPoi())
			{
				append(sb, s);
			}
			for (String s : nonNull(tags))
			{
				append(sb, s);
			}
			haystack = sb.toString().toLowerCase(Locale.ROOT);
		}
		return haystack.contains(query.toLowerCase(Locale.ROOT).trim());
	}

	void resetSearchCache()
	{
		haystack = null;
	}

	private static void append(StringBuilder sb, String s)
	{
		if (s != null)
		{
			sb.append(s).append('\n');
		}
	}

	private static List<String> nonNull(List<String> list)
	{
		return list == null ? Collections.emptyList() : list;
	}
}
