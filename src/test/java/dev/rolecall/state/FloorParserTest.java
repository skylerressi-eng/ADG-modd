package dev.rolecall.state;

import dev.rolecall.model.Floor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FloorParserTest {
	@Test
	void findsFloorInHypixelStyleLines() {
		assertEquals(Floor.F7, FloorParser.floor(List.of("11/12/26 m12AB", " ⏣ The Catacombs (F7)", "Time Elapsed: 05m")).orElseThrow());
		assertEquals(Floor.M7, FloorParser.floor(List.of(" ⏣ The Catacombs 🎂(M7)")).orElseThrow());
		assertEquals(Floor.F7, FloorParser.floor(List.of("§7 ⏣ §cThe Catacombs §7(F7)")).orElseThrow());
	}

	@Test
	void otherFloorsAndPlacesAreIgnored() {
		assertTrue(FloorParser.floor(List.of(" ⏣ The Catacombs (F6)")).isEmpty());
		assertTrue(FloorParser.floor(List.of(" ⏣ The Catacombs (E)")).isEmpty());
		assertTrue(FloorParser.floor(List.of(" ⏣ Village", "Purse: 1,000")).isEmpty());
		assertEquals("F6", FloorParser.find(List.of(" ⏣ The Catacombs (F6)")).orElseThrow()[1]);
	}
}
