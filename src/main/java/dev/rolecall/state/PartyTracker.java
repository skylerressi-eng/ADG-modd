package dev.rolecall.state;

import dev.rolecall.model.DungeonClass;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the dungeon tab list into a party map. Pure Java: give it the tab entries' display
 * strings. Pattern is Odin's: {@code [level] [RANK] Name ... (Class LEVEL)}; the class is
 * {@code DEAD} while a player is dead, in which case their last known class is kept.
 */
public final class PartyTracker {
	public static final Pattern TAB_PATTERN = Pattern.compile("^\\[(\\d+)] (?:\\[[^\\]]+] )*(\\w+) .*?\\((\\w+)(?: (\\w+))*\\)$");

	public record Member(String name, DungeonClass dungeonClass, boolean dead, String classLevel) {
	}

	private final Map<String, Member> members = new LinkedHashMap<>();
	private final Deque<String> unmatched = new ArrayDeque<>();

	public void reset() {
		members.clear();
		unmatched.clear();
	}

	/** Replaces the party from the latest tab list strings. */
	public void update(Collection<String> tabLines) {
		Map<String, Member> previous = new LinkedHashMap<>(members);
		members.clear();
		for (String raw : tabLines) {
			if (raw == null) {
				continue;
			}
			String line = raw.strip();
			Matcher m = TAB_PATTERN.matcher(line);
			if (!m.matches()) {
				// Only remember lines that look like player rows, for /rolecall debug.
				if (line.startsWith("[") && line.endsWith(")")) {
					rememberUnmatched(line);
				}
				continue;
			}
			String name = m.group(2);
			String cls = m.group(3);
			String level = m.group(4);
			if (cls.equalsIgnoreCase("EMPTY")) {
				continue;
			}
			if (cls.equalsIgnoreCase("DEAD")) {
				Member before = previous.get(name);
				members.put(name, new Member(name, before == null ? null : before.dungeonClass(), true,
						before == null ? null : before.classLevel()));
				continue;
			}
			Optional<DungeonClass> c = DungeonClass.parse(cls);
			if (c.isEmpty()) {
				rememberUnmatched(line);
				continue;
			}
			members.put(name, new Member(name, c.get(), false, level));
		}
	}

	private void rememberUnmatched(String line) {
		if (unmatched.contains(line)) {
			return;
		}
		unmatched.addFirst(line);
		while (unmatched.size() > 8) {
			unmatched.removeLast();
		}
	}

	/** Directly set a member (used by /rolecall sim). */
	public void put(String name, DungeonClass c) {
		members.put(name, new Member(name, c, false, null));
	}

	public Collection<Member> members() {
		return List.copyOf(members.values());
	}

	public boolean isEmpty() {
		return members.isEmpty();
	}

	public Optional<DungeonClass> classOf(String name) {
		if (name == null) {
			return Optional.empty();
		}
		for (Member m : members.values()) {
			if (m.name().equalsIgnoreCase(name)) {
				return Optional.ofNullable(m.dungeonClass());
			}
		}
		return Optional.empty();
	}

	/** Class → player names, in tab order. Classes nobody plays are absent. */
	public Map<DungeonClass, List<String>> byClass() {
		Map<DungeonClass, List<String>> map = new EnumMap<>(DungeonClass.class);
		for (Member m : members.values()) {
			if (m.dungeonClass() != null) {
				map.computeIfAbsent(m.dungeonClass(), k -> new ArrayList<>()).add(m.name());
			}
		}
		return map;
	}

	public boolean has(DungeonClass c) {
		return byClass().containsKey(c);
	}

	public List<String> unmatchedLines() {
		return List.copyOf(unmatched);
	}
}
