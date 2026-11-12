package dev.rolecall.ui;

import dev.rolecall.RoleCall;
import dev.rolecall.config.ConfigManager;
import dev.rolecall.data.Step;
import dev.rolecall.model.DungeonClass;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Outlines the right head in Hypixel's Spirit Leap menu. Render only: it never clicks, never
 * changes what a click does, and never touches the container.
 */
public final class LeapHighlight {
	private static final Set<String> TITLES = Set.of("Spirit Leap", "Teleport to Player");

	private LeapHighlight() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof ContainerScreen cs && TITLES.contains(screen.getTitle().getString().strip())) {
				ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> draw(cs, g));
			}
		});
	}

	private static void draw(ContainerScreen screen, GuiGraphicsExtractor g) {
		if (!ConfigManager.get().leapHighlight) return;
		Step cur = RoleCall.view().current();
		if (cur == null || cur.leap == null) return;
		DungeonClass target = DungeonClass.parse(cur.leap).orElse(null);
		if (target == null) return;
		List<String> names = RoleCall.namesOf(target);

		int rows = screen.getMenu().getRowCount();
		int left = (screen.width - 176) / 2;
		int top = (screen.height - (114 + rows * 18)) / 2;
		boolean found = false;
		for (Slot slot : screen.getMenu().slots) {
			if (slot.index >= rows * 9 || slot.container == Minecraft.getInstance().player.getInventory()) continue;
			if (slot.getItem().isEmpty()) continue;
			List<String> words = List.of(slot.getItem().getHoverName().getString().toLowerCase(Locale.ROOT).split("[^a-z0-9_]+"));
			for (String n : names) {
				if (words.contains(n.toLowerCase(Locale.ROOT))) {
					int x = left + slot.x, y = top + slot.y;
					for (int i = 1; i <= 2; i++) {
						g.outline(x - i, y - i, 16 + 2 * i, 16 + 2 * i, target.argb());
					}
					found = true;
				}
			}
		}
		Component msg = Component.literal("RoleCall: ").append(RoleCall.leapComponent(target))
				.append(found || names.isEmpty() ? Component.empty() : Component.literal(" (not in menu)").withColor(0xAAAAAA));
		g.centeredText(Minecraft.getInstance().font, msg, screen.width / 2, Math.min(screen.height - 10, top + 114 + rows * 18 + 4), 0xFFFFFFFF);
	}
}
