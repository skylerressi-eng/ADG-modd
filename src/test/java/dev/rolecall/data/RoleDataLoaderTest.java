package dev.rolecall.data;

import dev.rolecall.TestData;
import dev.rolecall.model.DungeonClass;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoleDataLoaderTest {
	private static final String ROLES = TestData.resource("data/roles.json");
	private static final String OBJ = TestData.resource("data/objectives.json");

	@Test
	void overrideReplacesRemovesAndInserts() {
		String user = """
				{"classes": {"MAGE": [
				  {"id": "m_s1_t4", "text": "Do T4 (ADG wording)"},
				  {"id": "m_p4", "remove": true},
				  {"id": "m_clear_1", "phase": "CLEAR", "text": "Blood rush"},
				  {"id": "m_p1_extra", "phase": "P1", "text": "Extra", "after": "m_p1"}
				]}}""";
		RoleData d = RoleDataLoader.load(ROLES, user, OBJ, null, null);
		assertEquals(List.of(), d.problems());
		List<String> ids = d.steps(DungeonClass.MAGE).stream().map(s -> s.id).toList();
		assertFalse(ids.contains("m_p4"));
		assertEquals(ids.indexOf("m_p1") + 1, ids.indexOf("m_p1_extra"));
		Step t4 = d.steps(DungeonClass.MAGE).stream().filter(s -> s.id.equals("m_s1_t4")).findFirst().orElseThrow();
		assertEquals("Do T4 (ADG wording)", t4.text);
		assertEquals(List.of("s1_t4"), t4.waypoints, "fields not in the override are kept");
	}

	@Test
	void badJsonDoesNotCrash() {
		RoleData d = RoleDataLoader.load(ROLES, "{ not json", OBJ, "[]", "{\"routes\": {\"predev\": [[1,2]]}}");
		assertEquals(75, d.stepCount(), "bundled steps still load");
		assertEquals(3, d.problems().size(), d.problems().toString());
	}

	@Test
	void invalidStepsAreDroppedWithAReason() {
		String user = """
				{"classes": {"TANK": [
				  {"id": "x1", "phase": "P9", "text": "?"},
				  {"id": "x2", "phase": "P1", "section": 2, "text": "?"},
				  {"id": "x3", "phase": "P2", "text": "?", "leap": "WIZARD"},
				  {"id": "x4", "phase": "P2", "text": "?", "until": {"objectiveDone": ["nope"]}}
				]}, "classes_typo": {}}""";
		RoleData d = RoleDataLoader.load(ROLES, user, OBJ, null, null);
		assertEquals(4, d.problems().size(), d.problems().toString());
	}

	@Test
	void userSpotsAndRoutes() {
		String spots = """
				{"spots": {"ss_wait": {"pos": [108, 120, 93], "label": "SS wait spot"}},
				 "routes": {"predev": [[70, 220, 30], [65, 180, 60], [60, 140, 120]]}}""";
		RoleData d = RoleDataLoader.load(ROLES, null, OBJ, null, spots);
		assertEquals("spot", d.objective("ss_wait").kind);
		assertEquals(3, d.route("predev").size());
	}
}
