package dev.rolecall.ui;

import dev.rolecall.config.ConfigManager;
import dev.rolecall.config.RoleCallConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag to move the HUD, scroll to scale it. Saves on close. */
public final class HudEditorScreen extends Screen {
	private boolean dragging;
	private double grabDx, grabDy;

	public HudEditorScreen() {
		super(Component.translatable("rolecall.editor.title"));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractTransparentBackground(graphics);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		RoleCallConfig c = ConfigManager.get();
		RoleHud.render(graphics, this.font, c.hudX, c.hudY, c.hudScale);
		int[] s = scaledSize();
		graphics.outline(c.hudX - 1, c.hudY - 1, s[0] + 2, s[1] + 2, dragging ? 0xFFFFFF55 : 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("rolecall.editor.help",
				String.format("%.2f", c.hudScale)), this.width / 2, this.height - 30, 0xFFFFFFFF);
	}

	private int[] scaledSize() {
		int[] s = RoleHud.size(this.font);
		float scale = ConfigManager.get().hudScale;
		return new int[]{Math.round(s[0] * scale), Math.round(s[1] * scale)};
	}

	private boolean overHud(double mx, double my) {
		RoleCallConfig c = ConfigManager.get();
		int[] s = scaledSize();
		return mx >= c.hudX && mx <= c.hudX + s[0] && my >= c.hudY && my <= c.hudY + s[1];
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && overHud(event.x(), event.y())) {
			RoleCallConfig c = ConfigManager.get();
			dragging = true;
			grabDx = event.x() - c.hudX;
			grabDy = event.y() - c.hudY;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			RoleCallConfig c = ConfigManager.get();
			int[] s = scaledSize();
			c.hudX = clamp((int) Math.round(event.x() - grabDx), 0, this.width - s[0]);
			c.hudY = clamp((int) Math.round(event.y() - grabDy), 0, this.height - s[1]);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging && event.button() == 0) {
			dragging = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		RoleCallConfig c = ConfigManager.get();
		c.hudScale = Math.max(0.5f, Math.min(3.0f, c.hudScale + (float) scrollY * 0.1f));
		int[] s = scaledSize();
		c.hudX = clamp(c.hudX, 0, this.width - s[0]);
		c.hudY = clamp(c.hudY, 0, this.height - s[1]);
		return true;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(v, Math.max(min, max)));
	}

	@Override
	public void onClose() {
		ConfigManager.save();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
