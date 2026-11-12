package dev.rolecall;

import dev.rolecall.cmd.RoleCallCommands;
import dev.rolecall.config.ConfigManager;
import dev.rolecall.ui.RoleHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Client entrypoint. RoleCall is display-only: it reads chat/tab list/scoreboard/position
 * and draws HUD text and waypoints. It never clicks, moves, sends chat, or touches packets.
 */
public final class RoleCallClient implements ClientModInitializer {
	public static final String MOD_ID = "rolecall";
	public static final Logger LOGGER = LoggerFactory.getLogger("RoleCall");

	private static final AtomicReference<Screen> PENDING_SCREEN = new AtomicReference<>();

	@Override
	public void onInitializeClient() {
		ConfigManager.load();
		RoleCallCommands.register();
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), new RoleHud());
		ClientTickEvents.END_CLIENT_TICK.register(RoleCallClient::onEndTick);
		LOGGER.info("RoleCall initialized (display-only)");
	}

	private static void onEndTick(Minecraft mc) {
		Screen pending = PENDING_SCREEN.getAndSet(null);
		if (pending != null) {
			mc.setScreenAndShow(pending);
		}
	}

	/** Opens a screen on the next tick; from a command the chat screen would replace it otherwise. */
	public static void openScreenNextTick(Screen screen) {
		PENDING_SCREEN.set(screen);
	}
}
