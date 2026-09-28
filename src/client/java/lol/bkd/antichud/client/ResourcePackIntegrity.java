package lol.bkd.antichud.client;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Detects x-ray texture packs.
 *
 * <p>The mod also <i>prevents</i> x-ray by force-enabling a resource pack that re-supplies the
 * vanilla stone, deepslate and netherrack textures at the top of the pack stack, so a texture pack
 * cannot make those three blocks see-through. This class is the independent detection layer.
 *
 * <p>It deliberately does <b>not</b> hash the textures. The forced pack ships genuine vanilla
 * bytes, so "the texture equals vanilla" is true both when the pack is working and when it has been
 * bypassed - the hash could never fail. Hashing is also the wrong question: a player using an
 * ordinary texture pack has legitimately changed these textures, and flagging that is a false
 * positive.
 *
 * <p>What an x-ray pack <i>must</i> do is make the blocks between you and the ore invisible. So the
 * test is opacity, not identity: an obstructing block texture is flagged when it is fully
 * transparent. Every block below is fully opaque in vanilla (verified against 1.21.11 and 26.3),
 * so this cannot fire on a legitimate texture pack, which recolours blocks but keeps them opaque.
 */
public class ResourcePackIntegrity {
    /**
     * Blocks that are solid, fully opaque, and commonly sit between a player and ore. Deliberately
     * excludes anything vanilla renders translucent - glass, leaves and ice are transparent or
     * partly transparent in vanilla and would fire on every launch.
     */
    private static final List<String> OPAQUE_BLOCKS = List.of(
            // overworld stone family
            "stone", "andesite", "diorite", "granite", "tuff", "calcite", "deepslate",
            "cobblestone", "mossy_cobblestone", "cobbled_deepslate", "stone_bricks",
            "mossy_stone_bricks", "deepslate_bricks", "tuff_bricks",
            "chiseled_stone_bricks", "chiseled_deepslate", "chiseled_tuff_bricks",
            "smooth_stone", "polished_andesite", "polished_diorite", "polished_granite",
            "polished_deepslate", "smooth_basalt", "polished_blackstone",
            "polished_blackstone_bricks", "blackstone",
            // soil and rubble
            "dirt", "coarse_dirt", "rooted_dirt", "grass_block_top", "grass_block_side",
            "dirt_path_top", "gravel", "sand", "red_sand", "clay",
            "soul_sand", "soul_soil", "podzol_top", "podzol_side", "mycelium_top",
            // nether
            "netherrack", "nether_bricks", "red_nether_bricks", "basalt_side", "basalt_top",
            "polished_basalt_side", "polished_basalt_top", "bone_block_side", "bone_block_top",
            "end_stone_bricks", "end_stone", "magma"
    );

    /**
     * Checks the supplied resource manager, which must already have its packs applied - that is why
     * this is driven from a resource reload listener rather than from mod init.
     *
     * @return true if every checked block is still opaque
     */
    public static boolean verify(ResourceManager resourceManager, UUID playerUuid, String username) {
        if (resourceManager == null) {
            return false;
        }

        int checked = 0;
        boolean clean = true;

        for (String block : OPAQUE_BLOCKS) {
            Identifier location = Identifier.fromNamespaceAndPath("minecraft", "textures/block/" + block + ".png");

            boolean fullyTransparent;
            try (InputStream is = resourceManager.open(location)) {
                if (is == null) {
                    // Not every name exists in every version, and a missing file is not tampering.
                    continue;
                }
                checked++;
                fullyTransparent = isFullyTransparent(is);
            } catch (Exception e) {
                // An unreadable or unsupported image is not evidence of an x-ray pack.
                continue;
            }

            if (fullyTransparent) {
                clean = false;
                LogSender.sendTamperProtectionReport(playerUuid, username,
                        "Block texture is fully transparent (x-ray indicator): " + block);
            }
        }

        if (clean) {
            System.out.println("[Antichud] X-ray check OK: " + checked
                    + " obstructing block textures are still opaque.");
        }
        return clean;
    }

    /**
     * True only when every pixel is fully transparent. Uses ImageIO rather than a hand-rolled PNG
     * reader on purpose: many vanilla block textures are 4-bit palette PNGs, and a pack can hide a
     * fully transparent entry in a palette's tRNS chunk, which a naive RGBA-only reader would miss.
     */
    private static boolean isFullyTransparent(InputStream png) throws Exception {
        BufferedImage image = ImageIO.read(png);
        if (image == null) {
            throw new IllegalArgumentException("not a readable image");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) != 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
