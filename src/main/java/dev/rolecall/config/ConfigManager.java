package dev.rolecall.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.rolecall.RoleCallClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and saves {@link RoleCallConfig}. Bad JSON falls back to defaults instead of crashing. */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	private static RoleCallConfig config;

	private ConfigManager() {
	}

	/** {@code config/rolecall/}: also where user overrides of the data files go. */
	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("rolecall");
	}

	public static Path path() {
		return dir().resolve("config.json");
	}

	public static RoleCallConfig get() {
		if (config == null) {
			load();
		}
		return config;
	}

	public static synchronized void load() {
		Path path = path();
		config = null;
		if (Files.exists(path)) {
			try {
				config = GSON.fromJson(Files.readString(path), RoleCallConfig.class);
			} catch (IOException | RuntimeException e) {
				RoleCallClient.LOGGER.error("Could not read {}, using defaults: {}", path, e.getMessage());
			}
		}
		if (config == null) {
			config = new RoleCallConfig();
		}
		config.normalize();
		save();
	}

	public static synchronized void save() {
		if (config == null) {
			return;
		}
		try {
			Files.createDirectories(dir());
			Files.writeString(path(), GSON.toJson(config));
		} catch (IOException e) {
			RoleCallClient.LOGGER.error("Could not save config: {}", e.getMessage());
		}
	}
}
