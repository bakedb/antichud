package lol.bkd.antichud.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Asks GitHub whether a newer antichud build has been published.
 *
 * <p>The call is made off the render thread and never blocks the game. If anything goes wrong
 * (offline, rate limited, unexpected payload) the returned future fails and the caller decides
 * what to do, which is to leave the player alone rather than lock them out of their own game.
 */
public final class UpdateChecker {
    public static final String RELEASES_PAGE = "https://github.com/bakedb/antichud/releases";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/bakedb/antichud/releases/latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * @param currentVersion version of the mod that is currently installed
     * @param latestVersion  tag of the newest published release
     * @param releaseUrl     human readable page for the newest release
     * @param downloadUrl    direct link to the released jar, or {@code null} when the release has no assets
     */
    public record UpdateInfo(String currentVersion, String latestVersion, String releaseUrl, String downloadUrl) {
        public boolean updateAvailable() {
            return ModVersion.compare(latestVersion, currentVersion) > 0;
        }

        /** Best link to hand to a player who needs to update. */
        public String downloadLink() {
            return downloadUrl == null || downloadUrl.isBlank() ? releaseUrl : downloadUrl;
        }
    }

    private UpdateChecker() {
    }

    public static CompletableFuture<UpdateInfo> checkAsync(String currentVersion) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LATEST_RELEASE_API))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "antichud/" + currentVersion)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("releases API responded with HTTP " + response.statusCode());
                    }
                    return parse(currentVersion, response.body());
                });
    }

    private static UpdateInfo parse(String currentVersion, String body) {
        JsonObject release;
        try {
            release = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception malformed) {
            throw new IllegalStateException("could not read the releases response: " + malformed.getMessage(), malformed);
        }

        String latestVersion = readString(release, "tag_name", null);
        if (latestVersion == null) {
            throw new IllegalStateException("releases response did not contain a tag name");
        }

        String releaseUrl = readString(release, "html_url", RELEASES_PAGE);
        return new UpdateInfo(currentVersion, latestVersion, releaseUrl, findJarAsset(release));
    }

    private static String findJarAsset(JsonObject release) {
        JsonElement assets = release.get("assets");
        if (assets == null || !assets.isJsonArray()) {
            return null;
        }

        String fallback = null;
        JsonArray array = assets.getAsJsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject asset = element.getAsJsonObject();
            String url = readString(asset, "browser_download_url", null);
            String name = readString(asset, "name", "");
            if (url == null || !name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                continue;
            }
            if (name.toLowerCase(Locale.ROOT).startsWith("antichud")) {
                return url;
            }
            if (fallback == null) {
                fallback = url;
            }
        }
        return fallback;
    }

    private static String readString(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }
}
