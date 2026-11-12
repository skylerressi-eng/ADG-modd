package dev.rolecall.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rolecall.model.DungeonClass;
import dev.rolecall.model.Floor;
import dev.rolecall.model.Phase;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds {@link RoleData} from the bundled JSON plus the user's override files. Pure Java
 * (takes strings) so it is unit-testable. Never throws: bad JSON or bad steps become
 * entries in {@link RoleData#problems()} and the rest still loads.
 *
 * <p>Override rules for roles.json: steps are matched by {@code id} within a class. A matching
 * step has its fields replaced one by one; {@code "remove": true} deletes it; a new id is
 * inserted after {@code "after": "<id>"} or appended. Objectives merge by id the same way.
 */
public final class RoleDataLoader {
	private static final Gson GSON = new Gson();
	private static final Set<String> EVENTS = Set.of("lightning", "storm_dead", "gate", "core");
	private static final Set<String> KINDS = Set.of("terminal", "device", "lever", "relic", "spot");

	private RoleDataLoader() {
	}

	public static RoleData load(String defaultRoles, String userRoles, String defaultObjectives,
								String userObjectives, String userSpots) {
		List<String> problems = new ArrayList<>();

		// ---- objectives (fixed) + user spots
		Map<String, JsonObject> objJson = new LinkedHashMap<>();
		readObjectives(defaultObjectives, "objectives.json (bundled)", objJson, problems);
		readObjectives(userObjectives, "config/rolecall/objectives.json", objJson, problems);
		Map<String, List<int[]>> routes = new LinkedHashMap<>();
		readSpots(userSpots, objJson, routes, problems);

		Map<String, Objective> objectives = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> e : objJson.entrySet()) {
			try {
				Objective o = GSON.fromJson(e.getValue(), Objective.class);
				o.id = e.getKey();
				if (o.pos == null || o.pos.length != 3) {
					problems.add("objective " + o.id + ": pos must be [x, y, z]");
					continue;
				}
				if (o.kind == null || !KINDS.contains(o.kind)) {
					problems.add("objective " + o.id + ": unknown kind '" + o.kind + "'");
					continue;
				}
				objectives.put(o.id, o);
			} catch (RuntimeException ex) {
				problems.add("objective " + e.getKey() + ": " + ex.getMessage());
			}
		}

		// ---- roles
		Map<DungeonClass, List<JsonObject>> rolesJson = new EnumMap<>(DungeonClass.class);
		readRoles(defaultRoles, "roles.json (bundled)", rolesJson, problems);
		readRoles(userRoles, "config/rolecall/roles.json", rolesJson, problems);

		Map<DungeonClass, List<Step>> steps = new EnumMap<>(DungeonClass.class);
		for (Map.Entry<DungeonClass, List<JsonObject>> e : rolesJson.entrySet()) {
			List<Step> list = new ArrayList<>();
			for (JsonObject jo : e.getValue()) {
				try {
					Step s = GSON.fromJson(jo, Step.class);
					String err = validate(s, objectives, routes);
					if (err != null) {
						problems.add(e.getKey() + " step " + s.id + ": " + err);
						continue;
					}
					list.add(s);
				} catch (RuntimeException ex) {
					problems.add(e.getKey() + " step: " + ex.getMessage());
				}
			}
			steps.put(e.getKey(), list);
		}
		return new RoleData(steps, objectives, routes, problems);
	}

	private static String validate(Step s, Map<String, Objective> objectives, Map<String, List<int[]>> routes) {
		if (s.id == null || s.id.isBlank()) return "missing id";
		if (s.text == null || s.text.isBlank()) return "missing text";
		Phase p = Phase.parse(s.phase);
		if (p == null || p == Phase.DONE) return "unknown phase '" + s.phase + "'";
		if (s.section != null && (p != Phase.P3 || s.section < 1 || s.section > 4)) {
			return "section must be 1-4 and only on P3";
		}
		if (s.leap != null && DungeonClass.parse(s.leap).isEmpty()) return "unknown leap class '" + s.leap + "'";
		if (s.floors != null) {
			for (String f : s.floors) {
				if (Floor.parse(f).isEmpty()) return "unknown floor '" + f + "'";
			}
		}
		if (s.until != null) {
			if (s.until.event != null && !EVENTS.contains(s.until.event)) return "unknown until.event '" + s.until.event + "'";
			if (s.until.objectiveDone != null) {
				for (String id : s.until.objectiveDone) {
					if (!objectives.containsKey(id)) return "until.objectiveDone: unknown objective '" + id + "'";
				}
			}
		}
		if (s.requires != null && s.requires.s4split != null
				&& !s.requires.s4split.equals("bers_device") && !s.requires.s4split.equals("bers_levers")) {
			return "requires.s4split must be bers_device or bers_levers";
		}
		// Unknown waypoint/route ids are allowed: user spots may not be recorded yet. They just don't draw.
		return null;
	}

	private static Optional<JsonObject> parseObject(String json, String name, List<String> problems) {
		if (json == null || json.isBlank()) {
			return Optional.empty();
		}
		try {
			JsonElement el = JsonParser.parseString(json);
			if (!el.isJsonObject()) {
				problems.add(name + ": top level must be an object");
				return Optional.empty();
			}
			return Optional.of(el.getAsJsonObject());
		} catch (RuntimeException ex) {
			problems.add(name + ": invalid JSON (" + ex.getMessage() + ")");
			return Optional.empty();
		}
	}

	private static void readRoles(String json, String name, Map<DungeonClass, List<JsonObject>> into, List<String> problems) {
		Optional<JsonObject> root = parseObject(json, name, problems);
		if (root.isEmpty() || !root.get().has("classes")) {
			return;
		}
		JsonObject classes = root.get().getAsJsonObject("classes");
		for (Map.Entry<String, JsonElement> ce : classes.entrySet()) {
			Optional<DungeonClass> c = DungeonClass.parse(ce.getKey());
			if (c.isEmpty()) {
				problems.add(name + ": unknown class '" + ce.getKey() + "'");
				continue;
			}
			if (!ce.getValue().isJsonArray()) {
				problems.add(name + ": " + ce.getKey() + " must be a list of steps");
				continue;
			}
			List<JsonObject> list = into.computeIfAbsent(c.get(), k -> new ArrayList<>());
			for (JsonElement se : ce.getValue().getAsJsonArray()) {
				if (!se.isJsonObject()) {
					problems.add(name + ": " + ce.getKey() + " has a non-object step");
					continue;
				}
				mergeStep(list, se.getAsJsonObject().deepCopy());
			}
		}
	}

	private static void mergeStep(List<JsonObject> list, JsonObject step) {
		String id = step.has("id") ? step.get("id").getAsString() : null;
		int existing = indexOf(list, id);
		boolean remove = step.has("remove") && step.get("remove").getAsBoolean();
		if (existing >= 0) {
			if (remove) {
				list.remove(existing);
				return;
			}
			JsonObject target = list.get(existing);
			for (Map.Entry<String, JsonElement> f : step.entrySet()) {
				if (!f.getKey().equals("after")) {
					target.add(f.getKey(), f.getValue());
				}
			}
			return;
		}
		if (remove) {
			return;
		}
		String after = step.has("after") ? step.remove("after").getAsString() : null;
		int at = indexOf(list, after);
		if (at >= 0) {
			list.add(at + 1, step);
		} else {
			list.add(step);
		}
	}

	private static int indexOf(List<JsonObject> list, String id) {
		if (id == null) {
			return -1;
		}
		for (int i = 0; i < list.size(); i++) {
			JsonObject o = list.get(i);
			if (o.has("id") && id.equals(o.get("id").getAsString())) {
				return i;
			}
		}
		return -1;
	}

	private static void readObjectives(String json, String name, Map<String, JsonObject> into, List<String> problems) {
		Optional<JsonObject> root = parseObject(json, name, problems);
		if (root.isEmpty() || !root.get().has("objectives")) {
			return;
		}
		for (JsonElement el : root.get().getAsJsonArray("objectives")) {
			if (!el.isJsonObject() || !el.getAsJsonObject().has("id")) {
				problems.add(name + ": objective without id");
				continue;
			}
			JsonObject o = el.getAsJsonObject();
			String id = o.get("id").getAsString();
			if (o.has("remove") && o.get("remove").getAsBoolean()) {
				into.remove(id);
				continue;
			}
			JsonObject target = into.get(id);
			if (target == null) {
				into.put(id, o.deepCopy());
			} else {
				for (Map.Entry<String, JsonElement> f : o.entrySet()) {
					target.add(f.getKey(), f.getValue());
				}
			}
		}
	}

	/** user_waypoints.json: {"spots": {"id": {"pos": [x,y,z], "label": "..."}}, "routes": {"name": [[x,y,z], ...]}} */
	private static void readSpots(String json, Map<String, JsonObject> objectives, Map<String, List<int[]>> routes,
								  List<String> problems) {
		String name = "config/rolecall/user_waypoints.json";
		Optional<JsonObject> root = parseObject(json, name, problems);
		if (root.isEmpty()) {
			return;
		}
		if (root.get().has("spots")) {
			Set<String> seen = new HashSet<>();
			for (Map.Entry<String, JsonElement> e : root.get().getAsJsonObject("spots").entrySet()) {
				if (!e.getValue().isJsonObject() || !seen.add(e.getKey())) {
					continue;
				}
				JsonObject o = e.getValue().getAsJsonObject().deepCopy();
				o.addProperty("id", e.getKey());
				if (!o.has("kind")) {
					o.addProperty("kind", "spot");
				}
				objectives.put(e.getKey(), o);
			}
		}
		if (root.get().has("routes")) {
			for (Map.Entry<String, JsonElement> e : root.get().getAsJsonObject("routes").entrySet()) {
				List<int[]> pts = new ArrayList<>();
				if (e.getValue().isJsonArray()) {
					for (JsonElement p : e.getValue().getAsJsonArray()) {
						JsonArray a = p.isJsonArray() ? p.getAsJsonArray() : null;
						if (a != null && a.size() == 3) {
							pts.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()});
						} else {
							problems.add(name + ": route " + e.getKey() + " has a bad point");
						}
					}
				}
				routes.put(e.getKey(), pts);
			}
		}
	}
}
