package dev.rolecall.cmd;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.rolecall.RoleCall;
import dev.rolecall.RoleCallClient;
import dev.rolecall.config.ConfigManager;
import dev.rolecall.config.RoleCallConfig;
import dev.rolecall.data.DataFiles;
import dev.rolecall.data.Step;
import dev.rolecall.model.DungeonClass;
import dev.rolecall.ui.ClassPickerScreen;
import dev.rolecall.ui.HudEditorScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** {@code /rolecall} command tree. Client-side only; nothing is sent to the server. */
public final class RoleCallCommands {
	private RoleCallCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				literal("rolecall")
						.executes(ctx -> open(new ClassPickerScreen()))
						.then(literal("class")
								.then(argument("class", StringArgumentType.word())
										.suggests((ctx, b) -> {
											b.suggest("auto");
											for (DungeonClass c : DungeonClass.values()) b.suggest(c.name().toLowerCase(Locale.ROOT));
											return b.buildFuture();
										})
										.executes(ctx -> setClass(ctx.getSource(), StringArgumentType.getString(ctx, "class")))))
						.then(literal("hud").executes(ctx -> open(new HudEditorScreen())))
						.then(literal("reload").executes(ctx -> reload(ctx.getSource())))
						.then(literal("status").executes(ctx -> status(ctx.getSource())))
						.then(literal("next").executes(ctx -> {
							RoleCall.nextStep();
							return 1;
						}))
						.then(literal("back").executes(ctx -> {
							RoleCall.previousStep();
							return 1;
						}))
						.then(literal("debug").executes(ctx -> {
							RoleCallConfig c = ConfigManager.get();
							c.debugOverlay = !c.debugOverlay;
							ConfigManager.save();
							ctx.getSource().sendFeedback(msg("Debug overlay " + (c.debugOverlay ? "on" : "off")));
							return 1;
						}))
						.then(literal("here")
								.then(argument("id", StringArgumentType.word())
										.executes(ctx -> here(ctx.getSource(), StringArgumentType.getString(ctx, "id"), null))
										.then(argument("label", StringArgumentType.greedyString())
												.executes(ctx -> here(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
														StringArgumentType.getString(ctx, "label"))))))
						.then(literal("unhere")
								.then(argument("id", StringArgumentType.word())
										.executes(ctx -> unhere(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
						.then(literal("route")
								.then(argument("name", StringArgumentType.word())
										.suggests((ctx, b) -> b.suggest("predev").buildFuture())
										.then(literal("add").executes(ctx -> route(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "add")))
										.then(literal("undo").executes(ctx -> route(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "undo")))
										.then(literal("clear").executes(ctx -> route(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "clear")))
										.then(literal("list").executes(ctx -> route(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "list")))))
						.then(literal("mine")
								.then(argument("name", StringArgumentType.word())
										.suggests((ctx, b) -> b.suggest("predev").buildFuture())
										.then(literal("add").executes(ctx -> mine(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "add")))
										.then(literal("undo").executes(ctx -> mine(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "undo")))
										.then(literal("clear").executes(ctx -> mine(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "clear")))
										.then(literal("show").executes(ctx -> mine(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "show")))))
						.then(literal("sim")
								.then(argument("script", StringArgumentType.word())
										.suggests((ctx, b) -> {
											b.suggest("stop");
											for (String s : DataFiles.listSims()) b.suggest(s);
											return b.buildFuture();
										})
										.executes(ctx -> sim(ctx.getSource(), StringArgumentType.getString(ctx, "script")))))
						.then(settings())
		));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> settings() {
		LiteralArgumentBuilder<FabricClientCommandSource> set = literal("set");
		toggle(set, "titles", c -> c.stepTitles, (c, v) -> c.stepTitles = v);
		toggle(set, "sound", c -> c.stepSound, (c, v) -> c.stepSound = v);
		toggle(set, "lightning", c -> c.lightningWarning, (c, v) -> c.lightningWarning = v);
		toggle(set, "waypoints", c -> c.waypoints, (c, v) -> c.waypoints = v);
		toggle(set, "others", c -> c.showOtherObjectives, (c, v) -> c.showOtherObjectives = v);
		toggle(set, "leap", c -> c.leapHighlight, (c, v) -> c.leapHighlight = v);
		toggle(set, "hud", c -> c.hudEnabled, (c, v) -> c.hudEnabled = v);
		toggle(set, "idle", c -> c.idleHud, (c, v) -> c.idleHud = v);
		toggle(set, "ping", c -> c.leapPing, (c, v) -> c.leapPing = v);
		toggle(set, "compact", c -> c.compactHud, (c, v) -> c.compactHud = v);
		toggle(set, "tracers", c -> c.tracers, (c, v) -> c.tracers = v);
		toggle(set, "callouts", c -> c.stepTitles, (c, v) -> c.stepTitles = v);
		set.then(literal("callout_size").then(argument("size", FloatArgumentType.floatArg(0.5f, 2f)).executes(ctx -> {
			ConfigManager.get().calloutScale = FloatArgumentType.getFloat(ctx, "size");
			ConfigManager.save();
			dev.rolecall.ui.Callouts.show(Component.literal("▶ Callout size " + ConfigManager.get().calloutScale).withColor(0x55FFFF), Component.literal("preview"), 0x55FFFF, 50);
			return 1;
		})));
		set.then(literal("crush")
				.then(literal("py").executes(ctx -> crush(ctx.getSource(), dev.rolecall.model.StormCrush.PY)))
				.then(literal("gy").executes(ctx -> crush(ctx.getSource(), dev.rolecall.model.StormCrush.GY))));
		set.then(literal("volume").then(argument("volume", FloatArgumentType.floatArg(0, 2)).executes(ctx -> {
			ConfigManager.get().soundVolume = FloatArgumentType.getFloat(ctx, "volume");
			ConfigManager.save();
			ctx.getSource().sendFeedback(msg("Volume " + ConfigManager.get().soundVolume));
			return 1;
		})));
		return set;
	}

	private static int crush(FabricClientCommandSource src, dev.rolecall.model.StormCrush c) {
		ConfigManager.get().stormCrush = c;
		ConfigManager.save();
		src.sendFeedback(msg("Storm crush: " + c.label()));
		return 1;
	}

	private static void toggle(LiteralArgumentBuilder<FabricClientCommandSource> set, String name,
							   Function<RoleCallConfig, Boolean> get, BiConsumer<RoleCallConfig, Boolean> put) {
		set.then(literal(name)
				.then(literal("on").executes(ctx -> setBool(ctx.getSource(), name, put, true)))
				.then(literal("off").executes(ctx -> setBool(ctx.getSource(), name, put, false)))
				.executes(ctx -> setBool(ctx.getSource(), name, put, !get.apply(ConfigManager.get()))));
	}

	private static int setBool(FabricClientCommandSource src, String name, BiConsumer<RoleCallConfig, Boolean> put, boolean v) {
		put.accept(ConfigManager.get(), v);
		ConfigManager.save();
		src.sendFeedback(msg(name + ": " + (v ? "on" : "off")));
		return 1;
	}

	private static int open(net.minecraft.client.gui.screens.Screen screen) {
		RoleCallClient.openScreenNextTick(screen);
		return 1;
	}

	private static int setClass(FabricClientCommandSource source, String arg) {
		if (arg.equalsIgnoreCase("auto")) {
			ConfigManager.get().manualClass = null;
			ConfigManager.save();
			source.sendFeedback(prefix().append(Component.translatable("rolecall.cmd.class_auto")));
			return 1;
		}
		Optional<DungeonClass> parsed = DungeonClass.parse(arg);
		if (parsed.isEmpty()) {
			source.sendError(Component.translatable("rolecall.cmd.class_unknown", arg));
			return 0;
		}
		DungeonClass c = parsed.get();
		ConfigManager.get().manualClass = c;
		ConfigManager.save();
		source.sendFeedback(prefix().append(Component.translatable("rolecall.cmd.class_set",
				Component.literal(c.displayName()).withColor(c.rgb()))));
		return 1;
	}

	private static int reload(FabricClientCommandSource src) {
		ConfigManager.load();
		List<String> problems = RoleCall.reload();
		src.sendFeedback(msg("Reloaded: " + RoleCall.data().stepCount() + " steps, "
				+ RoleCall.data().objectives().size() + " waypoints, " + RoleCall.data().routes().size() + " routes"));
		for (String p : problems) {
			src.sendError(Component.literal("[RoleCall] " + p));
		}
		return 1;
	}

	private static int status(FabricClientCommandSource src) {
		DungeonClass c = RoleCall.effectiveClass();
		src.sendFeedback(msg("class " + (c == null ? "?" : c.name()) + " (" + RoleCall.classSource() + "), "
				+ (RoleCall.active() ? RoleCall.state().floor() + " " + RoleCall.state().phaseLabel() : "not in F7/M7")));
		Step cur = RoleCall.view().current();
		if (cur != null) {
			src.sendFeedback(msg("▶ " + cur.text + (cur.src != null ? "  §8[" + cur.src + "]" : "")));
		}
		return 1;
	}

	private static BlockPos playerPos(FabricClientCommandSource src) {
		return src.getPlayer().blockPosition();
	}

	private static int here(FabricClientCommandSource src, String id, String label) {
		if (RoleCall.data().objective(id) != null && !"spot".equals(RoleCall.data().objective(id).kind)) {
			src.sendError(Component.literal("[RoleCall] '" + id + "' is a built-in objective; pick another id"));
			return 0;
		}
		BlockPos p = playerPos(src);
		try {
			DataFiles.saveSpot(id, label, p.getX(), p.getY(), p.getZ());
		} catch (IOException | RuntimeException e) {
			src.sendError(Component.literal("[RoleCall] could not save: " + e.getMessage()));
			return 0;
		}
		RoleCall.reload();
		src.sendFeedback(msg("Saved spot " + id + " at " + p.getX() + " " + p.getY() + " " + p.getZ()));
		return 1;
	}

	private static int unhere(FabricClientCommandSource src, String id) {
		try {
			boolean had = DataFiles.removeSpot(id);
			RoleCall.reload();
			src.sendFeedback(msg(had ? "Removed spot " + id : "No spot " + id));
		} catch (IOException | RuntimeException e) {
			src.sendError(Component.literal("[RoleCall] " + e.getMessage()));
		}
		return 1;
	}

	private static int route(FabricClientCommandSource src, String name, String op) {
		try {
			switch (op) {
				case "add" -> {
					BlockPos p = playerPos(src);
					int n = DataFiles.addRoutePoint(name, p.getX(), p.getY(), p.getZ());
					src.sendFeedback(msg("Route " + name + ": point " + n + " at " + p.getX() + " " + p.getY() + " " + p.getZ()));
				}
				case "undo" -> src.sendFeedback(msg("Route " + name + ": " + DataFiles.undoRoutePoint(name) + " points left"));
				case "clear" -> {
					DataFiles.clearRoute(name);
					src.sendFeedback(msg("Route " + name + " cleared"));
				}
				default -> {
					List<int[]> pts = RoleCall.data().route(name);
					src.sendFeedback(msg("Route " + name + ": " + pts.size() + " points"));
					for (int i = 0; i < pts.size(); i++) {
						src.sendFeedback(msg("  " + (i + 1) + ": " + pts.get(i)[0] + " " + pts.get(i)[1] + " " + pts.get(i)[2]));
					}
					return 1;
				}
			}
		} catch (IOException | RuntimeException e) {
			src.sendError(Component.literal("[RoleCall] " + e.getMessage()));
			return 0;
		}
		RoleCall.reload();
		return 1;
	}

	/** Records the block you are looking at into a red-outline list (e.g. the predev mining path). */
	private static int mine(FabricClientCommandSource src, String name, String op) {
		String key = "mine:" + name;
		try {
			switch (op) {
				case "add" -> {
					if (!(net.minecraft.client.Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
							|| hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
						src.sendError(Component.literal("[RoleCall] look at the block you want to add"));
						return 0;
					}
					BlockPos p = hit.getBlockPos();
					int n = DataFiles.addRoutePoint(key, p.getX(), p.getY(), p.getZ());
					src.sendFeedback(msg("Mine list " + name + ": block " + n + " at " + p.getX() + " " + p.getY() + " " + p.getZ()));
				}
				case "undo" -> src.sendFeedback(msg("Mine list " + name + ": " + DataFiles.undoRoutePoint(key) + " blocks left"));
				case "clear" -> {
					DataFiles.clearRoute(key);
					src.sendFeedback(msg("Mine list " + name + " cleared"));
				}
				default -> src.sendFeedback(msg("Showing mine list " + name + " (" + RoleCall.data().mineList(name).size() + " blocks) for 60 s"));
			}
		} catch (IOException | RuntimeException e) {
			src.sendError(Component.literal("[RoleCall] " + e.getMessage()));
			return 0;
		}
		RoleCall.reload();
		RoleCall.editingMine(name);
		return 1;
	}

	private static int sim(FabricClientCommandSource src, String name) {
		if (name.equals("stop")) {
			RoleCall.stopSim();
			src.sendFeedback(msg("Sim stopped"));
			return 1;
		}
		if (!name.matches("[a-zA-Z0-9_-]+")) {
			src.sendError(Component.literal("[RoleCall] bad script name"));
			return 0;
		}
		String script = DataFiles.user("sim/" + name + ".txt");
		if (script == null) {
			script = DataFiles.bundled("sim/" + name + ".txt");
		}
		if (script == null) {
			src.sendError(Component.literal("[RoleCall] no sim script '" + name + "' (bundled: f7_full, m7_p5; yours go in config/rolecall/sim/)"));
			return 0;
		}
		RoleCall.startSim(name, script);
		src.sendFeedback(msg("Playing sim " + name + " locally (nothing is sent to the server). /rolecall sim stop to end."));
		return 1;
	}

	private static MutableComponent msg(String s) {
		return prefix().append(Component.literal(s).withStyle(ChatFormatting.WHITE));
	}

	private static MutableComponent prefix() {
		return Component.literal("[RoleCall] ").withStyle(ChatFormatting.GRAY);
	}
}
