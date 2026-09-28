package lol.bkd.antichud.client;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

public class TamperProtection {
    public static void verify(UUID playerUuid, String username) {
        verifyCriticalClasses(playerUuid, username);
    }

    private static void verifyCriticalClasses(UUID playerUuid, String username) {
        // Only plain classes belong here. A @Mixin class cannot be loaded with Class.forName -
        // Mixin throws "Mixin transformation of ... failed" because a mixin is only valid inside
        // the transformation pipeline, not as a standalone class. Listing one here produced a false
        // tamper report on every single launch.
        String[] criticalClasses = {
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