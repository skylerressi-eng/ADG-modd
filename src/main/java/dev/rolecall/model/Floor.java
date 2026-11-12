package dev.rolecall.model;

import java.util.Optional;

/** The two floors RoleCall supports. Anything else means "not in a supported run". */
public enum Floor {
	F7, M7;

	public static Optional<Floor> parse(String s) {
		if (s == null) {
			return Optional.empty();
		}
		return switch (s.trim().toUpperCase(java.util.Locale.ROOT)) {
			case "F7" -> Optional.of(F7);
			case "M7" -> Optional.of(M7);
			default -> Optional.empty();
		};
	}
}
