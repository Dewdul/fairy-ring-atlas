/*
 * Copyright (c) 2026, Dewdul
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringatlas;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Value;

/**
 * The quick-click groups of the map's Groups panel: the prebuilt ones from groups.json (Slayer,
 * Farming) and the player's own. Prebuilt groups stay editable; a saved group keeps only what
 * differs from its default, so an untouched one follows the bundled data and "Reset to default"
 * just forgets the changes. Pure: the plugin keeps one instance on the client thread and saves
 * {@link #format} after each change.
 */
final class RingGroups
{
	static final int NAME_MAX = 24;
	static final int GROUPS_MAX = 30;

	/** A code in groups.json, with an optional short display hint. */
	static final class Code
	{
		String code;
		String note;
	}

	/** A prebuilt group in groups.json. */
	static final class Def
	{
		String id;
		String name;
		List<Code> codes;
	}

	static final class DefFile
	{
		List<Def> groups;
	}

	/** The saved form: the groups in order, and every prebuilt id ever offered (so a deleted one stays deleted). */
	private static final class Saved
	{
		List<Stored> groups;
		List<String> seen;
	}

	/** A saved group; for a prebuilt one a null name or codes means "as the default". */
	private static final class Stored
	{
		String id;
		String name;
		List<String> codes;
		Boolean collapsed;

		Stored(String id)
		{
			this.id = id;
		}
	}

	/** A group as the panel shows it. */
	@Value
	static class Group
	{
		String id;
		String name;
		List<String> codes;
		boolean collapsed;
		boolean prebuilt;
		boolean modified;
	}

	private final List<Def> defaults;
	private final List<Stored> groups = new ArrayList<>();
	private final Set<String> seen = new HashSet<>();

	private RingGroups(List<Def> defaults)
	{
		this.defaults = defaults;
	}

	/** The prebuilt groups from groups.json, cleaned up: valid codes only, no duplicates. */
	static List<Def> defaults(DefFile file)
	{
		List<Def> out = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		if (file == null || file.groups == null)
		{
			return out;
		}
		for (Def d : file.groups)
		{
			if (d == null || d.id == null || d.id.isEmpty() || !ids.add(d.id))
			{
				continue;
			}
			Def c = new Def();
			c.id = d.id;
			c.name = cleanName(d.name) == null ? d.id : cleanName(d.name);
			c.codes = new ArrayList<>();
			Set<String> codes = new HashSet<>();
			for (Code code : d.codes == null ? Collections.<Code>emptyList() : d.codes)
			{
				String n = code == null ? null : DialMath.normalize(code.code);
				if (n != null && codes.add(n))
				{
					Code k = new Code();
					k.code = n;
					k.note = code.note;
					c.codes.add(k);
				}
			}
			out.add(c);
		}
		return out;
	}

	/** The groups from a saved string; the prebuilt ones alone when nothing (or nothing readable) is saved. */
	static RingGroups parse(Gson gson, List<Def> defaults, String json)
	{
		RingGroups g = new RingGroups(defaults);
		Saved saved = null;
		if (json != null && !json.trim().isEmpty())
		{
			try
			{
				saved = gson.fromJson(json, Saved.class);
			}
			catch (JsonParseException e)
			{
				saved = null;
			}
		}
		if (saved != null && saved.groups != null)
		{
			Set<String> ids = new HashSet<>();
			for (Stored s : saved.groups)
			{
				if (s == null || s.id == null || s.id.isEmpty() || !ids.add(s.id) || g.groups.size() >= GROUPS_MAX)
				{
					continue;
				}
				Def d = g.def(s.id);
				Stored c = new Stored(s.id);
				c.name = cleanName(s.name);
				c.codes = s.codes == null ? null : cleanCodes(s.codes);
				c.collapsed = Boolean.TRUE.equals(s.collapsed) ? Boolean.TRUE : null;
				if (d == null && c.codes == null)
				{
					// a prebuilt group no longer bundled, never changed: nothing left to show
					continue;
				}
				if (d == null && c.name == null)
				{
					c.name = s.id;
				}
				g.groups.add(c);
				g.simplify(c);
			}
			if (saved.seen != null)
			{
				g.seen.addAll(saved.seen);
			}
		}
		// prebuilt groups never offered before (new installs, or added by an update) join at the end
		for (Def d : defaults)
		{
			if (g.seen.add(d.id) && g.find(d.id) == null)
			{
				g.groups.add(new Stored(d.id));
			}
		}
		return g;
	}

	String format(Gson gson)
	{
		Saved s = new Saved();
		s.groups = groups;
		s.seen = new ArrayList<>(seen);
		Collections.sort(s.seen);
		return gson.toJson(s);
	}

	/** The groups in panel order. */
	List<Group> view()
	{
		List<Group> out = new ArrayList<>(groups.size());
		for (Stored s : groups)
		{
			boolean prebuilt = def(s.id) != null;
			out.add(new Group(s.id, name(s), Collections.unmodifiableList(new ArrayList<>(codes(s))),
				Boolean.TRUE.equals(s.collapsed), prebuilt, prebuilt && (s.name != null || s.codes != null)));
		}
		return out;
	}

	/** The prebuilt groups the player deleted, which "Restore" can bring back. */
	List<Def> deleted()
	{
		List<Def> out = new ArrayList<>();
		for (Def d : defaults)
		{
			if (find(d.id) == null)
			{
				out.add(d);
			}
		}
		return out;
	}

