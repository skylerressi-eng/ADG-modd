package dev.rolecall.data;

import dev.rolecall.model.DungeonClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loaded, merged and validated roles + objectives + user spots/routes. Immutable once built. */
public final class RoleData {
	private final Map<DungeonClass, List<Step>> steps;
	private final Map<String, Objective> objectives;
	private final Map<String, List<int[]>> routes;
	private final List<String> problems;

	public RoleData(Map<DungeonClass, List<Step>> steps, Map<String, Objective> objectives,
					Map<String, List<int[]>> routes, List<String> problems) {
		Map<DungeonClass, List<Step>> s = new EnumMap<>(DungeonClass.class);
		for (DungeonClass c : DungeonClass.values()) {
			s.put(c, List.copyOf(steps.getOrDefault(c, List.of())));
		}
		this.steps = Collections.unmodifiableMap(s);
		this.objectives = Collections.unmodifiableMap(new LinkedHashMap<>(objectives));
		this.routes = Collections.unmodifiableMap(new LinkedHashMap<>(routes));
		this.problems = List.copyOf(problems);
	}

	public static RoleData empty() {
		return new RoleData(Map.of(), Map.of(), Map.of(), List.of());
	}

	public List<Step> steps(DungeonClass c) {
		return steps.get(c);
	}

	public Map<String, Objective> objectives() {
		return objectives;
	}

	public Objective objective(String id) {
		return objectives.get(id);
	}

	public List<int[]> route(String name) {
		return routes.getOrDefault(name, List.of());
	}

	public Map<String, List<int[]>> routes() {
		return routes;
	}

	/** Validation problems found while loading (shown in chat and the log). */
	public List<String> problems() {
		return problems;
	}

	public int stepCount() {
		int n = 0;
		for (List<Step> l : steps.values()) {
			n += l.size();
		}
		return n;
	}

	public List<Objective> objectivesOfKind(String kind, Integer section) {
		List<Objective> out = new ArrayList<>();
		for (Objective o : objectives.values()) {
			if (kind.equals(o.kind) && (section == null || section.equals(o.section))) {
				out.add(o);
			}
		}
		return out;
	}
}
