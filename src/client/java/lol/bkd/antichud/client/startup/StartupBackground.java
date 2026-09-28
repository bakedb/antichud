package lol.bkd.antichud.client.startup;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Objects;

/**
 * The per-player background for the loading overlay.
 *
 * <p>Drop a file named after the player into {@code assets/antichud/textures/gui/startup/} - for
 * example {@code assets/antichud/textures/gui/startup/ThatBakedBeans.jpg} - and that player gets
 * it behind the logo and the memory bar instead of the flat colour. Players without a file are
 * unaffected and keep the flat colour, so this is purely additive.
 *
 * <p>The image is read from the mod jar through the classloader rather than through the
 * {@link ResourceManager}, for the same reason {@code LoadingOverlayLogoTextureMixin} does it that
 * way: the loading overlay is on screen <i>because</i> the resource manager is still being built,
 * so it has nothing registered in it yet. The resource manager does eventually become usable, but
 * by then the overlay is already gone.
 */
public final class StartupBackground {

    private static final String FOLDER = "/assets/antichud/textures/gui/startup/";

    /**
     * Tried in order, so a player who drops in both a .png and a .jpg gets the .png.
     *
     * <p>These are the suffixes {@link #decode} can be handed, and what the JDK's ImageIO reads by
     * default: Minecraft's own {@code NativeImage.read} calls {@code PngInfo.validateHeader} and
     * rejects anything that is not a PNG, so it cannot be used for the .jpg the folder invites.
     *
     * <p>A deliberate subset - ImageIO also reads .wbmp, which is a 1-bit mobile format with no
     * business being a background. Note that ImageIO hands back only the first frame of an
     * animated .gif and the first page of a multi-page .tiff, so that is what gets drawn.
     */
    private static final String[] EXTENSIONS = { ".png", ".jpg", ".jpeg", ".bmp", ".gif", ".tif", ".tiff" };

    /**
     * blur and clamp decide the sampler: linear filtering keeps a photo from looking like a grid of
     * blocks once it is scaled to the window, and clamping stops the cover-fit oversampling from
     * wrapping around to the opposite edge. MipmapStrategy is unused on this path -
     * {@code ReloadableTexture.apply} only reads blur and clamp - and the logo's MEAN is a safe
     * neutral choice.
     */
    private static final TextureMetadataSection BACKGROUND_METADATA =
            new TextureMetadataSection(true, true, MipmapStrategy.MEAN, 0.0F);

    /** The name {@link #resolvedId} and friends were resolved for, or null before the first call. */
    private static String resolvedName;
    /** Texture id of the uploaded background, or null when this player has no background file. */
    private static Identifier resolvedId;
    private static int resolvedWidth;
    private static int resolvedHeight;
    private static boolean resolved;

    private StartupBackground() {
    }

    /**
     * Draws the player's background, scaled to cover the whole screen and centred on it.
     *
     * <p>Safe to call every frame: the file is looked up and the image decoded at most once.
     *
     * @return true when a background was found and drawn, so the caller can skip its own fill
     */
    public static boolean render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Identifier background = resolve(minecraft, minecraft.getUser().getName());
        if (background == null) {
            return false;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        // Cover, not fit: scale by whichever axis overflows more, so the top and bottom of a tall
        // screenshot are still filled instead of leaving bars where the colour would show through.
        float scale = Math.max((float) screenWidth / resolvedWidth, (float) screenHeight / resolvedHeight);
        int width = Math.max(1, Math.round(resolvedWidth * scale));
        int height = Math.max(1, Math.round(resolvedHeight * scale));

        graphics.blit(RenderPipelines.GUI_TEXTURED, background,
                (screenWidth - width) / 2, (screenHeight - height) / 2,
                0.0F, 0.0F, width, height,
                resolvedWidth, resolvedHeight, resolvedWidth, resolvedHeight, 0xFFFFFFFF);
        return true;
    }

