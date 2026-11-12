package dev.rolecall.model;

/** Whether the Healer does predev during P1. AUTO = on when the party has a Healer. */
public enum PredevMode {
	AUTO, ON, OFF;

	public PredevMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	/** Resolves AUTO against whether a Healer is in the party. */
	public boolean resolve(boolean partyHasHealer) {
		return switch (this) {
			case AUTO -> partyHasHealer;
			case ON -> true;
			case OFF -> false;
		};
	}
}
