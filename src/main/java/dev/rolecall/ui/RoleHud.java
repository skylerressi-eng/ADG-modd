package dev.rolecall.ui;

import dev.rolecall.RoleCall;
import dev.rolecall.config.ConfigManager;
import dev.rolecall.config.RoleCallConfig;
import dev.rolecall.data.Step;
import dev.rolecall.data.StepEngine;
import dev.rolecall.model.DungeonClass;
import dev.rolecall.state.RunState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The step HUD: header (class + phase + P3 progress), the current step in large text, its note,
 * the leap target, the next steps and any party gaps. Hidden outside F7/M7 and while the class
 * has nothing to do (e.g. clear, until clear steps are written).
 */
public final class RoleHud implements HudElement {
	private static final int PADDING = 3;
	private static final int WRAP = 220;
	private static final int WRAP_COMPACT = 170;
	private static final float STEP_SCALE = 1.5f;
	private static final int BACKGROUND = 0x90101010;

	private record Line(FormattedCharSequence text, float scale, int color) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		RoleCallConfig config = ConfigManager.get();
		if (!config.hudEnabled || mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}
		List<Line> lines;
		if (!RoleCall.active()) {
			if (!config.idleHud) {
				return;
			}
			// One small line so you can see RoleCall is loaded; the full HUD appears in F7/M7.
			lines = new ArrayList<>();
			DungeonClass c = RoleCall.effectiveClass();
			add(lines, mc.font, header(c, "waiting for F7/M7", false), 1f, 0xFFAAAAAA);
		} else {
			lines = build(mc.font, false);
		}
		if (lines.isEmpty()) {
			return;
		}
		draw(graphics, mc.font, lines, config.hudX, config.hudY, config.hudScale);
	}

	/** Draws the HUD (or a sample when nothing is active) for the editor. Returns {width, height} unscaled. */
	public static int[] renderPreview(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale) {
		List<Line> lines = build(font, true);
		return draw(graphics, font, lines, x, y, scale);
	}

	private static int[] size(Font font, List<Line> lines) {
		int w = 0, h = 0;
		for (Line l : lines) {
			w = Math.max(w, Math.round(font.width(l.text) * l.scale));
			h += Math.round(font.lineHeight * l.scale) + 1;
		}
		return new int[]{w + 2 * PADDING, h + 2 * PADDING - 1};
	}

	private static int[] draw(GuiGraphicsExtractor g, Font font, List<Line> lines, int x, int y, float scale) {
		int[] size = size(font, lines);
		x = Math.max(0, Math.min(x, g.guiWidth() - Math.round(size[0] * scale)));
		y = Math.max(0, Math.min(y, g.guiHeight() - Math.round(size[1] * scale)));
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.fill(0, 0, size[0], size[1], BACKGROUND);
		float ty = PADDING;
		for (Line l : lines) {
			g.pose().pushMatrix();
			g.pose().translate(PADDING, ty);
			g.pose().scale(l.scale, l.scale);
			g.text(font, l.text, 0, 0, l.color, true);
			g.pose().popMatrix();
			ty += Math.round(font.lineHeight * l.scale) + 1;
		}
		g.pose().popMatrix();
		return size;
	}

	private static void add(List<Line> out, Font font, Component c, float scale, int color) {
		int wrap = ConfigManager.get().compactHud ? WRAP_COMPACT : WRAP;
		for (FormattedCharSequence s : font.split(c, Math.round(wrap / scale * (scale > 1 ? 1.35f : 1f)))) {
			out.add(new Line(s, scale, color));
		}
	}

	private static List<Line> build(Font font, boolean preview) {
		List<Line> out = new ArrayList<>();
		RunState st = RoleCall.state();
		StepEngine.View v = RoleCall.view();
		DungeonClass cls = RoleCall.effectiveClass();

		if (preview && !RoleCall.active() && ConfigManager.get().compactHud) {
			DungeonClass c = cls != null ? cls : DungeonClass.HEALER;
			add(out, font, Component.literal(c.name()).withColor(c.rgb()).append(Component.literal("  P2 Storm").withColor(0x999999)), 0.85f, 0xFFFFFFFF);
			add(out, font, Component.literal("▶ Finish predev → Mage"), 1.2f, 0xFFFFFFFF);
			add(out, font, Component.literal("⇒ ").append(Component.literal("Leap → MAGE (Magey)").withColor(DungeonClass.MAGE.rgb())), 1f, 0xFFFFFFFF);
			add(out, font, Component.literal("Dig 7/12 · next 4m").withColor(0xFF5555), 1f, 0xFFFFFFFF);
			add(out, font, Component.literal("next: Drop GREEN pillar"), 0.85f, 0xFF888888);
			return out;
		}
		if (preview && !RoleCall.active()) {
			DungeonClass c = cls != null ? cls : DungeonClass.HEALER;
			add(out, font, header(c, "P1 Maxor", false), 1f, 0xFFAAAAAA);
			add(out, font, Component.literal("▶ PREDEV: mine into Goldor's arena"), STEP_SCALE, 0xFFFFFFFF);
			add(out, font, Component.literal("On pace = at or near the Lights device when Maxor dies"), 1f, 0xFF999999);
			add(out, font, Component.literal("next: Set Lights device"), 1f, 0xFF888888);
			add(out, font, Component.literal("then: Set Arrow Align"), 1f, 0xFF666666);
			return out;
		}

		if (cls == null) {
			add(out, font, header(null, st.phaseLabel(), st.phaseFromFallback()), 1f, 0xFFAAAAAA);
			add(out, font, Component.translatable("rolecall.hud.no_class"), 1f, 0xFFFFFF55);
			return out;
		}
		Step cur = v.current();
		if (cur == null) {
			return out; // nothing for this class right now (e.g. clear with no clear steps)
		}
		if (ConfigManager.get().compactHud) {
			compact(out, font, st, v, cls, cur);
			return out;
		}
		add(out, font, header(cls, st.phaseLabel(), st.phaseFromFallback()), 1f, 0xFFAAAAAA);
		add(out, font, Component.literal("▶ " + cur.text), STEP_SCALE, 0xFFFFFFFF);
		if (cur.note != null && !cur.note.isBlank()) {
			add(out, font, Component.literal(cur.note), 1f, 0xFF999999);
		}
		if (cur.leap != null) {
			DungeonClass target = DungeonClass.parse(cur.leap).orElse(null);
			add(out, font, RoleCall.leapComponent(target), 1.25f, 0xFFFFFFFF);
		}
		if (v.groupSize() > 1) {
			add(out, font, Component.literal("step " + (v.index() + 1) + "/" + v.groupSize()), 1f, 0xFF666666);
		}
		if (v.next() != null) {
			add(out, font, Component.literal("next: " + where(cur, v.next()) + v.next().titleText() + leapSuffix(v.next())), 1f, 0xFF888888);
		}
		if (v.then() != null) {
			add(out, font, Component.literal("then: " + where(cur, v.then()) + v.then().titleText() + leapSuffix(v.then())), 1f, 0xFF666666);
		}
		for (String gap : RoleCall.gaps()) {
			add(out, font, Component.literal(gap), 1f, 0xFFFFAA55);
		}
		return out;
	}

	/**
	 * Compact layout, about half the size of the detailed one:
	 * <pre>
	 * MAGE  P3 S1 3/7
	 * ▶ Terminal 4
	 * ⇒ Leap MAGE (Magey)
	 * Dig 5/12 · 6m
	 * next: S2 Terminal 2
	 * no Tank: Terminal 2, Terminal 1
	 * </pre>
	 */
	private static void compact(List<Line> out, Font font, RunState st, StepEngine.View v, DungeonClass cls, Step cur) {
		MutableComponent head = Component.literal(cls.name()).withColor(cls.rgb())
				.append(Component.literal("  " + st.phaseLabel().replace(" (", " ").replace(")", "")
						+ (st.phaseFromFallback() ? " est." : "")).withColor(0x999999));
		add(out, font, head, 0.85f, 0xFFFFFFFF);
		add(out, font, Component.literal("▶ " + cur.titleText()), 1.2f, 0xFFFFFFFF);
		if (cur.leap != null) {
			DungeonClass target = DungeonClass.parse(cur.leap).orElse(null);
			add(out, font, Component.literal("⇒ ").append(RoleCall.leapComponent(target)), 1f, 0xFFFFFFFF);
		}
		Minecraft mc = Minecraft.getInstance();
		int total = dev.rolecall.render.DigHelper.total();
		if (RoleCall.activeMineList() != null) {
			if (total == 0) {
				add(out, font, Component.literal("No dig blocks recorded (/rolecall mine predev add)").withColor(0xFF7777), 0.85f, 0xFFFFFFFF);
			} else {
				List<int[]> rem = dev.rolecall.render.DigHelper.remaining(mc);
				String dist = "";
				if (!rem.isEmpty() && mc.player != null) {
					int[] p = rem.get(0);
					dist = String.format(" · next %.0fm", Math.sqrt(mc.player.distanceToSqr(p[0] + .5, p[1] + .5, p[2] + .5)));
				}
				add(out, font, Component.literal("Dig " + (total - rem.size()) + "/" + total + dist).withColor(0xFF5555), 1f, 0xFFFFFFFF);
			}
		}
		if (v.next() != null) {
			add(out, font, Component.literal("next: " + where(cur, v.next()) + v.next().titleText()), 0.85f, 0xFF888888);
		}
		List<String> gaps = RoleCall.gaps();
		if (!gaps.isEmpty()) {
			add(out, font, Component.literal(String.join(" · ", gaps.stream()
					.map(g -> g.replace("Unassigned (no ", "no ").replace("): S" + st.section() + " ", ": ")).toList())), 0.8f, 0xFFFFAA55);
		}
	}

	/** "S2 " / "P4 " prefix when a later step belongs to another section or phase. */
	private static String where(Step cur, Step s) {
		if (s.phase != null && !s.phase.equals(cur.phase)) {
			String p = s.phase.equals("P3") && s.section != null ? "S" + s.section : s.phase.replace("P3_TUNNEL", "Tunnel");
			return p + ": ";
		}
		if (s.section != null && !s.section.equals(cur.section)) {
			return "S" + s.section + ": ";
		}
		return "";
	}

	private static String leapSuffix(Step s) {
		return s.leap != null && !s.titleText().toLowerCase(java.util.Locale.ROOT).contains("leap") ? " (Leap → " + s.leap + ")" : "";
	}

	private static Component header(DungeonClass c, String phase, boolean estimated) {
		MutableComponent cls = c != null
				? Component.literal(c.name()).withColor(c.rgb())
				: Component.literal("?").withColor(0xFFFF55);
		return Component.literal("RoleCall · ").append(cls).append(" · " + phase + (estimated ? " (est.)" : ""));
	}
}
