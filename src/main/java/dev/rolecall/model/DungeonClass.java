package dev.rolecall.model;

import java.util.Locale;
import java.util.Optional;

/**
 * The five Catacombs classes, with the colors Hypixel/Odin use for them.
 * Pure Java (no Minecraft classes) so it can be unit tested.
 */
public enum DungeonClass {
	ARCHER("Archer", '6', 0xFFAA00),
	BERSERK("Berserk", '4', 0xAA0000),
	MAGE("Mage", 'b', 0x55FFFF),
	HEALER("Healer", 'd', 0xFF55FF),
	TANK("Tank", '2', 0x00AA00);

	private final String displayName;
	private final char formatCode;
	private final int rgb;

	DungeonClass(String displayName, char formatCode, int rgb) {
		this.displayName = displayName;
		this.formatCode = formatCode;
		this.rgb = rgb;
	}

	public String displayName() {
		return displayName;
	}

	/** Legacy formatting code, e.g. {@code '6'} for {@code §6}. */
	public char formatCode() {
		return formatCode;
	}

	/** 0xRRGGBB, no alpha. */
	public int rgb() {
		return rgb;
	}

	/** 0xAARRGGBB, fully opaque. */
	public int argb() {
		return 0xFF000000 | rgb;
	}

	/**
	 * Parses a class name the way players and Hypixel write it: case-insensitive,
	 * accepts "Berserker" as well as "Berserk".
	 */
	public static Optional<DungeonClass> parse(String name) {
		if (name == null) {
			return Optional.empty();
		}
		String n = name.trim().toUpperCase(Locale.ROOT);
		if (n.equals("BERSERKER")) {
			n = "BERSERK";
		}
		for (DungeonClass c : values()) {
			if (c.name().equals(n)) {
				return Optional.of(c);
			}
		}
		return Optional.empty();
	}
}
