package dev.rolecall;

import dev.rolecall.config.ConfigManager;
import dev.rolecall.config.RoleCallConfig;
import dev.rolecall.data.DataFiles;
import dev.rolecall.data.RoleData;
import dev.rolecall.data.Step;
import dev.rolecall.data.StepEngine;
import dev.rolecall.model.DungeonClass;
import dev.rolecall.model.Floor;
import dev.rolecall.model.Phase;
import dev.rolecall.sim.SimPlayer;
import dev.rolecall.state.ChatTriggers;
import dev.rolecall.state.FloorParser;
import dev.rolecall.state.PartyTracker;
import dev.rolecall.state.RunState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The live glue between Minecraft and the pure-Java engine. Reads the scoreboard, tab list,
 * chat and your position; never sends anything. All methods run on the client thread.
 */
public final class RoleCall {
		/** A completion is credited to an objective only if the player stood this close to it. */
	private static final double ATTRIBUTION_RANGE = 10.0;

	private static final RunState STATE = new RunState();
	private static final PartyTracker PARTY = new PartyTracker();
	private static final SimPlayer SIM = new SimPlayer();
	private static RoleData data = RoleData.empty();

	private static ClientLevel lastLevel;
	private static long ticks;
	private static String ownName;
	private static DungeonClass simClass;
	private static boolean simPos;
	private static String rawFloorLine = "";

	private static StepEngine.View view = StepEngine.View.NONE;
	private static String lastStepId;
	private static String groupKey = "";
	private static int manualSkips;
	private static List<String> gaps = List.of();
	private static long lightningTitleUntil;

	private RoleCall() {
	}

	public static void init() {
		reload();
	}

	/** (Re)loads the JSON files and reports problems. Returns the problems. */
	public static List<String> reload() {
		data = DataFiles.loadAll();
		for (String p : data.problems()) {
			RoleCallClient.LOGGER.warn("Data problem: {}", p);
		}
		RoleCallClient.LOGGER.info("Loaded {} steps, {} waypoints, {} routes", data.stepCount(), data.objectives().size(), data.routes().size());
		return data.problems();
	}

	// ------------------------------------------------------------------ accessors

	public static RunState state() {
		return STATE;
	}

	public static PartyTracker party() {
		return PARTY;
	}

	public static RoleData data() {
		return data;
	}

	public static SimPlayer sim() {
		return SIM;
	}

	public static StepEngine.View view() {
		return view;
	}

	public static List<String> gaps() {
		return gaps;
	}

	public static String ownName() {
		return ownName;
	}

	public static String rawFloorLine() {
		return rawFloorLine;
	}

	public static int manualSkips() {
		return manualSkips;
	}

	/** Manual pick, else the class from /rolecall sim, else the class read from the tab list. */
	public static DungeonClass effectiveClass() {
		DungeonClass manual = ConfigManager.get().manualClass;
		if (manual != null) return manual;
		if (SIM.active() && simClass != null) return simClass;
		return PARTY.classOf(ownName).orElse(null);
	}

	public static String classSource() {
		if (ConfigManager.get().manualClass != null) return "manual";
		if (SIM.active() && simClass != null) return "sim";
		return PARTY.classOf(ownName).isPresent() ? "tab list" : "unknown";
	}

	public static StepEngine.Options options() {
		RoleCallConfig c = ConfigManager.get();
		return new StepEngine.Options(StepEngine.resolvePredev(c.predev, PARTY), c.s4Split);
	}

	/** The party members playing a class, for "Leap → MAGE (Name)". */
	public static List<String> namesOf(DungeonClass c) {
		List<String> out = new ArrayList<>(PARTY.byClass().getOrDefault(c, List.of()));
		out.removeIf(n -> n.equalsIgnoreCase(ownName));
		return out;
	}

	public static boolean active() {
		return STATE.inRun() || SIM.active();
	}

	// ------------------------------------------------------------------ manual step keys

	public static void nextStep() {
		if (view.current() != null && view.index() < view.groupSize() - 1) {
			manualSkips = view.index() + 1;
		}
	}

	public static void previousStep() {
		manualSkips = Math.max(0, Math.min(manualSkips, view.index()) - 1);
	}

	// ------------------------------------------------------------------ tick

	public static void tick(Minecraft mc) {
		ticks++;
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			resetRun();
			SIM.stop();
		}
		if (mc.player == null || mc.level == null) {
			return;
		}
		ownName = mc.player.getGameProfile().name();

