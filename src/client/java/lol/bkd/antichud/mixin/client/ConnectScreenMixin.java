package lol.bkd.antichud.mixin.client;

import lol.bkd.antichud.update.UpdateGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every way of joining a multiplayer server funnels through
 * {@link ConnectScreen#startConnecting(Screen, Minecraft, ServerAddress, ServerData, boolean, TransferState)}
 * (the server list, direct connect, quick play and server transfers), so gating that single call
 * covers all of them.
 */
@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {

    @Inject(method = "startConnecting", at = @At("HEAD"), cancellable = true)
    private static void antichud$requireUpToDateMod(Screen parentScreen, Minecraft minecraft, ServerAddress address,
                                                     ServerData serverData, boolean quickPlay, TransferState transferState,
                                                     CallbackInfo ci) {
        boolean allowed = UpdateGate.allowConnect(parentScreen, () -> ConnectScreen.startConnecting(
                parentScreen, minecraft, address, serverData, quickPlay, transferState));
        if (!allowed) {
            ci.cancel();
        }
    }
}
