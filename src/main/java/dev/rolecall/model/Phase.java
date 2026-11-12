package dev.rolecall.model;

/** Where in an F7/M7 run the party is. Order matters: later phases have a higher ordinal. */
public enum Phase {
	CLEAR("Clear"),
	P1("P1 Maxor"),
	P2("P2 Storm"),
	P3("P3 Goldor"),
	P3_TUNNEL("P3 Goldor tunnel"),
	P4("P4 Necron"),
	P5("P5 Wither King"),
	DONE("Done");

	private final String label;

	Phase(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public boolean atLeast(Phase other) {
		return ordinal() >= other.ordinal();
	}

	public static Phase parse(String s) {
		if (s == null) {
			return null;
		}
		try {
			return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
