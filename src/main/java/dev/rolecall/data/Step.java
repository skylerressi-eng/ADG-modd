package dev.rolecall.data;

import java.util.List;

/**
 * One instruction for one class, as written in roles.json. Gson fills the fields;
 * {@link RoleDataLoader} validates them.
 */
public final class Step {
	public String id;
	/** CLEAR, P1, P2, P3, P3_TUNNEL, P4, P5. */
	public String phase;
	/** P3 section 1-4; null = whole phase. */
	public Integer section;
	/** Main line shown on the HUD. */
	public String text;
	/** Short form for the on-screen title; falls back to {@link #text}. */
	public String shortText;
	/** Optional grey sub-line. */
	public String note;
	/** Class to leap to (ARCHER, BERSERK, MAGE, HEALER, TANK). */
	public String leap;
	public Until until;
	public Requires requires;
	/** Objective or user-spot ids to draw waypoints on while this step is current. */
	public List<String> waypoints;
	/** Name of a user route to draw while this step is current. */
	public String route;
	/** Name of a recorded block list (/rolecall mine) to outline in red while this step is current. */
	public String mine;
	/** Restrict to F7 or M7; null/empty = both. */
	public List<String> floors;
	/** Where the step came from. "⚠" in here means "check against the ADG video". */
	public String src;

	public String titleText() {
		return shortText != null && !shortText.isBlank() ? shortText : text;
	}

	/** What moves the HUD past this step. With no {@code until}, the step lasts until its phase/section ends. */
	public static final class Until {
		/** "lightning", "storm_dead", "gate", "core". */
		public String event;
		/** Done once all of these objective ids are completed. */
		public List<String> objectiveDone;
		/** Done once the player has been within {@link #radius} blocks of this objective/spot id. */
		public String reached;
		public Double radius;
		/** Only advances with the "next step" key. */
		public Boolean manual;
	}

	public static final class Requires {
		public Boolean predev;
		/** "bers_device" or "bers_levers". */
		public String s4split;
	}
}
