package lol.bkd.antichud.client;

import lol.bkd.antichud.update.UpdateGate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public class AntichudClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FabricLoader.getInstance().getModContainer("antichud").ifPresent(container -> {
            ResourceLoader.registerBuiltinPack(
                Identifier.fromNamespaceAndPath("antichud", "default_textures"),
                container,
                PackActivationType.ALWAYS_ENABLED
            );
        });

        // Started as early as possible so the answer is ready by the time the player reaches the
        // server list, without ever holding up the loading screen.
        UpdateGate.startCheck();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Backstop: the connect gate already stops outdated clients, this catches anything
            // that manages to get onto a server without going through it.
            if (UpdateGate.blocksMultiplayer()) {
                client.disconnectFromWorld(UpdateGate.blockedReason());
            }
        });

        runSecurityChecks();
    }

    private void runSecurityChecks() {
        Minecraft minecraft = Minecraft.getInstance();
        UUID playerUuid = minecraft.getUser().getProfileId();
        String username = minecraft.getUser().getName();

        if (playerUuid == null || username == null) {
            return;
        }

        TamperProtection.verify(playerUuid, username);
        ResourcePackIntegrity.verify(playerUuid, username);
        BannedModChecker.check(playerUuid, username);
    }
}
