package dev.rolecall.state;

import dev.rolecall.model.Floor;

import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds "The Catacombs (F7)" / "(M7)" in the sidebar. Each Hypixel sidebar line is a team
 * prefix + suffix (the score holder name in between is invisible colour codes and must be ignored),
 * often padded with emoji and invisible characters, so lines are reduced to plain ASCII first.
 */
public final class FloorParser {
	private static final Pattern FLOOR = Pattern.compile("The Catacombs \\(([EFM]\\d*)\\)");

	private FloorParser() {
	}

	public static String clean(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == '§' && i + 1 < s.length()) {
				i++; // legacy formatting code
				continue;
			}
			if (ch >= 32 && ch < 127) {
				b.append(ch);
			}
		}
		return b.toString().replaceAll(" {2,}", " ").strip();
	}

	/** The matching line (cleaned) and floor id like "F7", if any line has one. */
	public static Optional<String[]> find(Collection<String> sidebarLines) {
		for (String raw : sidebarLines) {
			String line = clean(raw);
			Matcher m = FLOOR.matcher(line);
			if (m.find()) {
				return Optional.of(new String[]{line, m.group(1)});
			}
		}
		return Optional.empty();
	}

	/** F7/M7 from the sidebar; empty if not in the Catacombs or on another floor. */
	public static Optional<Floor> floor(Collection<String> sidebarLines) {
		return find(sidebarLines).flatMap(f -> Floor.parse(f[1]));
	}
}
