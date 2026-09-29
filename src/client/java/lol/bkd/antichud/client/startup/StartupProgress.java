package lol.bkd.antichud.client.startup;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The "Downloading assets..." card drawn over the loading overlay while the startup background is
 * being fetched.
 *
 * <p><b>Why this does not use the vanilla font.</b> {@code Minecraft.font} is constructed at
 * {@code Minecraft.java:536}, but the {@code FontManager} that gives it its glyph atlas is
 * registered as a resource reload listener on the next line - and the initial reload is exactly
 * what is still in flight while the loading overlay is up. Drawing with it at this point either
 * renders nothing or throws, and there is no way to test for it cheaply. A 5x7 bitmap font
 * embedded here has no such dependency, draws in a few hundred {@code fill} calls, and cannot fail.
 *
 * <p>Everything is {@code fill} rectangles, so there is no texture, no blend state and no
 * dependency on the resource manager - the same constraint that makes the logo go through
 * {@code LoadingOverlayLogoTextureMixin}.
 */
public final class StartupProgress {

    private static final int GLYPH_W = 5;
    private static final int GLYPH_H = 7;
    /** One blank column between glyphs, in unscaled pixels. */
    private static final int TRACKING = 1;

    private static final int CARD_BG = 0xF01A1A2E;
    private static final int CARD_BORDER = 0xFF2E3159;
    private static final int TEXT = 0xFFE8E8F0;
    private static final int BAR_TRACK = 0x33FFFFFF;
    private static final int BAR_FILL = 0xFF8899FF;

    /** Width of the sweep when the total size is unknown, as a fraction of the bar. */
    private static final float INDETERMINATE_FRACTION = 0.28F;
    private static final long SWEEP_MS = 1200L;

    private StartupProgress() {
    }

    /**
     * Draws the card.
     *
     * @param progress 0..1, or negative when the size is not known yet
     */
    public static void draw(GuiGraphics graphics, int screenWidth, int screenHeight,
                            StartupAssets.Phase phase, float progress) {

        String message = switch (phase) {
            case CHECKING -> "Checking for your background";
            case DOWNLOADING -> "Downloading assets";
            default -> "Preparing";
        };

        // Bottom right, clear of the Mojang logo, which vanilla draws centred and after this.
        //
        // This card used to be centred as well, which meant the two sat exactly on top of each
        // other and the logo won - it is drawn later in the same method, so it covered the text
        // rather than being covered by it. A corner also leaves the background photograph
        // visible, which is the whole point of the wait.
        int margin = Math.max(4, Math.min(12, screenWidth / 40));

        // Sized off the available width, but only ever 1x on a screen that is not genuinely wide.
        // At 2x the 5x7 glyphs are twice the size they need to be for legibility, and a card that
        // big in a corner reads as a notification rather than as a line of status text.
        int advance = GLYPH_W + TRACKING;
        int wanted = message.length() * advance * 2;
        int scale = Math.max(1, Math.min(2, (screenWidth / 2 - 2 * margin) / Math.max(1, wanted)));
        int textWidth = message.length() * advance * scale;
        int barHeight = 3 * scale;
        int gap = 4 * scale;
        int pad = 5 * scale;

        int cardWidth = textWidth + 2 * pad;
        int cardHeight = GLYPH_H * scale + gap + barHeight + 2 * pad;
        // Clamped so an unusually small or short overlay cannot push the card off the edge.
        int left = Math.max(0, screenWidth - cardWidth - margin);
        int top = Math.max(0, screenHeight - cardHeight - margin);

        // No scrim. The card used to sit over the middle of the screen and dim everything to
        // argue that it was a screen in its own right; in a corner over a photograph that would
        // only darken the thing the player is waiting to see. The card background is 94% opaque,
        // so the text reads against any image without one.
        graphics.fill(left, top, left + cardWidth, top + cardHeight, CARD_BG);
        graphics.fill(left, top, left + cardWidth, top + 1, CARD_BORDER);
        graphics.fill(left, top + cardHeight - 1, left + cardWidth, top + cardHeight, CARD_BORDER);
        graphics.fill(left, top, left + 1, top + cardHeight, CARD_BORDER);
        graphics.fill(left + cardWidth - 1, top, left + cardWidth, top + cardHeight, CARD_BORDER);

        draw(graphics, message, left + pad, top + pad, scale);

        int barLeft = left + pad;
        int barRight = left + cardWidth - pad;
        int barTop = top + pad + GLYPH_H * scale + gap;
        graphics.fill(barLeft, barTop, barRight, barTop + barHeight, BAR_TRACK);

        if (progress <= 0.0F) {
            // Sweep rather than sit at 0%.
            //
            // This covers two cases that look identical on screen: a response with no
            // Content-Length, and one whose headers have arrived but whose body has not started.
            // The second is the common one in practice - a server that sends headers immediately
            // and then takes a moment to produce the file - and a determinate bar pinned at 0%
            // for that whole wait is indistinguishable from a hang. Once bytes start arriving
            // the bar becomes determinate and has something honest to show.
            float sweep = ((System.currentTimeMillis() % SWEEP_MS) / (float) SWEEP_MS);
            int span = Math.max(8, Math.round((barRight - barLeft) * INDETERMINATE_FRACTION));
            int x = barLeft + Math.round((barRight - barLeft - span) * sweep);
            graphics.fill(x, barTop, Math.min(barRight, x + span), barTop + barHeight, BAR_FILL);
        } else {
            int filled = Math.round((barRight - barLeft) * progress);
            graphics.fill(barLeft, barTop, barLeft + filled, barTop + barHeight, BAR_FILL);
        }
    }

