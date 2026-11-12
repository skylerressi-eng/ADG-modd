package dev.rolecall.cmd;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.rolecall.RoleCallClient;
import dev.rolecall.config.ConfigManager;
import dev.rolecall.model.DungeonClass;
import dev.rolecall.ui.ClassPickerScreen;
import dev.rolecall.ui.HudEditorScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Optional;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** {@code /rolecall} command tree. Client-side only; nothing is sent to the server. */
public final class RoleCallCommands {
	private RoleCallCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				literal("rolecall")
						.executes(ctx -> {
							RoleCallClient.openScreenNextTick(new ClassPickerScreen());
							return 1;
						})
						.then(literal("class")
								.then(argument("class", StringArgumentType.word())
										.suggests((ctx, b) -> {
											b.suggest("auto");
											for (DungeonClass c : DungeonClass.values()) {
												b.suggest(c.name().toLowerCase(Locale.ROOT));
											}
											return b.buildFuture();
										})
										.executes(ctx -> setClass(ctx.getSource(), StringArgumentType.getString(ctx, "class")))))
						.then(literal("hud").executes(ctx -> {
							RoleCallClient.openScreenNextTick(new HudEditorScreen());
							return 1;
						}))
		));
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

	private static net.minecraft.network.chat.MutableComponent prefix() {
		return Component.literal("[RoleCall] ").withStyle(ChatFormatting.GRAY);
	}
}
