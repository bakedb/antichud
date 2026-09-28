package lol.bkd.antichud.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LogSender {
    private static final String SERVER_URL = "https://antichud.bakedb.xyz/log";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Gson GSON = new Gson();

    // One report per distinct message per session. A single bad resource pack can trip several
    // checks at once, and without this the server gets the same report repeated on every reload.
    private static final Set<String> SENT = ConcurrentHashMap.newKeySet();

    public static void sendBannedModReport(UUID playerUuid, String username, String modName) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", playerUuid.toString());
        payload.addProperty("username", username);
        payload.addProperty("event_type", "BANNED_MOD");
        payload.addProperty("details", "'" + modName + "' mod detected.");
        sendAsync(payload);
    }

    public static void sendTamperProtectionReport(UUID playerUuid, String username, String details) {
        if (!SENT.add(details)) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", playerUuid.toString());
        payload.addProperty("username", username);
        payload.addProperty("event_type", "TAMPER_PROTECTION");
        payload.addProperty("details", details);
        System.out.println("[Antichud] Reporting: " + details);
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
                    // Log the success too: without this there is no way to tell from the game log
                    // whether a report was actually delivered.
                    if (response.statusCode() != 200) {
                        System.err.println("[Antichud] Failed to send report: " + response.statusCode());
                    } else {
                        System.out.println("[Antichud] Report accepted (HTTP 200).");
                    }
                })
                .exceptionally(throwable -> {
                    System.err.println("[Antichud] Error sending report: " + throwable.getMessage());
                    return null;
                });
    }
}