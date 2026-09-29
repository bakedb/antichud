package lol.bkd.antichud.mixin.client;

import lol.bkd.antichud.client.startup.StartupAssets;
import lol.bkd.antichud.client.startup.StartupBackground;
import lol.bkd.antichud.client.startup.StartupProgress;
import lol.bkd.antichud.client.startup.StartupSound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;

@Mixin(LoadingOverlay.class)
public class SplashOverlayMixin {

    /**
     * Draws the background and, while it is still being fetched, the "Downloading assets..." card
     * on top.
     *
     * <p>Injected at HEAD so the background lands under the logo and the memory bar, both of
     * which vanilla draws further down in the same method.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // A player with a cached file in config/antichud/startup gets that image instead of the
        // flat colour. On a first launch there is no file yet, so the card below stands in until
        // the download lands.
        if (!StartupBackground.render(graphics)) {
            graphics.fill(0, 0,
                    Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    Minecraft.getInstance().getWindow().getGuiScaledHeight(),
                    0xFF131731);
        }

        // The jingle, once per session, on the first frame the loading screen is up. It goes after
        // the background so the sound lands on a loaded image rather than a bare colour.
        StartupSound.play();

        // Last, so it covers the logo and the memory bar rather than being covered by them.
        // Both are already on screen at this point, which is the point: the player never sees the
        // bare splash, they see this until their background is ready.
        if (StartupAssets.holds()) {
            StartupProgress.draw(graphics,
                    Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    Minecraft.getInstance().getWindow().getGuiScaledHeight(),
                    StartupAssets.phase(), StartupAssets.progress());
        }
    }

    /**
     * Keeps the loading overlay up until the background download finishes.
     *
     * <p>Vanilla starts the fade-out from here, gated on the resource reload completing, which on
     * a warm cache is a few hundred milliseconds - not nearly enough for a 700KB download. Holding
     * the tick is what makes the splash arrive with the picture already on it, so the player
     * experiences this as a loading screen in front of the game rather than a colour that becomes
     * a photograph a moment later.
     *
     * <p>It is deliberately the same overlay rather than a second window or screen: Minecraft
     * creates its window and sets this overlay in the same constructor, so there is nothing to draw
     * a separate screen into before this point, and a second window would flash and steal focus.
     *
     * <p>{@link StartupAssets#holds()} is false once the download resolves and also once its hard
     * deadline passes, so a player who is offline or behind a captive portal gets the plain colour
     * instead of a game that will not start.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        if (StartupAssets.holds()) {
            ci.cancel();
        }
    }
}
