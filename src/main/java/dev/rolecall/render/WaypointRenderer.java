package dev.rolecall.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rolecall.RoleCall;
import dev.rolecall.config.ConfigManager;
import dev.rolecall.data.Objective;
import dev.rolecall.data.Step;
import dev.rolecall.model.DungeonClass;
import dev.rolecall.model.Phase;
import dev.rolecall.state.RunState;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws fixed-coordinate waypoints: a box, a label and the distance on the current step's
 * objectives (class colour), optionally the rest of the section dimmed, and the step's recorded
 * route as numbered points joined by lines. Fixed coordinates only, never entities.
 */
public final class WaypointRenderer {
	private static final float LINE_WIDTH = 3f;

	private record Wp(double x, double y, double z, String label, int argb) {
	}

	private WaypointRenderer() {
	}

	public static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (!ConfigManager.get().waypoints || mc.player == null || mc.level == null) {
			return;
		}
		List<int[]> mine = mineBlocks(mc);
		boolean run = RoleCall.active();
		List<Wp> boxes = run ? collect() : List.of();
		List<int[]> route = run ? route() : List.of();
		if (boxes.isEmpty() && route.isEmpty() && mine.isEmpty()) {
			return;
		}
		CameraRenderState cam = ctx.levelState().cameraRenderState;
		Vec3 c = cam.pos;
		SubmitNodeCollector out = ctx.submitNodeCollector();
		PoseStack ps = new PoseStack();

		out.submitCustomGeometry(ps, RenderTypes.lines(), (pose, buf) -> {
			for (Wp w : boxes) {
				box(buf, pose, (float) (w.x - c.x), (float) (w.y - c.y), (float) (w.z - c.z), w.argb);
			}
			for (int i = 0; i + 1 < route.size(); i++) {
				int[] a = route.get(i), b = route.get(i + 1);
				line(buf, pose, (float) (a[0] + .5 - c.x), (float) (a[1] + .5 - c.y), (float) (a[2] + .5 - c.z),
						(float) (b[0] + .5 - c.x), (float) (b[1] + .5 - c.y), (float) (b[2] + .5 - c.z), 0xFFFFFF55);
			}
			for (int[] p : route) {
				small(buf, pose, (float) (p[0] - c.x), (float) (p[1] - c.y), (float) (p[2] - c.z), 0xFFFFFF55);
			}
			for (int[] p : mine) {
				box(buf, pose, (float) (p[0] - c.x), (float) (p[1] - c.y), (float) (p[2] - c.z), 0xFFFF3030);
			}
		});

