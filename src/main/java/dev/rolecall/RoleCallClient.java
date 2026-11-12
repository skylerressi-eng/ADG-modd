package dev.rolecall;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rolecall.cmd.RoleCallCommands;
import dev.rolecall.config.ConfigManager;
import dev.rolecall.render.WaypointRenderer;
import dev.rolecall.ui.ClassPickerScreen;
import dev.rolecall.ui.DebugOverlay;
import dev.rolecall.ui.LeapHighlight;
import dev.rolecall.ui.RoleHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Client entrypoint. RoleCall is display-only: it reads chat, the tab list, the scoreboard and
 * your own position, and draws HUD text, fixed-coordinate waypoints and GUI outlines. It never
 * clicks, moves, sends chat or touches packets.
 */
public final class RoleCallClient implements ClientModInitializer {
	public static final String MOD_ID = "rolecall";
	public static final Logger LOGGER = LoggerFactory.getLogger("RoleCall");

	private static final AtomicReference<Screen> PENDING_SCREEN = new AtomicReference<>();
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
	private static KeyMapping nextKey;
	private static KeyMapping backKey;
	private static KeyMapping pickerKey;

	@Override
	public void onInitializeClient() {
		ConfigManager.load();
		RoleCall.init();
		RoleCallCommands.register();

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), new RoleHud());
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "debug"), new DebugOverlay());
		LevelRenderEvents.COLLECT_SUBMITS.register(WaypointRenderer::render);
		LeapHighlight.register();

		// Read-only: we look at the text and never cancel or change the message.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				RoleCall.onChat(message.getString());
			}
		});

		nextKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.rolecall.next", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
		backKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.rolecall.back", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
		pickerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.rolecall.picker", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(RoleCallClient::onEndTick);
		LOGGER.info("RoleCall initialized (display-only)");
	}

	private static void onEndTick(Minecraft mc) {
		Screen pending = PENDING_SCREEN.getAndSet(null);
		if (pending != null) {
			mc.setScreenAndShow(pending);
		}
		while (nextKey.consumeClick()) RoleCall.nextStep();
		while (backKey.consumeClick()) RoleCall.previousStep();
		while (pickerKey.consumeClick()) mc.setScreenAndShow(new ClassPickerScreen());
		RoleCall.tick(mc);
	}

	/** Opens a screen on the next tick; from a command the chat screen would replace it otherwise. */
	public static void openScreenNextTick(Screen screen) {
		PENDING_SCREEN.set(screen);
	}
}
