package dev.rolecall.model;

/**
 * Who takes the S4 Sharpshooter device and who takes the S4 levers. The Berserk and
 * Healer agree on this each run. {@link #dataId()} is the value used in roles.json.
 */
public enum S4Split {
	/** Berserk does Sharpshooter, Healer does levers. */
	BERS_DEVICE("bers_device"),
	/** Berserk does levers, Healer does Sharpshooter. */
	BERS_LEVERS("bers_levers");

	private final String dataId;

	S4Split(String dataId) {
		this.dataId = dataId;
	}

	public String dataId() {
		return dataId;
	}

	public S4Split other() {
		return this == BERS_DEVICE ? BERS_LEVERS : BERS_DEVICE;
	}
}
