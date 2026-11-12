package dev.rolecall.config;

import dev.rolecall.model.DungeonClass;
import dev.rolecall.model.PredevMode;
import dev.rolecall.model.S4Split;

/**
 * Serialized (Gson) settings, stored at {@code config/rolecall/config.json}.
 * Role text does not live here; that is in roles.json (Milestone 2).
 */
public class RoleCallConfig {
	/** Manually picked class, or null for Auto (detect from tab list). */
	public DungeonClass manualClass = null;

	public PredevMode predev = PredevMode.AUTO;
	public S4Split s4Split = S4Split.BERS_DEVICE;

	public boolean hudEnabled = true;
	public int hudX = 6;
	public int hudY = 6;
	public float hudScale = 1.0f;

	public boolean stepTitles = true;
	public boolean stepSound = true;
	public float soundVolume = 1.0f;

	public boolean lightningWarning = true;
	public boolean waypoints = true;
	public boolean showOtherObjectives = false;
	public boolean leapHighlight = true;
	public boolean debugOverlay = false;

	void normalize() {
		if (predev == null) {
			predev = PredevMode.AUTO;
		}
		if (s4Split == null) {
			s4Split = S4Split.BERS_DEVICE;
		}
		if (!(hudScale >= 0.5f && hudScale <= 3.0f)) {
			hudScale = 1.0f;
		}
		if (!(soundVolume >= 0f && soundVolume <= 2f)) {
			soundVolume = 1.0f;
		}
	}
}
