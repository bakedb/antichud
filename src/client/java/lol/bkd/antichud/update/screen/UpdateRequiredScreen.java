package lol.bkd.antichud.update.screen;

import lol.bkd.antichud.update.LinkOpener;
import lol.bkd.antichud.update.UpdateChecker;
import lol.bkd.antichud.update.UpdateGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Tells the player that a newer antichud build is published and that multiplayer is not going to
 * happen until they either update the mod or remove it.
 */
public class UpdateRequiredScreen extends Screen {
    private static final Component TITLE = Component.literal("Antichud update required");
    private static final int BUTTON_WIDTH = 150;
    private static final int WRAP_WIDTH = 260;

    private final Screen parent;

    public UpdateRequiredScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        Minecraft minecraft = Minecraft.getInstance();
        String link = downloadLink();

        int left = this.width / 2 - BUTTON_WIDTH - 1;
        int right = this.width / 2 + 1;
        int top = this.height / 2 + 40;

        addRenderableWidget(Button.builder(Component.literal("Download update"), button ->
                        LinkOpener.open(link))
                .bounds(left, top, BUTTON_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Copy link"), button -> {
                    minecraft.keyboardHandler.setClipboard(link);
                    button.setMessage(Component.literal("Copied"));
                })
                .bounds(right, top, BUTTON_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Check again"), button -> {
                    UpdateGate.recheck();
                    minecraft.setScreen(new UpdateCheckScreen(this.parent, null));
                })
                .bounds(left, top + 24, BUTTON_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(right, top + 24, BUTTON_WIDTH, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);

        int centre = this.width / 2;
        int y = this.height / 2 - 30;
        graphics.drawCenteredString(this.font, TITLE, centre, y, 0xFFFF5555);
        y += 14;

        UpdateChecker.UpdateInfo update = UpdateGate.info();
        if (update != null) {
            y = drawLines(graphics, centre, y, List.of(
                    Component.literal("Installed: " + update.currentVersion() + "    Latest: " + update.latestVersion())
            ), 0xFFFFFFFF);
        }
        y = drawLines(graphics, centre, y, List.of(
                Component.literal("Update antichud to play on multiplayer servers, or remove it from your mods folder to play without it.")
        ), 0xFFAAAAAA);
        y = drawLines(graphics, centre, y, List.of(
                Component.literal("Restart Minecraft once you have updated.")
        ), 0xFFAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    private int drawLines(GuiGraphics graphics, int centre, int y, List<Component> paragraphs, int colour) {
        for (Component paragraph : paragraphs) {
            for (FormattedCharSequence line : this.font.split(paragraph, WRAP_WIDTH)) {
                graphics.drawCenteredString(this.font, line, centre, y, colour);
                y += this.font.lineHeight;
            }
            y += 4;
        }
        return y;
    }

    private static String downloadLink() {
        UpdateChecker.UpdateInfo update = UpdateGate.info();
        return update == null ? UpdateChecker.RELEASES_PAGE : update.downloadLink();
    }
}
