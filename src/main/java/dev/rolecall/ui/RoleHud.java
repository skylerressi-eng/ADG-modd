package dev.rolecall.ui;

import dev.rolecall.config.ConfigManager;
import dev.rolecall.config.RoleCallConfig;
import dev.rolecall.model.DungeonClass;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The step HUD. Milestone 1 shows the picked class and a placeholder step; Milestone 2
 * replaces the placeholder with the StepEngine output and hides the HUD outside F7.
 */
public final class RoleHud implements HudElement {
	private static final int PADDING = 4;
	private static final int BACKGROUND = 0xA0101010;
	private static final int HEADER_COLOR = 0xFFAAAAAA;
	private static final int STEP_COLOR = 0xFFFFFFFF;
	private static final int NEXT_COLOR = 0xFF888888;
	private static final int AUTO_COLOR = 0xFFDDDDDD;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		RoleCallConfig config = ConfigManager.get();
		if (!config.hudEnabled || mc.player == null || mc.options.hideGui) {
			return;
		}
		render(graphics, mc.font, config.hudX, config.hudY, config.hudScale);
	}

	/** Width/height in unscaled pixels, for the editor's hit box. */
	public static int[] size(Font font) {
		Lines l = lines();
		int w = Math.max(font.width(l.header), Math.max(font.width(l.step) * 2, font.width(l.next)));
		int h = font.lineHeight + 4 + font.lineHeight * 2 + 4 + font.lineHeight;
		return new int[]{w + 2 * PADDING, h + 2 * PADDING};
	}

	/** Shared with {@link HudEditorScreen} so the preview matches the real HUD. */
	public static void render(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale) {
		Lines l = lines();
		int[] size = size(font);
		// Keep it on screen if the window or GUI scale shrank since it was placed.
		x = Math.max(0, Math.min(x, graphics.guiWidth() - Math.round(size[0] * scale)));
		y = Math.max(0, Math.min(y, graphics.guiHeight() - Math.round(size[1] * scale)));

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);

		graphics.fill(0, 0, size[0], size[1], BACKGROUND);
		int ty = PADDING;
		graphics.text(font, l.header, PADDING, ty, HEADER_COLOR, true);
		ty += font.lineHeight + 4;

		// Current step, drawn at 2x.
		graphics.pose().pushMatrix();
		graphics.pose().translate(PADDING, ty);
		graphics.pose().scale(2f, 2f);
		graphics.text(font, l.step, 0, 0, STEP_COLOR, true);
		graphics.pose().popMatrix();
		ty += font.lineHeight * 2 + 4;

		graphics.text(font, l.next, PADDING, ty, NEXT_COLOR, true);
		graphics.pose().popMatrix();
	}

	private record Lines(Component header, Component step, Component next) {
	}

	private static Lines lines() {
		DungeonClass picked = ConfigManager.get().manualClass;
		MutableComponent cls = picked != null
				? Component.literal(picked.name()).withColor(picked.rgb())
				: Component.translatable("rolecall.class.auto").withColor(AUTO_COLOR & 0xFFFFFF);
		Component header = Component.literal("RoleCall · ").append(cls)
				.append(" · ").append(Component.translatable("rolecall.hud.preview_phase"));
		Component step = Component.literal("▶ ").append(Component.translatable("rolecall.hud.dummy_step"));
		Component next = Component.translatable("rolecall.hud.dummy_next");
		return new Lines(header, step, next);
	}
}
