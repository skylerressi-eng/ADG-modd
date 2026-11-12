package dev.rolecall.state;

import dev.rolecall.model.Floor;
import dev.rolecall.model.Phase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunStateTest {
	@Test
	void fullF7Flow() {
		RunState s = new RunState();
		s.setFloor(Floor.F7);
		s.onChat("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!");
		assertEquals(Phase.P1, s.phase());
		assertTrue(s.inBoss());
		s.onChat("[BOSS] Storm: Pathetic Maxor, just like expected.");
		assertEquals(Phase.P2, s.phase());
		assertFalse(s.lightning());
		s.onChat("[BOSS] Storm: ENERGY HEED MY CALL!");
		assertTrue(s.lightning());
		s.onChat("[BOSS] Storm: I should have known that I stood no chance.");
		assertTrue(s.stormDead());
		assertEquals(Phase.P2, s.phase());
		s.onChat("[BOSS] Goldor: Who dares trespass into my domain?");
		assertEquals(Phase.P3, s.phase());
		assertEquals(1, s.section());
		assertEquals("P3 S1 (0/7)", s.phaseLabel());

		for (int i = 1; i <= 6; i++) s.onChat("A activated a terminal! (" + i + "/7)");
		assertEquals(1, s.section());
		assertEquals("P3 S1 (6/7)", s.phaseLabel());
		s.onChat("The gate has been destroyed!");
		assertTrue(s.gateDestroyed(1));
		s.onChat("A activated a lever! (7/7)");
		assertEquals(2, s.section());
		assertFalse(s.gateDestroyed(2));
		assertEquals("P3 S2 (0/8)", s.phaseLabel());

		for (int i = 1; i <= 8; i++) s.onChat("A activated a terminal! (" + i + "/8)");
		assertEquals(3, s.section());
		// S3's gate blown AFTER its last objective still counts for S3, not S4.
		for (int i = 1; i <= 7; i++) s.onChat("A activated a terminal! (" + i + "/7)");
		assertEquals(4, s.section());
		s.onChat("The gate has been destroyed!");
		assertTrue(s.gateDestroyed(2));
		s.onChat("The gate has been destroyed!");
		assertTrue(s.gateDestroyed(3));
		for (int i = 1; i <= 7; i++) s.onChat("A activated a terminal! (" + i + "/7)");
		assertEquals(4, s.section(), "S4 stays S4 until the core opens");

		s.onChat("The Core entrance is opening!");
		assertEquals(Phase.P3_TUNNEL, s.phase());
		s.onChat("[BOSS] Necron: You went further than any human before, congratulations.");
		assertEquals(Phase.P4, s.phase());
		s.onChat("[BOSS] Necron: All this, for nothing...");
		assertEquals(Phase.DONE, s.phase(), "F7 ends when Necron dies");
	}

	@Test
	void m7NecronDeathStartsP5() {
		RunState s = new RunState();
		s.setFloor(Floor.M7);
		s.onChat("[BOSS] Necron: You went further than any human before, congratulations.");
		s.onChat("[BOSS] Necron: All this, for nothing...");
		assertEquals(Phase.P5, s.phase());
		s.onChat("[BOSS] Wither King: You... again?");
		assertEquals(Phase.P5, s.phase());
		s.onChat("  ☠ Defeated Maxor, Storm, Goldor, Necron, and Wither King in 07m 01s");
		assertEquals(Phase.DONE, s.phase());
	}

	@Test
	void yFallbackWhenMessagesMissed() {
		RunState s = new RunState();
		s.setFloor(Floor.F7);
		s.onPosition(-50, 70, -50); // clear, outside boss
		assertFalse(s.inBoss());
		assertEquals(Phase.CLEAR, s.phase());
		s.onPosition(73, 170, 50); // joined mid-Storm
		assertTrue(s.inBoss());
		assertEquals(Phase.P2, s.phase());
		assertTrue(s.phaseFromFallback());
		// A real message wins over the fallback.
		s.onChat("[BOSS] Goldor: Who dares trespass into my domain?");
		assertEquals(Phase.P3, s.phase());
		assertFalse(s.phaseFromFallback());
	}

	@Test
	void yBands() {
		RunState s = new RunState();
		s.setFloor(Floor.F7);
		assertEquals(Phase.P1, s.phaseFromY(220));
		assertEquals(Phase.P2, s.phaseFromY(160));
		assertEquals(Phase.P3, s.phaseFromY(120));
		assertEquals(Phase.P4, s.phaseFromY(60));
		assertEquals(Phase.P4, s.phaseFromY(10), "no P5 on F7");
		s.setFloor(Floor.M7);
		assertEquals(Phase.P5, s.phaseFromY(10));
	}

	@Test
	void relogMidP3UsesProgressTotalAndPosition() {
		RunState s = new RunState();
		s.setFloor(Floor.F7);
		s.onChat("A activated a terminal! (3/8)");
		assertEquals(Phase.P3, s.phase());
		assertEquals(2, s.section(), "only S2 has 8 objectives");

		RunState t = new RunState();
		t.setFloor(Floor.F7);
		t.onPosition(0, 115, 85); // standing in S3
		assertEquals(Phase.P3, t.phase());
		assertEquals(3, t.section());
		t.onChat("A activated a terminal! (2/7)");
		assertEquals(3, t.section());
		assertEquals("P3 S3 (2/7)", t.phaseLabel());
	}

	@Test
	void noFloorNoRun() {
		RunState s = new RunState();
		s.onPosition(73, 221, 14);
		assertFalse(s.inRun());
		assertEquals(Phase.CLEAR, s.phase());
	}

	@Test
	void resetClearsEverything() {
		RunState s = new RunState();
		s.setFloor(Floor.F7);
		s.onChat("[BOSS] Goldor: Who dares trespass into my domain?");
		s.markObjectiveDone("s1_t1");
		s.reset();
		assertFalse(s.inRun());
		assertEquals(Phase.CLEAR, s.phase());
		assertFalse(s.objectiveDone("s1_t1"));
	}
}
