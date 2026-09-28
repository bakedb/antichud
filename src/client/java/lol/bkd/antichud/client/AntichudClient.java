package lol.bkd.antichud.client;

import lol.bkd.antichud.update.UpdateGate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import java.util.UUID;

public class AntichudClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // The forced vanilla texture pack is installed by PackRepositoryMixin, which injects it
        // into the selected pack list with required=true at Position.TOP. It is deliberately not
        // registered here as well: ResourceLoader.registerBuiltinPack resolves to
        // "resourcepacks/<id path>", i.e. "resourcepacks/default_textures", which does not exist,
        // so that call silently returned false and registered nothing.

        // Started as early as possible so the answer is ready by the time the player reaches the
        // server list, without ever holding up the loading screen.
        UpdateGate.startCheck();

        // Texture integrity can only be judged once the pack stack exists, so it is driven by a
        // resource reload listener rather than from here.
        ResourcePackWatcher.install();

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
        BannedModChecker.check(playerUuid, username);
    }
}
