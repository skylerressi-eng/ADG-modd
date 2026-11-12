package dev.rolecall;

import dev.rolecall.data.RoleData;
import dev.rolecall.data.RoleDataLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class TestData {
	private TestData() {
	}

	public static String resource(String path) {
		try (InputStream in = TestData.class.getResourceAsStream("/assets/rolecall/" + path)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static RoleData bundled() {
		return RoleDataLoader.load(resource("data/roles.json"), null, resource("data/objectives.json"), null, null);
	}
}
