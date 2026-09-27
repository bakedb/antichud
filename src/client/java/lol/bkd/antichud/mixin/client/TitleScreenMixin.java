package lol.bkd.antichud.mixin.client;

import lol.bkd.antichud.update.UpdateBanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the update notice on screen as soon as the game has finished loading.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Unique
    private UpdateBanner.LinkArea antichud$downloadLink;

    @Inject(method = "render", at = @At("TAIL"))
    private void antichud$renderUpdateNotice(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.antichud$downloadLink = UpdateBanner.render(graphics, Minecraft.getInstance().font,
                graphics.guiWidth(), graphics.guiHeight(), mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void antichud$openDownloadPage(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == 0 && this.antichud$downloadLink != null
                && this.antichud$downloadLink.contains(event.x(), event.y())) {
            UpdateBanner.openDownloadPage();
            cir.setReturnValue(true);
        }
    }
}
