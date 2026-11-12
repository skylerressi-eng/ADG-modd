package dev.rolecall.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rolecall.RoleCallClient;
import dev.rolecall.config.ConfigManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads the bundled data files and the user's files in config/rolecall/, and writes user spots/routes. */
public final class DataFiles {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private DataFiles() {
	}

	public static RoleData loadAll() {
		ensureUserStubs();
		return RoleDataLoader.load(
				bundled("data/roles.json"), user("roles.json"),
				bundled("data/objectives.json"), user("objectives.json"),
				user("user_waypoints.json"));
	}

	public static String bundled(String path) {
		try (InputStream in = DataFiles.class.getResourceAsStream("/assets/rolecall/" + path)) {
			return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			RoleCallClient.LOGGER.error("Could not read bundled {}: {}", path, e.getMessage());
			return null;
		}
	}

	public static String user(String name) {
		Path p = ConfigManager.dir().resolve(name);
		try {
			return Files.exists(p) ? Files.readString(p) : null;
		} catch (IOException e) {
			RoleCallClient.LOGGER.error("Could not read {}: {}", p, e.getMessage());
			return null;
		}
	}

	/** Creates empty override files with a how-to comment, so players can find them. */
	private static void ensureUserStubs() {
		writeIfMissing("roles.json", """
				{
				  "_comment": [
				    "Your edits to the RoleCall role steps. Same format as the bundled roles.json (see the README).",
				    "Steps are matched by id inside a class: give the id and only the fields you want to change.",
				    "\\"remove\\": true deletes a step; a new id is added (use \\"after\\": \\"<id>\\" to place it).",
				    "Example: {\\"classes\\": {\\"MAGE\\": [{\\"id\\": \\"m_s1_t4\\", \\"text\\": \\"Do T4, then help with T3\\"}]}}",
				    "Run /rolecall reload after saving."
				  ],
				  "classes": {}
				}
				""");
		writeIfMissing("objectives.json", """
				{
				  "_comment": "Override or add fixed waypoints. Same format as the bundled objectives.json, matched by id.",
				  "objectives": []
				}
				""");
	}

	private static void writeIfMissing(String name, String content) {
		Path p = ConfigManager.dir().resolve(name);
		try {
			if (!Files.exists(p)) {
				Files.createDirectories(p.getParent());
				Files.writeString(p, content);
			}
		} catch (IOException e) {
			RoleCallClient.LOGGER.error("Could not create {}: {}", p, e.getMessage());
		}
	}

	// ------------------------------------------------------------------ user spots and routes

	private static JsonObject readUserWaypoints() {
		String s = user("user_waypoints.json");
		try {
			if (s != null && !s.isBlank()) {
				JsonObject o = JsonParser.parseString(s).getAsJsonObject();
				if (!o.has("spots")) o.add("spots", new JsonObject());
				if (!o.has("routes")) o.add("routes", new JsonObject());
				return o;
			}
		} catch (RuntimeException e) {
			RoleCallClient.LOGGER.error("user_waypoints.json is invalid; not overwriting it");
			throw e;
		}
		JsonObject o = new JsonObject();
		o.add("spots", new JsonObject());
		o.add("routes", new JsonObject());
		return o;
	}

	private static void writeUserWaypoints(JsonObject o) throws IOException {
		Path p = ConfigManager.dir().resolve("user_waypoints.json");
		Files.createDirectories(p.getParent());
		Files.writeString(p, GSON.toJson(o));
	}

	public static void saveSpot(String id, String label, int x, int y, int z) throws IOException {
		JsonObject root = readUserWaypoints();
		JsonObject spot = new JsonObject();
		JsonArray pos = new JsonArray();
		pos.add(x);
		pos.add(y);
		pos.add(z);
		spot.add("pos", pos);
		spot.addProperty("label", label == null || label.isBlank() ? id : label);
		root.getAsJsonObject("spots").add(id, spot);
		writeUserWaypoints(root);
	}

	public static boolean removeSpot(String id) throws IOException {
		JsonObject root = readUserWaypoints();
		boolean had = root.getAsJsonObject("spots").remove(id) != null;
		writeUserWaypoints(root);
		return had;
	}

	/** Appends a point and returns the route's new length. */
	public static int addRoutePoint(String route, int x, int y, int z) throws IOException {
		JsonObject root = readUserWaypoints();
		JsonObject routes = root.getAsJsonObject("routes");
		JsonArray pts = routes.has(route) ? routes.getAsJsonArray(route) : new JsonArray();
		JsonArray p = new JsonArray();
		p.add(x);
		p.add(y);
		p.add(z);
		pts.add(p);
		routes.add(route, pts);
		writeUserWaypoints(root);
		return pts.size();
	}

	public static int undoRoutePoint(String route) throws IOException {
		JsonObject root = readUserWaypoints();
		JsonObject routes = root.getAsJsonObject("routes");
		if (!routes.has(route) || routes.getAsJsonArray(route).isEmpty()) {
			return 0;
		}
		JsonArray pts = routes.getAsJsonArray(route);
		pts.remove(pts.size() - 1);
		writeUserWaypoints(root);
		return pts.size();
	}

	public static void clearRoute(String route) throws IOException {
		JsonObject root = readUserWaypoints();
		root.getAsJsonObject("routes").remove(route);
		writeUserWaypoints(root);
	}

	public static String[] listSims() {
		return new String[]{"f7_full", "m7_p5"};
	}
}
