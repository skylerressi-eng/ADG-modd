package dev.rolecall.render;

import dev.rolecall.RoleCall;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The recorded blocks to dig (/rolecall mine) that are still there, in the order you recorded them,
 * so "next" follows your path. Blocks that are already air are skipped.
 */
public final class DigHelper {
	private DigHelper() {
	}

	public static List<int[]> remaining(Minecraft mc) {
		String name = RoleCall.activeMineList();
		if (name == null || mc.level == null) {
			return List.of();
		}
		List<int[]> out = new ArrayList<>();
		for (int[] p : RoleCall.data().mineList(name)) {
			if (!mc.level.getBlockState(new BlockPos(p[0], p[1], p[2])).isAir()) {
				out.add(p);
			}
		}
		return out;
	}

	/** Total recorded blocks in the active list (0 if none recorded). */
	public static int total() {
		String name = RoleCall.activeMineList();
		return name == null ? 0 : RoleCall.data().mineList(name).size();
	}
}