	/** The bundled hint for a code in a prebuilt group, or null. */
	String note(String id, String code)
	{
		Def d = def(id);
		if (d != null && code != null)
		{
			for (Code c : d.codes)
			{
				if (code.equals(c.code))
				{
					return c.note == null || c.note.trim().isEmpty() ? null : c.note.trim();
				}
			}
		}
		return null;
	}

	boolean contains(String id, String code)
	{
		Stored s = find(id);
		return s != null && codes(s).contains(code);
	}

	boolean add(String id, String code)
	{
		Stored s = find(id);
		String c = DialMath.normalize(code);
		if (s == null || c == null || codes(s).contains(c))
		{
			return false;
		}
		List<String> list = new ArrayList<>(codes(s));
		list.add(c);
		s.codes = list;
		simplify(s);
		return true;
	}

	boolean remove(String id, String code)
	{
		Stored s = find(id);
		if (s == null || !codes(s).contains(code))
		{
			return false;
		}
		List<String> list = new ArrayList<>(codes(s));
		list.remove(code);
		s.codes = list;
		simplify(s);
		return true;
	}

	/** Moves a code to a position within its group. */
	boolean move(String id, String code, int index)
	{
		Stored s = find(id);
		if (s == null || !codes(s).contains(code))
		{
			return false;
		}
		List<String> list = new ArrayList<>(codes(s));
		list.remove(code);
		list.add(Math.max(0, Math.min(index, list.size())), code);
		s.codes = list;
		simplify(s);
		return true;
	}

	boolean rename(String id, String name)
	{
		Stored s = find(id);
		String n = cleanName(name);
		if (s == null || n == null)
		{
			return false;
		}
		s.name = n;
		simplify(s);
		return true;
	}

	boolean delete(String id)
	{
		return groups.remove(find(id));
	}

	boolean toggleCollapsed(String id)
	{
		Stored s = find(id);
		if (s == null)
		{
			return false;
		}
		s.collapsed = Boolean.TRUE.equals(s.collapsed) ? null : Boolean.TRUE;
		return true;
	}

	/** A prebuilt group back to its bundled name and codes. */
	boolean reset(String id)
	{
		Stored s = find(id);
		if (s == null || def(id) == null)
		{
			return false;
		}
		s.name = null;
		s.codes = null;
		return true;
	}

	/** A deleted prebuilt group back, as bundled, at the end. */
	boolean restore(String id)
	{
		if (def(id) == null || find(id) != null)
		{
			return false;
		}
		groups.add(new Stored(id));
		return true;
	}

	/** A new group of the player's own, holding the code when given; returns its id, or null. */
	String create(String name, String code)
	{
		String n = cleanName(name);
		if (n == null || groups.size() >= GROUPS_MAX)
		{
			return null;
		}
		int i = 1;
		while (find("u" + i) != null || def("u" + i) != null)
		{
			i++;
		}
		Stored s = new Stored("u" + i);
		s.name = n;
		s.codes = new ArrayList<>();
		String c = DialMath.normalize(code);
		if (c != null)
		{
			s.codes.add(c);
		}
		groups.add(s);
		return s.id;
	}

	// ------------------------------------------------------------------ helpers

	private Stored find(String id)
	{
		for (Stored s : groups)
		{
			if (s.id.equals(id))
			{
				return s;
			}
		}
		return null;
	}

	private Def def(String id)
	{
		for (Def d : defaults)
		{
			if (d.id.equals(id))
			{
				return d;
			}
		}
		return null;
	}

	private String name(Stored s)
	{
		Def d = def(s.id);
		return s.name != null ? s.name : d != null ? d.name : s.id;
	}

	private List<String> codes(Stored s)
	{
		if (s.codes != null)
		{
			return s.codes;
		}
		Def d = def(s.id);
		List<String> out = new ArrayList<>();
		if (d != null)
		{
			for (Code c : d.codes)
			{
				out.add(c.code);
			}
		}
		return out;
	}

	/** A prebuilt group that matches its default again goes back to following it. */
	private void simplify(Stored s)
	{
		Def d = def(s.id);
		if (d == null)
		{
			return;
		}
		if (d.name.equals(s.name))
		{
			s.name = null;
		}
		if (s.codes != null)
		{
			List<String> c = s.codes;
			s.codes = null;
			if (!c.equals(codes(s)))
			{
				s.codes = c;
			}
		}
	}

	/** Printable ASCII (the RuneScape fonts have nothing else), no menu tags, trimmed and capped; null if empty. */
	static String cleanName(String name)
	{
		if (name == null)
		{
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (char c : name.toCharArray())
		{
			if (c >= 32 && c < 127 && c != '<' && c != '>')
			{
				sb.append(c);
			}
		}
		String n = sb.toString().trim().replaceAll(" +", " ");
		n = n.length() > NAME_MAX ? n.substring(0, NAME_MAX).trim() : n;
		return n.isEmpty() ? null : n;
	}

	private static List<String> cleanCodes(List<String> codes)
	{
		List<String> out = new ArrayList<>();
		for (String c : codes)
		{
			String n = DialMath.normalize(c);
			if (n != null && !out.contains(n))
			{
				out.add(n);
			}
		}
		return out;
	}
}
