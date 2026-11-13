package dev.rolecall.ui;

import dev.rolecall.config.ConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * RoleCall's own on-screen callouts: a compact banner just above the crosshair, with a coloured
 * accent bar, a short main line and an optional sub-line. Smaller and less intrusive than
 * vanilla titles. Urgent callouts (leap, lightning) are bigger, pulse, and jump the queue.
 */
public final class Callouts implements HudElement {
	private static final int FADE = 5;

	private record Callout(Component main, Component sub, int accent, int ticks, boolean urgent) {
	}

	private static final Deque<Callout> QUEUE = new ArrayDeque<>();
	private static Callout current;
	private static int age;

	/** Normal callout (step change, objective done, gate...). Queued behind the current one. */
	public static void show(Component main, Component sub, int accent, int ticks) {
		if (!ConfigManager.get().stepTitles) return;
		if (QUEUE.size() >= 3) QUEUE.pollFirst();
		QUEUE.addLast(new Callout(main, sub, accent, ticks, false));
	}

	/** Urgent callout (leap, lightning): replaces whatever is showing. */
	public static void urgent(Component main, Component sub, int accent, int ticks) {
		if (!ConfigManager.get().stepTitles) return;
		QUEUE.clear();
		current = new Callout(main, sub, accent, ticks, true);
		age = 0;
	}

	public static void clear() {
		QUEUE.clear();
		current = null;
	}

	/** Advance one client tick. */
	public static void tick() {
		if (current != null && ++age >= current.ticks) {
			current = null;
		}
		if (current == null && !QUEUE.isEmpty()) {
			current = QUEUE.pollFirst();
			age = 0;
			// Don't let a backlog make callouts late: speed through queued ones.
			if (!QUEUE.isEmpty()) {
				current = new Callout(current.main, current.sub, current.accent, Math.min(current.ticks, 25), current.urgent);
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Callout c = current;
		if (c == null || mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}
		float t = age + deltaTracker.getGameTimeDeltaPartialTick(false);
		float alpha = Math.min(1f, Math.min(t / FADE, (c.ticks - t) / FADE));
		if (alpha <= 0.05f) return;

		Font font = mc.font;
		float user = ConfigManager.get().calloutScale;
		float mainScale = (c.urgent ? 1.6f : 1.25f) * user;
		float subScale = 0.85f * user;
		int mainW = font.width(c.main);
		int subW = c.sub == null ? 0 : font.width(c.sub);
		float w = Math.max(mainW * mainScale, subW * subScale) + 14 * user;
		float h = font.lineHeight * mainScale + (c.sub == null ? 0 : font.lineHeight * subScale + 2) + 8 * user;
		float x = g.guiWidth() / 2f - w / 2f;
		float y = g.guiHeight() * 0.30f - h;

		int a = Math.round(alpha * 255);
		int bg = (Math.round(alpha * 0xB0) << 24) | 0x0C0C0C;
		int accent = (a << 24) | (c.accent & 0xFFFFFF);

		g.pose().pushMatrix();
		g.pose().translate(x, y);
		int iw = Math.round(w), ih = Math.round(h);
		g.fill(0, 0, iw, ih, bg);
		g.fill(0, 0, Math.max(2, Math.round(3 * user)), ih, accent);
		if (c.urgent && ((age / 4) % 2 == 0)) {
			g.outline(-1, -1, iw + 2, ih + 2, accent);
		}
		float ty = 4 * user;
		g.pose().pushMatrix();
		g.pose().translate((w - mainW * mainScale) / 2f + 2, ty);
		g.pose().scale(mainScale, mainScale);
		g.text(font, c.main, 0, 0, (a << 24) | 0xFFFFFF, true);
		g.pose().popMatrix();
		if (c.sub != null) {
			ty += font.lineHeight * mainScale + 2;
			g.pose().pushMatrix();
			g.pose().translate((w - subW * subScale) / 2f + 2, ty);
			g.pose().scale(subScale, subScale);
			g.text(font, c.sub, 0, 0, (a << 24) | 0xBBBBBB, true);
			g.pose().popMatrix();
		}
		g.pose().popMatrix();
	}
}