    private static void draw(GuiGraphics graphics, String value, int x, int y, int scale) {
        int cursor = x;
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toUpperCase(value.charAt(i));
            int[] rows = glyph(c);
            if (rows != null) {
                for (int row = 0; row < GLYPH_H; row++) {
                    int bits = rows[row];
                    // Walk the five columns so each row costs only the set bits, not 5 fills.
                    for (int col = 0; col < GLYPH_W; col++) {
                        if ((bits & (1 << (GLYPH_W - 1 - col))) != 0) {
                            graphics.fill(cursor + col * scale, y + row * scale,
                                    cursor + (col + 1) * scale, y + (row + 1) * scale, TEXT);
                        }
                    }
                }
            }
            cursor += (GLYPH_W + TRACKING) * scale;
        }
    }

    private static int[] glyph(char c) {
        if (c < FIRST || c > LAST) {
            return c == ' ' ? BLANK : null;
        }
        return GLYPHS[c - FIRST];
    }

    private static final char FIRST = ' ';
    private static final char LAST = 'Z';
    private static final int[] BLANK = {0, 0, 0, 0, 0, 0, 0};

    /**
     * 5x7 glyphs for space through Z, one mask per row, with bit 4 of each mask being the
     * leftmost column. Indexed by {@code c - FIRST}, so the table has to stay in ASCII order.
     */
    private static final int[][] GLYPHS = {
            {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}, // space
            {0x00, 0x00, 0x00, 0x5F, 0x00, 0x00, 0x00}, // !
            {0x00, 0x00, 0x07, 0x00, 0x07, 0x00, 0x00}, // "
            {0x14, 0x7F, 0x14, 0x7F, 0x14, 0x00, 0x00}, // #
            {0x24, 0x2A, 0x7F, 0x2A, 0x12, 0x00, 0x00}, // $
            {0x23, 0x13, 0x08, 0x64, 0x62, 0x00, 0x00}, // %
            {0x36, 0x49, 0x55, 0x22, 0x50, 0x00, 0x00}, // &
            {0x00, 0x05, 0x03, 0x00, 0x00, 0x00, 0x00}, // '
            {0x00, 0x1C, 0x22, 0x41, 0x00, 0x00, 0x00}, // (
            {0x00, 0x41, 0x22, 0x1C, 0x00, 0x00, 0x00}, // )
            {0x14, 0x08, 0x3E, 0x08, 0x14, 0x00, 0x00}, // *
            {0x08, 0x08, 0x3E, 0x08, 0x08, 0x00, 0x00}, // +
            {0x00, 0x50, 0x30, 0x00, 0x00, 0x00, 0x00}, // ,
            {0x08, 0x08, 0x08, 0x08, 0x08, 0x00, 0x00}, // -
            {0x00, 0x60, 0x60, 0x00, 0x00, 0x00, 0x00}, // .
            {0x20, 0x10, 0x08, 0x04, 0x02, 0x00, 0x00}, // /
            {0x3E, 0x51, 0x49, 0x45, 0x3E, 0x00, 0x00}, // 0
            {0x00, 0x42, 0x7F, 0x40, 0x00, 0x00, 0x00}, // 1
            {0x42, 0x61, 0x51, 0x49, 0x46, 0x00, 0x00}, // 2
            {0x21, 0x41, 0x45, 0x4B, 0x31, 0x00, 0x00}, // 3
            {0x18, 0x14, 0x12, 0x7F, 0x10, 0x00, 0x00}, // 4
            {0x27, 0x45, 0x45, 0x45, 0x39, 0x00, 0x00}, // 5
            {0x3C, 0x4A, 0x49, 0x49, 0x30, 0x00, 0x00}, // 6
            {0x01, 0x71, 0x09, 0x05, 0x03, 0x00, 0x00}, // 7
            {0x36, 0x49, 0x49, 0x49, 0x36, 0x00, 0x00}, // 8
            {0x06, 0x49, 0x49, 0x29, 0x1E, 0x00, 0x00}, // 9
            {0x00, 0x36, 0x36, 0x00, 0x00, 0x00, 0x00}, // :
            {0x00, 0x56, 0x36, 0x00, 0x00, 0x00, 0x00}, // ;
            {0x08, 0x14, 0x22, 0x41, 0x00, 0x00, 0x00}, // <
            {0x14, 0x14, 0x14, 0x14, 0x14, 0x00, 0x00}, // =
            {0x00, 0x41, 0x22, 0x14, 0x08, 0x00, 0x00}, // >
            {0x02, 0x01, 0x51, 0x09, 0x06, 0x00, 0x00}, // ?
            {0x32, 0x49, 0x79, 0x41, 0x3E, 0x00, 0x00}, // @
            {0x7E, 0x11, 0x11, 0x11, 0x7E, 0x00, 0x00}, // A
            {0x7F, 0x49, 0x49, 0x49, 0x36, 0x00, 0x00}, // B
            {0x3E, 0x41, 0x41, 0x41, 0x22, 0x00, 0x00}, // C
            {0x7F, 0x41, 0x41, 0x22, 0x1C, 0x00, 0x00}, // D
            {0x7F, 0x49, 0x49, 0x49, 0x41, 0x00, 0x00}, // E
            {0x7F, 0x09, 0x09, 0x09, 0x01, 0x00, 0x00}, // F
            {0x3E, 0x41, 0x49, 0x49, 0x7A, 0x00, 0x00}, // G
            {0x7F, 0x08, 0x08, 0x08, 0x7F, 0x00, 0x00}, // H
            {0x00, 0x41, 0x7F, 0x41, 0x00, 0x00, 0x00}, // I
            {0x20, 0x40, 0x41, 0x3F, 0x01, 0x00, 0x00}, // J
            {0x7F, 0x08, 0x14, 0x22, 0x41, 0x00, 0x00}, // K
            {0x7F, 0x40, 0x40, 0x40, 0x40, 0x00, 0x00}, // L
            {0x7F, 0x02, 0x0C, 0x02, 0x7F, 0x00, 0x00}, // M
            {0x7F, 0x04, 0x08, 0x10, 0x7F, 0x00, 0x00}, // N
            {0x3E, 0x41, 0x41, 0x41, 0x3E, 0x00, 0x00}, // O
            {0x7F, 0x09, 0x09, 0x09, 0x06, 0x00, 0x00}, // P
            {0x3E, 0x41, 0x51, 0x21, 0x5E, 0x00, 0x00}, // Q
            {0x7F, 0x09, 0x19, 0x29, 0x46, 0x00, 0x00}, // R
            {0x46, 0x49, 0x49, 0x49, 0x31, 0x00, 0x00}, // S
            {0x01, 0x01, 0x7F, 0x01, 0x01, 0x00, 0x00}, // T
            {0x3F, 0x40, 0x40, 0x40, 0x3F, 0x00, 0x00}, // U
            {0x1F, 0x20, 0x40, 0x20, 0x1F, 0x00, 0x00}, // V
            {0x3F, 0x40, 0x38, 0x40, 0x3F, 0x00, 0x00}, // W
            {0x63, 0x14, 0x08, 0x14, 0x63, 0x00, 0x00}, // X
            {0x07, 0x08, 0x70, 0x08, 0x07, 0x00, 0x00}, // Y
            {0x61, 0x51, 0x49, 0x45, 0x43, 0x00, 0x00}  // Z
    };
}
