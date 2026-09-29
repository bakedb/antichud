package lol.bkd.antichud.client.startup;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches the player's startup background from the server and keeps a copy on disk.
 *
 * <p>The images used to ship inside the mod jar, which meant adding one meant publishing a new
 * build for all six supported Minecraft versions. They now live on the server instead and are
 * cached under {@code config/antichud/startup}, so dropping a file into the server's
 * {@code assets/startup} directory is the entire deploy process.
 *
 * <p>Everything here runs on a daemon thread and is driven by {@link #begin}, which is safe to
 * call from the render thread on every frame of the loading overlay. The overlay holds itself
 * visible for as long as this is in flight (see {@link #holds}) so the player watches a progress
 * bar rather than sitting in front of a flat colour that suddenly becomes a photograph.
 *
 * <p>The request carries {@code If-None-Match} for both the manifest and the image, so the common
 * case - nothing has changed since the last launch - costs a 304 with no body and no image
 * download at all. The manifest is the single source of truth: adding, replacing or deleting a
 * file on the server changes its ETag, which is the only thing that makes clients look again.
 */
public final class StartupAssets {

    /** Where the images live. Same host as the log endpoint in LogSender. */
    private static final String BASE_URL = "https://antichud.bakedb.xyz/assets/startup/";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Hard ceiling on a single download.
     *
     * <p>{@link java.util.concurrent.CompletableFuture} aside, this is a plain file fetch over
     * HTTPS, but nothing stops a misconfigured or hostile server from sending a 2GB body, and the
     * result is read fully into memory before it is decoded. These are 300-700KB images; anything
     * past this is a bug or an attack, and failing is much cheaper than an OOM on the render
     * thread.
     */
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    /**
     * The longest the loading overlay is held for this, regardless of what the network is doing.
     *
     * <p>The overlay's fade-out is normally gated on the resource reload finishing; holding it
     * extends that. If the hold were unbounded then an offline player, a captive portal or a
     * dropped connection would wedge the game on a splash screen forever, which is a far worse
     * failure than the flat colour this is meant to avoid. Past this deadline the overlay is
     * released and whatever is on disk - or the flat colour - is used.
     */
    private static final long HOLD_CEILING_MS = 12_000L;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** What the UI shows and, more importantly, when the overlay is allowed to go away. */
    public enum Phase {
        /** Nothing started yet. */
        IDLE,
        /** Asking the server which background this player has. */
        CHECKING,
        /** Downloading the image itself. */
        DOWNLOADING,
        /** A background is on disk and will be drawn. */
        READY,
        /** The server is up and says this player has no background. Not an error. */
        ABSENT,
        /** The server could not be reached or the file was not usable. The cache is used as-is. */
        FAILED
    }

    private static final AtomicReference<Phase> PHASE = new AtomicReference<>(Phase.IDLE);
    private static final AtomicLong DOWNLOADED = new AtomicLong();
    private static final AtomicLong TOTAL = new AtomicLong(-1);
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile long startedAt;
    private static volatile String error;

    private StartupAssets() {
    }

    /**
     * Starts the fetch if it is not already running. Called from the render thread.
     *
     * <p>Idempotent and cheap after the first call: an atomic compare-and-set and a return. The
     * username is not passed in because it is not always available - see
     * {@link StartupBackground#resolve} for why it is read where it is.
     */
    public static void begin() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        startedAt = System.currentTimeMillis();
        Thread worker = new Thread(StartupAssets::run, "Antichud Startup Assets");
        worker.setDaemon(true);
        worker.start();
    }

    public static Phase phase() {
        return PHASE.get();
    }

    /** 0..1 while the size is known, or -1 for an indeterminate bar. */
    public static float progress() {
        long total = TOTAL.get();
        if (total <= 0) {
            return -1.0F;
        }
        return Math.min(1.0F, (float) DOWNLOADED.get() / (float) total);
    }

    public static String error() {
        return error;
    }

    /**
     * Whether the loading overlay should stay on screen.
     *
     * <p>True from the first call until this resolves one way or another, and never for longer
     * than {@link #HOLD_CEILING_MS}. A {@code null} username resolves immediately to
     * {@link Phase#FAILED} so an offline or LAN session never sits on a held splash.
     */
    /**
     * Whether the fetch has come to rest, one way or the other.
     *
     * <p>Distinct from {@link #holds()}, which additionally stops holding once the ceiling is
     * reached. {@code StartupBackground} needs the narrower question: "could a file still appear
     * on disk for this player?" That is only true before this returns true, and it is what decides
     * whether a failed lookup is a final answer or just a frame that was too early.
     */
    public static boolean settled() {
        if (!STARTED.get()) {
            return false;
        }
        Phase phase = PHASE.get();
        return phase == Phase.READY || phase == Phase.ABSENT || phase == Phase.FAILED;
    }

    public static boolean holds() {
        if (!STARTED.get()) {
            return false;
        }
        Phase phase = PHASE.get();
        if (phase == Phase.READY || phase == Phase.ABSENT || phase == Phase.FAILED) {
            return false;
        }
        return System.currentTimeMillis() - startedAt < HOLD_CEILING_MS;
    }

    private static void run() {
        try {
            fetch();
        } catch (Throwable failure) {
            // Never let this reach the game thread as an exception: the only consequence of any
            // failure here is a missing background, and the cache (if any) is still on disk.
            fail(failure);
        }
        // One line per launch saying what the fetch actually decided. Every failure mode above
        // ends in the same place visually - a flat colour - so without this there is no way to
        // tell an offline player from a player the server has no entry for, short of guessing.
        System.out.println("[Antichud] Startup backgrounds: " + PHASE.get()
                + (error == null ? "" : " (" + error + ")")
                + " after " + (System.currentTimeMillis() - startedAt) + "ms");
    }

    private static void fetch() {
        String name = StartupBackground.sanitise(MinecraftUser.name());
        if (name == null) {
            fail(new IllegalStateException("no player name available"));
            return;
        }
        // Lower cased to match the manifest's keys, which are built the same way server-side.
        String key = name.toLowerCase(Locale.ROOT);

        Path dir = cacheDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            fail(e);
            return;
        }

        PHASE.set(Phase.CHECKING);
        JsonObject manifest = readManifest(dir);
        if (manifest == null) {
            fail(new IOException("could not fetch the manifest"));
            return;
        }

        JsonObject entry = lookup(manifest, key);
        if (entry == null) {
            // The server is healthy and simply has nothing for this player. Fall through to
            // whatever is already cached, because a player who has a background cached and then
            // loses their slot on the server should not lose their picture too.
            if (cachedFile(dir, key).isPresent()) {
                PHASE.set(Phase.READY);
            } else {
                PHASE.set(Phase.ABSENT);
            }
            return;
        }

        String file = entry.has("file") ? entry.get("file").getAsString() : null;
        String etag = entry.has("etag") ? entry.get("etag").getAsString() : null;
        if (file == null || !file.matches("[A-Za-z0-9._-]+")) {
            fail(new IOException("manifest entry for '" + key + "' has no usable file name"));
            return;
        }

        // Already have this exact revision, so there is nothing to download. This is what keeps a
        // normal launch down to a single 304.
        Optional<Path> cached = cachedFile(dir, key);
        if (cached.isPresent() && etag != null && etag.equals(readText(dir.resolve(key + ".etag")))) {
            PHASE.set(Phase.READY);
            return;
        }

        // Saved under the player's own name, not the name the server happens to call the file.
        // StartupBackground#findFile looks the image up by player name and has no way to consult
        // the manifest, so storing it under the server's name would work only for as long as the
        // two agreed - and a server-side rename or a player renamed on their launcher would leave
        // a file on disk that nothing can ever find again. Keeping the extension (so ImageIO
        // still sniffs the format) and dropping the rest of the name makes the local file name a
        // function of the local key alone.
        String extension = extensionOf(file);
        String saved = key + extension;

        PHASE.set(Phase.DOWNLOADING);
        DOWNLOADED.set(0);
        TOTAL.set(-1);
        if (download(file, dir.resolve(key + ".part"), TOTAL)) {
            move(dir.resolve(key + ".part"), dir.resolve(saved));
            if (etag != null) {
                writeText(dir.resolve(key + ".etag"), etag);
            }
            dropSuperseded(dir, key, saved);
            PHASE.set(Phase.READY);
        } else {
            fail(new IOException("could not download " + file));
        }
    }

    /**
     * The lower-cased extension of a manifest file name, or an empty string if it has none.
     *
     * <p>Lower cased so the saved name is stable: the manifest is generated from a directory
     * listing, where {@code Daltzed.PNG} and {@code Daltzed.png} would otherwise produce two
     * different local file names for the same image.
     */
    private static String extensionOf(String file) {
        int dot = file.lastIndexOf('.');
        return dot < 0 ? "" : file.substring(dot).toLowerCase(Locale.ROOT);
    }

    /**
     * Fetches the manifest, revalidating against the ETag from last time.
     *
     * <p>The body is cached locally because a 304 means the server is not sending it again, and
     * the cached copy is what says which file to ask for next. Without it a returning player
     * would be told "nothing changed" and then have no idea what they were supposed to have.
     */
    private static JsonObject readManifest(Path dir) {
        Path bodyFile = dir.resolve("manifest.json");
        Path etagFile = dir.resolve("manifest.etag");
        String knownEtag = readText(etagFile);

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "manifest.json"))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "antichud");
        if (knownEtag != null) {
            request.header("If-None-Match", knownEtag);
        }

        try {
            HttpResponse<String> response = HTTP.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
            String newEtag = response.headers().firstValue("etag").orElse(null);

            if (response.statusCode() == 304) {
                return parse(bodyFile);
            }
            if (response.statusCode() != 200) {
                // Fall back to the cached manifest rather than giving up: an outdated background
                // beats no background.
                return parse(bodyFile);
            }

            writeText(bodyFile, response.body());
            if (newEtag != null) {
                writeText(etagFile, newEtag);
            }
            return parse(bodyFile);
        } catch (InterruptedException e) {
            // The worker is a daemon, so this only happens if something interrupts it during
            // shutdown. Put the flag back rather than swallowing it, and let the run loop treat
            // the interrupted fetch as a failure.
            Thread.currentThread().interrupt();
            return parse(bodyFile);
        } catch (IOException | RuntimeException e) {
            return parse(bodyFile);
        }
    }

    private static JsonObject parse(Path bodyFile) {
        String body = readText(bodyFile);
        if (body == null) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(body);
            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static JsonObject lookup(JsonObject manifest, String key) {
        JsonElement assets = manifest.get("assets");
        if (assets == null || !assets.isJsonObject()) {
            return null;
        }
        JsonElement entry = assets.getAsJsonObject().get(key);
        return entry != null && entry.isJsonObject() ? entry.getAsJsonObject() : null;
    }

    /**
     * Streams the image to {@code target}, reporting progress as it goes.
     *
     * <p>Deliberately sent <i>without</i> {@code If-None-Match}. The manifest is what decides
     * whether a download is needed, and it has already been revalidated by the time this runs, so
     * there is nothing left to revalidate here. Sending the ETag anyway is actively wrong: on a
     * first launch the client has no copy at all, the server correctly answers 304, and the player
     * ends up with a manifest saying they have a background and no background on disk.
     *
     * @return true when the file was written in full
     */
    private static boolean download(String file, Path target, AtomicLong total) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + file))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "antichud")
                .header("Accept", "image/*")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                error = "server responded " + response.statusCode();
                return false;
            }

            // Only trust Content-Length for the progress bar, never for the cap: a chunked
            // response simply reports nothing and the bar stays indeterminate.
            long declared = response.headers().firstValueAsLong("content-length").orElse(-1L);
            total.set(declared > 0 ? declared : -1L);

            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[16 * 1024];
                long written = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    written += read;
                    if (written > MAX_BYTES) {
                        throw new IOException("asset exceeded the " + MAX_BYTES + " byte cap");
                    }
                    out.write(buffer, 0, read);
                    DOWNLOADED.set(written);
                }
                return written > 0;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException e) {
            error = String.valueOf(e.getMessage());
            return false;
        }
    }

    /**
     * The cached image for a player, if one is on disk and non-empty.
     *
     * <p>Sorted before taking the first match: {@code Files.list} makes no ordering promise, and
     * if a server ever serves the same player under two names the client has to pick the same one
     * every launch rather than flip between them.
     */
    static Optional<Path> cachedFile(Path dir, String key) {
        try (var entries = Files.list(dir)) {
            return entries.filter(Files::isRegularFile)
                    .filter(path -> belongsTo(path, key))
                    .filter(path -> !isEmpty(path))
                    .sorted()
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Whether a cache file is the image for this player.
     *
     * <p>The prefix test alone would also match the sidecars, so they are excluded by name. The
     * name is compared lower cased because the key is, and the server may serve any casing.
     */
    private static boolean belongsTo(Path path, String key) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith(key)
                && !name.endsWith(".etag")
                && !name.endsWith(".part")
                && !name.equals("manifest.json");
    }

    /**
     * Deletes any other image left over for this player under a name the server no longer uses.
     *
     * <p>Replacing {@code Tick838.png} with {@code Tick838.jpg} on the server would otherwise leave
     * both on disk, and {@link #cachedFile} has no way to tell which is current. Removing the
     * losers after a successful install keeps one file per player.
     */
    private static void dropSuperseded(Path dir, String key, String keep) {
        try (var entries = Files.list(dir)) {
            entries.filter(Files::isRegularFile)
                    .filter(path -> belongsTo(path, key))
                    .filter(path -> !path.getFileName().toString().equals(keep))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Leaving a stale file behind is harmless; it gets cleaned next time.
                        }
                    });
        } catch (IOException ignored) {
            // Best effort.
        }
    }

    private static boolean isEmpty(Path path) {
        try {
            return Files.size(path) == 0L;
        } catch (IOException e) {
            return false;
        }
    }

    static Path cacheDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("antichud").resolve("startup");
    }

    private static void fail(Throwable failure) {
        error = String.valueOf(failure.getMessage());
        System.out.println("[Antichud] Startup backgrounds unavailable (" + error + "); using what is on disk.");
        PHASE.set(Phase.FAILED);
    }

    /**
     * Writes via a sibling .part file and moves it into place.
     *
     * <p>Without this, a launch killed halfway through a download leaves a truncated JPEG that
     * looks valid to the size check above. {@link StartupBackground} caches its decode for the
     * whole session, so the player would stare at a half-drawn image until they deleted it by
     * hand. The move is atomic on every filesystem Minecraft runs on, so a reader either sees
     * the old file or the new one.
     */
    private static void move(Path from, Path to) {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            try {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException("could not install " + to, e);
            }
        }
    }

    private static String readText(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeText(Path path, String contents) {
        Path tmp = path.resolveSibling(path.getFileName() + ".part");
        try {
            Files.writeString(tmp, contents, StandardCharsets.UTF_8);
            move(tmp, path);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("could not write " + path, e);
        }
    }

    /** Small indirection so the worker can ask for the name without a hard dependency on the client. */
    static final class MinecraftUser {
        private MinecraftUser() {
        }

        static String name() {
            try {
                return net.minecraft.client.Minecraft.getInstance().getUser().getName();
            } catch (Throwable notReadyYet) {
                return null;
            }
        }
    }
}