		Font font = mc.font;
		RunState st = RoleCall.state();
		for (Wp w : boxes) {
			double dist = Math.sqrt(sq(st.x() - (w.x + .5)) + sq(st.y() - (w.y + .5)) + sq(st.z() - (w.z + .5)));
			label(out, ps, font, cam, w.x + .5, w.y + 1.75, w.z + .5, w.label, w.argb);
			label(out, ps, font, cam, w.x + .5, w.y + 1.25, w.z + .5, String.format("%.0fm", dist), 0xFFDDDDDD);
		}
		for (int i = 0; i < route.size(); i++) {
			int[] p = route.get(i);
			label(out, ps, font, cam, p[0] + .5, p[1] + 1.2, p[2] + .5, String.valueOf(i + 1), 0xFFFFFF55);
		}
	}

	private static double sq(double d) {
		return d * d;
	}

	/** Current step's objectives (not done yet), plus the rest of the section dimmed if enabled. */
	private static List<Wp> collect() {
		List<Wp> out = new ArrayList<>();
		RunState st = RoleCall.state();
		Step cur = RoleCall.view().current();
		DungeonClass cls = RoleCall.effectiveClass();
		int classColor = cls == null ? 0xFFFFFFFF : cls.argb();
		Set<String> mine = new LinkedHashSet<>();
		if (cur != null && cur.waypoints != null) {
			mine.addAll(cur.waypoints);
		}
		for (String id : mine) {
			Objective o = RoleCall.data().objective(id);
			if (o == null || st.objectiveDone(id) || !floorOk(o)) continue;
			int color = o.color != null ? 0xFF000000 | Integer.parseInt(o.color, 16) : classColor;
			out.add(new Wp(o.x(), o.y(), o.z(), o.label, color));
		}
		if (ConfigManager.get().showOtherObjectives && st.phase() == Phase.P3 && st.section() > 0) {
			for (Objective o : RoleCall.data().objectives().values()) {
				if (mine.contains(o.id) || o.section == null || o.section != st.section() || st.objectiveDone(o.id)) continue;
				out.add(new Wp(o.x(), o.y(), o.z(), o.label, 0x80909090));
			}
		}
		return out;
	}

	private static boolean floorOk(Objective o) {
		return o.floors == null || o.floors.isEmpty() || (RoleCall.state().floor() != null && o.floors.contains(RoleCall.state().floor().name()));
	}

	/** Recorded blocks to mine that are still there (already-mined blocks are skipped). */
	private static List<int[]> mineBlocks(Minecraft mc) {
		String name = RoleCall.activeMineList();
		if (name == null) {
			return List.of();
		}
		List<int[]> out = new ArrayList<>();
		for (int[] p : RoleCall.data().mineList(name)) {
			if (!mc.level.getBlockState(new net.minecraft.core.BlockPos(p[0], p[1], p[2])).isAir()) {
				out.add(p);
			}
		}
		return out;
	}

	private static List<int[]> route() {
		Step cur = RoleCall.view().current();
		return cur == null || cur.route == null ? List.of() : RoleCall.data().route(cur.route);
	}

	private static void label(SubmitNodeCollector out, PoseStack ps, Font font, CameraRenderState cam,
							  double x, double y, double z, String text, int color) {
		Vec3 c = cam.pos;
		double dist = Math.sqrt(sq(x - c.x) + sq(y - c.y) + sq(z - c.z));
		float s = 0.03f * (float) Math.max(1.0, dist / 6.0);
		ps.pushPose();
		ps.translate(x - c.x, y - c.y, z - c.z);
		ps.mulPose(cam.orientation);
		ps.scale(s, -s, s);
		Component comp = Component.literal(text);
		out.submitText(ps, -font.width(comp) / 2f, 0, comp.getVisualOrderText(), false, Font.DisplayMode.SEE_THROUGH,
				LightCoordsUtil.FULL_BRIGHT, color, 0x60000000, 0);
		ps.popPose();
	}

	private static void box(VertexConsumer b, PoseStack.Pose p, float x, float y, float z, int c) {
		float X = x + 1, Y = y + 1, Z = z + 1;
		line(b, p, x, y, z, X, y, z, c);
		line(b, p, x, y, z, x, Y, z, c);
		line(b, p, x, y, z, x, y, Z, c);
		line(b, p, X, y, z, X, Y, z, c);
		line(b, p, X, y, z, X, y, Z, c);
		line(b, p, x, Y, z, X, Y, z, c);
		line(b, p, x, Y, z, x, Y, Z, c);
		line(b, p, x, y, Z, X, y, Z, c);
		line(b, p, x, y, Z, x, Y, Z, c);
		line(b, p, X, Y, z, X, Y, Z, c);
		line(b, p, X, y, Z, X, Y, Z, c);
		line(b, p, x, Y, Z, X, Y, Z, c);
	}

	private static void small(VertexConsumer b, PoseStack.Pose p, float x, float y, float z, int c) {
		float a = 0.35f, d = 0.65f;
		line(b, p, x + a, y + a, z + a, x + d, y + d, z + d, c);
		line(b, p, x + d, y + a, z + a, x + a, y + d, z + d, c);
		line(b, p, x + a, y + a, z + d, x + d, y + d, z + a, c);
		line(b, p, x + d, y + a, z + d, x + a, y + d, z + a, c);
	}

	private static void line(VertexConsumer b, PoseStack.Pose p, float x1, float y1, float z1, float x2, float y2, float z2, int c) {
		float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len == 0) return;
		nx /= len;
		ny /= len;
		nz /= len;
		b.addVertex(p, x1, y1, z1).setColor(c).setNormal(p, nx, ny, nz).setLineWidth(LINE_WIDTH);
		b.addVertex(p, x2, y2, z2).setColor(c).setNormal(p, nx, ny, nz).setLineWidth(LINE_WIDTH);
	}
}
