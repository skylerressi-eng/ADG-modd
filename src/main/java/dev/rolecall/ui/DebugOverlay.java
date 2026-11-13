package dev.rolecall.ui;

import dev.rolecall.RoleCall;
import dev.rolecall.config.ConfigManager;
import dev.rolecall.data.StepEngine;
import dev.rolecall.model.DungeonClass;
import dev.rolecall.state.PartyTracker;
import dev.rolecall.state.RunState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** {@code /rolecall debug}: everything RoleCall detected, top-right. For checking things in game. */
public final class DebugOverlay implements HudElement {
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!ConfigManager.get().debugOverlay || mc.player == null) {
			return;
		}
		RunState s = RoleCall.state();
		StepEngine.View v = RoleCall.view();
		StepEngine.Options o = RoleCall.options();
		List<String> lines = new ArrayList<>();
		lines.add("§eRoleCall debug");
		lines.add("scoreboard: " + (RoleCall.rawFloorLine().isEmpty() ? "§7(no Catacombs line)" : RoleCall.rawFloorLine()));
		lines.add("floor: " + s.floor() + "  inBoss: " + s.inBoss());
		lines.add("phase: " + s.phase() + (s.phaseFromFallback() ? " §6(Y fallback)" : "") + "  section: " + s.section()
				+ "  progress: " + s.progress() + "/" + s.progressTotal());
		lines.add("gates: " + s.gateDestroyed(1) + "/" + s.gateDestroyed(2) + "/" + s.gateDestroyed(3)
				+ "  lightning: " + s.lightning() + "  stormDead: " + s.stormDead());
		DungeonClass c = RoleCall.effectiveClass();
		lines.add("you: " + RoleCall.ownName() + "  class: " + c + " §7(" + RoleCall.classSource() + ")");
		lines.add("predev: " + o.predev() + " §7(" + ConfigManager.get().predev + ")§r  s4: " + o.s4Split().dataId() + "  crush: " + o.crush().dataId());
		lines.add("step: " + (v.current() == null ? "-" : v.current().id + " (" + (v.index() + 1) + "/" + v.groupSize() + ")")
				+ "  skips: " + RoleCall.manualSkips());
		lines.add(String.format("pos: %.1f %.1f %.1f  nearest: %s", s.x(), s.y(), s.z(), RoleCall.nearestObjective()));
		lines.add("done: " + s.doneObjectives().size() + " " + String.join(",", s.doneObjectives().stream().sorted().limit(8).toList()));
		PartyTracker p = RoleCall.party();
		for (PartyTracker.Member m : p.members()) {
			lines.add(" " + m.name() + " " + m.dungeonClass() + (m.dead() ? " §c(dead)" : ""));
		}
		for (String u : p.unmatchedLines()) {
			lines.add("§cunmatched tab: §7" + u);
		}
		if (RoleCall.sim().active()) {
			lines.add("§dsim " + RoleCall.sim().name() + " " + RoleCall.sim().progress() + (RoleCall.sim().finished() ? " (finished)" : ""));
		}
		int problems = RoleCall.data().problems().size();
		lines.add("data: " + RoleCall.data().stepCount() + " steps, " + RoleCall.data().objectives().size() + " waypoints"
				+ (problems > 0 ? " §c" + problems + " problems (see log)" : ""));

		float scale = 0.75f;
		int w = 0;
		for (String l : lines) w = Math.max(w, mc.font.width(l));
		g.pose().pushMatrix();
		g.pose().translate(g.guiWidth() - (w + 8) * scale, g.guiHeight() * 0.25f);
		g.pose().scale(scale, scale);
		g.fill(0, 0, w + 6, lines.size() * 10 + 2, 0xB0000000);
		int y = 2;
		for (String l : lines) {
			g.text(mc.font, l, 3, y, 0xFFFFFFFF, false);
			y += 10;
		}
		g.pose().popMatrix();
	}
}
