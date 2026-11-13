package dev.rolecall.model;

/**
 * How the party crushes Storm in P2. {@link #dataId()} is the value used in roles.json
 * ({@code "requires": {"crush": "py"}}).
 */
public enum StormCrush {
	/** Purple/yellow: Tank crushes on purple, Mage on yellow. No third player needed. */
	PY("py", "Purple / Yellow"),
	/** Green/yellow: Healer drops green, Tank drops yellow. */
	GY("gy", "Green / Yellow");

	private final String dataId;
	private final String label;

	StormCrush(String dataId, String label) {
		this.dataId = dataId;
		this.label = label;
	}

	public String dataId() {
		return dataId;
	}

	public String label() {
		return label;
	}

	public StormCrush other() {
		return this == PY ? GY : PY;
	}
}
