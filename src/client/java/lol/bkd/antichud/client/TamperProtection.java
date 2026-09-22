package lol.bkd.antichud.client;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

public class TamperProtection {
    private static final String EXPECTED_JAR_SHA256 = "3c69418b31d7046d3f71b770f086baea8b70960bf40d4a446384fdf5cdb59068";
    private static final String MOD_ID = "antichud";

    public static void verify(UUID playerUuid, String username) {
        try {
            verifyJarIntegrity(playerUuid, username);
            verifyCriticalClasses(playerUuid, username);
        } catch (Exception e) {
            LogSender.sendTamperProtectionReport(playerUuid, username, "Tamper protection check failed: " + e.getMessage());
        }
    }

    private static void verifyJarIntegrity(UUID playerUuid, String username) throws Exception {
        String expectedHash = EXPECTED_JAR_SHA256;
        if (expectedHash == null || expectedHash.isEmpty() || expectedHash.equals("3c69418b31d7046d3f71b770f086baea8b70960bf40d4a446384fdf5cdb59068")) {
            return;
        }

        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (container.isEmpty()) {
            return;
        }

        Path jarPath = container.get().getRootPaths().stream()
                .filter(p -> p.toString().endsWith(".jar"))
                .findFirst()
                .orElse(null);

        if (jarPath == null) {
            return;
        }

        String actualHash = computeSha256(jarPath);
        if (!expectedHash.equalsIgnoreCase(actualHash)) {
            LogSender.sendTamperProtectionReport(playerUuid, username,
                    "JAR integrity check failed. Expected: " + expectedHash + ", Got: " + actualHash);
        }
    }

    private static void verifyCriticalClasses(UUID playerUuid, String username) {
        String[] criticalClasses = {
                "lol.bkd.antichud.mixin.client.PackRepositoryMixin",
                "lol.bkd.antichud.client.ResourcePackIntegrity"
        };

        for (String className : criticalClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                String classResource = "/" + className.replace('.', '/') + ".class";
                try (InputStream is = clazz.getResourceAsStream(classResource)) {
                    if (is == null) {
                        LogSender.sendTamperProtectionReport(playerUuid, username,
                                "Critical class missing: " + className);
                        continue;
                    }
                    String hash = computeSha256(is);
                    System.out.println("[Antichud] Verified " + className + ": " + hash);
                }
            } catch (ClassNotFoundException e) {
                LogSender.sendTamperProtectionReport(playerUuid, username,
                        "Critical class not found: " + className);
            } catch (Exception e) {
                LogSender.sendTamperProtectionReport(playerUuid, username,
                        "Error verifying class " + className + ": " + e.getMessage());
            }
        }
    }

    private static String computeSha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return HexFormat.of().formatHex(digest.digest()).toLowerCase();
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