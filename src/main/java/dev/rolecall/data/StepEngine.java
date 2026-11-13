package dev.rolecall.data;

import dev.rolecall.model.DungeonClass;
import dev.rolecall.model.Floor;
import dev.rolecall.model.Phase;
import dev.rolecall.model.S4Split;
import dev.rolecall.state.PartyTracker;
import dev.rolecall.state.RunState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Picks the current/next step for a class from {@link RoleData} and {@link RunState}. Pure Java.
 *
 * <p>Steps are grouped by (phase, section). Inside a group they run in file order; a step is
 * finished when its {@code until} is satisfied (or the player skipped it with the "next step"
 * key). A step without {@code until} lasts until its group ends.
 */
public final class StepEngine {
	private StepEngine() {
	}

	/** Toggles that decide which optional steps apply. */
	public record Options(boolean predev, S4Split s4Split, dev.rolecall.model.StormCrush crush) {
		public Options(boolean predev, S4Split s4Split) {
			this(predev, s4Split, dev.rolecall.model.StormCrush.PY);
		}
	}

	/** What the HUD shows. {@code current} is null when the class has nothing for this phase. */
	public record View(Step current, Step next, Step then, int index, int groupSize) {
		public static final View NONE = new View(null, null, null, 0, 0);
	}

	public static View compute(RoleData data, RunState state, DungeonClass cls, Options opt, int manualSkips) {
		if (cls == null || !state.inRun() || state.phase() == Phase.DONE) {
			return View.NONE;
		}
		List<Step> group = group(data, cls, state.floor(), state.phase(), state.section(), opt);
		List<Step> upcoming = new ArrayList<>();
		// Current = the step after the last finished one, so a missed event (e.g. no lightning line)
		// can't leave the HUD stuck once a later step is already done. Manual skips push further.
		int idx = -1;
		if (!group.isEmpty()) {
			int lastDone = -1;
			for (int i = 0; i < group.size(); i++) {
				if (isDone(group.get(i), state)) {
					lastDone = i;
				}
			}
			idx = Math.min(Math.max(lastDone + 1, manualSkips), group.size() - 1);
		}
		if (idx >= 0) {
			upcoming.addAll(group.subList(idx, group.size()));
		}
		// Look ahead into the following groups for "next"/"then".
		Phase p = state.phase();
		int sec = state.section();
		for (int guard = 0; guard < 8 && upcoming.size() < 3; guard++) {
			int[] nxt = nextGroup(p, sec, state.floor());
			if (nxt == null) {
				break;
			}
			p = Phase.values()[nxt[0]];
			sec = nxt[1];
			upcoming.addAll(group(data, cls, state.floor(), p, sec, opt));
		}
		if (idx < 0) {
			return new View(null, get(upcoming, 0), get(upcoming, 1), 0, 0);
		}
		return new View(get(upcoming, 0), get(upcoming, 1), get(upcoming, 2), idx, group.size());
	}

	private static Step get(List<Step> l, int i) {
		return i < l.size() ? l.get(i) : null;
	}

	/** The steps of one (phase, section) group for a class, filtered by floor and toggles. */
	public static List<Step> group(RoleData data, DungeonClass cls, Floor floor, Phase phase, int section, Options opt) {
		List<Step> out = new ArrayList<>();
		for (Step s : data.steps(cls)) {
			if (Phase.parse(s.phase) != phase) continue;
			if (phase == Phase.P3 && s.section != null && s.section != section) continue;
			if (!appliesTo(s, floor, opt)) continue;
			out.add(s);
		}
		return out;
	}

	public static boolean appliesTo(Step s, Floor floor, Options opt) {
		if (s.floors != null && !s.floors.isEmpty() && (floor == null || !s.floors.contains(floor.name()))) {
			return false;
		}
		if (s.requires != null) {
			if (s.requires.predev != null && s.requires.predev != opt.predev()) return false;
			if (s.requires.s4split != null && !s.requires.s4split.equals(opt.s4Split().dataId())) return false;
			if (s.requires.crush != null && !s.requires.crush.equals(opt.crush().dataId())) return false;
		}
		return true;
	}

