package dev.rolecall.state;

import dev.rolecall.state.ChatTriggers.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatTriggersTest {
	private static Kind kind(String line) {
		return ChatTriggers.parse(line).map(ChatTriggers.Event::kind).orElse(null);
	}

	@Test
	void bossLines() {
		assertEquals(Kind.P1_START, kind("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!"));
		assertEquals(Kind.P2_START, kind("[BOSS] Storm: Pathetic Maxor, just like expected."));
		assertEquals(Kind.STORM_DEAD, kind("[BOSS] Storm: I should have known that I stood no chance."));
		assertEquals(Kind.P3_START, kind("[BOSS] Goldor: Who dares trespass into my domain?"));
		assertEquals(Kind.CORE_OPEN, kind("The Core entrance is opening!"));
		assertEquals(Kind.P4_START, kind("[BOSS] Necron: You went further than any human before, congratulations."));
		assertEquals(Kind.NECRON_DEAD, kind("[BOSS] Necron: All this, for nothing..."));
		assertEquals(Kind.WITHER_KING, kind("[BOSS] Wither King: You... again?"));
		assertEquals(Kind.LIGHTNING, kind("[BOSS] Storm: ENERGY HEED MY CALL!"));
		assertEquals(Kind.LIGHTNING, kind("[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!"));
		assertEquals(Kind.GATE, kind("The gate has been destroyed!"));
		assertEquals(Kind.RUN_END, kind("                      ☠ Defeated Maxor, Storm, Goldor, and Necron in 05m 12s"));
	}

	@Test
	void progressLines() {
		ChatTriggers.Event e = ChatTriggers.parse("Skyler_99 activated a terminal! (3/7)").orElseThrow();
		assertEquals(Kind.TERMINAL, e.kind());
		assertEquals("Skyler_99", e.player());
		assertEquals(3, e.count());
		assertEquals(7, e.total());
		assertEquals(Kind.DEVICE, kind("Bob completed a device! (8/8)"));
		assertEquals(Kind.LEVER, kind("Bob activated a lever! (1/7)"));
	}

	@Test
	void anchoredAndStrict() {
		assertNull(kind("Party > [MVP+] Bob: The gate has been destroyed!"));
		assertNull(kind("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE! extra"));
		assertNull(kind("Bob activated a terminal (3/7)"));
		assertNull(kind(""));
		assertNull(kind(null));
	}
}
