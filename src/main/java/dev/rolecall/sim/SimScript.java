package dev.rolecall.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * A fake run for {@code /rolecall sim}: one command per line, played locally into RoleCall's
 * own handlers. Nothing is ever sent to the server. Pure Java so tests replay the same scripts.
 *
 * <pre>
 * # comment
 * floor F7|M7           set the floor (as if read from the scoreboard)
 * me NAME CLASS         your IGN and class (as if read from the tab list)
 * party NAME CLASS      another party member
 * pos X Y Z             fake your position
 * chat TEXT             a chat line, exactly as Hypixel prints it
 * done OBJECTIVE_ID     mark a terminal/device/lever done (stands in for "nearest player" attribution)
 * wait TICKS            pause (20 ticks = 1 s)
 * </pre>
 */
public final class SimScript {
	public record Cmd(String op, String arg, int line) {
	}

	private SimScript() {
	}

	public static List<Cmd> parse(String text) {
		List<Cmd> out = new ArrayList<>();
		String[] lines = text.split("\\R");
		for (int i = 0; i < lines.length; i++) {
			String l = lines[i].strip();
			if (l.isEmpty() || l.startsWith("#")) {
				continue;
			}
			int sp = l.indexOf(' ');
			String op = (sp < 0 ? l : l.substring(0, sp)).toLowerCase(java.util.Locale.ROOT);
			String arg = sp < 0 ? "" : l.substring(sp + 1).strip();
			out.add(new Cmd(op, arg, i + 1));
		}
		return out;
	}
}
