package dev.rolecall.data;

import java.util.List;

/** A fixed F7/M7 boss location from objectives.json, or a user spot saved with /rolecall here. */
public final class Objective {
	public String id;
	/** "terminal", "device", "lever", "relic", "spot". */
	public String kind;
	/** P3 section 1-4, or null. */
	public Integer section;
	public String label;
	public int[] pos;
	/** For levers: which side ("left"/"right") the guide calls it. */
	public String side;
	/** Restrict to F7 or M7; null = both. */
	public List<String> floors;
	/** Hex colour like "FF5555" for the box; null = class colour. */
	public String color;

	public Objective() {
	}

	public Objective(String id, String kind, Integer section, String label, int x, int y, int z) {
		this.id = id;
		this.kind = kind;
		this.section = section;
		this.label = label;
		this.pos = new int[]{x, y, z};
	}

	public int x() {
		return pos[0];
	}

	public int y() {
		return pos[1];
	}

	public int z() {
		return pos[2];
	}

	public double distSq(double px, double py, double pz) {
		double dx = px - (x() + 0.5), dy = py - (y() + 0.5), dz = pz - (z() + 0.5);
		return dx * dx + dy * dy + dz * dz;
	}
}
