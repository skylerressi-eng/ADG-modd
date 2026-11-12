package dev.rolecall.ui;

import dev.rolecall.config.ConfigManager;
import dev.rolecall.config.RoleCallConfig;
import dev.rolecall.model.DungeonClass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** {@code /rolecall}: pick your class (or Auto) and the per-run toggles. */
public final class ClassPickerScreen extends Screen {
	private static final int CLASS_W = 76;
	private static final int CLASS_H = 36;
	private static final int GAP = 6;

	private final Map<DungeonClass, Button> classButtons = new EnumMap<>(DungeonClass.class);
	private Button autoButton;
	private Button predevButton;
	private Button splitButton;

	public ClassPickerScreen() {
		super(Component.translatable("rolecall.picker.title"));
	}

	@Override
	protected void init() {
		classButtons.clear();
		DungeonClass[] classes = DungeonClass.values();
		int rowW = classes.length * CLASS_W + (classes.length - 1) * GAP;
		int x = (this.width - rowW) / 2;
		int y = this.height / 2 - 60;
		for (DungeonClass c : classes) {
			Button b = Button.builder(Component.literal(c.displayName()).withColor(c.rgb()), btn -> pick(c))
					.bounds(x, y, CLASS_W, CLASS_H).build();
			classButtons.put(c, addRenderableWidget(b));
			x += CLASS_W + GAP;
		}

		int cx = this.width / 2;
		y += CLASS_H + 12;
		autoButton = addRenderableWidget(Button.builder(Component.translatable("rolecall.picker.auto"), b -> pick(null))
				.bounds(cx - 100, y, 200, 20)
				.tooltip(Tooltip.create(Component.translatable("rolecall.picker.auto.tooltip"))).build());
		y += 26;
		predevButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
					RoleCallConfig cfg = ConfigManager.get();
					cfg.predev = cfg.predev.next();
					refreshLabels();
				}).bounds(cx - 100, y, 200, 20)
				.tooltip(Tooltip.create(Component.translatable("rolecall.picker.predev.tooltip"))).build());
		y += 24;
		splitButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
					RoleCallConfig cfg = ConfigManager.get();
					cfg.s4Split = cfg.s4Split.other();
					refreshLabels();
				}).bounds(cx - 150, y, 300, 20)
				.tooltip(Tooltip.create(Component.translatable("rolecall.picker.s4.tooltip"))).build());
		y += 32;
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(cx - 50, y, 100, 20).build());
		refreshLabels();
	}

	private void pick(DungeonClass c) {
		ConfigManager.get().manualClass = c;
		refreshLabels();
	}

	private void refreshLabels() {
		RoleCallConfig cfg = ConfigManager.get();
		autoButton.setMessage(Component.translatable(cfg.manualClass == null
				? "rolecall.picker.auto.selected" : "rolecall.picker.auto"));
		predevButton.setMessage(Component.translatable("rolecall.picker.predev",
				Component.translatable("rolecall.predev." + cfg.predev.name().toLowerCase(Locale.ROOT))));
		splitButton.setMessage(Component.translatable("rolecall.picker.s4",
				Component.translatable("rolecall.s4." + cfg.s4Split.dataId())));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 90, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("rolecall.picker.subtitle"),
				this.width / 2, this.height / 2 - 78, 0xFFAAAAAA);

		// Thick class-colored frame around the picked class.
		DungeonClass picked = ConfigManager.get().manualClass;
		if (picked != null) {
			Button b = classButtons.get(picked);
			for (int i = 1; i <= 2; i++) {
				graphics.outline(b.getX() - i, b.getY() - i, b.getWidth() + 2 * i, b.getHeight() + 2 * i, picked.argb());
			}
		}
	}

	@Override
	public void onClose() {
		ConfigManager.save();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
