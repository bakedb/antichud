package lol.bkd.antichud.mixin.client;

import lol.bkd.antichud.client.startup.StartupBackground;
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
    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // A player with a file in assets/antichud/textures/gui/startup/ gets that image instead of
        // the flat colour. Anything drawn here lands under the logo and the memory bar, both of
        // which vanilla draws further down in the same method.
        if (!StartupBackground.render(graphics)) {
            graphics.fill(0, 0,
                    Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    Minecraft.getInstance().getWindow().getGuiScaledHeight(),
                    0xFF131731);
        }

        // The jingle, once per session, on the first frame the loading screen is up. It goes after
        // the background so the sound lands on a loaded image rather than a bare colour.
        StartupSound.play();
    }
}