    /**
     * Looks the background up once and uploads it, caching the answer for the rest of the session.
     *
     * <p>Re-resolved only if the name changes, which in practice never happens - but if it does,
     * the previous upload is released so it does not sit on the GPU until the game exits.
     */
    private static synchronized Identifier resolve(Minecraft minecraft, String username) {
        String name = sanitise(username);
        if (resolved && Objects.equals(resolvedName, name)) {
            return resolvedId;
        }

        if (resolvedId != null) {
            minecraft.getTextureManager().release(resolvedId);
        }

        resolved = true;
        resolvedName = name;
        resolvedId = null;
        resolvedWidth = 0;
        resolvedHeight = 0;

        if (name == null) {
            return null;
        }

        String file = findFile(name);
        if (file == null) {
            return null;
        }

        try {
            // Measured with a throwaway decode rather than handed to the texture: apply() closes
            // whatever NativeImage it is given, so keeping it would not save anything, and the
            // texture has to decode again on the next reload regardless.
            NativeImage image = decode(file);
            resolvedWidth = image.getWidth();
            resolvedHeight = image.getHeight();
            image.close();

            // Lower cased because an Identifier path may only hold [a-z0-9/._-] and player names
            // are not lower case. The id is only a lookup key - loadContents ignores it and reads
            // the jar - so folding the case costs nothing, and two names differing only in case
            // are the same player as far as Minecraft is concerned anyway.
            Identifier id = Identifier.fromNamespaceAndPath("antichud",
                    "textures/gui/startup/" + file.toLowerCase(Locale.ROOT));
            minecraft.getTextureManager().registerAndLoad(id, new StartupTexture(id, file));
            resolvedId = id;
        } catch (Throwable failure) {
            // A file we ship and cannot read is our bug, not the player's, and it is not worth a
            // crash on the way into the game. Fall back to the plain colour and say so.
            System.out.println("[Antichud] Startup background '" + file + "' could not be loaded: " + failure);
        }
        return resolvedId;
    }

    /**
     * Finds the background file for a sanitised name, or null if this player has none.
     *
     * <p>The exact name wins; the all-lowercase name is tried too, because a launcher reporting
     * "thatbakedbeans" should still find "ThatBakedBeans.jpg".
     */
    private static String findFile(String name) {
        for (String candidate : new String[] { name, name.toLowerCase(Locale.ROOT) }) {
            for (String extension : EXTENSIONS) {
                String file = candidate + extension;
                if (exists(file)) {
                    return file;
                }
            }
        }
        return null;
    }

    private static boolean exists(String file) {
        try (InputStream in = StartupBackground.class.getResourceAsStream(FOLDER + file)) {
            return in != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static NativeImage decode(String file) {
        try (InputStream in = StartupBackground.class.getResourceAsStream(FOLDER + file)) {
            if (in == null) {
                throw new IOException("not in the mod jar");
            }
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IOException("no ImageIO reader for it");
            }

            // getRGB in one call rather than per pixel: this runs on the render thread while the
            // loading overlay is up, and a 1080p background is two million pixels.
            int width = image.getWidth();
            int height = image.getHeight();
            int[] argb = image.getRGB(0, 0, width, height, null, 0, width);

            NativeImage nativeImage = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    nativeImage.setPixel(x, y, argb[y * width + x]);
                }
            }
            return nativeImage;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to decode " + FOLDER + file, e);
        }
    }

    /**
     * Reduces a username to something that can only ever name a file inside the startup folder.
     *
     * <p>Anything outside {@code A-Z a-z 0-9 _ -} becomes an underscore, which in particular
     * removes the slashes, dots and percent signs that a name like {@code ../../secret} or
     * {@code %2e%2e} would need to climb out of the folder. A name that sanitises to nothing gets
     * no background at all rather than matching a file by accident.
     */
    private static String sanitise(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        StringBuilder safe = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            safe.append((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' ? c : '_');
        }
        return safe.toString();
    }

    /**
     * A texture that decodes from the mod jar and ignores the resource manager it is handed.
     *
     * <p>SimpleTexture is the right base: ReloadableTexture re-runs loadContents for every texture
     * it already holds whenever a reload starts, so overriding that one method is what keeps the
     * background working after a resource pack change - the reload being the very thing that draws
     * the loading overlay a second time.
     */
    private static final class StartupTexture extends SimpleTexture {
        private final String file;

        StartupTexture(Identifier id, String file) {
            super(id);
            this.file = file;
        }

        @Override
        public TextureContents loadContents(ResourceManager resourceManager) {
            return new TextureContents(decode(this.file), BACKGROUND_METADATA);
        }
    }
}
