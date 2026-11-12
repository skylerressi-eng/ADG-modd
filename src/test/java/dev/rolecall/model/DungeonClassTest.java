package dev.rolecall.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DungeonClassTest {
	@Test
	void parsesHypixelAndShortNames() {
		assertEquals(DungeonClass.BERSERK, DungeonClass.parse("Berserker").orElseThrow());
		assertEquals(DungeonClass.BERSERK, DungeonClass.parse("berserk").orElseThrow());
		assertEquals(DungeonClass.MAGE, DungeonClass.parse(" MAGE ").orElseThrow());
		assertTrue(DungeonClass.parse("DEAD").isEmpty());
		assertTrue(DungeonClass.parse(null).isEmpty());
	}

	@Test
	void colorsMatchHypixelCodes() {
		assertEquals('6', DungeonClass.ARCHER.formatCode());
		assertEquals('4', DungeonClass.BERSERK.formatCode());
		assertEquals('d', DungeonClass.HEALER.formatCode());
		assertEquals('b', DungeonClass.MAGE.formatCode());
		assertEquals('2', DungeonClass.TANK.formatCode());
		assertEquals(0xFF55FFFF, DungeonClass.MAGE.argb());
	}

	@Test
	void predevAutoFollowsHealer() {
		assertTrue(PredevMode.AUTO.resolve(true));
		assertFalse(PredevMode.AUTO.resolve(false));
		assertFalse(PredevMode.OFF.resolve(true));
		assertEquals(S4Split.BERS_LEVERS, S4Split.BERS_DEVICE.other());
	}
}
