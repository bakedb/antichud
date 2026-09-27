package lol.bkd.antichud.update;

import lol.bkd.antichud.update.screen.UpdateCheckScreen;
import lol.bkd.antichud.update.screen.UpdateRequiredScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds the result of the GitHub update check and gates multiplayer on it.
 *
 * <p>The rule is deliberately narrow: a player is only stopped when we positively know a newer
 * release exists. If the check is still running the connection attempt is held for a moment,
 * and if the check could not be completed the player is let through, because being unable to
 * reach GitHub is not evidence of anything.
 */
public final class UpdateGate {
    public enum Status {
        /** The check is still in flight. */
        CHECKING,
        /** The installed build is the newest published release. */
        UP_TO_DATE,
        /** A newer release exists, multiplayer requires updating. */
        UPDATE_AVAILABLE,
        /** The check failed, the version could not be verified. */
        UNKNOWN
    }

    private static final String MOD_ID = "antichud";
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private static volatile Status status = Status.CHECKING;
    private static volatile UpdateChecker.UpdateInfo info;
    private static volatile String failure;

    private UpdateGate() {
    }

    /** Version of the antichud jar that is currently running. */
    public static String installedVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    /** Kicks off the check once per launch. Safe to call from anywhere on the client thread. */
    public static void startCheck() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        runCheck();
    }

    /** Runs the check again, used by the "check again" button. */
    public static void recheck() {
        STARTED.set(true);
        runCheck();
    }

    public static Status status() {
        return status;
    }

    public static UpdateChecker.UpdateInfo info() {
        return info;
    }

    public static String failure() {
        return failure;
    }

    /** True when this client must update before it is allowed onto a multiplayer server. */
    public static boolean blocksMultiplayer() {
        return status == Status.UPDATE_AVAILABLE;
    }

    /**
     * Guards a connection attempt.
     *
     * @param parent screen the connection was started from, used to return the player there
     * @param resume  re-runs the connection once the check says it is allowed
     * @return true when the caller may connect right away, false when it was intercepted
     */
    public static boolean allowConnect(Screen parent, Runnable resume) {
        Minecraft minecraft = Minecraft.getInstance();
        UpdateChecker.UpdateInfo current = info;

        switch (status) {
            case UPDATE_AVAILABLE -> {
                System.out.println("[Antichud] Blocked a connection, antichud " + installedVersion()
                        + " is older than " + (current == null ? "the latest release" : current.latestVersion()) + ".");
                minecraft.setScreen(new UpdateRequiredScreen(parent));
                return false;
            }
            case CHECKING -> {
                System.out.println("[Antichud] Connection held until the update check finishes.");
                minecraft.setScreen(new UpdateCheckScreen(parent, resume));
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    /** Shown to a player who somehow got onto a server while outdated. */
    public static Component blockedReason() {
        UpdateChecker.UpdateInfo current = info;
        String latest = current == null ? "a newer version" : current.latestVersion();
        return Component.literal("antichud " + installedVersion() + " is out of date. Update to " + latest
                + " from " + UpdateChecker.RELEASES_PAGE + ", or remove the mod to play multiplayer.");
    }

    private static void runCheck() {
        String version = installedVersion();
        status = Status.CHECKING;
        info = null;
        failure = null;

        UpdateChecker.checkAsync(version).whenComplete((result, error) -> {
            if (error != null || result == null) {
                failure = error == null ? "no result" : String.valueOf(error.getMessage());
                status = Status.UNKNOWN;
                System.out.println("[Antichud] Update check failed (" + failure + "), multiplayer is not restricted.");
                return;
            }

            info = result;
            if (result.updateAvailable()) {
                status = Status.UPDATE_AVAILABLE;
                System.out.println("[Antichud] Update available, installed " + result.currentVersion()
                        + " but " + result.latestVersion() + " is published. " + result.releaseUrl());
            } else {
                status = Status.UP_TO_DATE;
                System.out.println("[Antichud] Running the latest release (" + result.latestVersion() + ").");
            }
        });
    }
}
