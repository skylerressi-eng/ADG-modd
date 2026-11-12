package dev.rolecall.state;

import dev.rolecall.model.Floor;
import dev.rolecall.model.Phase;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Everything RoleCall knows about the current F7/M7 run. Pure Java: fed by chat lines,
 * the player's position and the floor from the scoreboard; reset on world change.
 */
public final class RunState {
	/**
	 * Rough centre (x, z) of each P3 section's objectives, used to guess the section after a
	 * relog mid-P3. Averages of the objective coordinates in objectives.json.
	 */
	private static final double[][] SECTION_CENTROIDS = {
			{101.4, 94.6},  // S1
			{45.4, 129.6},  // S2
			{3.7, 80.3},    // S3
			{65.3, 35.7}    // S4
	};

	private Floor floor;
	private boolean inBoss;
	private Phase phase = Phase.CLEAR;
	private boolean phaseFromFallback;
	private int section;
	private int progress;
	private int progressTotal;
	private final boolean[] gateDestroyed = new boolean[5];
	private boolean lightning;
	private boolean stormDead;
	private final Set<String> doneObjectives = new HashSet<>();
	private final Set<String> reachedSpots = new HashSet<>();
	private double x, y, z;
	private boolean hasPosition;

	public void reset() {
		floor = null;
		inBoss = false;
		phase = Phase.CLEAR;
		phaseFromFallback = false;
		section = 0;
		progress = 0;
		progressTotal = 0;
		java.util.Arrays.fill(gateDestroyed, false);
		lightning = false;
		stormDead = false;
		doneObjectives.clear();
		reachedSpots.clear();
		hasPosition = false;
	}

	// ------------------------------------------------------------------ inputs

	/** From the sidebar "The Catacombs (F7)" line. Null = not in an F7/M7 dungeon. */
	public void setFloor(Floor floor) {
		this.floor = floor;
	}

	/** Parses and applies a chat line. Returns the trigger it matched, if any. */
	public Optional<ChatTriggers.Event> onChat(String line) {
		Optional<ChatTriggers.Event> e = ChatTriggers.parse(line);
		e.ifPresent(this::apply);
		return e;
	}

	public void apply(ChatTriggers.Event e) {
		switch (e.kind()) {
			case P1_START -> setPhase(Phase.P1);
			case P2_START -> setPhase(Phase.P2);
			case STORM_DEAD -> {
				if (phase.ordinal() < Phase.P2.ordinal()) {
					setPhase(Phase.P2);
				}
				stormDead = true;
			}
			case P3_START -> {
				setPhase(Phase.P3);
				section = 1;
				progress = 0;
				progressTotal = 7;
			}
			case CORE_OPEN -> {
				setPhase(Phase.P3_TUNNEL);
				section = 4;
			}
			case P4_START -> setPhase(Phase.P4);
			case NECRON_DEAD -> setPhase(floor == Floor.M7 ? Phase.P5 : Phase.DONE);
			case WITHER_KING -> setPhase(Phase.P5);
			case RUN_END -> setPhase(Phase.DONE);
			case LIGHTNING -> lightning = true;
			case GATE -> onGate();
			case TERMINAL, DEVICE, LEVER -> onProgress(e);
		}
	}

	private void setPhase(Phase p) {
		phase = p;
		phaseFromFallback = false;
		inBoss = true;
		if (p == Phase.P3 || p == Phase.P3_TUNNEL) {
			stormDead = true;
		}
	}

	private void onProgress(ChatTriggers.Event e) {
		if (phase != Phase.P3) {
			// Missed Goldor's line (relog/joined late): a terminal message means we're in P3.
			setPhase(Phase.P3);
		}
		if (section == 0) {
			section = e.total() == 8 ? 2 : guessSectionFromPosition().orElse(1);
		}
		if (e.total() == 8 && section != 2) {
			section = 2; // only S2 has 8 objectives
		}
		progress = e.count();
		progressTotal = e.total();
		if (e.count() >= e.total() && section < 4) {
			section++;
			progress = 0;
			progressTotal = section == 2 ? 8 : 7;
		}
	}