		if (SIM.active()) {
			SIM.tick(SIM_SINK);
		} else if (ticks % 20 == 0) {
			readFloor(mc);
			readTabList(mc);
		}
		if (!simPos) {
			STATE.onPosition(mc.player.getX(), mc.player.getY(), mc.player.getZ());
		}
		recompute(mc);
	}

	private static void resetRun() {
		STATE.reset();
		PARTY.reset();
		simClass = null;
		simPos = false;
		view = StepEngine.View.NONE;
		lastStepId = null;
		groupKey = "";
		manualSkips = 0;
		gaps = List.of();
	}

	private static void recompute(Minecraft mc) {
		DungeonClass cls = effectiveClass();
		StepEngine.Options opt = options();
		String key = STATE.phase() + "/" + STATE.section();
		if (!key.equals(groupKey)) {
			groupKey = key;
			manualSkips = 0;
		}
		view = StepEngine.compute(data, STATE, cls, opt, manualSkips);
		StepEngine.updateReached(data, STATE, view.current());
		view = StepEngine.compute(data, STATE, cls, opt, manualSkips);
		if (ticks % 10 == 0) {
			gaps = StepEngine.partyGaps(data, STATE, cls, PARTY, opt);
		}

		String id = view.current() == null ? null : view.current().id;
		if (id != null && !id.equals(lastStepId)) {
			onStepChanged(mc, view.current(), cls);
		}
		lastStepId = id;
	}

	// ------------------------------------------------------------------ cues

	private static void onStepChanged(Minecraft mc, Step step, DungeonClass cls) {
		RoleCallConfig c = ConfigManager.get();
		if (c.stepTitles && ticks >= lightningTitleUntil) {
			mc.gui.hud.setTimes(4, 40, 10);
			mc.gui.hud.setTitle(Component.literal(step.titleText()).withColor(cls == null ? 0xFFFFFF : cls.rgb()));
			mc.gui.hud.setSubtitle(step.leap != null
					? leapComponent(DungeonClass.parse(step.leap).orElse(null))
					: Component.empty());
		}
		ding(mc, 1.6f);
	}

	private static void ding(Minecraft mc, float pitch) {
		RoleCallConfig c = ConfigManager.get();
		if (c.stepSound && c.soundVolume > 0) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), pitch, c.soundVolume));
		}
	}

	public static Component leapComponent(DungeonClass target) {
		if (target == null) {
			return Component.empty();
		}
		List<String> names = namesOf(target);
		String who = names.isEmpty() ? "" : " (" + String.join(", ", names) + ")";
		return Component.literal("Leap → " + target.name() + who).withColor(target.rgb());
	}

	// ------------------------------------------------------------------ chat

	/** Called for every game (non-overlay) chat line, and for sim chat lines. */
	public static void onChat(String text) {
		Optional<ChatTriggers.Event> parsed = ChatTriggers.parse(text);
		if (parsed.isEmpty()) {
			return;
		}
		ChatTriggers.Event e = parsed.get();
		if (!active()) {
			// These lines only exist in the F7/M7 boss. If the sidebar wasn't read yet, start anyway
			// (as F7; the sidebar corrects it to M7 within a second).
			if (e.kind() == ChatTriggers.Kind.RUN_END || e.kind() == ChatTriggers.Kind.GATE) {
				return;
			}
			STATE.setFloor(Floor.F7);
		}
		if (e.isProgress()) {
			attribute(e);
		}
		STATE.apply(e);
		if (e.kind() == ChatTriggers.Kind.LIGHTNING && ConfigManager.get().lightningWarning) {
			Minecraft mc = Minecraft.getInstance();
			mc.gui.hud.setTimes(2, 60, 10);
			mc.gui.hud.setTitle(Component.translatable("rolecall.title.lightning").withColor(0xFF5555));
			mc.gui.hud.setSubtitle(Component.translatable("rolecall.title.lightning.sub").withColor(0xFFFF55));
			lightningTitleUntil = ticks + 60;
			ding(mc, 0.7f);
		}
	}

	/** Hides the waypoint nearest to whoever completed it (Skyblocker's approach): only objectives of that kind in this section. */
	private static void attribute(ChatTriggers.Event e) {
		Minecraft mc = Minecraft.getInstance();
		double[] pos = playerPos(mc, e.player());
		if (pos == null) {
			return;
		}
		int section = STATE.section() != 0 ? STATE.section()
				: (e.total() == 8 ? 2 : STATE.guessSectionFromPosition().orElse(0));
		dev.rolecall.data.Objective best = null;
		double bestD = ATTRIBUTION_RANGE * ATTRIBUTION_RANGE;
		for (dev.rolecall.data.Objective o : data.objectivesOfKind(e.objectiveKind(), section == 0 ? null : section)) {
			if (STATE.objectiveDone(o.id)) continue;
			double d = o.distSq(pos[0], pos[1], pos[2]);
			if (d < bestD) {
				bestD = d;
				best = o;
			}
		}
		if (best != null) {
			STATE.markObjectiveDone(best.id);
		}
	}

	private static double[] playerPos(Minecraft mc, String name) {
		if (mc.level == null || name == null) {
			return null;
		}
		if (name.equalsIgnoreCase(ownName)) {
			return new double[]{STATE.x(), STATE.y(), STATE.z()};
		}
		for (Player p : mc.level.players()) {
			if (p.getGameProfile().name().equalsIgnoreCase(name)) {
				return new double[]{p.getX(), p.getY(), p.getZ()};
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ scoreboard + tab list

	private static void readFloor(Minecraft mc) {
		Scoreboard sb = mc.level.getScoreboard();
		net.minecraft.world.scores.Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
		List<String> lines = new ArrayList<>();
		if (obj != null) {
			for (PlayerScoreEntry entry : sb.listPlayerScores(obj)) {
				if (entry.isHidden()) continue;
				PlayerTeam team = sb.getPlayersTeam(entry.owner());
				if (team != null) {
					// Like Skyblocker: prefix + suffix only; the holder name in between is invisible colour codes.
					lines.add(team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString());
				}
				if (entry.display() != null) {
					lines.add(entry.display().getString());
				}
			}
		}
		Optional<String[]> found = FloorParser.find(lines);
		rawFloorLine = found.map(f -> f[0]).orElse(lines.isEmpty() ? "" : "(no Catacombs line in " + lines.size() + " lines)");
		Floor floor = found.flatMap(f -> Floor.parse(f[1])).orElse(null);
		if (found.isPresent() && floor == null && STATE.floor() != null) {
			resetRun(); // a different Catacombs floor: not F7/M7
		} else if (floor != null && floor != STATE.floor()) {
			STATE.setFloor(floor);
		}
		// No Catacombs line at all: keep what we have (the sidebar can blink during loading);
		// the run is reset on world change.
	}

	private static void readTabList(Minecraft mc) {
		if (mc.getConnection() == null) {
			return;
		}
		List<String> lines = new ArrayList<>();
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			Component name = info.getTabListDisplayName();
			if (name != null) {
				lines.add(name.getString());
			}
		}
		PARTY.update(lines);
	}

	// ------------------------------------------------------------------ sim

	public static void startSim(String name, String script) {
		resetRun();
		SIM.start(name, script);
	}

	public static void stopSim() {
		SIM.stop();
		resetRun();
	}

	private static final SimPlayer.Sink SIM_SINK = new SimPlayer.Sink() {
		@Override
		public void floor(String f) {
			STATE.setFloor(Floor.parse(f).orElseThrow(() -> new IllegalArgumentException("floor must be F7 or M7")));
		}

		@Override
		public void me(String name, DungeonClass c) {
			simClass = c;
			PARTY.put(ownName != null ? ownName : name, c);
		}

		@Override
		public void party(String name, DungeonClass c) {
			PARTY.put(name, c);
		}

		@Override
		public void pos(double x, double y, double z) {
			simPos = true;
			STATE.onPosition(x, y, z);
		}

		@Override
		public void chat(String line) {
			// "You" in a script means the local player.
			onChat(line.startsWith("You ") && ownName != null ? ownName + line.substring(3) : line);
		}

		@Override
		public void done(String objectiveId) {
			STATE.markObjectiveDone(objectiveId);
		}

		@Override
		public void error(String message) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				mc.player.sendSystemMessage(Component.literal("[RoleCall sim] " + message).withColor(0xFF5555));
			}
		}
	};

	/** Id and distance of the objective nearest to you, for /rolecall debug. */
	public static String nearestObjective() {
		if (!STATE.hasPosition()) return "-";
		dev.rolecall.data.Objective best = null;
		double bestD = Double.MAX_VALUE;
		for (dev.rolecall.data.Objective o : data.objectives().values()) {
			double d = o.distSq(STATE.x(), STATE.y(), STATE.z());
			if (d < bestD) {
				bestD = d;
				best = o;
			}
		}
		return best == null ? "-" : String.format("%s (%.1fm)", best.id, Math.sqrt(bestD));
	}

	public static boolean isPhase(Phase p) {
		return STATE.phase() == p;
	}
}
