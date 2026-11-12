package dev.rolecall.sim;

import dev.rolecall.model.DungeonClass;

import java.util.List;

/**
 * Plays a {@link SimScript} one command per tick (or slower with {@code wait}). The commands are
 * handed to a {@link Sink}: RoleCall's own handlers. Nothing reaches the server.
 */
public final class SimPlayer {
	public interface Sink {
		void floor(String floor);

		void me(String name, DungeonClass c);

		void party(String name, DungeonClass c);

		void pos(double x, double y, double z);

		void chat(String line);

		void done(String objectiveId);

		void error(String message);
	}

	private List<SimScript.Cmd> cmds = List.of();
	private int index;
	private int waitTicks;
	private String name;
	private boolean active;

	public void start(String name, String script) {
		this.name = name;
		this.cmds = SimScript.parse(script);
		this.index = 0;
		this.waitTicks = 0;
		this.active = true;
	}

	public void stop() {
		active = false;
		cmds = List.of();
	}

	public boolean active() {
		return active;
	}

	public boolean finished() {
		return active && index >= cmds.size();
	}

	public String name() {
		return name;
	}

	public String progress() {
		return Math.min(index, cmds.size()) + "/" + cmds.size();
	}

	/** Runs commands until a wait or the end. Call once per client tick. */
	public void tick(Sink sink) {
		if (!active) {
			return;
		}
		if (waitTicks > 0) {
			waitTicks--;
			return;
		}
		while (index < cmds.size()) {
			SimScript.Cmd c = cmds.get(index++);
			try {
				switch (c.op()) {
					case "floor" -> sink.floor(c.arg());
					case "me", "party" -> {
						String[] p = c.arg().split("\\s+");
						DungeonClass cls = DungeonClass.parse(p[1]).orElseThrow(() -> new IllegalArgumentException("unknown class " + p[1]));
						if (c.op().equals("me")) sink.me(p[0], cls);
						else sink.party(p[0], cls);
					}
					case "pos" -> {
						String[] p = c.arg().split("\\s+");
						sink.pos(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
					}
					case "chat" -> sink.chat(c.arg());
					case "done" -> sink.done(c.arg());
					case "wait" -> {
						waitTicks = Math.max(0, Integer.parseInt(c.arg()) - 1);
						return;
					}
					default -> sink.error("line " + c.line() + ": unknown command '" + c.op() + "'");
				}
			} catch (RuntimeException e) {
				sink.error("line " + c.line() + ": " + e.getMessage());
			}
		}
	}
}
