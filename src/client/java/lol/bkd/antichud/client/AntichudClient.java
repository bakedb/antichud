package lol.bkd.antichud.client;

import net.fabricmc.api.ClientModInitializer;
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