package lol.bkd.antichud.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public class LogSender {
    private static final String SERVER_URL = "https://antichud.bakedb.xyz/log";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Gson GSON = new Gson();

    public static void sendBannedModReport(UUID playerUuid, String username, String modName) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", playerUuid.toString());
        payload.addProperty("username", username);
        payload.addProperty("event_type", "BANNED_MOD");
        payload.addProperty("details", "'" + modName + "' mod detected.");
        sendAsync(payload);
    }

    public static void sendTamperProtectionReport(UUID playerUuid, String username, String details) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", playerUuid.toString());
        payload.addProperty("username", username);
        payload.addProperty("event_type", "TAMPER_PROTECTION");
        payload.addProperty("details", details);
        sendAsync(payload);
    }

    private static void sendAsync(JsonObject payload) {
        String json = GSON.toJson(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        System.err.println("[Antichud] Failed to send report: " + response.statusCode());
                    }
                })
                .exceptionally(throwable -> {
                    System.err.println("[Antichud] Error sending report: " + throwable.getMessage());
                    return null;
                });
    }
}