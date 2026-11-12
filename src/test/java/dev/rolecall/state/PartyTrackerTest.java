package dev.rolecall.state;

import dev.rolecall.model.DungeonClass;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PartyTrackerTest {
	@Test
	void readsAllFiveClasses() {
		PartyTracker p = new PartyTracker();
		p.update(List.of(
				"         Party (5)",
				"[412] Skyler ♲ (Healer L)",
				"[380] [YOUTUBE] Arch ⚔ (Archer L)",
				"[350] [MVP+] Bers (Berserk XLV)",
				"[333] Magey (Mage XL)",
				"[301] Tanky ✿ (Tank L)"));
		assertEquals(DungeonClass.HEALER, p.classOf("Skyler").orElseThrow());
		assertEquals(DungeonClass.ARCHER, p.classOf("Arch").orElseThrow());
		assertEquals(DungeonClass.BERSERK, p.classOf("Bers").orElseThrow());
		assertEquals(DungeonClass.MAGE, p.classOf("magey").orElseThrow());
		assertEquals(DungeonClass.TANK, p.classOf("Tanky").orElseThrow());
		assertEquals(5, p.byClass().size());
		assertTrue(p.unmatchedLines().isEmpty());
	}

	@Test
	void deadKeepsLastClass() {
		PartyTracker p = new PartyTracker();
		p.update(List.of("[300] Bob (Mage L)"));
		p.update(List.of("[300] Bob (DEAD)"));
		PartyTracker.Member m = p.members().iterator().next();
		assertTrue(m.dead());
		assertEquals(DungeonClass.MAGE, m.dungeonClass());
	}

	@Test
	void duplicatesAndGaps() {
		PartyTracker p = new PartyTracker();
		p.update(List.of("[1] A (Mage L)", "[1] B (Mage L)", "[1] C (Archer L)", "[1] D (Tank L)", "[1] E (Berserk L)"));
		assertEquals(List.of("A", "B"), p.byClass().get(DungeonClass.MAGE));
		assertFalse(p.has(DungeonClass.HEALER));
	}

	@Test
	void remembersUnmatchedPlayerLines() {
		PartyTracker p = new PartyTracker();
		p.update(List.of("[1] A (Wizard L)", "Server: mini123"));
		assertEquals(List.of("[1] A (Wizard L)"), p.unmatchedLines());
	}
}