	/** Marks the current step's {@code until.reached} spot once the player is close enough. */
	public static void updateReached(RoleData data, RunState state, Step current) {
		if (current == null || current.until == null || current.until.reached == null || !state.hasPosition()) {
			return;
		}
		Objective o = data.objective(current.until.reached);
		double r = current.until.radius != null ? current.until.radius : 5.0;
		if (o != null && o.distSq(state.x(), state.y(), state.z()) <= r * r) {
			state.markReached(o.id);
		}
	}

	public static boolean isDone(Step s, RunState state) {
		Step.Until u = s.until;
		if (u == null || Boolean.TRUE.equals(u.manual)) {
			return false;
		}
		if (u.event != null) {
			boolean hit = switch (u.event) {
				case "lightning" -> state.lightning();
				case "storm_dead" -> state.stormDead();
				case "gate" -> state.gateDestroyed(s.section != null ? s.section : state.section());
				case "core" -> state.phase().atLeast(Phase.P3_TUNNEL);
				default -> false;
			};
			if (hit) return true;
		}
		if (u.objectiveDone != null && !u.objectiveDone.isEmpty()) {
			boolean all = true;
			for (String id : u.objectiveDone) {
				all &= state.objectiveDone(id);
			}
			if (all) return true;
		}
		return u.reached != null && state.reached(u.reached);
	}

	/** {phaseOrdinal, section} of the group after this one, or null at the end. */
	private static int[] nextGroup(Phase p, int section, Floor floor) {
		return switch (p) {
			case CLEAR -> new int[]{Phase.P1.ordinal(), 0};
			case P1 -> new int[]{Phase.P2.ordinal(), 0};
			case P2 -> new int[]{Phase.P3.ordinal(), 1};
			case P3 -> section < 4 ? new int[]{Phase.P3.ordinal(), section + 1} : new int[]{Phase.P3_TUNNEL.ordinal(), 4};
			case P3_TUNNEL -> new int[]{Phase.P4.ordinal(), 0};
			case P4 -> floor == Floor.M7 ? new int[]{Phase.P5.ordinal(), 0} : null;
			default -> null;
		};
	}

	/** Predev resolved against the party (Auto = on when someone plays Healer; unknown party = on). */
	public static boolean resolvePredev(dev.rolecall.model.PredevMode mode, PartyTracker party) {
		return mode.resolve(party.isEmpty() || party.has(DungeonClass.HEALER));
	}

	// ------------------------------------------------------------------ party gaps

	/** "Unassigned" and "duplicate" lines for the current P3 section. Empty outside P3 or with no party info. */
	public static List<String> partyGaps(RoleData data, RunState state, DungeonClass me, PartyTracker party, Options opt) {
		List<String> out = new ArrayList<>();
		if (state.phase() != Phase.P3 || state.section() == 0 || party.isEmpty()) {
			return out;
		}
		Map<DungeonClass, List<String>> by = party.byClass();
		for (DungeonClass c : DungeonClass.values()) {
			if (by.containsKey(c)) continue;
			for (Step s : group(data, c, state.floor(), Phase.P3, state.section(), opt)) {
				if (s.section != null) {
					out.add("Unassigned (no " + c.displayName() + "): S" + state.section() + " " + s.titleText());
				}
			}
		}
		if (me != null && by.getOrDefault(me, List.of()).size() >= 2) {
			List<Step> mine = group(data, me, state.floor(), Phase.P3, state.section(), opt);
			if (!mine.isEmpty()) {
				out.add(by.get(me).size() + "× " + me.displayName() + ": split \"" + mine.get(0).titleText()
						+ "\" with your duplicate");
			}
		}
		return out;
	}
}