	/**
	 * "The gate has been destroyed!" belongs to the earliest section (1-3) at or before the current
	 * one whose gate is still up; the gate is sometimes blown after the section's last objective.
	 */
	private void onGate() {
		int upTo = Math.max(1, Math.min(section, 3));
		for (int s = 1; s <= upTo; s++) {
			if (!gateDestroyed[s]) {
				gateDestroyed[s] = true;
				return;
			}
		}
	}

	/** The player's own position, every tick. Drives the in-boss check and the missed-message fallbacks. */
	public void onPosition(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.hasPosition = true;
		if (floor == null) {
			return;
		}
		// Odin's F7/M7 boss-room check.
		if (x > -7 && z > -7) {
			inBoss = true;
		}
		if (inBoss && phase == Phase.CLEAR) {
			phase = phaseFromY(y);
			phaseFromFallback = true;
			if (phase == Phase.P3) {
				section = guessSectionFromPosition().orElse(1);
				progress = 0;
				progressTotal = section == 2 ? 8 : 7;
			}
		}
	}

	/** Odin's Y bands for the F7/M7 boss. P5 only exists on M7. */
	public Phase phaseFromY(double y) {
		if (y > 210) return Phase.P1;
		if (y > 155) return Phase.P2;
		if (y > 100) return Phase.P3;
		if (y > 45) return Phase.P4;
		return floor == Floor.M7 ? Phase.P5 : Phase.P4;
	}

	public Optional<Integer> guessSectionFromPosition() {
		if (!hasPosition) {
			return Optional.empty();
		}
		int best = 1;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < SECTION_CENTROIDS.length; i++) {
			double dx = x - SECTION_CENTROIDS[i][0];
			double dz = z - SECTION_CENTROIDS[i][1];
			double d = dx * dx + dz * dz;
			if (d < bestD) {
				bestD = d;
				best = i + 1;
			}
		}
		return Optional.of(best);
	}

	public void markObjectiveDone(String id) {
		doneObjectives.add(id);
	}

	public void markReached(String spotId) {
		reachedSpots.add(spotId);
	}

	// ------------------------------------------------------------------ queries

	public Floor floor() {
		return floor;
	}

	public boolean inRun() {
		return floor != null;
	}

	public boolean inBoss() {
		return inBoss;
	}

	public Phase phase() {
		return phase;
	}

	public boolean phaseFromFallback() {
		return phaseFromFallback;
	}

	/** P3 section 1-4, or 0 outside P3. */
	public int section() {
		return phase == Phase.P3 || phase == Phase.P3_TUNNEL ? section : 0;
	}

	public int progress() {
		return progress;
	}

	public int progressTotal() {
		return progressTotal;
	}

	public boolean gateDestroyed(int section) {
		return section >= 1 && section <= 4 && gateDestroyed[section];
	}

	public boolean lightning() {
		return lightning;
	}

	public boolean stormDead() {
		return stormDead;
	}

	public boolean objectiveDone(String id) {
		return doneObjectives.contains(id);
	}

	public Set<String> doneObjectives() {
		return Set.copyOf(doneObjectives);
	}

	public boolean reached(String spotId) {
		return reachedSpots.contains(spotId);
	}

	public double x() {
		return x;
	}

	public double y() {
		return y;
	}

	public double z() {
		return z;
	}

	public boolean hasPosition() {
		return hasPosition;
	}

	/** e.g. "P3 S2 (5/8)", "P2 Storm", "P5 Wither King". */
	public String phaseLabel() {
		if (phase == Phase.P3 && section > 0) {
			return "P3 S" + section + " (" + progress + "/" + progressTotal + ")";
		}
		return phase.label();
	}
}
