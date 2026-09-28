package lol.bkd.antichud.update;

/**
 * Opens a link in the system browser.
 *
 * <p>Minecraft 26.3 emptied {@code Util.OS} and moved the actual opening to {@code Blaze3D}, so
 * the call site differs per version. Keeping it in one place means the rest of the update code
 * stays version independent.
 */
public final class LinkOpener {
    private LinkOpener() {
    }

    public static void open(String url) {
        //? if >=26.3 {
        /*com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(url));
        *///?} else {
        net.minecraft.util.Util.getPlatform().openUri(url);
        //?}
    }
}
