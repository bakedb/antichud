package lol.bkd.antichud.update.screen;

import lol.bkd.antichud.update.UpdateGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shown when a player tries to join a server while the update check is still running.
 *
 * <p>The connection is held instead of being refused so the player does not have to guess why
 * nothing happened. As soon as the check resolves the connection either continues by itself or
 * the {@link UpdateRequiredScreen} takes over.
 */
public class UpdateCheckScreen extends Screen {
    private static final Component TITLE = Component.literal("Checking for antichud updates");
    private static final Component MESSAGE = Component.literal("Hang on, verifying that this is the latest build");

    private final Screen parent;
    private Runnable resume;

    public UpdateCheckScreen(Screen parent, Runnable resume) {
        super(TITLE);
        this.parent = parent;
        this.resume = resume;
    }

    @Override
    public void tick() {
        super.tick();

        if (UpdateGate.status() == UpdateGate.Status.CHECKING) {
            return;
        }

        Runnable pending = this.resume;
        Screen previous = this.parent;
        this.resume = null;

        Minecraft minecraft = Minecraft.getInstance();
        if (UpdateGate.blocksMultiplayer()) {
            minecraft.setScreen(new UpdateRequiredScreen(previous));
        } else if (pending != null) {
            pending.run();
        } else {
            minecraft.setScreen(previous);
        }
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(this.width / 2 - 100 / 2, this.height / 2 + 30, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);

        int centre = this.width / 2;
        int y = this.height / 2 - 20;
        graphics.drawCenteredString(this.font, TITLE, centre, y, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, MESSAGE, centre, y + 20, 0xFFAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
