package dev.rolecall.data;

import dev.rolecall.TestData;
import dev.rolecall.model.DungeonClass;
import dev.rolecall.model.Floor;
import dev.rolecall.model.Phase;
import dev.rolecall.model.S4Split;
import dev.rolecall.sim.SimScript;
import dev.rolecall.state.PartyTracker;
import dev.rolecall.state.RunState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StepEngineTest {
	private static final RoleData DATA = TestData.bundled();
	private static final StepEngine.Options DEFAULT = new StepEngine.Options(true, S4Split.BERS_DEVICE);

	private static String current(RunState s, DungeonClass c, StepEngine.Options o) {
		StepEngine.View v = StepEngine.compute(DATA, s, c, o, 0);
		return v.current() == null ? null : v.current().id;
	}

	@Test
	void bundledDataLoadsCleanly() {
		assertEquals(List.of(), DATA.problems());
		assertEquals(79, DATA.stepCount());
		assertEquals(39, DATA.objectives().size());
		for (DungeonClass c : DungeonClass.values()) {
			for (Step s : DATA.steps(c)) {
				if (s.waypoints != null) {
					for (String w : s.waypoints) {
						if (!w.equals("ss_wait")) {
							assertNotNull(DATA.objective(w), c + " " + s.id + " → unknown waypoint " + w);
						}
					}
				}
			}
		}
	}

	@Test
	void everyClassHasAStepInEveryF7Group() {
		for (DungeonClass c : DungeonClass.values()) {
			for (Phase p : List.of(Phase.P1, Phase.P2, Phase.P3_TUNNEL, Phase.P4)) {
				assertFalse(StepEngine.group(DATA, c, Floor.F7, p, 0, DEFAULT).isEmpty(), c + " " + p);
			}
			for (int sec = 1; sec <= 4; sec++) {
				for (S4Split split : S4Split.values()) {
					assertFalse(StepEngine.group(DATA, c, Floor.F7, Phase.P3, sec, new StepEngine.Options(true, split)).isEmpty(),
							c + " S" + sec);
				}
			}
			assertTrue(StepEngine.group(DATA, c, Floor.F7, Phase.P5, 0, DEFAULT).isEmpty(), "no P5 on F7");
			assertFalse(StepEngine.group(DATA, c, Floor.M7, Phase.P5, 0, DEFAULT).isEmpty(), c + " M7 P5");
		}
	}

	/** The P3 terminal table from the guide: who does what in each section. */
	@Test
	void p3AssignmentsMatchGuide() {
		RoleData d = DATA;
		assertEquals(List.of("s1_t4"), wps(d, DungeonClass.MAGE, 1));
		assertEquals(List.of("s2_t2"), wps(d, DungeonClass.MAGE, 2));
		assertEquals(List.of("s3_t2"), wps(d, DungeonClass.MAGE, 3));
		assertEquals(List.of("s4_t2"), wps(d, DungeonClass.MAGE, 4));
		assertEquals(List.of("s1_t3"), wps(d, DungeonClass.BERSERK, 1));
		assertEquals(List.of("s2_t4", "s2_t3"), wps(d, DungeonClass.BERSERK, 2));
		assertEquals(List.of("s3_t3"), wps(d, DungeonClass.BERSERK, 3));
		assertEquals(List.of("s4_t3", "s4_device"), wps(d, DungeonClass.BERSERK, 4));
		assertEquals(List.of("s1_t2", "s1_t1"), wps(d, DungeonClass.TANK, 1));
		assertEquals(List.of("s2_t1", "s2_t3"), wps(d, DungeonClass.TANK, 2));
		assertEquals(List.of("s3_t1"), wps(d, DungeonClass.TANK, 3));
		assertEquals(List.of("s4_t1"), wps(d, DungeonClass.TANK, 4));
		assertEquals(List.of("s1_device"), wps(d, DungeonClass.HEALER, 1));
		assertEquals(List.of("s2_device", "s2_lever_left"), wps(d, DungeonClass.HEALER, 2));
		assertEquals(List.of("s3_device", "s3_lever_a", "s3_lever_b"), wps(d, DungeonClass.HEALER, 3));
		assertEquals(List.of("s4_lever_a", "s4_lever_b"), wps(d, DungeonClass.HEALER, 4));
		assertEquals(List.of("s1_lever_a", "s1_lever_b"), wps(d, DungeonClass.ARCHER, 1));
		assertEquals(List.of("s2_t5", "s2_lever_right"), wps(d, DungeonClass.ARCHER, 2));
		assertEquals(List.of("s3_t4", "s3_lever_a", "s3_lever_b"), wps(d, DungeonClass.ARCHER, 3));
		assertEquals(List.of("s4_t4", "s4_lever_a", "s4_lever_b"), wps(d, DungeonClass.ARCHER, 4));
	}

	private static List<String> wps(RoleData d, DungeonClass c, int section) {
		List<String> out = new ArrayList<>();
		for (Step s : StepEngine.group(d, c, Floor.F7, Phase.P3, section, DEFAULT)) {
			if (s.waypoints != null) out.addAll(s.waypoints);
		}
		return out;
	}

	@Test
	void s4SplitSwapsSharpshooterAndLevers() {
		StepEngine.Options rev = new StepEngine.Options(true, S4Split.BERS_LEVERS);
		List<String> bers = StepEngine.group(DATA, DungeonClass.BERSERK, Floor.F7, Phase.P3, 4, rev).stream().map(s -> s.id).toList();
		List<String> heal = StepEngine.group(DATA, DungeonClass.HEALER, Floor.F7, Phase.P3, 4, rev).stream().map(s -> s.id).toList();
		assertEquals(List.of("b_s4_t3", "b_s4_levers"), bers);
		assertEquals(List.of("h_s4_ss"), heal);
	}

	/** Replays the bundled f7_full sim and records each class's step at every chat line. */
	@Test
	void fullF7RunPerClass() {
		Map<DungeonClass, List<String>> seen = replay("sim/f7_full.txt", DEFAULT);
		assertEquals(List.of("h_p1_mine", "h_p1_lights", "h_p1_arrows", "h_p2_finish", "h_p2_py", "h_p2_leap",
				"h_s1_ss", "h_s2_lights", "h_s2_lever", "h_s2_gate", "h_s3_arrows", "h_s3_levers", "h_s4_levers",
				"h_tunnel", "h_p4"), seen.get(DungeonClass.HEALER));
		assertEquals(List.of("m_p1", "m_p2_clear", "m_p2_py", "m_p2_leap", "m_s1_t4", "m_s2_t2", "m_s3_t2",
				"m_s4_t2", "m_tunnel", "m_p4"), seen.get(DungeonClass.MAGE));
		assertEquals(List.of("a_p1_ss", "a_p2_wait", "a_p2_levers", "a_s1_levers", "a_s1_gate", "a_s1_stand",
				"a_s2_t5", "a_s2_lever", "a_s3_t4", "a_s3_gate", "a_s3_levers", "a_s4_t4", "a_s4_levers", "a_tunnel",
				"a_p4"), seen.get(DungeonClass.ARCHER));
		assertEquals(List.of("t_p1", "t_p2_py_raise", "t_p2_py_crush", "t_p2_leap", "t_s1_t2", "t_s1_t1", "t_s2_t1",
				"t_s2_help", "t_s3_t1", "t_s4_t1", "t_tunnel", "t_p4"), seen.get(DungeonClass.TANK));
		assertEquals(List.of("b_p1", "b_p2", "b_s1_t3", "b_s2_t4", "b_s2_t3", "b_s3_t3", "b_s4_t3", "b_s4_ss",
				"b_tunnel", "b_p4"), seen.get(DungeonClass.BERSERK));
	}

	@Test
	void predevOffChangesHealerAndArcher() {
		StepEngine.Options off = new StepEngine.Options(false, S4Split.BERS_DEVICE);
		Map<DungeonClass, List<String>> seen = replay("sim/f7_full.txt", off);
		assertEquals(List.of("h_p1_party", "h_p2_py", "h_p2_ss"), seen.get(DungeonClass.HEALER).subList(0, 3));
		assertEquals(List.of("a_p1_dps", "a_p2_clear", "a_p2_levers"), seen.get(DungeonClass.ARCHER).subList(0, 3));
	}

	/** Green/yellow crush: Healer drops green, Tank drops yellow, Mage leads Storm under both. */
	@Test
	void greenYellowCrush() {
		StepEngine.Options gy = new StepEngine.Options(true, S4Split.BERS_DEVICE, dev.rolecall.model.StormCrush.GY);
		Map<DungeonClass, List<String>> seen = replay("sim/f7_full.txt", gy);
		assertEquals(List.of("m_p1", "m_p2_clear", "m_p2_storm", "m_p2_leap"), seen.get(DungeonClass.MAGE).subList(0, 4));
		assertEquals(List.of("t_p1", "t_p2_pad", "t_p2_yellow", "t_p2_leap"), seen.get(DungeonClass.TANK).subList(0, 4));
		assertEquals(List.of("h_p2_finish", "h_p2_green", "h_p2_leap"), seen.get(DungeonClass.HEALER).subList(3, 6));
	}

	/** Purple/yellow: the Tank crushes on purple and leaps to Mage; the Mage crushes on yellow. */
	@Test
	void purpleYellowCrushRoles() {
		Step tank = StepEngine.group(DATA, DungeonClass.TANK, Floor.F7, Phase.P2, 0, DEFAULT).get(1);
		assertEquals("t_p2_py_crush", tank.id);
		assertEquals("MAGE", tank.leap);
		assertTrue(tank.text.contains("PURPLE"));
		Step mage = StepEngine.group(DATA, DungeonClass.MAGE, Floor.F7, Phase.P2, 0, DEFAULT).get(1);
		assertTrue(mage.text.contains("YELLOW pad"), mage.text);
		for (DungeonClass c : DungeonClass.values()) {
			for (Step st : StepEngine.group(DATA, c, Floor.F7, Phase.P2, 0, DEFAULT)) {
				assertFalse(st.text.contains("GREEN"), c + " " + st.id + " mentions green in py");
			}
		}
	}

	@Test
	void m7P5() {
		Map<DungeonClass, List<String>> seen = replay("sim/m7_p5.txt", DEFAULT);
		assertEquals(List.of("h_p4", "h_p5_relic", "h_p5_drags"), seen.get(DungeonClass.HEALER));
		assertEquals(List.of("m_p4", "m_p5_relic"), seen.get(DungeonClass.MAGE), "mage hasn't reached the blue cauldron");
		assertEquals(List.of("a_p4", "a_p5"), seen.get(DungeonClass.ARCHER));
	}

	@Test
	void nextLooksAcrossPhases() {
		RunState s = new RunState();
		s.setFloor(Floor.F7);
		s.onChat("[BOSS] Storm: Pathetic Maxor, just like expected.");
		s.onChat("[BOSS] Storm: I should have known that I stood no chance.");
		StepEngine.View v = StepEngine.compute(DATA, s, DungeonClass.MAGE, DEFAULT, 0);
		assertEquals("m_p2_leap", v.current().id);
		assertEquals("m_s1_t4", v.next().id);
		assertEquals("m_s2_t2", v.then().id);
	}

	@Test
	void manualSkipAdvancesWithinGroup() {
		RunState s = new RunState();
		s.setFloor(Floor.F7);
		s.onChat("[BOSS] Storm: Pathetic Maxor, just like expected.");
		assertEquals("t_p2_py_raise", StepEngine.compute(DATA, s, DungeonClass.TANK, DEFAULT, 0).current().id);
		assertEquals("t_p2_py_crush", StepEngine.compute(DATA, s, DungeonClass.TANK, DEFAULT, 1).current().id);
		assertEquals("t_p2_leap", StepEngine.compute(DATA, s, DungeonClass.TANK, DEFAULT, 9).current().id, "never past the last");
	}

	@Test
	void clearAndOutsideRunShowNothing() {
		RunState s = new RunState();
		assertNull(StepEngine.compute(DATA, s, DungeonClass.MAGE, DEFAULT, 0).current());
		s.setFloor(Floor.F7);
		assertNull(StepEngine.compute(DATA, s, DungeonClass.MAGE, DEFAULT, 0).current(), "no CLEAR steps yet");
	}

	@Test
	void noHealerParty() {
		PartyTracker p = new PartyTracker();
		p.update(List.of("[1] A (Archer L)", "[1] B (Berserk L)", "[1] C (Mage L)", "[1] D (Tank L)", "[1] E (Mage L)"));
		assertFalse(StepEngine.resolvePredev(dev.rolecall.model.PredevMode.AUTO, p), "Auto predev off without a Healer");
		RunState s = new RunState();
		s.setFloor(Floor.F7);
		s.onChat("[BOSS] Goldor: Who dares trespass into my domain?");
		StepEngine.Options off = new StepEngine.Options(false, S4Split.BERS_DEVICE);
		List<String> gaps = StepEngine.partyGaps(DATA, s, DungeonClass.MAGE, p, off);
		assertTrue(gaps.contains("Unassigned (no Healer): S1 Simon Says"), gaps.toString());
		assertTrue(gaps.contains("2× Mage: split \"Terminal 4\" with your duplicate"), gaps.toString());
	}

	private static Map<DungeonClass, List<String>> replay(String script, StepEngine.Options opt) {
		Map<DungeonClass, List<String>> seen = new LinkedHashMap<>();
		Map<DungeonClass, RunState> states = new LinkedHashMap<>();
		for (DungeonClass c : DungeonClass.values()) {
			seen.put(c, new ArrayList<>());
			states.put(c, new RunState());
		}
		for (SimScript.Cmd cmd : SimScript.parse(TestData.resource(script))) {
			for (DungeonClass c : DungeonClass.values()) {
				RunState s = states.get(c);
				switch (cmd.op()) {
					case "floor" -> s.setFloor(Floor.parse(cmd.arg()).orElseThrow());
					case "chat" -> s.onChat(cmd.arg());
					case "done" -> s.markObjectiveDone(cmd.arg());
					case "pos" -> {
						String[] p = cmd.arg().split(" ");
						s.onPosition(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
					}
					default -> {
					}
				}
				StepEngine.View v = StepEngine.compute(DATA, s, c, opt, 0);
				StepEngine.updateReached(DATA, s, v.current());
				v = StepEngine.compute(DATA, s, c, opt, 0);
				List<String> l = seen.get(c);
				if (v.current() != null && (l.isEmpty() || !l.get(l.size() - 1).equals(v.current().id))) {
					l.add(v.current().id);
				}
			}
		}
		return seen;
	}
}
