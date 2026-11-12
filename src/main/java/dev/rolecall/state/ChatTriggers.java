package dev.rolecall.state;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every chat line RoleCall reacts to, in one place. All patterns are matched against
 * {@code Component.getString()} (formatting already stripped). Strings were checked against
 * Odin (SplitsManager, TickTimers, TerminalsStatus) and Skyblocker (DungeonSplitsWidget).
 */
public final class ChatTriggers {
	private ChatTriggers() {
	}

	public enum Kind {
		/** Boss entry; F7/M7 P1 starts. */
		P1_START,
		P2_START,
		/** Storm is dead (P2 over, Goldor not started yet). */
		STORM_DEAD,
		P3_START,
		/** All four sections done; Goldor tunnel. */
		CORE_OPEN,
		P4_START,
		/** F7: run over. M7: P5 starts. */
		NECRON_DEAD,
		/** M7 P5 (Wither King appears). */
		WITHER_KING,
		/** End-of-run summary line. */
		RUN_END,
		/** Storm giga-lightning (the "py" countdown). */
		LIGHTNING,
		TERMINAL,
		DEVICE,
		LEVER,
		GATE
	}

	/** A matched trigger. {@code player}/{@code count}/{@code total} are only set for TERMINAL/DEVICE/LEVER. */
	public record Event(Kind kind, String player, int count, int total) {
		public static Event of(Kind kind) {
			return new Event(kind, null, 0, 0);
		}

		public boolean isProgress() {
			return kind == Kind.TERMINAL || kind == Kind.DEVICE || kind == Kind.LEVER;
		}

		/** Objective kind name used in objectives.json ("terminal", "device", "lever"). */
		public String objectiveKind() {
			return switch (kind) {
				case TERMINAL -> "terminal";
				case DEVICE -> "device";
				case LEVER -> "lever";
				default -> null;
			};
		}
	}

	private record Simple(Kind kind, Pattern pattern) {
	}

	private static final List<Simple> SIMPLE = List.of(
			new Simple(Kind.P1_START, Pattern.compile("^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$")),
			new Simple(Kind.P2_START, Pattern.compile("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$")),
			new Simple(Kind.STORM_DEAD, Pattern.compile("^\\[BOSS] Storm: I should have known that I stood no chance\\.$")),
			new Simple(Kind.P3_START, Pattern.compile("^\\[BOSS] Goldor: Who dares trespass into my domain\\?$")),
			new Simple(Kind.CORE_OPEN, Pattern.compile("^The Core entrance is opening!$")),
			new Simple(Kind.P4_START, Pattern.compile("^\\[BOSS] Necron: You went further than any human before, congratulations\\.$")),
			new Simple(Kind.NECRON_DEAD, Pattern.compile("^\\[BOSS] Necron: All this, for nothing\\.\\.\\.$")),
			new Simple(Kind.WITHER_KING, Pattern.compile("^\\[BOSS] Wither King: You\\.\\.\\. again\\?$")),
			new Simple(Kind.RUN_END, Pattern.compile("^\\s*☠ Defeated .+ in 0?[\\dhms ]+?\\s*(\\(NEW RECORD!\\))?$")),
			new Simple(Kind.LIGHTNING, Pattern.compile("^\\[BOSS] Storm: (ENERGY HEED MY CALL|THUNDER LET ME BE YOUR CATALYST)!$")),
			new Simple(Kind.GATE, Pattern.compile("^The gate has been destroyed!$"))
	);

	private static final Pattern PROGRESS = Pattern.compile(
			"^(\\S{1,16}) (activated a terminal|completed a device|activated a lever)! \\((\\d)/(\\d)\\)$");

	public static Optional<Event> parse(String line) {
		if (line == null || line.isEmpty()) {
			return Optional.empty();
		}
		Matcher m = PROGRESS.matcher(line);
		if (m.matches()) {
			Kind kind = switch (m.group(2)) {
				case "activated a terminal" -> Kind.TERMINAL;
				case "completed a device" -> Kind.DEVICE;
				default -> Kind.LEVER;
			};
			return Optional.of(new Event(kind, m.group(1), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))));
		}
		for (Simple s : SIMPLE) {
			if (s.pattern.matcher(line).matches()) {
				return Optional.of(Event.of(s.kind));
			}
		}
		return Optional.empty();
	}
}
