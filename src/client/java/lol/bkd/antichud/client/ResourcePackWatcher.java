package lol.bkd.antichud.client;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.UUID;

/**
 * Runs {@link ResourcePackIntegrity} against the resource manager that is actually in effect.
 *
 * <p>This has to be a resource reload listener. The old code called the check from
 * {@code ClientModInitializer#onInitializeClient}, which runs before Minecraft has built its
 * resource manager at all - {@code getResourceManager()} returned {@code null} and the check
 * returned immediately, so it never compared a single byte. A reload listener runs after the pack
 * stack has been assembled, and again on every reload, so enabling or disabling a texture pack
 * afterwards is caught too.
 */
public final class ResourcePackWatcher {
    private ResourcePackWatcher() {
    }

    public static void install() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath("antichud", "resource_pack_watcher");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        // The player session is not available during mod init, so read it here and
                        // cache it. If it is somehow still unavailable, skip: reporting under a
                        // made-up identity would be worse than not reporting at all.
                        if (!cacheIdentity()) {
                            return;
                        }
                        ResourcePackIntegrity.verify(manager, cachedUuid, cachedName);
                    }
                });
    }

    private static UUID cachedUuid;
    private static String cachedName;

    private static boolean cacheIdentity() {
        if (cachedUuid == null) {
            try {
                var user = net.minecraft.client.Minecraft.getInstance().getUser();
                cachedUuid = user.getProfileId();
                cachedName = user.getName();
            } catch (Throwable ignored) {
                // Not ready yet; try again on the next reload.
            }
        }
        return cachedUuid != null && cachedName != null;
    }
}
