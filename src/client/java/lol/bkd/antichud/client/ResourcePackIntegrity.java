package lol.bkd.antichud.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HexFormat;

public class ResourcePackIntegrity {
    private static final Map<Identifier, String> EXPECTED_TEXTURE_HASHES = new HashMap<>();

    static {
        EXPECTED_TEXTURE_HASHES.put(
                Identifier.fromNamespaceAndPath("minecraft", "textures/block/stone.png"),
                "037b082f321810af54137d08f54c2e68e5e556a9cc39dafad7863b155d6d221f"
        );
        EXPECTED_TEXTURE_HASHES.put(
                Identifier.fromNamespaceAndPath("minecraft", "textures/block/netherrack.png"),
                "0baee95bbff82f4aecc0887e7c3d33763b629e28b80276e3573e71b0bf4396ab"
        );
        EXPECTED_TEXTURE_HASHES.put(
                Identifier.fromNamespaceAndPath("minecraft", "textures/block/deepslate.png"),
                "db25544a7210ec6fe691ea3aa95041217bbd7c3b777c2d4e7ac13a89aebbfd47"
        );
    }

    public static void verify(UUID playerUuid, String username) {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        
        if (resourceManager == null) {
            return;
        }

        for (Map.Entry<Identifier, String> entry : EXPECTED_TEXTURE_HASHES.entrySet()) {
            Identifier location = entry.getKey();
            String expectedHash = entry.getValue();

            if (expectedHash == null || expectedHash.length() != 64) {
                continue;
            }

            try (InputStream is = resourceManager.open(location)) {
                if (is == null) {
                    LogSender.sendTamperProtectionReport(playerUuid, username,
                            "Resource pack texture missing: " + location);
                    continue;
                }

                String actualHash = computeSha256(is);
                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    LogSender.sendTamperProtectionReport(playerUuid, username,
                            "Resource pack texture modified: " + location + " (expected: " + expectedHash + ", got: " + actualHash + ")");
                }
            } catch (Exception e) {
                LogSender.sendTamperProtectionReport(playerUuid, username,
                        "Error verifying texture " + location + ": " + e.getMessage());
            }
        }
    }

    private static String computeSha256(InputStream is) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        return HexFormat.of().formatHex(digest.digest()).toLowerCase();
    }
}